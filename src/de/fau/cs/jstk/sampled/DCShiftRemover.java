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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DCShiftRemover {
	/** Default context for measuring the DC shift (in ms)*/
	public static final int DEFAULT_CONTEXT_SIZE = 1000;
	
	/** context size in samples */
	private int css;
	
	/** current mean */
	private long mean = 0;
	
	/** history of samples (ring buffer) */
	private long [] hist;
	
	/** current write index for history */
	private int ind = 0;
	
	/** bit rate used */
	private int fs;
	
	/**
	 * Create a DC shift remover for the given AudioSource
	 * @param source
	 */
	public DCShiftRemover(AudioSource source, int bitRate) {
		this(source, DEFAULT_CONTEXT_SIZE, bitRate);
	}
	
	/**
	 * Create a DC shift remover for the given Audiosource and context size
	 * @param source
	 * @param contextSize context to measure DC shift in ms
	 */
	public DCShiftRemover(AudioSource source, int contextSize, int bitRate) {
		fs = bitRate / 2;
		css = source.getSampleRate() / 1000 * contextSize;
		hist = new long [css];
	}
	
	/**
	 * Read from the AudioSource and apply the DC shift. Note that the shift
	 * requires some runtime to function properly
	 */
	public void removeDC(byte [] buf, int read) throws IOException {
		int transferred = 0;
		
		// mind the bit rate
		if (fs == 1) {
			// 8bit: just copy; it's signed and little endian
			for (int i = 0; i < read; ++i) {
				hist[(ind + i) % css] = (long) buf[i];
				transferred++;
			}
		} else {
			// > 8bit
			ByteBuffer bb = ByteBuffer.wrap(buf);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			int i;
			for (i = 0; i < read / fs; ++i) {
				if (fs == 2) {
					hist[(ind + i) % css] = (long) bb.getShort();
				} else if (fs == 4) {
					hist[(ind + i) % css] = (long) bb.getInt();
				} else
					throw new IOException("unsupported bit rate");
				transferred++;
			}
		}
		
		// get mean
		mean = 0;
		for (long d : hist)
			mean += d / css;
		
		// apply the dc shift, retransform to byte array
		for (int i = 0; i < transferred; ++i) {
			if (fs == 1)
				buf[i] = (byte) (hist[ind + i] - mean);
			else {
				ByteBuffer bb = ByteBuffer.allocate(fs);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				
				if (fs == 2)
					bb.putShort((short)hist[ind + i]);
				else if (fs == 4)
					bb.putInt((int)hist[ind + i]);
				
				System.arraycopy(bb.array(), 0, buf, i*fs, fs);
			}
		}
		
		// increment the ring buffer index
		ind = (ind + transferred) % css;
	}
	
	/**
	 * Local DC shift removal (simple mean subtraction)
	 * @param buf
	 */
	public static void removeDC(double [] buf) {
		removeDC(buf, buf.length);
	}
	
	public static void removeDC(double [] buf, int n) {
		double m = 0.;
		double k = 1.0 / n;
		
		for (int i = 0; i < n; ++i)
			m += k * buf[i];
		
		for (int i = 0; i < n; ++i)
			buf[i] -= m;
	}
}
