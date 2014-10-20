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
package de.fau.cs.jstk.framed;

import de.fau.cs.jstk.sampled.AudioSource;

/**
 * Hanning window: windowed_signal = signal * (0.5 - 0.5*Math.cos(2. * offset * Math.PI / (length - 1)))
 * @author sikoried
 *
 */
public class HannWindow extends Window {
	
	public HannWindow(AudioSource source) {
		super(source);
	}
	
	public HannWindow(AudioSource source, int length, int shift, boolean samples) {
		super(source, length, shift, samples);
	}

	protected double [] initWeights() {
		return weights(nsw);
	}
	
	public static double [] weights(int size) {
		double [] w = new double [size];
		for (int i = 0; i < size; ++i)
			w[i] = 0.5 - 0.5 * Math.cos(2. * i * Math.PI / (size - 1));
		return w;
	}

	public String toString() {
		return "framed.HanningWindow " + super.toString();
	}
}
