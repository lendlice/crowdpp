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
import java.util.LinkedList;
import java.util.List;

import de.fau.cs.jstk.stat.Sample;

/**
 * The SampleReader reads instances of Samples from the given Reader using an 
 * internal LineNumberReader.
 * 
 * @author sikoried
 */
public class SampleReader implements SampleSource {
	/** read line by line line-by-line */
	private LineNumberReader lnr = null;
	
	/**
	 * Allocate a new SampleReader to read from the given stream. Indicate if
	 * if this is an ASCII stream (System.in)
	 * @param is
	 * @param ascii
	 * @param classif is the classification result present?
	 * @throws IOException
	 */
	public SampleReader(Reader rd) throws IOException {
		if (rd instanceof LineNumberReader)
			lnr = (LineNumberReader) rd;
		else
			lnr = new LineNumberReader(rd);
	}
	
	/**
	 * Read the next Sample from the InputStream.
	 * @return null if no more Sample in the stream
	 * @throws IOException
	 */
	public Sample read() throws IOException {
		String line = lnr.readLine();
		if (line == null)
			return null;
		
		String [] split = line.trim().split("\\s+");
		
		if (split.length < 3)
			throw new IOException("line " + lnr.getLineNumber() + ": no feature data");
		try {
			short c = Short.parseShort(split[0]);
			short y = Short.parseShort(split[1]);
			double [] x = new double [split.length - 2];
			
			for (int i = 0; i < x.length; ++i)
				x[i] = Double.parseDouble(split[2 + i]);
			return new Sample(c, y, x);
		} catch (NumberFormatException e) {
			throw new IOException("line " + lnr.getLineNumber() + ": invalid number format");
		}
	}
	
	/**
	 * Read in a list of Samples from the given Reader in ASCII format, 
	 * i.e. line a la "numeric-label feat1 feat2 ..."
	 * @param rd Reader to read from
	 * @return
	 * @throws IOException
	 */
	public static List<Sample> readFile(Reader rd) throws IOException{
		List<Sample> list = new LinkedList<Sample>();
		SampleReader sr = new SampleReader(rd);
		Sample s;
		while ((s = sr.read()) != null) 
			list.add(s);
			
		return list;
	}
}
