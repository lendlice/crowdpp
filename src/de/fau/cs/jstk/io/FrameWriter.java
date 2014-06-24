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
import java.io.Writer;

/**
 * Use the FrameWriter to write ASCII frames to the given Writer
 * @author sikoried
 *
 */
public class FrameWriter implements FrameDestination {
	/** Writer to write to */
	private Writer wr = null;
	
	/**
	 * Initialize a new FrameWriter to write ASCII frames
	 * @param wr
	 * @throws IOException
	 */
	public FrameWriter(Writer wr) throws IOException {
		this.wr = wr;
	}
	
	/**
	 * Write a double precision frame
	 */
	public void write(double [] x) throws IOException {
		int i = 0;
		for (; i < x.length - 1; ++i)
			wr.append(Double.toString(x[i]) + " ");
		wr.append(Double.toString(x[i]) + "\n");		
	}

	/**
	 * Write a single precision frame
	 */
	public void write(float [] x) throws IOException {
		int i = 0;
		for (; i < x.length - 1; ++i)
			wr.append(Float.toString(x[i]) + " ");
		wr.append(Float.toString(x[i]) + "\n");
	}
	
	/**
	 * Flush the writer 
	 */
	public void flush() throws IOException {
		wr.flush();	
	}
	
	/**
	 * Flush and close the writer 
	 */
	public void close() throws IOException {
		wr.flush();
		wr.close();
	}
}
