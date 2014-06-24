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
import java.util.Arrays;

import de.fau.cs.jstk.io.FrameSource;

/**
 * Voiced/Unvoiced (VUV) detection with the threshold-classifier after Kiessling (K-) 
 * 
 * <p>
 * The classifier uses the three features zero crossings, squared mean amplitude and maximum amplitude.
 * </p>
 * 
 * <p>
 * In the data stream the VUVDetection gets a buffer containing a windowed frame and 
 * shifts a buffer containing a 1. for voiced or a 0. for unvoiced in the first value 
 * and the windowed frame in the following values.
 * </p>
 * 
 * @author sndolahm
 * 
 * @see Window
 */
public class VUVDetection implements FrameSource {
	
	/**	default threshold for zero crossings */
	public static final double DEFAULT_TZCR = 3.03934E3; // expedient for VERBMOBIL sample
	// public static final int DEFAULT_TZCR = 2.5E3; suggested by O'Shaughnessy
	/**	default threshold for squared mean amplitude */
	public static final double DEFAULT_TENENORM = 1.4875E-4; // after VERBMOBIL sample
	/** default threshold for maximum amplitude */
	public static final double DEFAULT_TMAXNORM = 1.63075E-2; //after VERBMOBIL sample 
	
	
	/** Window to read from */
	private Window source;
	
	/** sampling rate of the input signal */
	private double sr;
	
	/** incoming frame size */
	private int fs_in = 0;
	
	
	/** buffer used to read from source */
	private double [] inbuf;
	
	
	/** threshold for zero-crossing rate */
	private double tzcr = DEFAULT_TZCR;
	
	/** threshold for mean squared amplitude */
	private double tenenorm = DEFAULT_TENENORM;
	
	/** threshold for absolute maximum */
	private double tmaxnorm = DEFAULT_TMAXNORM;
	
	
	// PUBLIC
	
	/**
	 * constructs a VUV detector using the given window and default thresholds
	 * 
	 * @param source window to read from
	 * @param sampleRate sampling rate of input signal
	 */
	public VUVDetection(Window source, int sampleRate) {
		this(source, sampleRate, DEFAULT_TZCR, DEFAULT_TENENORM, DEFAULT_TMAXNORM);
	}
	
	/**
	 * constructs a VUV detector using the given window and thresholds
	 * 
	 * @param source window to read from
	 * @param sampleRate sampling rate of input signal
	 * @param tz maximum number of zero crossings for voiced
	 * @param te minimum mean squared amplitude for voiced
	 * @param tm minimum absolute amplitude for voiced
	 */
	public VUVDetection(Window source, int sampleRate, double tz, double te, double tm) {
		this.source = source;
		
		this.fs_in = source.getFrameSize();
		this.sr = (double) sampleRate;
		// allocate buffer
		this.inbuf = new double [fs_in];
		
		this.tzcr = tz; 
		this.tenenorm = te;
		this.tmaxnorm = tm;
	}

	/**
	 * read the next frame from the Window and store the VUV decision in the
	 * first buffer value, shifting the window in the following values
	 * 
	 * @see de.fau.cs.jstk.io.FrameSource#read(double[])
	 */
	public boolean read(double [] buf) throws IOException {
		
		if (!source.read(inbuf))
			return false;			
		
		Arrays.fill(buf, 0.);
		System.arraycopy(inbuf, 0, buf, 1, fs_in); // shifting window
		
		int zcr = 0; // zero crossings
		double enenorm = inbuf[0]*inbuf[0]; // energy norm
		double maxnorm = Math.abs(buf[0]); // maximum norm
		
		for (int i = 1; i < fs_in; ++i) {
			if (inbuf[i-1]*inbuf[i] <= -1) 
				zcr++;
			enenorm += inbuf[i]*inbuf[i];
			if (Math.abs(inbuf[i]) > maxnorm)  
				maxnorm = Math.abs(inbuf[i]); 
		}
		
		zcr *= sr/fs_in;
		enenorm /= fs_in;
		
		buf[0] = (zcr < tzcr && enenorm > tenenorm && maxnorm > tmaxnorm) ? 1.: 0.;
		
		return true;
	}
	
	public FrameSource getSource() {
		return source;
	}
		
	public String toString() {		
		StringBuffer buf = new StringBuffer();
		buf.append("VUVDetection: fs_in=" + fs_in + " sr=" + sr + " tzcr=" + tzcr + " tenenorm=" + tenenorm + "tmaxnorm=" + tmaxnorm);
		return buf.toString();
	}

	public int getFrameSize() {
		return fs_in + 1;
	}
}
