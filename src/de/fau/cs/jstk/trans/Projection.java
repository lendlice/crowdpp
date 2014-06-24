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
import java.util.LinkedList;

import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;

/**
 * Load and apply a linear projection y = A * (x-m) with optional dimension 
 * reduction.
 * 
 * @author sikoried
 */
public class Projection {

	/**
	 * The Accumulator collects statistics for class-dependent mean and covariance.
	 */
	protected static class Accumulator {
		private long n = 0;
		private double [] mue = null;
		private double [] cov = null;
		
		/**
		 * Allocate a new Accumulator for the given feature dimension
		 * @param fd feature dimension
		 */
		Accumulator(int fd) {
			mue = new double [fd];
			cov = new double [fd * (fd + 1) / 2];
		}
		
		/**
		 * Get the feature dimension of the accumulator
		 * @return
		 */
		int getFd() {
			return mue.length;
		}
		
		/**
		 * Accumulate the given feature vector and increment counts.
		 * @param x
		 */
		void accumulate(double [] x) {
			int k = 0;
			for (int i = 0; i < x.length; ++i) {
				mue[i] += x[i];
				for (int j = 0; j <= i; ++j)
					cov[k++] += x[i] * x[j];
			}
			
			n++;
		}
		
		/**
		 * Get the number of observations contributing to this accumulator
		 * @return
		 */
		long getCount() {
			return n;
		}
		
		/**
		 * Compute the mean value according to the current accumulated statistics
		 * @return
		 */
		double [] getMean() {
			double [] m = mue.clone();
			for (int i = 0; i < m.length; ++i)
				m[i] /= n;
			return m;
		}
		
		/**
		 * Compute the covariance matrix according to the current accumulated
		 * statistics. This is a lower triangle matrix.
		 * @return
		 */
		double [] getCovariance() {
			double [] m = getMean();
			double [] c = cov.clone();
			int k = 0;
			for (int i = 0; i < m.length; ++i)
				for (int j = 0; j <= i; ++j, k++)
					c[k] = (c[k] / n) - (m[i] * m[j]);
			return c;
		}
	}
	
	/** projection matrix [rows][columns] */
	protected double [][] proj;
	
	/** mean to subtract before transformation */
	protected double [] mean;
	
	/** input feature dimension */
	protected int fd;
	
	/** internal use only, generate an empty Projection */
	protected Projection(int fd) {
		this.mean = null;
		this.proj = null;
		this.fd = fd;
	}
	
	/**
	 * Allocate a new Projection with the given projection matrix
	 * @param proj projection matrix [rows][columns]
	 */
	public Projection(double [] mean, double [][] proj) {
		this.mean = mean;
		this.proj = proj;
		this.fd = proj[0].length;
	}
	
	/**
	 * Allocate a new Projection and load the transformation from the given 
	 * file (in Frame format).
	 * @param file
	 * @throws IOException
	 */
	public Projection(File file) throws IOException {
		load(file);
		this.fd = mean.length;
	}
	
	public double [][] getProjection() {
		return proj;
	}
	
	/**
	 * Load a double [][] array from the given file in Frame format
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private void load(File file) throws IOException {
		FrameInputStream fr = new FrameInputStream(file);
		double [] buf = new double [fr.getFrameSize()];
		
		// read mean
		if (!fr.read(buf))
			throw new IOException("Projection.load(): Could not read mean vector");
		mean = buf.clone();
		
		// read matrix
		LinkedList<double []> rows = new LinkedList<double []>();
		while (fr.read(buf))
			rows.add(buf.clone());
		
		proj = rows.toArray(new double [rows.size()][]);
		
		// be nice, close file
		fr.close();
	}
	
	/**
	 * Save the current projection to the given file in Frame format
	 * @param file
	 * @throws IOException
	 */
	public void save(File file) throws IOException {
		if (fd == 0 || proj == null)
			throw new RuntimeException("Projection.save(): Projection not initialized");
		
		FrameOutputStream fw = new FrameOutputStream(fd, file);
		
		// write mean
		fw.write(mean);
		
		// write matrix
		for (double [] p : proj)
			fw.write(p);
		
		fw.close();
	}
	
