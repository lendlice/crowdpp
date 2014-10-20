/*
	Copyright (c) 2009-2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Stefan Steidl
		Korbinian Riedhammer

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

public class TriangularWindow extends Window {

	public TriangularWindow(AudioSource source) {
		super(source);
	}
	
	public TriangularWindow(AudioSource source, int length, int shift, boolean samples) {
		super(source, length, shift, samples);
	}

	protected double[] initWeights() {
		double [] w = new double [nsw];
		for (int i = 0; i < nsw/2; i++) {
			w[i] = (2.0 * i) / nsw + 2.0 / nsw;
			w[i + nsw/2] = 2.0 * (nsw/2 - 1 - i) / nsw; 
		}
		return w;
	}

	public String toString() {
		return "framed.TriangularWindow " + super.toString();
	}
}
