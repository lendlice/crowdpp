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

/**
 * wraps a double array as an AudioSource.
 * 
 * @author hoenig 
 * 
 */
public class AudioBuffer implements AudioSource {

	int sampleRate;
	
	private double [] audioData;
	int pos = 0;
	
	public AudioBuffer(double [] audioData, int sampleRate){
		this.audioData = audioData;
		this.sampleRate = sampleRate;
	}
	
	public boolean getPreEmphasis() {
		return false;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public int read(double[] buf) throws IOException {
		return read(buf, buf.length);
	}
	
	public int read(double[] buf, int length) throws IOException {			
		if (audioData == null)
			return -1;
		
		int samplesLeft = audioData.length - pos;
		
		int numCopy;
		
		if (samplesLeft < length)
			numCopy = samplesLeft;
		else 
			numCopy = length;

		System.arraycopy(audioData, pos, buf, 0, numCopy);
		pos += numCopy;
		return numCopy;
	}

	/**
	 * FIXME: refusing to re-implement pre-emphasis here 
	 */
	public void setPreEmphasis(boolean applyPreEmphasis, double a) {
		if (applyPreEmphasis)
			throw new Error("pre-emphasis not implemented");
	}

	public void tearDown() throws IOException {
		audioData = null;
	}
}
