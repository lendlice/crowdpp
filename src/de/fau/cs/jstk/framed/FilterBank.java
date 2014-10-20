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
import java.util.LinkedList;

import de.fau.cs.jstk.exceptions.MalformedParameterStringException;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.sampled.AudioFileReader;
import de.fau.cs.jstk.sampled.RawAudioFormat;


public class FilterBank implements FrameSource {
	public static class Vtln {
		public double low;
		public double high; 
		public double factor;
		
		public Vtln(double low, double high, double factor) {
			this.low = low;
			this.high = high;
			this.factor = factor;
		}
	}
	
	/** Epsilon constant for logarithm */
	public static double EPSILON = 1E-6;
	
	/// frame source to read from (usually a power spectrum)
	private SpectralTransformation source;
	
	/// read buffer
	private double [] buf;
	
	/// Filters to apply to the input vector
	private Filter [] filterBank;
	
	public FilterBank(SpectralTransformation source, Filter [] filterBank) {
		this.source = source;
		this.filterBank = filterBank;
		
		buf = new double [source.getFrameSize()];
	}
	
	public int getFrameSize() {
		return filterBank.length;
	}

	public FrameSource getSource() {
		return source;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("framed.FilterBank (" + filterBank.length + " filters)\n");
		for (Filter f : filterBank)
			sb.append(f + "\n");
		return sb.toString();
	}
	
	/**
	 * Apply all filters to the incoming array.
	 */
	public boolean read(double [] buf) throws IOException {
		if (!source.read(this.buf))
			return false;
		
		for (int i = 0; i < filterBank.length; ++i)
			buf[i] = filterBank[i].apply(this.buf); 
		
		return true;
	}

	/**
	 * Any filter of the filter bank needs to provide the apply function. This
	 * allows for simple and complex filter algorithms transparently.
	 * 
	 * @author sikoried
	 */
	public interface Filter {
		public double apply(double [] frame);
	}
	
	/**
	 * The linear filter applies it's weights as a scalar and normalizes the
	 * resulting sum by the sum of weights. Most common flavors are the
	 * rectangular and triangular weights.
	 * 
	 * @author sikoried
	 */
	public static class LinearFilter implements Filter {
		private boolean log = true;
		private int from, to;
		
		private double [] wt;
		private double wt_sum;
		
		/**
		 * Prepare a new linear filter with the given weights and apply log
		 * after summation.
		 * @param from
		 * @param to (included)
		 * @param weights
		 */
		public LinearFilter(int from, int to, double [] weights) {
			this(from, to, weights, true);
		}
		
		/**
		 * Prepare a new linear filter with the given weights.
		 * @param from
		 * @param to (included)
		 * @param weights
		 * @param log apply logarithm?
		 */
		public LinearFilter(int from, int to, double [] weights, boolean log) {
			this.from = from;
			this.to = to;
			this.log = log;
			
			wt = weights.clone();
			wt_sum = 0.;

			for (double w : wt)
				wt_sum += w;
		}
		
		/**
		 * Generate triangular weights for given left, center and right indices.
		 * Note that the weights are computed over a fictional triangle with 
		 * (left-1 ...right+1) to obtain weights for the left and right offsets.
		 * @param left
		 * @param center
		 * @param right
		 * @return
		 */
		public static double [] triangularWeights(int left, int center, int right) {
			double [] w = new double [right - left + 1];
			
			// left triangle slope
			double dleft = 1. / ((int)(center - left) + 1);
			
			// right triangle slope
			double dright = 1. / ((int)(right - center) + 1);
			
			double cw = 0;
			int k = 0, j;
			
			// ascending left part
			for (j = left; j <= center; ++j)
				w[k++] = (cw += dleft);
			
			// descending right part
			cw = 1.;
			for (; j <= right; ++j)
				w[k++] = (cw -= dright);
			
			return w;
		}
		
		/**
		 * Generate a weight array consisting of ones for uniform rectangle
		 * @param from
		 * @param to
		 * @return
		 */
		public static double [] rectangularWeights(int from, int to) {
			double [] w = new double [to - from + 1];
			Arrays.fill(w, 1.);
			return w;
		}
		
		public double apply(double [] frame) {
			double accu = 0.;
			
			// sum over triangle
			int j, k = 0;
			for (j = from; j <= to; ++j)
				accu += wt[k++] * frame[j];
			
			// normalize
			accu /= wt_sum;
			
			// log?
			if (log)
				accu = Math.log(accu + EPSILON);
			
			return accu;
		}
		
