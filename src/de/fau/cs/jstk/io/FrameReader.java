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
import java.io.LineNumberReader;
import java.io.Reader;

/**
 * Read Frames from the given Reader
 * @author sikoried
 *
 */
public class FrameReader implements FrameSource {
	/** line number reader to read from */
	private LineNumberReader lnr = null;
	
	/** peek the first line */
	private String peek = null;
	
	/** input frame dimension */
	private int fd = 0;
	
	/** first line to read */
	private double [] first = null;
	
	/**
	 * Initialize a new FrameReader using the given reader.
	 * @param rd
	 * @throws IOException
	 */
	public FrameReader(Reader rd) throws IOException {
		if (rd instanceof LineNumberReader)
			lnr = (LineNumberReader) rd;
		else
			lnr = new LineNumberReader(rd);
		
		// read first line
		if ((peek = lnr.readLine()) == null)
			throw new IOException("no line to read!");
		
		String [] split = peek.trim().split("\\s+");
		fd = split.length;
		first = new double [fd];
		try {
			for (int i = 0; i < fd; ++i)
				first[i] = Double.parseDouble(split[i]);
		} catch (NumberFormatException e) {
			throw new IOException("line " + lnr.getLineNumber() + ": invalid number format");
		}
	}
	
	/**
	 * Read the next Frame from the Reader
	 */
	public boolean read(double [] buf) throws IOException {
		if (first != null) {
			if (buf.length != fd)
				throw new IOException("Wrong buffer size on read()");
			System.arraycopy(first, 0, buf, 0, fd);
			first = null;
		} else {
			String l = lnr.readLine();
			if (l == null)
				return false;
			
			String [] split = l.trim().split("\\s+");
			if (split.length != fd)
				throw new IOException("line " + lnr.getLineNumber() + ": invalid number of features");
			
			try {
				for (int i = 0; i < fd; ++i)
					buf[i] = Double.parseDouble(split[i]);
			} catch (NumberFormatException e) {
				throw new IOException("line " + lnr.getLineNumber() + ": invalid number format");
			}
		}
		return true;
	}

	/**
	 * Get the input frame size 
	 */
	public int getFrameSize() {
		return fd;
	}

	/**
	  * Get the underlying source
	  * @return null for FrameReaders
	  */
	public FrameSource getSource() {
		return null;
	}
}
