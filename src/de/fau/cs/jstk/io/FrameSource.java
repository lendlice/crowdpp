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
package de.fau.cs.jstk.io;

import java.io.IOException;

/**
 * Once we leave the signal (i.e. sampled) level, we deal with frames. The 
 * Implementation of this interface allows for flexible combinations of the 
 * target algorithms. Usually, implementing objects receive an initialized 
 * source to read from.
 * 
 * @author sikoried
 *
 */
public interface FrameSource {
	/**
	 * Extract the next frame from the the source stream using a window function
	 * @param buf buffer to save the frame; implementing objects may depend
	 * on a constant dimension during subsequent calls
	 * @return true on success, false if the stream terminated before the window was filled
	 */
	public boolean read(double [] buf) throws IOException;
	
	/**
	 * Return the length of the frames (needed for the read call)
	 */
	public int getFrameSize();
	
	/**
	 * Return a String representation of the FrameSource
	 */
	public String toString();
	
	/**
	 * Return the source this instance reads from.
	 */
	public FrameSource getSource();
}
