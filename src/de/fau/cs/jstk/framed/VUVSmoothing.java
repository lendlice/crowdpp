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


/**
 * Voiced/Unvoiced (VUV) smoothing using the minimum length
 * 
 * <p>
 * A voiced frame is set unvoiced, if it is not a member of at least 3 sequent voiced frames.<br>
 * It is set voiced, if it is not a member of at least 2 sequent unvoiced frames
 * </p>
 * 
 * <p>
 * In the data stream the VUVDetection gets a buffer containing a 1. for voiced or a 0. for unvoiced in the first value 
 * and the windowed frame in the following values. It shifts the same buffer with a updated first value.
 * </p>
 * 
 * @author sndolahm
 *
 * @see VUVDetection
 */

public class VUVSmoothing implements FrameSource {
	
	/** Window to read from */
	private VUVDetection source;
	
	/** incoming frame size */
	private int fs_in = 0;
	
	
	/** internal ring buffer for context caching */
	private double [][] ringbuf = null;
		
	/** index within ringbuf for next write */
	private int ind_read = -1;
	private int ind_write = -1;
	
	/** longest context to consider */
	private final int lc = 5; // a ringbuf length of 5 allows to analyze 3 sequent frames in both directions 


	/** index where the padding started */
	private int initial_padding = -1;
	
	
	/**
	 * constructs a VUV detector using the given data stream from a {@link VUVDetection}
	 * 
	 * @param source {@link VUVDetection} to read from
	 */
	public VUVSmoothing(VUVDetection source) {
		this.source = source;
		this.fs_in = source.getFrameSize();
	}

	public FrameSource getSource() {
		return source;
	}
	
	/** 
	 * Read the next frame from the VUVDetection and overwrite the first value by the smoothing decision;
	 * shifting the rest of the frame
	 * 
	 * @see de.fau.cs.jstk.io.FrameSource#read(double[])
	 */
	public boolean read(double [] buf) throws IOException {
		
		/* reading new frame into ringbuffer */
		if (!bufferRead())
			return false;
		
		System.arraycopy(ringbuf[ind_read], 0, buf, 0, fs_in);
		
		int cnt = 0;
		int i = 0;
		while(i <= lc/2 && ringbuf[(ind_read + lc - i) % lc][0] == ringbuf[ind_read][0]) {cnt++; i++;}
		i=1;
		while(i <= lc/2 && ringbuf[(ind_read + lc + i) % lc][0] == ringbuf[ind_read][0]) {cnt++; i++;}
		
		/* update first value by smoothing decision */
		if (buf[0]==1 && cnt<3) buf[0]=0; 
		else if (buf[0]==0 && cnt<2) buf[0]=1;
		
		return true;	
	}
	
	/**
	 * read next frame into ringbuffer
	 * 
	 * @return true on success, false if the stream terminated before the buffer was filled
	 */
	private boolean bufferRead() throws IOException {
		
		// increment read and write indices
		ind_read = (ind_read + 1) % lc;
		ind_write = (ind_write + 1) % lc;
		
		if (ringbuf == null) {
			// beginning-of-stream; initialize, read right context and pad left context!
			ringbuf = new double [lc][fs_in];
			
			// read center frame and right context
			for (int i = 0; i <= lc/2; ++i) {
				if (!source.read(ringbuf[lc/2 + i]))
					return false;
			}
			
			// padding by duplicating
			for (int i = 0; i < lc/2; ++i)
				System.arraycopy(ringbuf[lc/2], 0, ringbuf[i], 0, fs_in);
					
			// set current read position to the middle
			ind_read = lc/2;
			
			// set current write position to 0 (assume having read lc samples already)
			ind_write = lc-1;
		
		} else {
		
			if (!source.read(ringbuf[ind_write])) {
				// first encounter of end-of-stream; remember position for later
				if (initial_padding < 0)
					initial_padding = ind_write;
				// no more genuine frames? done!
				else if (ind_write == (initial_padding + lc/2) % lc)
					return false;
				
				// padding by duplicating
				System.arraycopy(ringbuf[(ind_write + lc - 1) % lc], 0, ringbuf[ind_write], 0, fs_in);
			}
			
		}
		
		return true;
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {		
		StringBuffer buf = new StringBuffer();
		buf.append("");
		return buf.toString();
	}
	
	/* (non-Javadoc)
	 * @see framed.FrameSource#getFrameSize()
	 */
	public int getFrameSize() {
		return source.getFrameSize();
	}
	
}
