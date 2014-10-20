/*
	Copyright (c) 2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer

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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;

public final class LabelFrameInputStream implements FrameSource {

	private int l;
	private InputStream labelstr;
	private FrameSource source;
	
	private int [] ignore = null;
	
	public LabelFrameInputStream(InputStream labelstream, FrameSource source) {
		this.labelstr = new BufferedInputStream(labelstream);
		this.source = source;
	}
	
	public boolean read(double [] buf) throws IOException {
		if (ignore == null) {
			l = labelstr.read();
			
			if (l == -1)
				return false;
			
			return source.read(buf);
		} else {
			int skip = 0;
			while ((l = labelstr.read()) != -1 && contains(l, ignore))
				skip++;
			
			// too bad, end of stream!
			if (l == -1)
				return false;
			
			// skip wrong frames
			while (skip-- > 0)
				source.read(buf);
			
			return source.read(buf);
		}		
	}

	private static boolean contains(int needle, int [] haystack) {
		if (haystack == null)
			return false;
		
		for (int i : haystack)
			if (i == needle)
				return true;
		return false;
	}
	
	public void setIgnoreFrames(int [] ignore) {
		this.ignore = ignore;
	}
	
	public int getLabel() { 
		return l;
	}
	
	public int getFrameSize() {
		return source.getFrameSize();
	}

	public FrameSource getSource() {
		return source;
	}
	
	public static final String SYNOPSIS = 
		"Filter a frame file using a framelabel (.lfrm) file.\n\n" +
		"usage: io.LabelFrameInputStream ignore-chars iolist\n" +
		"  ignore-chars:  labels to be ignored\n" +
		"  iolist: list file containing 3 items per line (input.frm input.lfrm output.frm)";
	
	public static void main(String [] args) throws IOException {
		if (args.length != 2) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		byte [] ignb = args[0].getBytes();
		int [] ign = new int [ignb.length];
		for (int i = 0; i < ign.length; ++i)
			ign[i] = ignb[i];
		
		LineNumberReader lnr = new LineNumberReader(new FileReader(args[1]));
		String l;
		while ((l = lnr.readLine()) != null) {
			String [] spl = l.trim().split("\\s+");
			if (spl.length != 3) {
				System.err.println("ignoring malformed line " + lnr.getLineNumber() + " : " + l);
				continue;
			}
			
			FrameInputStream fis = new FrameInputStream(new File(spl[0]));
			FileInputStream lis = new FileInputStream(spl[1]);
			LabelFrameInputStream lfis = new LabelFrameInputStream(lis, fis);
			
			lfis.setIgnoreFrames(ign);
			
			FrameOutputStream fos = new FrameOutputStream(fis.getFrameSize(), new File(spl[2]));
			
			double [] x = new double [lfis.getFrameSize()];
			while (lfis.read(x))
				fos.write(x);
			
			fos.close();
			fis.close();
			lis.close();
		}
	}
}