		public String toString() {
			return "LinearFilter " + from + "-" + to + " log=" + log + " wt=" + Arrays.toString(wt);
		}
	}

	/** mel frequency to Hz */
	public static double fmel2fHz(double fmel) {
	    return (Math.exp( fmel / 1127.) - 1.) * 700.;
	}

	/** Hz to mel frequency */
	public static double fHz2fmel(double fHz) {
	    return 1127. * Math.log(1. + fHz / 700.);
	}
	
	/** Hz to power spectrum index */
	private static int fHz2Ind(double fHz, SpectralTransformation st) {
		int ind = (int) Math.round(fHz / st.getResolution());
		if (ind < 0)
			ind = 0;
		else if (ind >= st.getFrameSize())
			ind = st.getFrameSize() - 1;
		return ind;
	}
	
	/** mel to power spectrum index */
	private static int fmel2Ind(double fmel, SpectralTransformation st) {
		return fHz2Ind(fmel2fHz(fmel), st);
	}
	
	/**
	 * Warp the frequency for vocal tract length normalization (VTLN), from
	 * kaldi src/feat/mel-computations.cc
	 * 
	 * @param vtln_low_cutoff
	 * @param vtln_high_cutoff
	 * @param low_freq
	 * @param high_freq
	 * @param vtln_warp_factor
	 * @param freq
	 * @return
	 */
	private static double vtlnWarpFreq(double vtln_low_cutoff, double vtln_high_cutoff,
			double low_freq, double high_freq, double vtln_warp_factor, double freq) {
		if (freq < low_freq || freq > high_freq)
			return freq;

		if (vtln_low_cutoff <= low_freq)
			throw new RuntimeException("vtln_low_cutoff <= low_freq");
		if (vtln_high_cutoff >= high_freq)
			throw new RuntimeException("vtln_high_cutoff >= high_freq");

		double l = vtln_low_cutoff * Math.max(1., vtln_warp_factor);
		double h = vtln_high_cutoff * Math.min(1., vtln_warp_factor);
		double scale = 1. / vtln_warp_factor;
		double Fl = scale * l; // F(l);
		double Fh = scale * h; // F(h);

		if (l <= low_freq || h >= high_freq)
			throw new RuntimeException("l <= low_freq || h >= high_freq");

		// slope of left part of the 3-piece linear function
		double scale_left = (Fl - low_freq) / (l - low_freq);
		// [slope of center part is just "scale"]

		// slope of right part of the 3-piece linear function
		double scale_right = (high_freq - Fh) / (high_freq - h);

		if (freq < l) {
			return low_freq + scale_left * (freq - low_freq);
		} else if (freq < h) {
			return scale * freq;
		} else {
			return high_freq + scale_right * (freq - high_freq);
		}
	}
	
	/**
	 * Warp the frequency for vocal tract length normalization (VTLN), from
	 * kaldi src/feat/mel-computations.cc
	 * 
	 * @param vtln_low_cutoff
	 * @param vtln_high_cutoff
	 * @param low_freq
	 * @param high_freq
	 * @param vtln_warp_factor
	 * @param mel
	 * @return
	 */
	private static double vtlnWarpMelFreq(double vtln_low_cutoff, double vtln_high_cutoff,
			double low_freq, double high_freq, double vtln_warp_factor, double mel) {
		 return fHz2fmel(vtlnWarpFreq(vtln_low_cutoff, vtln_high_cutoff,
                 low_freq, high_freq,
                 vtln_warp_factor, fmel2fHz(mel)));
	}
	
	public static FilterBank generateMelFilterBank(SpectralTransformation st, String parameterString) 
			throws MalformedParameterStringException {
		return generateMelFilterBank(st, parameterString, null);
	}
	
