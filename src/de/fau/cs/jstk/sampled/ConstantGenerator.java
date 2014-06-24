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
package de.fau.cs.jstk.sampled;

import java.io.IOException;

public class ConstantGenerator extends Synthesizer {

	private double constant = .3;
	
	public ConstantGenerator() {
		super();
	}
	
	public ConstantGenerator(long duration) {
		super(duration);
	}
	
	public ConstantGenerator(double constant) {
		super();
		this.constant = constant;
	}
	
	public ConstantGenerator(long duration, double constant) {
		super(duration);
		this.constant = constant;
	}
	
	public void setConstant(double constant) {
		this.constant = constant;
	}
	
	public double getConstant() {
		return constant;
	}
	
	protected void synthesize(double[] buf, int n) {
		for (int i = 0; i < n; ++i)
			buf[i] = constant;
	}
	public void tearDown() throws IOException {
		// nothing to do here
	}

	public String toString() {
		return "ConstantGenerator: sample_rate=" + getSampleRate() + " constant=" + constant;
	}

}
