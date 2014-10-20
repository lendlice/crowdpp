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
package de.fau.cs.jstk.stat;

import java.io.IOException;
import java.util.Scanner;


import org.apache.log4j.Logger;

/**
 * The DensityDiagonal is a Gaussian density with a diagonal covariance which 
 * allows easy/explicit computation of the inverse of the covariance matrix.
 * 
 * @author sikoried
 */
public final class DensityDiagonal extends Density {
	private static Logger logger = Logger.getLogger(DensityDiagonal.class);
	
	private double [] icov;
	
	/**
	 * Create a new Density with diagonal covariance.
	 * @param dim Feature dimension
	 */
	public DensityDiagonal(int dim) {
		super(dim);
		cov = new double [fd];
		icov = new double [fd];
	}
	
	/**
	 * Allocate a new diagonal density and copy the values from the referenced
	 * @param copy
	 */
	public DensityDiagonal(DensityDiagonal copy) {
		this(copy.apr, copy.mue, copy.cov);
		this.id = copy.id;
	}
	
	/**
	 * Allocate a new diagonal Density and copy the values from the referenced 
	 * full covariance density
	 * @param copy
	 */
	public DensityDiagonal(DensityFull copy) {
		this(copy.mue.length);
		this.apr = copy.apr;
		this.id = copy.id;
		System.arraycopy(copy.mue, 0, mue, 0, fd);
		int k = 0;
		for (int i = 0; i < fd; ++i) {
			for (int j = 0; j <= i; ++j) {
				if (i == j)
					cov[i] = copy.cov[k];
				k++;
			}
		}
		
		update();
	}
	
	/**
	 * Allocate a new DensityDiagonal and read the parameters from the scanner
	 * @param fd
	 * @param scanner
	 * @throws IOException
	 */
	public DensityDiagonal(int fd, Scanner scanner) throws IOException {
		this(fd);
		
		while (!scanner.hasNextDouble())
			scanner.next();
		double a = scanner.nextDouble();
		
		double [] mue = new double [fd];
		while (!scanner.hasNextDouble()) 
			scanner.next();	
		for (int i = 0; i < fd; ++i)
			mue[i] = scanner.nextDouble();
		
		double [] cov = new double [fd];
		while (!scanner.hasNextDouble())
			scanner.next();
		for (int i = 0; i < fd; ++i)
			cov[i] = scanner.nextDouble();
		
		fill(a, mue, cov);
	}

	/**
	 * Create a new Density with diagonal covariance
	 * @param apr prior probability
	 * @param mue mean vector
	 * @param cov covariance vector
	 */
	public DensityDiagonal(double apr, double [] mue, double [] cov) {
		this(mue.length);
		fill(apr, mue, cov);
	}

	/** Update the internal variables. Required after modification. */
	public void update() {
		// check for NaN!
		long nans = 0;
		long minc = 0;
		for (int i = 0; i < fd; ++i) {
			if (Double.isNaN(mue[i])) {
				mue[i] = 0;
				nans++;
			}
			if (Double.isNaN(cov[i])) {
				cov[i] = MIN_COV;
				nans++;
			} else if (cov[i] < MIN_COV) {
				cov[i] = MIN_COV;
				minc++;
			}
			
			icov[i] = 1.0 / cov[i];
		}

		logdet = 0.;		
		for (double c : cov)
			logdet += Math.log(c);
		lapr = Math.log(apr);
		
		if (Double.isNaN(apr) || Double.isNaN(lapr)) {
			apr = MIN_WEIGHT;
			lapr = Math.log(MIN_WEIGHT);
			nans++;
		}
		
		if (nans > 0)
			logger.fatal("Density#" + id + ".update(): fixed " + nans + " NaNs");
		if (minc > 0)
			logger.info("Density#" + id + ".update(): enforced " + minc + " min covariances");
	}

	/**
	 * Evaluate the density for the given sample vector x. score keeps the
	 * probability (without the prior).
	 * @param x feature vector
	 * @return prior times score
	 */
	public double evaluate(double [] x) {
		// score = exp(-.5 * ( log(det) + fd*log(2*pi) + (x-mue)^T cov^-1 (x-mue)))

		// log of determinant + log(2*pi)
		score = logdet + logpiconst;
		
		// mahalanobis dist
		double h;
		for (int i = 0; i < fd; ++i) {
			h = x[i] - mue[i];
			// h *= h;
			// score +=  h / cov[i];
			score += h * h * icov[i]; 
		}
		
		score *= -.5;
		
		lh = lapr + score;
		
		score = Math.exp(score);
		
		if (Double.isNaN(score) || score < MIN_PROB)
			score = MIN_PROB;
		
		ascore = apr * score;
		
		return ascore;
	}
	
	/**
	 * Create a deep copy of this instance.
	 */
	public Density clone() {
		return new DensityDiagonal(this);
	}

	/**
	 * Obtain a string representation of the density.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("apr = " + apr + "\nmue =");
		for (double m : mue)
			sb.append(" " + m);
		sb.append("\ncov =");
		for (double c : cov)
			sb.append(" " + c);
		// sb.append("\nlogdet = " + logdet + "\nlogpiconst = " + logpiconst);
		return sb.toString();
	}
	
	/** random number generator for sample drawing */
	private static java.util.Random gen = new java.util.Random(System.currentTimeMillis());
	
	/**
	 * Draw a random sample from the diagonal density
	 * @return
	 */
	public double [] drawSample() {
		double [] x = new double [fd];
		for (int i = 0; i < fd; ++i)
			x[i] = mue[i] + gen.nextGaussian() * cov[i];
		
		return x;
	}

	@Override
	public DensityDiagonal marginalize(boolean [] keep) {

		if (keep.length != fd)
			throw new IllegalArgumentException("dimension mismatch (keep.length = " + keep.length + " != fd = " + fd);
				
		int dim = 0;
	    for (boolean value : keep)
	    	if (value)
	    		dim++;
	    		
		DensityDiagonal d = new DensityDiagonal(dim);
	    		
	    double [] mueNew = new double[dim];
	    double [] covNew = new double[dim];
	    		
	    int i, j;
	    for (i = j = 0; i < fd; i++)
	    	if (keep[i]){
	    		mueNew[j] = mue[i];
	    		covNew[j] = cov[i];
	    		j++;
	    	}
	    if (dim != j)
	    	throw new Error("implementation error");
		
	    
	    		
		d.fill(apr,
				mueNew,
				covNew);				

		return d;
	}
}
