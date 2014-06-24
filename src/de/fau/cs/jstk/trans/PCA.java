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
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.stat.Sample;
import de.fau.cs.jstk.util.Pair;

/**
 * Compute and apply a principal components analysis (PCA). The input data needs
 * to be (at least) zero-means normalized, covariance normalization might help,
 * too.
 * 
 * @author sikoried
 * 
 */
public class PCA extends Projection {
	/** Global statistics of the data */
	private Accumulator global = null;

	private double [] evals = null;
	
	/**
	 * Allocate a new PCA for the given feature dimension
	 * @param fd
	 */
	public PCA(int fd) {
		super(fd);
		this.global = new Accumulator(fd);
	}

	/**
	 * Accumulate the given frame source for a later transformation computation.
	 * If no previous accumulator is found, a new one is generated.
	 * 
	 * @param source
	 * @throws IOException
	 */
	public void accumulate(FrameSource source) throws IOException {
		double[] buf = new double[source.getFrameSize()];
		while (source.read(buf))
			accumulate(buf);
	}

	/**
	 * Accumulate data from the given list (for smaller applications)
	 * 
	 * @param data
	 */
	public void accumulate(List<Sample> data) {
		try {
			for (Sample s : data)
				accumulate(s.x);
		} catch (Exception e) {
			// there can't be any exception...
			System.err.println("PCA.accumulate(List<Sample>): Unexpected Exception!");
			System.err.println(e.toString());
		}
	}

	/**
	 * Accumulate the given sample (most basic call, used by other accumulate
	 * calls).
	 * 
	 * @param sample
	 */
	public void accumulate(double [] sample) throws IOException {
		global.accumulate(sample);
	}

	/**
	 * Using the accumulated covariance, compute the transformation matrix.
	 */
	public void estimate() {
		// save the mean value
		mean = global.getMean();
		
		// transfer the covariance data to proper format
		Matrix mat = new Matrix(fd, fd);
		if (global.getCount() == 0)
			throw new RuntimeException("PCA.estimate(): No samples, no estimate!");
		
		int k = 0;
		double [] cov = global.getCovariance();
		for (int i = 0; i < fd; ++i) {
			for (int j = 0; j <= i; ++j) {
				mat.set(i, j, cov[k]);
				mat.set(j, i, cov[k]);
				k++;
			}
		}

		// do the eigen decomposition
		EigenvalueDecomposition eig = mat.eig();

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
		
		// transfer the eigenvectors to the projection matrix
		int numv = (int) (global.getCount() > fd ? fd : global.getCount());
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
	 * Produce a String representation of the PCA containing both Projection and
	 * PCA information.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		
		sb.append("Projection = \n");
		sb.append(super.toString());
		
		sb.append("PCA = \n");
		sb.append("evals = " + Arrays.toString(evals));
		
		return sb.toString();
	}

	public static final String SYNOPSIS = 
		"sikoried, 2/2/2011\n" +
		"Compute a principal components analysis (PCA) transformation and save the\n" +
		"resulting projection y = A * (x - mean) to the given projection file.\n" +
		"usage: transformations.PCA proj list [indir]\n" +
		"  proj  : output file for projection (Frame format)\n" +
		"  list  : file list (files need to be Frame format)\n" +
		"  indir : (optional) directory where the input files are located\n";

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 3) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		String outf = args[0];
		String listf = args[1];
		String indir = args.length == 3 ? args[2] + System.getProperty("file.separator") : "";
		
		
		PCA pca = null;
		
		BufferedReader br = new BufferedReader(new FileReader(listf));
		String line;
		while ((line = br.readLine()) != null) {
			FrameInputStream fr = new FrameInputStream(new File(indir + line));
			
			if (pca == null)
				pca = new PCA(fr.getFrameSize());
			
			pca.accumulate(fr);
		}
		br.close();

		pca.estimate();

		pca.save(new File(outf));
	}
}
