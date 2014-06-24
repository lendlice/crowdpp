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
package de.fau.cs.jstk.sampled;

import java.io.IOException;

/**
 * Use the Synthesizer to generate synthetic audio data for test usage.
 * Extending classes need to implement the synthesize method, utilizing the
 * protected member samples indicating the number of samples passed since init.
 * 
 * @author sikoried
 * 
 */
public abstract class Synthesizer implements AudioSource {

	public static int DEFAULT_SAMPLE_RATE = 16000;
	
	/** sample rate of the synthesizer */
	private int sr = DEFAULT_SAMPLE_RATE;
	
	/** number of samples already read */
	private int samples = 0;
	
	/** duration of the synthesis in samples */
	private long duration = 0;
	
	/** get the number of already synthesized samples (for internal use) */
	protected int getSamples() { return samples; }
	
	/** get the duration of the synthesis (in samples, not ms!) */
	protected long getDuration() { return duration; }
	
	/** sleep time: number of ms to wait before the read() call is returned */
	private int sleep = 0;
	
	/** blocking source: if true, the read() method will take about 1/sampleRate seconds */
	private boolean blockingSource = false;
	
	private int msPerSample = 1000 / sr;
	
	public Synthesizer() {
	
	}
	
	/**
	 * Generate a Symthesizer of certain duration
	 * @param duration time in ms
	 */
	Synthesizer(long duration) {
		this.duration = sr / 1000 * duration;
		this.msPerSample = 1000 / sr;
	}
	
	Synthesizer(int sampleRate) {
		this.sr = sampleRate;
		this.msPerSample = 1000 / sr;
	}
	
	/**
	 * Generate a specific Synthesizer
	 * @param sampleRate in Hz
	 * @param duration in ms
	 */
	Synthesizer(int sampleRate, long duration) {
		this.sr = sampleRate;
		this.duration = sr / 1000 * duration;
		this.msPerSample = 1000 / sr;
	}
	
	public boolean isBlockingSource() {
		return blockingSource;
	}
	
	public void setBlocking(boolean blocking) {
		blockingSource = blocking;
	}
	
	public boolean getPreEmphasis() {
		return false;
	}
	
	public void setPreEmphasis(boolean applyPreEmphasis, double a) {
		throw new RuntimeException("method unimplemented");
	}

	public int getSampleRate() {
		return sr;
	}
	
	public void setSleepTime(int sleep) {
		this.sleep = sleep;
	}
	
	public int getSleepTime() {
		return sleep;
	}
	
	private boolean end_of_stream = false;
	

	public int read(double[] buf) throws IOException {
		return read(buf, buf.length);
	}
	
	/**
	 * This function handles the memory i/o and length of the stream (if
	 * applicable). Calls the virtual synthesize method.
	 * 
	 * @see synthesize
	 */
	public int read(double[] buf, int length) throws IOException {
		if (end_of_stream)
			return -1;
		
		int read = length;
		
		// check for end of stream
		if (duration != 0 && samples + length > duration) {
			end_of_stream = true;
			read = (int)(duration - samples);
		}
		
		// remember timestamp
		long ts = 0;
		
		if (blockingSource)
			ts = System.currentTimeMillis();
		
		// synthesize the signal
		synthesize(buf, read);
		
		// increase counter
		samples += read;
		
		// simulate a blocking audio source, good for visualizations
		try {
			long sleep = this.sleep;
			
			// blocking source? compute the remaining sleep time
			if (blockingSource) {
				sleep = msPerSample*length - (System.currentTimeMillis() - ts);
			}
			
			if (sleep > 0) 
				Thread.sleep(sleep); 
		} catch (Exception e) {
			// nothing to do
		}
		
		return read;
	}
	
	/**
	 * Actual synthesizer to be implemented by extending class. If the 
	 * absolute time is required, use the protected variable samples.
	 * 
	 * @see getSamples
	 * @see getDuration
	 * @see getSampleRate
	 * 
	 * @param buf Buffer to save values to
	 * @param n number of samples to generate (0 < n <= buf.length)
	 * @param to time offset in samples from the beginning
	 */
	protected abstract void synthesize(double [] buf, int n);

	/**
	 * String representation of the actual synthesizer
	 */
	public abstract String toString();

	public static void main(String [] args) throws IOException {
		if (args.length < 2) {
			String synopsis = "usage: Synthesizer duration(ms) freq1 [freq2 ...]> ssg\noutput is 16kHz 16bit ssg";
			System.out.println(synopsis);
			System.exit(1);
		}
		
		double [] freqs = new double [args.length-1];
		for (int i = 1; i < args.length; ++i)
			freqs[i-1] = Double.parseDouble(args[i]);
		
		SineGenerator sg = new SineGenerator(Long.parseLong(args[0]), freqs);
		
		System.err.println(sg);
		
		double [] buf = new double [160];
		while (sg.read(buf) > 0) {
			// convert double into 2byte sample
			for (double d : buf) {
				short s = new Double(d*((double)Short.MAX_VALUE + 1)).shortValue();
				byte [] b = new byte[] { (byte)(s & 0x00FF), (byte)((s & 0xFF00)>>8) };
				System.out.write(b);
			}
			System.out.flush();
		}
		
		
	}
}
