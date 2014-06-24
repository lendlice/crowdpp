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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;



/**
 * Use this class to capture audio directly from a microphone. The default i
 * s 16kHz at 16bit. Other sampling and bit rates are possible, but capturing is 
 * limited to signed, little endian.
 * 
 * Use the main program to list available mixer names; if no mixer name is 
 * specified on instantiation, the Java AudioSystem default is used.
 * 
 * @author sikoried
 *
 */
public class AudioCapture implements AudioSource {

	AudioFormat	af = null;
	AudioInputStream ais = null;
	
	private TargetDataLine tdl = null;
	
	/** memorize the bit rate */
	private int br = 16;
	
	/** memorize the frame size */
	private int fs = 0;
	
	private static final int DEFAULT_SAMPLE_RATE = 16000;
	private static final int DEFAULT_BIT_RATE = 16;
	
	/** apply pre-emphasis? */
	private boolean preemphasize = true;
	
	/** value required for first frame of pre-emphasis */
	private double s0 = 0.;
	
	/** pre-emphasis factor */
	private double a = AudioFileReader.DEFAULT_PREEMPHASIS_FACTOR;
	
	/** factor to scale signed values to -1...1 */
	private double scale = 1.;
	
	/** default size for internal buffer */
//	private static int DEFAULT_INTERNAL_BUFFER = 128;
	
	private double desiredBufDur = 0, actualBufDur;
	
	/** internal buffer as substitute for external */
	private double [] internalBuffer = null;
	
	/** name of the target mixer or null if default mixer */
	public String mixerName = null;
	
	/** For microphone critical applications: do not fall back to default by default */
	private static final boolean DEFAULT_MIXER_FALLBACK = false;
	
	/** if the desired mixer is not available, fall back to default? */
	private boolean defaultMixerFallBack = DEFAULT_MIXER_FALLBACK;
	
	/** remove DC shift if desired */
	private DCShiftRemover dc = null;
	