	public static FilterBank generateMelFilterBank(SpectralTransformation st, String parameterString, Vtln vtln)
		throws MalformedParameterStringException {
		
		/** Default lower boundary frequency (Hz) of mel filter bank */
		final double DEFAULT_LB = 188.;
		
		/** Default upper boundary frequency (Hz) of mel filter bank */
		final double DEFAULT_UB = 6071.;
		
		/** Default filter width in mel */
		final double DEFAULT_FW = 226.79982;
		
		/** Default filter overlap */
		final double DEFAULT_FO = 0.5;
				
		double lb = DEFAULT_LB;
		double ub = DEFAULT_UB;
		double fw = DEFAULT_FW;
		double fo = DEFAULT_FO;
		
		/** apply log after filter? */
		boolean logMel = true;
		
		if (parameterString != null) {
			String [] help = parameterString.split(",");
			lb = Double.parseDouble(help[0]);
			ub = Double.parseDouble(help[1]);
			fw = Double.parseDouble(help[2]);
			fo = Double.parseDouble(help[3]);
					
			// check for requested defaults
			if (lb < 0.)
				lb = DEFAULT_LB;
			if (ub < 0.)
				ub = DEFAULT_UB;
			if (fw < 0.)
				fw = DEFAULT_FW;
			if (fo < 0.)
				fo = DEFAULT_FO;
		}
	
		return new FilterBank(st, generateMelFilterBank(st, logMel, fw, lb, ub, fo, vtln));
	}

	/**
	 * Generate a Mel filter bank for the given parameters
	 * @param frameSize
	 * @param sampleRate
	 * @param log
	 * @param filterWidthInMel
	 * @param lowerBoundary
	 * @param upperBoundary
	 * @param minNumFilters
	 * @param vtln_factor
	 * @return
	 */
	public static Filter [] generateMelFilterBank(SpectralTransformation st, 
			boolean log, 
			double filterWidthInMel, 
			double lowerBoundary, // filterbank boundaries (Hz) 
			double upperBoundary, 
			double minNumFilters, double vtln_factor) {
		return generateMelFilterBank(st, log, filterWidthInMel, lowerBoundary, upperBoundary, minNumFilters, new Vtln(lowerBoundary + 100., upperBoundary - 500, vtln_factor));
	}
	
	/**
	 * Generate a Mel filter bank for the given parameters
	 * @param frameSize
	 * @param sampleRate
	 * @param log
	 * @param filterWidthInMel
	 * @param lowerBoundary
	 * @param upperBoundary
	 * @param minNumFilters
	 * @return
	 */
	public static Filter [] generateMelFilterBank(SpectralTransformation st, 
			boolean log, 
			double filterWidthInMel, 
			double lowerBoundary, // filterbank boundaries (Hz) 
			double upperBoundary, 
			double minNumFilters) {
		return generateMelFilterBank(st, log, filterWidthInMel, lowerBoundary, upperBoundary, minNumFilters, null);
	}
	/**
	 * Generate a Mel filter bank for the given parameters
	 * @param frameSize
	 * @param sampleRate
	 * @param log
	 * @param filterWidthInMel
	 * @param lowerBoundary
	 * @param upperBoundary
	 * @param minNumFilters
	 * @param vtln_low 
	 * @param vtln_high
	 * @param vtln_factor
	 * @return
	 */
	public static Filter [] generateMelFilterBank(SpectralTransformation st, 
			boolean log, 
			double filterWidthInMel, 
			double lowerBoundary, // filterbank boundaries (Hz) 
			double upperBoundary, 
			double minNumFilters, 
			Vtln vtln) {
		
		// start and end of the filter bank
		double lb_mel = fHz2fmel(lowerBoundary);
		double ub_mel = fHz2fmel(upperBoundary);
		
		// determine the number of filters and filter overlap
		double fo;
		if (minNumFilters < 1.)
			fo = (1. - minNumFilters);
		else
			fo = .5;
		
		int sfb = (int) Math.ceil((ub_mel - lb_mel - filterWidthInMel) / (filterWidthInMel * fo) + 1);
		
		// minimum number of filters satisfied?
		if (minNumFilters >= 1. && sfb < minNumFilters)
			sfb = (int) minNumFilters;
		
		// in case it didn't match, we need to adjust the overlap a bit
		fo = (ub_mel - lb_mel - filterWidthInMel) / (filterWidthInMel * (sfb-1.));
		
		// build up filters
		Filter [] filters = new Filter [sfb];
		
		// compute triangular frequencies and indices
		for (int i = 0; i < sfb; ++i) {
			double l = lb_mel + fo*filterWidthInMel*i;
			double r = lb_mel + filterWidthInMel + fo*filterWidthInMel*i;
			
			// VTLN?
			if (vtln != null) {
				l = vtlnWarpMelFreq(vtln.low, vtln.high, lowerBoundary, upperBoundary, vtln.factor, l);
				r = vtlnWarpMelFreq(vtln.low, vtln.high, lowerBoundary, upperBoundary, vtln.factor, r);
			}
			
			filters[i] = generateMelFilter(st, l, r, FilterType.TRIANGULAR, true);
		}
		
		return filters;
	}

