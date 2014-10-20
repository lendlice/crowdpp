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

import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.util.Arithmetics;

/**
 * The discrete HMM state is the very basic form of HMM state. It features a 
 * discrete output probability for a certain alphabet of observations.
 * 
 * @author sikoried
 */
public final class DState extends State {

	/** discrete output probability */
	private double [] b = null;
	
	/** alphabet for probability lookup */
	private double [] alphabet = null;
	
	/** accumulator for distribution reestimation */
	private transient double [] baccu = null;
	
	/** 
	 * Create an HMM state with discrete output probability for the given 
	 * alphabet. Output distribution is uniformly initialized.
	 * @param alphabet
	 */
	public DState(double [] alphabet) {
		this.alphabet = alphabet;
		b = new double [alphabet.length];
		
		for (int i = 0; i < b.length; ++i)
			b[i] = 1. / b.length;
	}

	/**
	 * Create a discrete HMM state from the given template.
	 * @param copy
	 */
	public DState(DState copy) {
		// alphabet may be shared
		this.alphabet = copy.alphabet;
		
		// emission needs to be unique
		this.b = copy.b.clone();
	}
	
	/** 
	 * Create an HMM state with discrete output probability from the given 
	 * InputStream
	 * @param is
	 */
	public DState(InputStream is) throws IOException {
		alphabet = new double [IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN)];
		if (!IOUtil.readDouble(is, alphabet, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("could not read alphabet");
		
		b = new double [IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN)];
		if (!IOUtil.readDouble(is, b, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("could not read distribution");
	}
	
	/**
	 * Write the discrete state to the given output stream
	 * @param os
	 */
	void write(OutputStream os) throws IOException {
		IOUtil.writeByte(os, getTypeCode());
		IOUtil.writeInt(os, alphabet.length, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, alphabet, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, b.length, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, b, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * For a given observation token, determine the index in the probability 
	 * look-up table
	 * @param tok
	 * @return index
	 */
	private int resolve(double tok) {
		int i = 0;
		while (alphabet[i] != tok)
			++i;
		return i;
	}
	/**
	 * Discard the current accumulator.
	 */
	public void discard() {
		baccu = null;
	}

	/**
	 * Initialize a new accumulator.
	 */
	public void init() {
		baccu = new double [b.length];
	}
	
	/**
	 * For the given state posterior, accumulate the statistics.
	 * @param gamma state posterior
	 * @param x corresponding observation
	 */
	public void accumulate(double gamma, double [] x) {
		baccu[resolve(x[0])] += gamma;
	}

	/**
	 * Propagate the sufficient statistics of the given state to the local
	 * accumulator.
	 */
	public void propagate(State source) {
		DState state = (DState) source;
		Arithmetics.vadd2(baccu, state.baccu);
	}
	
	public void interpolate(State source, double rho) {
		// compute the sum over all gammas
		double r = 0.;
		for (double d : baccu)
			r += d;
		
		// compute probability-dependent weighting factor
		r = rho / (rho + r);
		
		Arithmetics.interp1(baccu, ((DState) source).baccu, r);
	}
	
	public void pinterpolate(double wt, State source) {
		Arithmetics.interp1(b, ((DState) source).b, wt);
		Arithmetics.makesumto1(b);
	}
	
	/**
	 * Re-estimate the discrete output distribution by normalizing the sum to 1.
	 */
	public void reestimate() {
		double sum = 0.;
		for (int i = 0; i < b.length; ++i)
			sum += baccu[i];
		
		if (sum == 0.) {
			System.err.println("no activity, aborting re-estimation");
			return;
		}
		
		for (int i = 0; i < b.length; ++i)
			b[i] = baccu[i] / sum;
	}
	
	public double gamma() {
		double s = 0.;
		for (double d : baccu)
			s += d;
		return s;
	}
	
	/**
	 * Compute the state emission probability for the given observation.
	 * @param x observation
	 * @return state emission probability
	 */
	public double emits(double [] x) {
		return b[resolve(x[0])];
	}
	
	/**
	 * Generate a String representation of this state.
	 */
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("b = [");
		for (double bb : b) 
			buf.append(" " + bb);
		buf.append(" ]");
		
		if (baccu != null)
			buf.append(" emission accumulator present");
		
		return buf.toString();
	}

	/**
	 * Get the type code for DState: 'd'
	 */
	public byte getTypeCode() {
		return 'd';
	}
}
