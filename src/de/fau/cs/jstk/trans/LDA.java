/*
	Copyright (c) 2009-2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer
		Tobias Bocklet

	This file is part of the Java Speech Toolkit (JSTK).

	The JSTK is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	The JSTK is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with the JSTK. If not, see <http://www.gnu.org/licenses/>.
*/
package de.fau.cs.jstk.trans;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.BasicConfigurator;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.LabelFrameInputStream;
import de.fau.cs.jstk.io.SampleInputStream;
import de.fau.cs.jstk.stat.Sample;
import de.fau.cs.jstk.util.Arithmetics;
import de.fau.cs.jstk.util.LabelTranslator;
import de.fau.cs.jstk.util.Pair;

public class LDA extends Projection {

	/** global stats for all seen data */
	private Accumulator global = null;
	
	/** class dependent stats */
	private HashMap<Short, Accumulator> stats = new HashMap<Short, Accumulator>();
	
	/** for internal purposes: remember inverse(Sw) */
	private double [][] Swi = null;
	
	/** for internal purposes: remember ev(inv(Sw) Sb) */
	private double [] evals = null;
	
	/**
	 * Allocate a new LDA for the given feature dimension
	 * @param fd
	 */
	public LDA(int fd) {
		super(fd);
		global = new Accumulator(fd);
	}
	
	/**
	 * Accumulate a List of Samples
	 * @param list
	 */
	public void accumulate(List<Sample> list) {
		for (Sample s : list)
			accumulate(s.c, s.x);
	}
	
	/**
	 * Accumulate a single Sample
	 * @param s
	 */
	public void accumulate(Sample s) {
		accumulate(s.c, s.x);
	}
	
	/** 
	 * Accumulate an observation for given class
	 * @param c class
	 * @param x observation
	 */
	public void accumulate(short c, double [] x) {
		// build up global stats
		global.accumulate(x);
		
		// make sure we have an accumulator
		if (!stats.containsKey(c))
			stats.put(c, new Accumulator(x.length));
		
		// build up class dependent stats
		stats.get(c).accumulate(x);
	}
	
	/**
	 * Estimate the projection matrix. The resulting projection will reduce the
	 * dimension of the input data at least to (number of classes - 1). If
	 * desired, specify manual priors for the classes.
	 * @param priors HashMap(ClassID->prior) to specify manual priors, or null
	 */
	public void estimate(HashMap<Short, Double> priors) {
		// compute priors if necessary
		if (priors == null) {
			priors = new HashMap<Short, Double>();
			for (Entry<Short, Accumulator> e : stats.entrySet())
				priors.put(e.getKey(), (double) e.getValue().getCount() / global.getCount());
		}
		
		fd = global.getFd();
		double [] gm = global.getMean();
		
		// build up within-class-covariance (lower triangular)
		double [] sw = new double [fd * (fd + 1) / 2];
		
		// build up between-class-covariance (lower triangular)
		double [] sb = new double [fd * (fd + 1) / 2];
		
		for (Entry<Short, Accumulator> e : stats.entrySet()) {
			double p = priors.get(e.getKey());
			double [] m = e.getValue().getMean();
			double [] c = e.getValue().getCovariance();
			
			// sum_k p_k K_k
			for (int i = 0; i < sw.length; ++i)
				sw[i] += p * c[i];
			
			// sum_k p_k (m_k - m)(m_k - m)^T 
			int k = 0;
			for (int i = 0; i < m.length; ++i)
				for (int j = 0; j <= i; ++j)
					sb[k++] += p * (m[i] - gm[i]) * (m[j] - gm[j]);
		}
		
		// build up the matrices for JAMA use
		Matrix Sw = new Matrix(fd, fd);
		Matrix Sb = new Matrix(fd, fd);
		int k = 0;
		for (int i = 0; i < fd; ++i) {
			for (int j = 0; j <= i; ++j) {
				Sw.set(i, j, sw[k]);
				Sw.set(j, i, sw[k]);
				
				Sb.set(i, j, sb[k]);
				Sb.set(j, i, sb[k]);
				
				k++;
			}
		}
		
		// compute pseudo inverse to avoid regularization issues
		Matrix Swi = new Matrix(Arithmetics.pinv(Sw.getArray(), 1e-12));
		this.Swi = Swi.getArray();

		// eig(p-inv(Sw) Sb)
		EigenvalueDecomposition eig = new EigenvalueDecomposition(Swi.times(Sb));
		
		// save the eigen vectors (use transposed for java convenience)
		double [][] vhelp = eig.getV().transpose().getArray();
		LinkedList<Pair<double [], Double>> sortedEV = new LinkedList<Pair<double [], Double>>();
		for (int i = 0; i < fd; ++i)
			sortedEV.add(new Pair<double [], Double>(vhelp[i], eig.getD().get(i, i)));
		
		// sort strongest EV first
		Collections.sort(sortedEV, new Comparator<Pair<double [], Double>>() {
			public int compare(Pair<double[], Double> o1,
					Pair<double[], Double> o2) {
				return (int) Math.signum(o2.b - o1.b);
			}
		});
		
		// save the global mean
		mean = global.getMean();
		
		// keep (num classes - 1) eigenvectors
		int numv = Math.min(stats.size() - 1, sortedEV.size());
		proj = new double [numv][];
		evals = new double [numv];
		Iterator<Pair<double [], Double>> it = sortedEV.iterator();
		for (int i = 0; i < numv; ++i) {
			Pair<double [], Double> p = it.next();
			proj[i] = p.a;
			evals[i] = p.b;
		}
	}
	
