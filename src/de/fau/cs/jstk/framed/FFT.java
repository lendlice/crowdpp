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


import java.io.File;
import java.io.IOException;

import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.sampled.AudioSource;
import de.fau.cs.jstk.sampled.RawAudioFormat;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;


public class FFT extends SpectralTransformation implements FrameSource {

	/** default minimum coefficients for FFT, padding w/ zeros if required */
	private static int MINIMUM_FFT_COEFFICIENTS = 512;
		
	/** normalize the spectrum energy to 1 */
	private boolean normalize = true;
	
	private double spectralEnergy;
	
	/** input frame size */
	private int fs_in = 0;
	
	/** output frame size */
	private int fs_out = 0;
	
	/** internal read buffer */
	private double [] buf_read = null;
	
	/** internal fft buffer */
	private double [] buf_fft = null;
	
	/** FFT object */
	private DoubleFFT_1D fft = null;
	
    public FFT(FrameSource source, int min_fft_size) {
        this(source, true, true, 1, min_fft_size);
    }

	
	/**
	 * Construct a new FFT object. Depending on the source frame size, the output
	 * frame size will be 512 or the next power of 2. Frames will be padded with 
	 * zeros. This is done to allow for a better frequency resolution.
	 * @param source FrameSource to read from
	 */
	public FFT(FrameSource source) {
		this(source, true, true, 0, MINIMUM_FFT_COEFFICIENTS);
	}
	
	/**
	 * Construct a new FFT object. 
	 * @param source FrameSource to read from
	 * @param pad pad to the next power of 2
	 * @param normalize normalize the spectral energy to 1
	 */
	public FFT(FrameSource source, boolean pad, boolean normalize) {
		this(source, pad, normalize, 0, MINIMUM_FFT_COEFFICIENTS);
	}
	
	/**
	 * Construct a new FFT object.
	 * @param source FrameSource to read from
	 * @param pad pad to the next power of 2
	 * @param normalize normalize the spectral energy to 1
	 * @param coefficients number of FFT coefficients to use, 0 if default (source.getFrameSize())
	 * @param min_fft_coefficients minimum coefficients for FFT, padding w/ zeros if required
	 */
	public FFT(FrameSource source, boolean pad, boolean normalize, int coefficients, int min_fft_coefficients) {
		this.source = source;
		this.normalize = normalize;
		
		// init internal buffers
		fs_in = source.getFrameSize();
		buf_read = new double [fs_in];
		
		blockSize = (coefficients > 0 ? coefficients : fs_in);
		
		if (pad) {
			// pad to the next power of 2, min 512
			int min = min_fft_coefficients; // MINIMUM_FFT_COEFFICIENTS;
			
			while (min <= blockSize)
				min = min << 1;
			
			blockSize = min;
		} else {
			// maybe the frame is larger than the default fft frame?
			if (blockSize < fs_in)
				blockSize = fs_in;
		}
		
		// set up FFT
		fft = new DoubleFFT_1D(blockSize);
		buf_fft = new double [blockSize];
		fs_out = blockSize/2 + 1;
	}
	
	/**
	 * Change normalization parameter at runtime.
	 * @param normalize
	 */
	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}
	
	public int getFrameSize() {
		return fs_out;
	}
		
	/**
	 * Read the next frame and apply FFT, and compute the squared spectral magnitude. The output data size is (in/2 + in%2).
	 */
	public boolean read(double[] buf) 
		throws IOException {
		// read frame from source
		if (!source.read(buf_read))
			return false;
		
		// copy data, pad w/ zeros
		System.arraycopy(buf_read, 0, buf_fft, 0, fs_in);
		for (int i = fs_in; i < blockSize; ++i)
			buf_fft[i] = 0.;
		
		// compute FFT and power spectrum
		fft.realForward(buf_fft);
		
		// refer to the documentation of DoubleFFT_1D.realForward for indexing!
		buf[0] = Math.abs(buf_fft[0]);
		spectralEnergy = buf[0];
		
		for (int i = 1; i < (blockSize - (blockSize % 2))/2; ++i) {
			// buf[i] = Math.sqrt(buf_fft[2*i]*buf_fft[2*i] + buf_fft[2*i+1]*buf_fft[2*i+1]);
			buf[i] = buf_fft[2*i]*buf_fft[2*i] + buf_fft[2*i+1]*buf_fft[2*i+1];
			spectralEnergy += buf[i];
		}
		
		if (blockSize % 2 == 0)
			buf[blockSize/2] = buf_fft[1] * buf_fft[1]; // Math.abs(buf_fft[1]);
		else
			buf[blockSize/2] = buf_fft[blockSize-1]*buf_fft[blockSize-1] + buf_fft[1]*buf_fft[1]; // Math.sqrt(buf_fft[blockSize-1]*buf_fft[blockSize-1] + buf_fft[1]*buf_fft[1]);
		
		spectralEnergy += buf[blockSize/2];
		
		// normalize the spectral energy to 1
		if (normalize && spectralEnergy > 0.) {
			for (int i = 0; i < fs_out; ++i)
				buf[i] /= spectralEnergy;
		}
			
		return true;
	}
	
	public double getRawSpectralEnergy() {
		return spectralEnergy;
	}
	
	public String toString() {
		return "framed.FFT fs_in=" + fs_in + " blockSize=" + blockSize + " fs_out=" + fs_out;
	}

	public FrameSource getSource() {
		return source;
	}
	
	public static final String SYNOPSIS = 
		"sikoried, 4/20/2010\n" +
		"Compute the FFT given a format, file and window description. Output is ASCII\n" +
		"if no output-file is given.\n\n" +
		"usage: framed.FFT <format-string> <window-string> <in-file> [out-file]";
	
	public static void main(String [] args) throws Exception {
		if (args.length < 3 || args.length > 4) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		String sFormat = args[0];
		String sWindow = args[1];
		String inFile = args[2];
		String outFile = (args.length > 3 ? args[3] : null);
		
		AudioSource as = new de.fau.cs.jstk.sampled.AudioFileReader(inFile, RawAudioFormat.create(sFormat), true);
		Window w = Window.create(as, sWindow);
		FrameSource spec = new FFT(w);
		
		FrameOutputStream fw = (outFile == null ? null : new FrameOutputStream(spec.getFrameSize(), new File(outFile)));
		
		double [] buf = new double [spec.getFrameSize()];
		
		while (spec.read(buf)) {
			if (fw != null)
				fw.write(buf);
			else {
				int i = 0;
				for (; i < buf.length-1; ++i)
					System.out.print(buf[i] + " ");
				System.out.println(buf[i]);
			}
		}		
		
		if (fw != null)
			fw.close();
	}	
}
