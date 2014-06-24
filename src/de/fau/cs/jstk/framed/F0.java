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

import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.sampled.AudioFileReader;
import de.fau.cs.jstk.sampled.AudioSource;
import de.fau.cs.jstk.sampled.RawAudioFormat;


/**
 * Extract the F0 candidates from the given autocorrelation.
 * 
 * @author sikoried
 *
 */
public class F0 implements FrameSource {
	public static final double DEFAULT_UB = 600.;
	
	public static final double DEFAULT_LB = 50.;
	
	/** number of candidates to extract */
	private int nc;
	
	/** size of incoming frames */
	private int fs_in;
	
	/** sampling rate of the input signal */
	private double sr;
	
	/** source to read from */
	private AutoCorrelation source;
	
	/** buffer used to read from source */
	private double [] ac;
	
	/** autocorrelation values of the maxima */
	private double [] maxv;
	
	/** lower F0 boundary (Hz) */
	private double lb = DEFAULT_LB;
	
	/** upper F0 boundary (Hz) */
	private double ub = DEFAULT_UB;
	
	private int ind_lb;
	
	private int ind_ub;
	
	/** 
	 * Create a new F0 extractor, providing the best F0 estimate.
	 * @param source
	 * @param sampleRate
	 */
	public F0(AutoCorrelation source, int sampleRate) {
		this(source, sampleRate, 1, DEFAULT_LB, DEFAULT_UB);
	}
	
	/**
	 * Create a new F0 extractor, providing a list of alternate F0 estimates
	 * @param source
	 * @param sampleRate
	 * @param candidates number of candidates to extract
	 */
	public F0(AutoCorrelation source, int sampleRate, int candidates) {
		this(source, sampleRate, candidates, DEFAULT_LB, DEFAULT_UB);
	}
	
	/** 
	 * Create a new F0 extractor, providing a list of alternate F0 estimates
	 * @param source
	 * @param sampleRate
	 * @param candidates number of candidates to extract
	 * @param lower lower boundary for plausible F0
	 * @param upper upper boundary for plausible F0
	 */
	public F0(AutoCorrelation source, int sampleRate, int candidates, double lower, double upper) {
		this.source = source;
		this.nc = candidates;
		this.fs_in = source.getFrameSize();
		this.sr = (double) sampleRate;
		this.lb = lower;
		this.ub = upper;
		
		// allocate buffers
		this.ac = new double [fs_in];
		this.ac = new double [fs_in];
		this.maxv = new double [nc];
		
		// indices: they go from high to low -- the shorter the lag, the higher the freq!
		ind_lb = (int) (sr / ub);
		ind_ub = (int) (sr / lb);
		
		int s = 0;
		if (source.firstIndexVUVDecision()) {
			s = 1;
		}
		
		if (ind_ub >= fs_in - s) {
			// frequency is not observable, reset!
			ind_ub = fs_in-1-s;
			lb = sr / ind_ub;
		}
	}

	public FrameSource getSource() {
		return source;
	}
	
	public String toString() {
		return "F0: sample_rate=" + sr + " allowing frequencies within " + lb + "Hz (ind=" + ind_lb + ") and " + ub + "Hz (ind=" + ind_ub + ")";
	}
	
	public int getFrameSize() {
		return nc;
	}
	
	/** 
	 * Read the next frame and extract the F0 candidates.
	 */
	public boolean read(double [] buf) throws IOException {
		if (!source.read(ac))
			return false;
		
		// reset maxima
		Arrays.fill(maxv, -Double.MAX_VALUE);
		Arrays.fill(buf, 0.);
		
		int s = 0;
		if (source.firstIndexVUVDecision()) {
			s = 1;
			if (ac[0] == 0) {
				return true;
			} else {
				// ignore first entry of array ac
			}
		}
				
		// locate maxima, omit first value
		for (int i = ind_lb; i < ind_ub; ++i) {
			if (ac[s+i] >= ac[s+i-1] && ac[s+i] >= ac[s+i+1] && ac[s+i] > maxv[0]) {
				// if we have a local max AND it's bigger than the smallest max
				// then add it to the candidate list
				if (nc == 1) {
					maxv[0] = ac[s+i];
					buf[0] = sr / i;
				} else {
					// find the right position to insert
					int p = 0;
					while (p < nc - 1 && ac[s+i] > maxv[p+1])
						p++;
					
					// shift the old maxima
					for (int j = 1; j <= p; ++j) { 
						maxv[j-1] = maxv[j]; 
						buf[j-1] = buf[j];
					}
					
					// insert the new max
					maxv[p] = ac[s+i];
					buf[p] = sr / i;
				}
			}
		}
		
		return true;
	}
	
	public static final String SYNOPSIS = 
		"framed.F0 [format-string] file num-cand > frame-output";
	
	public static void main(String [] args) throws Exception {
		if (args.length < 2) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		AudioSource as = new AudioFileReader(args[0], 
				RawAudioFormat.create(args.length > 2 ? args[1] : "f:" + args[0]), 
				true);
		
		Window w = new HammingWindow(as, 32, 16, false);
		AutoCorrelation ac = new FastACF(w);
		F0 f0 = new F0(ac, as.getSampleRate(), Integer.parseInt(args[1]));
		
		System.err.println(as);
		System.err.println(w);
		System.err.println(ac);
		System.err.println(f0);
		
		double [] buf = new double [f0.getFrameSize()];
		FrameOutputStream fos = new FrameOutputStream(buf.length);
		
		while (f0.read(buf)) {
			// fos.write(buf);
			System.out.println(buf[0]);
		}
		fos.close();
	}
}
