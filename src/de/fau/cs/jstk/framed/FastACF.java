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
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

/**
 *	This class serves for computing the autocorrelation function (ACF).
 *	The computing is done using the FFT.
 * 	If a window was applied to the signal, the first half of the ACF
 *  is divided by the ACF values of the window
 */
public class FastACF implements AutoCorrelation {

	/** FrameSource to read from */
	private FrameSource source;

	/** internal read buffer */
	private double [] buf;
	
	/**internal fft buffer*/
	private double [] buf_fft;

	/** outgoing frame size */
	private int fsOut;
	
	/** Incoming frame size*/
	private int fs_in;
	
	/** internal fft frame size*/
	private int fs_fft;
	
	/** FFT buffer size*/
	private int fs_fft_int;
	
	/** FFT object */
	private DoubleFFT_1D fft = null;
	
	/** Autrocorrelation values of Window*/
	double [] wacf = null;
	
	/**
	 * Construct an AutoCorrelation object using the given source to read from.
	 * If the underlying source is a Window, the first half of the ACF is 
	 * divided by the ACF values of the window
	 * 
	 * @param source
	 */
	public FastACF(FrameSource source) {
		this.source = source;
		this.init();
	}
	
	public int getFrameSize() {
		return fsOut;
	}

	public FrameSource getSource() {
	    return source;
	}

	private void init() {
		// init internal buffer
		fs_in = source.getFrameSize();
		buf = new double [fs_in];
		
		// we applied a window thus get its ACF
		if (source instanceof Window) {
			wacf = new double [source.getFrameSize()];
			SimpleACF.ac(((Window) source).getWeights(), wacf);
		}
	
		// searching for power of 2 for FFT length
		double log2L = Math.log(fs_in) / Math.log(2);
		int exp = (int) Math.ceil(log2L);
		fs_fft = 2 * (int) Math.pow(2., exp);
		fs_fft_int = 2 * fs_fft;
		buf_fft = new double [fs_fft_int];	
		
		fft = new DoubleFFT_1D(fs_fft);
		fsOut = fs_in;
	}
	
	/**
	 * Reads from the FrameSource and computes the autocorrelation using the FFT
	 * ACF = IFFT ( |FFT|^2 )
	 */
	public boolean read(double[] buf) throws IOException {
		if (!source.read(this.buf))
			return false;
		
		System.arraycopy(this.buf, 0, buf_fft, 0, fs_in);
		
		// compute FFT
		fft.realForwardFull(buf_fft);

		// setting absolute values as real part and zero for imaginary part
		// <-> compute power spectrum 
		for (int i = 0; i < fs_fft_int/2; i++){
			double tmp;
			tmp = buf_fft[2*i] * buf_fft[2*i] + buf_fft[2*i+1] * buf_fft[2*i+1];
			buf_fft[2*i] = tmp;
			buf_fft[2*i+1] = 0.;
		}

		// compute inverse FFT
		fft.complexInverse(buf_fft, true);
		
		// get the real part
		// divide first half! by ACF of window if FrameSource source was a Window
		double firstValue = buf_fft[0];
		if(wacf != null){
			firstValue /= wacf[0];
			
			for (int i = 0; i < (int)(fsOut * .5); i++)
				buf[i] = buf_fft[2*i] / firstValue/wacf[i];
			
			for (int i = (int)(fsOut * .5); i < fsOut; i++)	
				buf[i] = buf_fft[2*i] / firstValue;		
		} else {
			buf[0] = buf_fft[0];
			for (int i = 0; i < fsOut; i++)	
				buf[i] = buf_fft[2*i] / firstValue;
		}
		
		return true;
	}
	
	public double[] getReadBuf(){
		return buf;
	}
	
	public int getReadBufFrameSize(){
		return buf.length;
	}
	
	public String toString() {
		return "framed.FastACF fs_in= " + fs_in + " fs_fft=" + fs_fft;
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
		FastACF acf = new FastACF(window);
		
		System.err.println(as);
		System.err.println(window);
		System.err.println(acf);
		
		double [] buf = new double[acf.getFrameSize()];

		FrameOutputStream fos = new FrameOutputStream(buf.length);
		
		while (acf.read(buf))
			fos.write(buf);
		
		fos.close();
	}

	@Override
	public boolean firstIndexVUVDecision() {
		return false;
	}
}
