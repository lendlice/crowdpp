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
 * Any audio source must support the basic operations: read samples, 
 * provide the sample rate (samples per seconds) and should be printable for 
 * debug purposes. It should also support pre-emphasis.
 *  
 * FIXME: isn't sampling rate (and pre-emphasis, if all AudioSources should
 * support it), generic? Shouln't this be an abstract class (hoenig)?
 * 
 * @see AudioFileReader.preEmphasize
 * 
 * @author sikoried
 *
 */
public interface AudioSource {

	public int read(double [] buf) throws IOException;
	
	/**
	 * Read length samples from the AudioSource.
	 * @param buf Previously allocated buffer to store the read audio samples.
	 * @return Number of actually read audio samples. -1 means that the source has ended.
	 */
	public int read(double [] buf, int length) throws IOException;
	
	/**
	 * Get the frame rate
	 * @return number of samples per second
	 */
	public int getSampleRate();
	
	/**
	 * Get a string representation of the source
	 */
	public String toString();
	
	/**
	 * Does the AudioSource perform pre-emphasis?
	 */
	public boolean getPreEmphasis();
	
	/**
	 * Toggle the pre-emphasis of the audio signal
	 * @param applyPreEmphasis apply pre-emphasis?
	 * @param a the pre-emphasis factor: x'(n) = x(n) - a*x(n-1)
	 */
	public void setPreEmphasis(boolean applyPreEmphasis, double a);
	
	/**
	 * Tear down the AudioSource (i.e. release file handlers, etc)
	 */
	public void tearDown() throws IOException;
}
