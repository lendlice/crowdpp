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
package de.fau.cs.jstk.stat.hmm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.stat.Density.Flags;
import de.fau.cs.jstk.stat.DensityDiagonal;
import de.fau.cs.jstk.stat.DensityFull;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.stat.MleDensityAccumulator.MleOptions;
import de.fau.cs.jstk.stat.MleMixtureAccumulator;

/**
 * The continuous HMM state features an individual mixture density. Though this
 * results in easier code, the training complexity increases due to the large 
 * number of variables.
 * 
 * @author sikoried
 */
public class CState extends State {

	/** codebook for this state */
	Mixture cb = null;
	
	MleMixtureAccumulator acc = null;
	
	private double ga = 0.;
	
	/** cache for mixture posteriors */
	private transient double [] p;
	
	/**
	 * Generate a new State using a copy of the given codebook.
	 * @param codebook
	 */
	public CState(Mixture codebook) {
		this.cb = codebook.clone();
		this.p = new double [codebook.nd];
	}
	
	/**
	 * Generate a new continuous state by a deep copy of the referenced one.
	 * @param copy
	 */
	public CState(CState copy) {
		this.cb = copy.cb.clone();
		this.p = new double [this.cb.nd];
	}
	
	/** 
	 * Create an HMM state by reading from the given InputStream
	 * @param is
	 */
	public CState(InputStream is) throws IOException {
		cb = new Mixture(is);
		p = new double [cb.nd];
	}
	
	/**
	 * Write the continuous state to the given OutputStream
	 */
	void write(OutputStream os) throws IOException {
		IOUtil.writeByte(os, getTypeCode());
		cb.write(os);
	}
	
	/**
	 * Emission probability of feature vector x
	 */
	public double emits(double[] x) {
		return cb.evaluate2(x);
	}

	/**
	 * Initialize a new accumulator.
	 */
	public void init() {
		try {
			acc = new MleMixtureAccumulator(cb.fd, cb.nd, cb.diagonal() ? DensityDiagonal.class : DensityFull.class);
		} catch (Exception e) {
			throw new RuntimeException(e.toString());
		}
		ga = 0.;
	}

	/**
	 * Accumulate the given feature vector using the state's posterior.
	 */
	public void accumulate(double gamma, double [] x) {
		// save your breath
		if (gamma == 0.)
			return;
		
		// evaluate the mixtures and compute posteriors
		cb.evaluate(x);
		cb.posteriors(p);
		
		// sum up all gammas for later interpolation
		ga += gamma;
		
		// for all densities...
		for (int j = 0; j < cb.nd; ++j) {
			// gamma_t(i,k)
			acc.accumulate(gamma * p[j], x, j);
		}
	}
	
	public double gamma() {
		return ga;
	}
	
	/**
	 * Absorb the given state's accumulator and delete if afterwards.
	 */
	public void propagate(State source) {
		CState state = (CState) source;	
		
		// absorb the statistics
		acc.propagate(state.acc);
	}
	
	/**
	 * Interpolate the local sufficient statistics with the ones from the
	 * referenced state.
	 */
	public void interpolate(State source, double rho) {
		CState state = (CState) source;
		acc.interpolate(state.acc, rho / (rho + ga));
	}
	
	public void pinterpolate(double wt, State source) {
		cb.pinterpolate(wt, ((CState) source).cb);
	}
	
	/**
	 * Reestimate this state's codebook.
	 */
	public void reestimate() {
		Mixture old = cb.clone();
		MleMixtureAccumulator.MleUpdate(old, MleOptions.pDefaultOptions, Flags.fAllParams, acc, cb);
	}
	
	/**
	 * Discard the current accumulator.
	 */
	public void discard() {
		ga = 0.;
		acc = null;
	}
	
	/**
	 * Generate a String representation of this state.
	 */
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(cb.toString());				
		return buf.toString();
	}

	/** 
	 * Get the type code for continuous states 'c'
	 */
	public byte getTypeCode() {
		return 'c';
	}
}
