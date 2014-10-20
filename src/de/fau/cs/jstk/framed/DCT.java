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
import edu.emory.mathcs.jtransforms.dct.DoubleDCT_1D;

public class DCT extends SpectralTransformation implements FrameSource {
	
	/** the frame source to read from */
	private FrameSource source = null;
	
	/** FFT object */
	private DoubleDCT_1D dct = null;
	
	/** perform scaling? */
	private boolean scale = false;
	
	/**
	 * Construct a new FFT object. Frame size stays unchanged, first coefficient
	 * is replaced by the short time energy (in case of a Mel filter bank input
	 * the sum over the bands)
	 */
	public DCT(FrameSource source, boolean scale) {
		this.source = source;
		this.scale = scale;
		this.blockSize = source.getFrameSize();
		
		// init DCT
		dct = new DoubleDCT_1D(blockSize);
	}
	
	public int getFrameSize() {
		return source.getFrameSize();
	}
	
	public String toString() {
		return "framed.DCT frame_size="+ source.getFrameSize() + " scale=" + scale;
	}
	
	public FrameSource getSource() {
		return source;
	}
	
	/**
	 * Read the next frame and apply DCT.
	 */
	public boolean read(double[] buf) 
		throws IOException {
		
		// read frame from source
		if (!source.read(buf))
			return false;
		
		// do dct in-place
		dct.forward(buf, scale);
		
		return true;
	}
}
