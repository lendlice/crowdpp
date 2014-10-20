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
import java.util.List;

import de.fau.cs.jstk.stat.Sample;

/**
 * Write out Samples to a given Writer
 * 
 * @author sikoried
 */
public class SampleWriter implements SampleDestination {
	/** OutputStream to write to */
	private Writer wr;
	
	/**
	 * Allocate a new SampleWriter to write to the given OutputStream either
	 * binary or ASCII data. You may also request to save the classification 
	 * result.
	 * @param wr
	 * @throws IOException
	 */
	public SampleWriter(Writer wr) throws IOException {
		this.wr = wr;
	}
	
	/**
	 * Write the given Sample to the stream.
	 * @param s
	 * @throws IOException
	 */
	public void write(Sample s) throws IOException {
		wr.append(s.toClassifiedString() + "\n");
	}
	
	
	/**
	 * Flush the stream.
	 * @throws IOException
	 */
	public void flush() throws IOException {
		wr.flush();		
	}
	
	/**
	 * Flush and close the stream.
	 * @throws IOException
	 */
	public void close() throws IOException {
		wr.flush();
		wr.close();
	}
	
	/**
	 * In the end, close the data file to prevent data loss!
	 */
	public void finalize() throws Throwable{
		try {
			wr.flush();
			wr.close();
		} finally {
			super.finalize();
		}
	}
	
	/**
	 * Write a list of Samples to the given OutputStream in ASCII format. Note
	 * that the original class info is saved, but not the assigned (y).
	 * @param wr
	 * @param list List of Samples to write
	 * @throws IOException
	 */
	public static void writeToAscii(Writer wr, List<Sample> list) throws IOException {
		SampleWriter sw = new SampleWriter(wr);
		for (Sample s : list) 
			sw.write(s);
		sw.close();
	}
}