	public double [] getEigenvalues() {
		return evals;
	}
	
	/**
	 * Produce a String representation of the LDA containing both Projection and
	 * LDA information.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		
		sb.append("Projection = \n");
		sb.append(super.toString());
		
		sb.append("LDA = \n");
		sb.append("Swi = \n");
		for (double [] d : Swi)
			sb.append(Arrays.toString(d) + "\n");
		sb.append("evals = " + Arrays.toString(evals));
		
		return sb.toString();
	}

	public static final String SYNOPSIS =
		"sikoried, 2/2/2011\n" +
		"Compute LDA using (regularized) pseudo-inverse (SVD) and save the resulting\n" +
		"transformation y = A * (x-m) to the given projection file.\n" +
		"usage: transformations.LDA proj list1 [list2 ...] indir\n" +
		"  proj  : output file for projection (Frame format)\n" +
		"  list  : file list(s); in case of single list expecting binary sample format instead of frame.\n" +
		"  indir : directory where the input files are located (use . for current dir)\n";
	
	public static void main(String[] args) throws IOException {
		BasicConfigurator.configure();
		
		if (args.length < 2) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		String outf = args[0];
		String indir = args[args.length-1] + System.getProperty("file.separator");
		
		// copy list file(s)
		String [] lifs = new String [args.length - 2];
		System.arraycopy(args, 1, lifs, 0, lifs.length);
		
		LabelTranslator lt = new LabelTranslator("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
		
		LDA lda = null;
		
		if (lifs.length == 1) {
			// only single list: assume binary frame format
			LineNumberReader br = new LineNumberReader(new FileReader(lifs[0]));
			String line;
			while ((line = br.readLine()) != null) {
				
				String [] spl = line.split("\\s+");
				
				if (spl.length == 1) {
					SampleInputStream sis = new SampleInputStream(new FileInputStream(indir + line));
					Sample s;
					while ((s = sis.read()) != null) {
						if (lda == null)
							lda = new LDA(s.x.length);
						lda.accumulate(s);
					}
				} else if (spl.length == 2) {
					FrameInputStream fis = new FrameInputStream(new File(indir + spl[0]));
					FileInputStream lis = new FileInputStream(indir + spl[1]);
					LabelFrameInputStream lfis = new LabelFrameInputStream(lis, fis);
					
					double [] x = new double [lfis.getFrameSize()];
					
					// init LDA
					if (lda == null)
						lda = new LDA(x.length);
					
					while (lfis.read(x)) {
						int l = lt.labelToId(lfis.getLabel());
						lda.accumulate((short) l, x);
					}
					
					fis.close();
					lis.close();
				} else
					throw new IOException("invalid line " + br.getLineNumber() + " : " + line);
			}
			
			br.close();
		} else {
			// basic case: individual list file per class
			for (int i = 0; i < lifs.length; ++i) {
				BufferedReader br = new BufferedReader(new FileReader(lifs[i]));
				String line;
				while ((line = br.readLine()) != null) {
					FrameInputStream fis = new FrameInputStream(new File(indir + line));
					double [] buf = new double [fis.getFrameSize()];
					
					if (lda == null)
						lda = new LDA(buf.length);
					
					while (fis.read(buf)) 				
						lda.accumulate((short) i, buf);
				}
				
				br.close();
			}
		}
		
		lda.estimate(null);
		lda.save(new File(outf));
	}
}
