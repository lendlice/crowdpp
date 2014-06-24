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

import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.FrameSource;

public abstract class SpectralTransformation implements FrameSource {
	private static Logger logger = Logger.getLogger(SpectralTransformation.class);
	
	/** the frame source to read from */
	protected FrameSource source = null;
	
	/** the input block size to the actual transformation */
	protected int blockSize = 0;
	
	/** 
	 * Get the frequency resolution of this FFT. Only possible if reading from
	 * a Window at some point.
	 * 
	 * @return frequency resolution in Hz
	 */
	public double getResolution() {		
		if (source instanceof Window) {
			Window wnd = (Window) source;
			return (double) wnd.source.getSampleRate() / (double) blockSize;
		} else if (source instanceof SimulatedFrameSource) {
			SimulatedFrameSource src = (SimulatedFrameSource) source;
			return (double) src.getSampleRate() / (double) src.getBlockSize();
		} else {
			logger.debug("SpectralTransformation.getResolution(): WARNING -- no sampled source, returning invalid resolution!");
			return 0.;
		}
	}
}
