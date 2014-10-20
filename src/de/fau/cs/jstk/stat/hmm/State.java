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
import java.util.HashMap;

import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.stat.Mixture;

/**
 * The abstract State class enforces a certain interface for any implementing 
 * Hmm state and provides read/write functions for transparent I/O
 * 
 * @author sikoried
 */
public abstract class State {
	/**
	 * Return the type code of the implementing state.
	 * @return
	 */
	public abstract byte getTypeCode();
	
	/**
	 * Compute the state emission probability for the given observation.
	 * @param x observation
	 * @return state emission probability
	 */
	public abstract double emits(double [] x);
	
	/** 
	 * Initialize the accumulator.
	 */
	public abstract void init();
	
	/**
	 * Discard the current accumulator.
	 */
	public abstract void discard();
	
	/**
	 * Propagate the sufficient statistics of the referenced state to this
	 * instance.
	 * @param source state to obtain the sufficient statistics 
	 */
	public abstract void propagate(State source);
	
	/**
	 * Interpolate the sufficient statistics with the sufficient statistics of
	 * the referenced state with a given basic interpolation weight.
	 * @param source
	 * @param rho
	 */
	public abstract void interpolate(State source, double rho);
	
	/**
	 * Interpolate the local parameters (!) with the referenced ones
	 * @param wt this = wt * source + (1 - wt) * this
	 * @param source
	 */
	public abstract void pinterpolate(double wt, State source);
	
	/**
	 * Return the accumulated gamma value
	 * @return
	 */
	public abstract double gamma();
	
	/**
	 * Re-estimate the state parameters from the current accumulator and delete
	 * the accumulator afterwards.
	 */
	public abstract void reestimate();
	
	/**
	 * For the given state posterior, accumulate the statistics.
	 * @param gamma state posterior
	 * @param x corresponding observation
	 */
	public abstract void accumulate(double gamma, double [] x);
	
	/**
	 * Generate a String representation for this state
	 */
	public abstract String toString();
	
	/**
	 * Read a D/C/SC State or codebook information from the given ObjectInputStream
	 * @param ois
	 * @return
	 * @throws IOException
	 */
	public static State read(InputStream is, HashMap<Integer, Mixture> shared) 
		throws IOException {
		byte type = IOUtil.readByte(is);
		
		if (type == 'd')
			return new DState(is);
		else if (type == 'c')
			return new CState(is);
		else if (type == 's')
			return new SCState(is, shared);
		else 
			throw new IOException("State.read(): unknown state typ '" + type + "'");
	}
	
	/**
	 * Save the current State to the given ObjectOutputStream
	 * @param os
	 * @throws IOException
	 */
	abstract void write(OutputStream os) throws IOException;
}
