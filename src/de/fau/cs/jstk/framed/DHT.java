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

import java.io.IOException;

import de.fau.cs.jstk.io.FrameSource;
import edu.emory.mathcs.jtransforms.dht.DoubleDHT_1D;

public class DHT extends SpectralTransformation implements FrameSource {

	/** FFT object */
	private DoubleDHT_1D dht = null;

	/**
	 * Construct a new FFT object. Frame size stays unchanged.
	 */
	public DHT(FrameSource source) {
		this.source = source;
		this.blockSize = source.getFrameSize();
		
		// init FFT
		dht = new DoubleDHT_1D(blockSize);
	}
	
	public int getFrameSize() {
		return source.getFrameSize();
	}
	
	public FrameSource getSource() {
		return source;
	}
	
	public String toString() {
		return "dht: frame_size=" + source.getFrameSize();
	}
	
	/**
	 * Read the next frame and apply DHT.
	 */
	public boolean read(double[] buf)
		throws IOException {
		
		// read frame from source
		if (!source.read(buf))
			return false;
		
		// do dht in-place
		dht.forward(buf);

		return true;
	}
}
