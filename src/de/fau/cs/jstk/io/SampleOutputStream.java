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
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.List;

import de.fau.cs.jstk.stat.Sample;

/**
 * Write Samples to an OutputStream
 * 
 * @author sikoried
 */
public class SampleOutputStream implements SampleDestination {
	/** stream to write the Samples to */
	private OutputStream os = null;
	
	/** output frame dimension */
	private int fd;
	
	/**
	 * Initialize a new SampleOutputStream on the given OutputStream for a given
	 * feature dimension.
	 * @param os
	 * @param fd
	 * @throws IOException
	 */
	public SampleOutputStream(OutputStream os, int fd) throws IOException {
		this.os = os;
		this.fd = fd;
		IOUtil.writeInt(os, fd, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Write the given Sample to the OutputStream
	 * @param s
	 * @throws IOException
	 */
	public void write(Sample s) throws IOException {
		if (s.x.length != fd)
			throw new IOException("Referenced Sample feature dimension does not match Writer's dimension");
		
		IOUtil.writeShort(os, s.c, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeShort(os, s.y, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeFloat(os, s.x, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Flush the OutputStream
	 * @throws IOException
	 */
	public void flush() throws IOException {
		os.flush();
		
	}
	
	/**
	 * Flush and close the OutputStream
	 * @throws IOException
	 */
	public void close() throws IOException {
		os.flush();
		os.close();
	}
	
	/**
	 * In the end, close the data file to prevent data loss!
	 */
	public void finalize() throws Throwable{
		try {
			os.flush();
			os.close();
		} finally {
			super.finalize();
		}
	}
	
	/**
	 * Write a list of Samples to the given OutputStream 
	 * @param os
	 * @param list List of Samples to write
	 * @throws IOException
	 */
	public static void writeToAscii(OutputStream os, List<Sample> list) throws IOException {
		SampleOutputStream sos = new SampleOutputStream(os, list.get(0).x.length);
		for (Sample s : list) 
			sos.write(s);
		sos.close();
	}
}
