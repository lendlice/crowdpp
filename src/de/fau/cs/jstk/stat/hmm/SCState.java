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
import java.nio.ByteOrder;
import java.util.HashMap;

import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.stat.MleMixtureAccumulator;
import de.fau.cs.jstk.util.Arithmetics;


/**
 * The semi-continuous HMM state uses a shared codebook of Gaussians and its own
 * mixture weights to compute the emission probabilities. As the codebook is
 * shared, so is the accumulator of the codebook. 
 * 
 * @author sikoried
 */
public final class SCState extends State {
	private static Logger logger = Logger.getLogger(SCState.class);
	
	/** shared codebook */
	public Mixture cb = null;
	
	public MleMixtureAccumulator sharedAcc = null;
	
	/** individual mixture weights */
	double [] c = null;
	
	/** cached emission probability */
	double b;
	
	/** cached mixture posterior */
	double [] p;
	
	Accumulator a;
	
	/**
	 * Create a new semi-continuous state using the given codebook.
	 * @param codebook
	 */
	public SCState(Mixture codebook) {
		this.cb = codebook;
		this.c = new double [cb.nd];
		this.p = new double [cb.nd];
		
		for (int i = 0; i < cb.nd; ++i)
			c[i] = 1. / cb.nd;
	}
	
	/**
	 * Create a new semi-continuous state based on the referenced one.
	 * @param copy
	 */
	public SCState(SCState copy) {
		this.cb = copy.cb;
		this.c = copy.c.clone();
		this.p = new double [this.cb.nd];
	}

	/** 
	 * Create an HMM state with semi-continuous output probability by reading
	 * from the given InputStream and shared densities
	 * @param is
	 * @param shared lookup table for shared mixtures
	 */
	public SCState(InputStream is, HashMap<Integer, Mixture> shared) 
		throws IOException {
		
		c = new double [IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN)];
		if (!IOUtil.readDouble(is, c, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("could not read weights");
		
		int cbId = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		
		if (!shared.containsKey(cbId))
			throw new IOException("SCState(): missing codebook " + cbId);
		
		cb = shared.get(cbId);
		p = new double [cb.nd];
	}
	
	public void setSharedAccumulator(MleMixtureAccumulator acc) {
		this.sharedAcc = acc;
	}
	
	/**
	 * Write out the SCState. Note that instead of the MixtureDensity, only its 
	 * ID is written!
	 * @param os
	 */
	void write(OutputStream os) throws IOException {
		IOUtil.writeByte(os, getTypeCode());
		IOUtil.writeInt(os, c.length, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, c, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, cb.id, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Probability of this state to emit the feature vector x
	 */
	public double emits(double[] x) {
		cb.evaluate2(x);
		b = 0.;
		
		for (int i = 0; i < c.length; ++i)
			b += c[i] * cb.components[i].score;
		
		return b;
	}

	public double gamma() {
		if (a == null)
			return 0.;
		else
			return a.gamma;
	}
	
	private class Accumulator {
		long frames = 0;
		double gamma = 0.;
		double [] c = new double [SCState.this.c.length];
		
		void propagate(Accumulator source) {
			if (source.frames == 0)
				return;
			
			frames += source.frames;
			gamma += source.gamma;
			
			Arithmetics.vadd2(c,  source.c);
		}
		
		void interpolate(Accumulator source, double rho) {
			double r = rho / (rho + gamma);
			
			gamma = r * source.gamma + (1. - r) * gamma;
			Arithmetics.interp1(c, source.c, r);
		}
	}
	
	/**
	 * Initialize the internal accumulator. As the codebook is shared, only
	 * the state dependent accumulators are initialized.
	 */
	public void init() {
		if (a != null)
			logger.warn("replacing existing accumulator!");
		
		a = new Accumulator();
	}
	
	/**
	 * Accumulate the feature vector given the state's posterior probability.
	 */
	public void accumulate(double gamma, double[] x) {
		// save your breath
		if (gamma == 0.)
			return;
		
		// collect statistics
		a.frames++;
		a.gamma += gamma;
		
		// evaluate codebook, compute posteriors
		cb.evaluate2(x);
		cb.posteriors(p, c);
		
		// for all densities...
		double gamma2;
		for (int j = 0; j < cb.nd; ++j) {
			// gamma_t(i,k)
			gamma2 = gamma * p[j];
			
			// caccu is state-dependent!
			a.c[j] += gamma2;
			
			// this is the sum over all states, as the cb and its accu are shared!
			sharedAcc.accumulate(gamma2, x, j);
		}
	}
	
	/**
	 * Propagate the sufficient statistics of the referenced SCState to the 
	 * local accumulator.
	 */
	public void propagate(State source) {
		a.propagate(((SCState )source).a);
	}
	
	/** 
	 * Interpolate the sufficient statistics of the referenced SCState with 
	 * the local accumulator.
	 */
	public void interpolate(State source, double rho) {
		a.interpolate(((SCState) source).a, rho);
	}

	public void pinterpolate(double wt, State source) {
		Arithmetics.interp1(c, ((SCState) source).c, wt);
		Arithmetics.makesumto1(c);
	}
	
	/**
	 * Re-estimate this state's parameters from the accumulator.
	 */
	public void reestimate() {
		double sum = 0.;
		for (int i = 0; i < c.length; ++i)
			sum += a.c[i];
		
		// only re-estimate weights if there has been any accumulation
		if (sum > 0.)
			for (int i = 0; i < c.length; ++i)
				c[i] = a.c[i] / sum;
		else
			logger.info("no activity for weights, aborting re-estimation");
	}
	
	/**
	 * Discard the current accumulator.
	 */
	public void discard() {
		c = null;
	}
	
	/**
	 * Generate a String representation of this state.
	 */
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("c = [");
		for (double cc : c) 
			buf.append(" " + cc);
		buf.append(" ]");
		
		if (c != null)
			buf.append(" weight accumulator present");
		
		return buf.toString();
	}
	
	public Mixture getMixture() {
		return cb;
	}

	/**
	 * Get the type byte for semi-contiuous states 's'
	 */
	public byte getTypeCode() {
		return 's';
	}
}
