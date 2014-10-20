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
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;

import de.fau.cs.jstk.stat.Sample;

/**
 * Read Samples from a binary InputStream.
 * @author sikoried
 *
 */
public class SampleInputStream implements SampleSource {
	/** InputStream to read Sample instances from */
	private InputStream is = null;
	
	/** input frame dimension */
	private int fd = 0;
	
	/**
	 * Initialize a new SampleInputStream on the given InputStream
	 * @param is
	 */
	public SampleInputStream(InputStream is) throws IOException {
		this.is = is;
		fd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Read the next Sample from the InputStream and save it to the referenced
	 * Sample instance
	 * @return null if no more Sample could be read
	 */
	public Sample read() throws IOException {
		// read label and assigned label
		short [] cy = new short [2];
		if (!IOUtil.readShort(is, cy, ByteOrder.LITTLE_ENDIAN))
			return null;
				
		double [] x = new double [fd];
		if (!IOUtil.readFloat(is,x, ByteOrder.LITTLE_ENDIAN)) {
			is.close();
			return null;
		}
		
		return new Sample(cy[0], cy[1], x);
	}
	
	/**
	 * Read a list of Sample from the given InputStream
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static List<Sample> readList(InputStream is) throws IOException {
		List<Sample> ret = new LinkedList<Sample>();
		SampleInputStream sri = new SampleInputStream(is);
		
		Sample s;
		while ((s = sri.read()) != null)
			ret.add(s);

		return ret;
	}
}
