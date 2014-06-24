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
package de.fau.cs.jstk.framed;

import java.io.IOException;

import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.sampled.AudioSource;
import de.fau.cs.jstk.sampled.RawAudioFormat;

/**
 * Compute the autocorrelation coefficients. Make sure to use a proper window!
 * 
 * @author sikoried
 */
public class SimpleACF implements AutoCorrelation {

	/** FrameSource to read from */
	private FrameSource source;

	/** internal read buffer */
	private double[] buf;

	/** frame size */
	private int fs;

	private boolean vuv;
	
	/**
	 * Construct an AutoCorrelation object using the given source to read from.
	 * 
	 * @param source
	 */
	public SimpleACF(FrameSource source) {
		this.source = source;
		this.fs = source.getFrameSize();
		this.buf = new double[fs];
		this.vuv = false;
	}
	
	public SimpleACF(VUVDetection source) {
		this((FrameSource) source);
		this.vuv = true;
	}

	public FrameSource getSource() {
		return source;
	}
	
	public int getFrameSize() {
		return fs;
	}

	public String toString() {
		return "framed.SimpleACF fs=" + fs;
	}
	
	/**
	 * Reads from the window and computes the autocorrelation (the lazy way...)
	 */
	public boolean read(double[] buf) throws IOException {
		if (!source.read(this.buf))
			return false;
		
		// compute autocorrelation
		ac(this.buf, buf, vuv);
		
		return true;
	}
	
	public static void ac(double [] in, double [] out) {
		ac(in, out, false);
	}

	/**
	 * Compute the autocorrelation (explicit way)
	 * @param in
	 * @param out
	 */
	public static void ac(double [] in, double [] out, boolean vuv) {
		int s = (vuv) ? 1 : 0;
		// compute autocorrelation
		out[0] = in[0];
		for (int j = 0; j < in.length - s; ++j) {
			out[s+j] = 0.;
			for (int i = 0; i < in.length - s; ++i) {
				out[s+j] += in[s + (i + j) % (in.length - s)] * in[s + i];
			}
		}
	}
	
	public boolean firstIndexVUVDecision() {
		return vuv;
	}
	
	public static final String SYNOPSIS = 
		"framed.AutoCorrelation [format-string] file > frame-output";
	
	public static void main(String [] args) throws Exception {
		if (args.length < 1) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		AudioSource as = new de.fau.cs.jstk.sampled.AudioFileReader(args[0],
				RawAudioFormat.create(args.length > 1 ? args[1] : "f:"
						+ args[0]), true);
		Window window = new HammingWindow(as, 25, 10, false);
		AutoCorrelation acf = new SimpleACF(window);
		
		System.err.println(as);
		System.err.println(window);
		System.err.println(acf);
		
		double [] buf = new double[acf.getFrameSize()];

		FrameOutputStream fos = new FrameOutputStream(buf.length);
		
		while (acf.read(buf))
			fos.write(buf);
		
		fos.close();
	}
}