	/**
	 * Create an AudioCapture object reading from the specified mixer. To obtain
	 * a mixer name, use AudioCapture.main and call w/ argument "-L"
	 * @param mixerName Name of the mixer to use
	 * @param defaultMixerFallBack fall back to default mixer if desired mixer not available
	 * @throws IOException
	 */
	public AudioCapture(String mixerName, boolean defaultMixerFallBack) 
		throws IOException {
		this.defaultMixerFallBack = defaultMixerFallBack;
		af = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				DEFAULT_SAMPLE_RATE, 
				DEFAULT_BIT_RATE, 
				1, 
				DEFAULT_BIT_RATE/8, 
				DEFAULT_SAMPLE_RATE, 
				false
		);
		this.mixerName = mixerName;
		initialize();
	}
	
	/**
	 * Create an AudioCapture object reading from the specified mixer. To obtain
	 * a mixer name, use AudioCapture.main and call w/ argument "-L"
	 * @param mixerName Name of the mixer to use; searches for any occurance of 
	 * 'mixerName' in the available mixer names
	 * @throws IOException
	 */
	public AudioCapture(String mixerName) 
		throws IOException {
		this(mixerName, DEFAULT_MIXER_FALLBACK);
	}
	
	/**
	 * Create an AudioCapture object reading from the specified mixer using 
	 * the given sample rate and bit rate. To obtain a mixer name, use AudioCapture.main
	 * and call w/ argument "-L"
	 * @param mixerName Name of the mixer to read from
	 * @param defaultMixerFallBack fall back to default mixer if desired mixer not available
	 * @param bitRate bit rate to use (usually 8 or 16 bit)
	 * @param frameRate sample rate to read (usually 8000 or 16000)
	 * @throws IOException
	 */
	public AudioCapture(String mixerName, boolean defaultMixerFallBack, int bitRate, int frameRate,
			double desiredBufDur)
		throws IOException {
		this.defaultMixerFallBack = defaultMixerFallBack;
		this.mixerName = mixerName;		
		af = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				frameRate, 
				bitRate, 
				1, 
				bitRate/8, 
				frameRate, 
				false
		);
		this.desiredBufDur = desiredBufDur;
		initialize();

	}		
	
	/**
	 * Create an AudioCapture object reading from the specified mixer using 
	 * the given sample rate and bit rate. To obtain a mixer name, use AudioCapture.main
	 * and call w/ argument "-L"
	 * @param mixerName Name of the mixer to read from
	 * @param bitRate bit rate to use (usually 8 or 16 bit)
	 * @param sampleRate sample rate to read (usually 8000 or 16000)
	 * @param desiredBufDur duration of line buffer in seconds (=latency). 
	 *        Use 0.0 to let TargetDataLine.open decide
	 * @throws IOException
	 */
	public AudioCapture(String mixerName, int bitRate, int sampleRate, double desiredBufDur) 
		throws IOException {
		this(mixerName, DEFAULT_MIXER_FALLBACK, bitRate, sampleRate, desiredBufDur);
	}
	
	/**
	 * Create the default capture object: 16kHz at 16bit
	 * @throws IOException
	 */
	public AudioCapture() 
		throws IOException {
		this(null);
	}
	
	/**
	 * Create a specific capture object (bound to signed, little endian).
	 * @param bitRate target bit rate (usually 8 or 16bit)
	 * @param sampleRate target sample rate (usually 8 or 16kHz)
	 * @throws IOException
	 */
	public AudioCapture(int bitRate, int sampleRate) 
		throws IOException {
		this(null, bitRate, sampleRate, 0.0);
	}
	
	/**
	 * Initialize the (blocking) capture: query the device, set up and start
	 * the data lines.
	 * @throws IOException
	 */
	private void initialize()
		throws IOException {
		
		br = af.getSampleSizeInBits();
		fs = br/8;
		
		// query a capture device according to the audio format
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, af);
		
		try {
			if (mixerName != null) {
				// query target mixer line
				Mixer.Info target = MixerUtil.getMixerInfoFromName(mixerName, true);			
				
				if (target != null)
					tdl = (TargetDataLine) AudioSystem.getMixer(target).getLine(info);
				
				if (tdl == null) {
					if (defaultMixerFallBack) {
						System.err.println("WARNING: couldn't query mixer '" + mixerName + "', falling back to default mixer");
						mixerName = null;
					} else
						throw new IOException("the desired mixer '" + mixerName + "' was not available");
				}
			}
			
			if (tdl == null) {
				// get default mixer line
				tdl = (TargetDataLine) AudioSystem.getLine(info);
			}
			
			try{
				if (desiredBufDur != 0){
					int desiredBufSize = (int)Math.round(desiredBufDur * af.getFrameRate())
					* af.getFrameSize();
					tdl.open(af, desiredBufSize);
					if (tdl.getBufferSize() != desiredBufSize){
						System.err.println("AudioCapture.initialize: could not set desiredBufDur = " + desiredBufDur + 
								" which corresponds to a buffer size of " + desiredBufSize + ". Got bufSize = " + 
								tdl.getBufferSize());
					}
				}
				else
					tdl.open(af);
			}
			// important to catch IllegalArgumentException also!!
			catch (IllegalArgumentException e){
				throw new LineUnavailableException(e.getMessage());
			}


			actualBufDur = tdl.getBufferSize() / af.getFrameSize() / af.getFrameRate();
			
			
			tdl.start();
		} catch (Exception e) {
			throw new IOException("AudioCapture: exception when initializing", e);
		}
		
		// set up the audio stream
		ais = new AudioInputStream(tdl);
		
		// compute the scaling factor
		enableScaling();
	}

	/**
	 * Return the current sampling rate
	 */
	public int getSampleRate() {
		return (int)af.getSampleRate();
	}
	
	public int getBitRate() {
		return br;
	}
	
	public double getActualBufDur(){
		return actualBufDur;
	}
	
	
	/**
	 * Tear down the audio capture environment (free resources)
	 */
	public void tearDown() throws IOException {
		
		/* no, this causes problems e.g. in Windows
		System.err.println("AudioCapture:tearDown: tdl.drain()");
		tdl.drain();
		*/		
		tdl.close();		
		ais.close();		
	}
		

	/** the private reading buffer; will be allocated dynamically */
	private byte [] byteBuf = null;


	public int read(double[] buf) throws IOException{
		return read(buf, 
				buf == null ? internalBuffer.length : buf.length);
	}
	
	/**
	 * Read the next length samples (blocking). Samples are normalized to 
	 * [-1;1]
	 * 
	 * @param buf double buffer; will try to read length samples. 
	 * If buf is null, the internal buffer will be used (call enableInternalBuffer
	 * in advance!)
	 * 
	 * @return number of samples actually read
	 * 
	 * @see AudioFileReader.read
	 */
	public int read(double[] buf, int length) 
		throws IOException {
		
		double [] out = (buf == null ? internalBuffer : buf);			
		
		/* sikoried: I'm not sure how long the OS buffers the data, so the 
		 * processing better be fast ;)
		 */
		
		int ns = length;
		
		// memorize buffer size
		if (this.byteBuf == null || this.byteBuf.length < ns*fs)
			this.byteBuf = new byte [ns*fs];
		
		int readBytes = ais.read(this.byteBuf, 0, ns * fs);
		
		// anything read?		
		if (readBytes < 0)
			return -1;
		else if (readBytes == 0)
			return 0;
		
		int readFrames = readBytes / fs;
		
		// dc shift?
		if (dc != null)
			dc.removeDC(this.byteBuf, readBytes);
		
		// conversion
		if (br == 8) {
			// 8bit: just copy; it's signed and little endian
			for (int i = 0; i < readBytes; ++i) {
				out[i] = scale * (new Byte(this.byteBuf[i]).doubleValue());
				if (out[i] > 1.)
					out[i] = 1.;
				if (buf[i] < -1.)
					out[i] = -1.;
			}			
		} else {
			// > 8bit
			ByteBuffer bb = ByteBuffer.wrap(this.byteBuf);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			int i;
			for (i = 0; i < readFrames; ++i) {
				if (br == 16) {
					out[i] = scale * (double) bb.getShort();
				} else if (br == 32) {
					out[i] = scale * (double) bb.getInt();
				} else
					throw new IOException("unsupported bit rate");
			}			
		}
		
		if (preemphasize) {
			// set out-dated buffer elements to zero
			if (readFrames < ns) {
				for (int i = readFrames; i < ns; ++i)
					buf[i] = 0.;
			}
			
			// remember last signal value
			double help = out[readFrames-1];
			
			AudioFileReader.preEmphasize(out, a, s0);
			
			s0 = help;
		}
		
		return readFrames;
	}

	private static Mixer.Info [] getMixerList(AudioFormat af) {
		return MixerUtil.getMixerList(af, true);
	}
	
	
	
	/**
	 * Return a string representation of the capture device
	 */
	public String toString() {
		return "AudioCapture: " + (mixerName != null ? "(mixer: " + mixerName + ") " : "") + af.toString();
	}
	
	public boolean getPreEmphasis() {
		return preemphasize;
	}
	
	/**
	 * Enable pre-emphasis with given factor
	 */
	public void setPreEmphasis(boolean applyPreEmphasis, double a) {
		preemphasize = applyPreEmphasis;
		this.a = a;
	}
	
	/**
	 * Return the raw buffer; mind 8/16 bit and signed/unsigned conversion!
	 */
	public byte [] getRawBuffer() {
		return this.byteBuf;
	}
	
	/**
	 * Return the converted buffer containing (normalized) values
	 * @return
	 */
	public double [] getBuffer() {
		return internalBuffer;
	}
	
	public AudioFormat getAudioFormat(){
		return af;
	}
	
	/** enables the DC shift or updates the instance for the given context size */
	public void enableDCShift(int contextSize) {
		dc = new DCShiftRemover(this, br, contextSize == 0 ? DCShiftRemover.DEFAULT_CONTEXT_SIZE : contextSize);
	}
	
	/** turn DC shift off */
	public void disableDCShift() {
		dc = null;
	}
	
	/**
	 * Enable the internal Buffer (instead of the local one)
	 * @param bufferSize
	 */
	public void enableInternalBuffer(int bufferSize) {
		if (bufferSize <= 0){
			//internalBuffer = new double [DEFAULT_INTERNAL_BUFFER];
			internalBuffer = new double[tdl.getBufferSize() / fs];			
		}
		else
			internalBuffer = new double [bufferSize];
	}
	
	/**
	 * Disable the use of the internal buffer (the internal buffer won't be used
	 * if read() receives a valid buffer.
	 */
	public void disableInternalBuffer() {
		internalBuffer = null;
	}
	
	/** 
	 * Disable the [-1;1] scaling to retrieve the original numeric values of the
	 * signal.
	 */
	public void disableScaling() {
		scale = 1.;
	}
	
	/** 
	 * Enable scaling of the signal to [-1;1] depending on its bit rate
	 */
	public void enableScaling() {
		scale = 1. / (2 << (br - 1));
	}
	
	public static final String synopsis = 
		"usage: AudioCapture -r <sample-rate> [options]\n" +
		"Record audio data from an audio device and print it to stdout; supported\n" +
		"sample rates: 16000, 8000\n" +
		"\n" +
		"Other options:\n" +
		"  -L\n" +
		"    List available mixers for audio capture and exit; the mixer name\n" +
		"    or ID can be used to specify the capture device (useful for\n" +
		"    multiple microphones or to enforce a certain device).\n" +
		"  -m <mixder-name>\n" +
		"    Use given mixer for audio input instead of default device\n" +
		"  -a\n" +
		"    Change output mode to ASCII (one sample per line) instead of SSG\n" +
		"  -o <out-file>\n" +
		"    Save output to given file (default: stdout)\n" +
		"  -h\n" +
		"    Display this help text\n";
	
	public static void main(String[] args) throws IOException {
		if (args.length < 1 || args[0].equals("-h")) {
			System.err.println(synopsis);
			System.exit(1);
		}
		
		int sr = DEFAULT_SAMPLE_RATE;
		int br = DEFAULT_BIT_RATE;
		
		boolean ascii = false;
		boolean listMixers = false;
		String outf = null;
		String mixer = null;
		
		// parse args
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-L"))
				listMixers = true;
			else if (args[i].equals("-o"))
				outf = args[++i];
			else if (args[i].equals("-a"))
				ascii = true;
			else if (args[i].equals("-m"))
				mixer = args[++i];
			else if (args[i].equals("-r"))
				sr = Integer.parseInt(args[++i]);
			else if (args[i].equals("-h")) {
				System.err.println(synopsis);
				System.exit(1);
			} else
				System.err.println("ignoring unknown parameter '" + args[i] + "'");
		}
				
		// init the output stream
		OutputStream osw = (outf == null ? System.out : new FileOutputStream(outf));
		
		// only list mixers and exit
		if (listMixers) {
			for (Mixer.Info i : getMixerList(null))
				osw.write((i.getName() + "\n").getBytes());
			osw.flush();
			osw.close();
			System.exit(0);
		}
		
		// there's actually gonna be recording, init source!
		/* ??? why restrict? - hoenig
		if (!(sr == 16000 || sr == 8000)) {
			System.err.println("unsupported sample rate; choose 16000 or 8000");
			System.exit(1);
		}		
		*/
		AudioSource as = new AudioCapture(mixer, br, sr, 0);
		
		as.setPreEmphasis(false, 1.0);
		
		System.err.println(as.toString());
		
		// use any reasonable buffer size, e.g. 256 (about 16ms at 16kHz)
		double [] buf = new double [256];
		while (as.read(buf) > 0) {
			if (ascii) {
				for (double d : buf)
					osw.write(("" + d + "\n").getBytes());
			} else {
				// convert double into 2byte sample
				for (double d : buf) {
					short s = new Double(d*((double)Short.MAX_VALUE + 1)).shortValue();
					byte [] b = new byte[] { (byte)(s & 0x00FF), (byte)((s & 0xFF00)>>8) };
					osw.write(b);
				}
			}
			osw.flush();
		}
		
		osw.close();
	}

}