	/**
	 * Generage
	 * @param st
	 * @param log
	 * @param param
	 * @return
	 */
	public static FilterBank generateFilterBank(SpectralTransformation st, boolean log, String param)
		throws MalformedParameterStringException {
		LinkedList<Filter> fb = new LinkedList<Filter>();
		
		String [] fd = param.trim().split(":");
		
		for (String f : fd) {
			String [] p = f.split(",");
			
			if (p.length != 4)
				throw new MalformedParameterStringException("Invalid filter bank format '" + f + "'; must be <type>,<shape>,<start>,<end>");
			
			FilterType ft = null;
			if (p[1].equals("r"))
				ft = FilterType.RECTANGULAR;
			else if (p[1].equals("t"))
				ft = FilterType.TRIANGULAR;
			else
				throw new MalformedParameterStringException("Unknown filter bank shape '" + p[1] + "'");
			
			if (p[0].equals("m"))
				fb.add(generateMelFilter(st, Double.parseDouble(p[2]), Double.parseDouble(p[3]), ft, log));
			else if (p[0].equals("f"))
				fb.add(generateFreqFilter(st, Double.parseDouble(p[2]), Double.parseDouble(p[3]), ft, log));
			else
				throw new MalformedParameterStringException("Invalid filter bank type '" + p[0] + "' must be mel or freq");
		}
		
		return new FilterBank(st, fb.toArray(new Filter [fb.size()]));
	}
	
	/** Available filter types */
	public static enum FilterType {
		RECTANGULAR, /// all samples in frame equally weighted
		TRIANGULAR   /// triangular, peaking in the center
	}
	
	/**
	 * Generate a filter for the given requirements
	 * @param frameSize input frame size
	 * @param sampleRate underlying sample rate
	 * @param fromFreq from (Hz)
	 * @param toFreq including (Hz)
	 * @param type 
	 * @param log apply log?
	 * @return
	 */
	public static Filter generateFreqFilter(SpectralTransformation st, double fromFreq, double toFreq, FilterType type, boolean log) {
		Filter ret = null;
		
		int from = fHz2Ind(fromFreq, st);
		int to = fHz2Ind(toFreq, st);
		
		switch (type) {
		case RECTANGULAR:
			ret = new LinearFilter(from, to, LinearFilter.rectangularWeights(from, to), log);
			break;
		case TRIANGULAR:
			int center = fHz2Ind(fromFreq + (toFreq - fromFreq) / 2., st);
			ret = new LinearFilter(from, to, LinearFilter.triangularWeights(from, center, to), log);
			break;
		}
		
		return ret;
	}
	
	/**
	 * Generate a filter for the given requirements
	 * @param frameSize input frame size
	 * @param sampleRate underlying sample rate
	 * @param fromMel from (mel)
	 * @param toMel including (mel)
	 * @param type
	 * @param log apply log?
	 * @return
	 */
	public static Filter generateMelFilter(SpectralTransformation st, double fromMel, double toMel, FilterType type, boolean log) {
		Filter ret = null;
		
		int from = fmel2Ind(fromMel, st);
		int to = fmel2Ind(toMel, st);
		
		switch (type) {
		case RECTANGULAR:
			ret = new LinearFilter(from, to, LinearFilter.rectangularWeights(from, to), log);
			break;
		case TRIANGULAR:
			int center = fmel2Ind(fromMel + (toMel - fromMel) / 2., st);
			ret = new LinearFilter(from, to, LinearFilter.triangularWeights(from, center, to), log);
			break;
		}
		
		return ret;
	}
	
	public static void main(String [] args) throws Exception {
		AudioFileReader afr = new AudioFileReader(System.in, RawAudioFormat.getRawAudioFormat("ssg/16"), true);
		Window wnd = new HammingWindow(afr, 25, 10, false);
		FFT fft = new FFT(wnd);
		
		for (double w : new double [] { 0.6, 1.0, 1.4 }) {
			Filter [] fb = generateMelFilterBank(
					fft, 
					true, 
					226.79982, 
					20., 
					8000., 
					23, 
					new Vtln(100, 7400,	w));
			System.err.println("fb.length " + fb.length);
			for (Filter f : fb) 
				System.err.println(f.toString());
		}
	}
}