	/**
	 * Get a plain ASCII String representation of the mean (1st row) and matrix
	 * @return
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		
		if (mean != null) {
			for (double p : mean)
				sb.append(p + " ");
			sb.append("\n");
		} else
			sb.append("mean = null\n");
		
		if (proj != null) {
			for (double [] p : proj) {
				for (double pp : p)
					sb.append(pp + " ");
				sb.append("\n");
			}
		} else 
			sb.append("proj = null\n");
		
		return sb.toString();
	}
	
	/**
	 * Transform the data vector x and store it in y.
	 * 
	 * @param x input data vector
	 * @param y output data vector (use a different dimension for dimension reduction)
	 */
	public void transform(double [] x, double [] y) {
		if (x.length != fd)
			throw new RuntimeException("Projection.transform(): x.length != fd");
		if (y.length > x.length)
			throw new RuntimeException("Projection.transform(): x.length < y.length");
		if (y.length > proj.length)
			throw new RuntimeException("Projection.transform(): y.length < proj.length");
		
		// set to zero as we do a sum!
		Arrays.fill(y, 0.);

		// for all desired dimensions (i.e. rows)
		for (int i = 0; i < y.length; ++i)
			for (int j = 0; j < x.length; ++j)
				y[i] += proj[i][j] * (x[j] - mean[j]);
	}

	/**
	 * In-place transformation of data vector x (no reduction). Preferrably use
	 * transform(x,y). Note that if the projection enforces reduction due to its
	 * dimensions, zeroes will be put in untouched dimensions.
	 * @param x
	 */
	public void transform(double[] x) {
		if (x.length != fd)
			throw new RuntimeException("Projection.transform(): x.length != fd");

		if (x.length > proj.length)
			throw new RuntimeException("Projection.transform(): x.length < proj.length");
		
		final double [] buf = new double [fd];
		System.arraycopy(x, 0, buf, 0, fd);

		// for all desired eigen vectors
		for (int i = 0; i < proj.length; ++i)
			for (int j = 0; j < fd; ++j)
				x[i] += proj[i][j] * (buf[j] - mean[j]);
		
		// null the untouched dimensions, if any
		for (int i = proj.length; i < x.length; ++i)
			x[i] = 0.;
	}
	
	/**
	 * Determine the maximum output dimension
	 * @return
	 */
	public int getMaxOutputDimension() {
		return Math.min(fd, proj.length);
	}
	
	public static final String SYNOPSIS = 
		"sikoried, 2/2/2011\n" +
		"Apply a linear projection y = A * (x-m) and reduce dimension if required.\n" +
		"usage: transformations.Projection proj dim list outdir [indir]\n" +
		"  proj   : projection Matrix (Frame format, row by row)\n" +
		"  dim    : output dimension, 0 for maximum dimension (i.e. no reduction)\n" +
		"  list   : list file (data in Frame format)\n" +
		"  outdir : directory to store output files\n" +
		"  indir  : (optional) input directory\n";
	
	public static void main(String[] args) throws IOException {
		if (args.length < 4 || args.length > 5) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		// output feature dimension
		int ofd = Integer.parseInt(args[1]);
		String outdir = args[3] + System.getProperty("file.separator");
		String indir = (args.length == 5 ? args[4] + System.getProperty("file.separator") : ""); 
		
		// load projection
		Projection proj = new Projection(new File(args[0]));
		
		BufferedReader br = new BufferedReader(new FileReader(args[2]));
		String line;
		while ((line = br.readLine()) != null) {
			FrameInputStream fr = new FrameInputStream(new File(indir + line));
			double [] buf = new double [fr.getFrameSize()];
			double [] out = new double [ofd == 0 ? proj.getMaxOutputDimension() : ofd];
			FrameOutputStream fw = new FrameOutputStream(out.length, new File(outdir + line));
			
			while (fr.read(buf)) {
				proj.transform(buf, out);
				fw.write(out);
			}
			fw.close();
		}
	}
}
