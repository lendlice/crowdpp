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

import android.annotation.SuppressLint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * Use the AudioPlay class to play back audio files on a certain device. Is used
 * by bin.ThreadedPlayer
 * @author sikoried
 * @see de.fau.cs.jstk.sampled.ThreadedPlayer
 */
@SuppressLint("DefaultLocale")
public class AudioPlay implements LineListener {
	private SourceDataLine line;
	
	private double [] doubleBuf = null;
	private byte [] byteBuf = null;
	private int fs;
	
	private String mixerName = null;
	private AudioSource source = null;
	
	/** output bit rate */
	public static final int BIT_RATE = 16;
	
	private AudioFormat af;
	
	/** add a little delay after stopping the playback, to make sure the line is empty */
	public static int PLAYBACK_LAG = 400;
	
	private boolean lineOpened = false;

	/** data scaling coefficient */
	private double scale = 1.;

	/** zero for default, milliseconds for manual buffer size */
	private double manualBufferSize = 0.;
	
	/**
	 * Creates a new AudioPlay object using the given AudioSource.
	 * @param source
	 * @throws IOException
	 * @throws LineUnavailableException
	 */
	public AudioPlay(AudioSource source) 
		throws IOException, LineUnavailableException {
		this(null, source, 0.);
	}
	
	/**
	 * Creates a new AudioPlay object and initializes it. If the desired mixer
	 * is null or can't provide the DataLine, the default mixer is used.
	 * 
	 * @param mixerName name of mixer to use for play back (or null for default)
	 * @param source where to read the data from
	 * 
	 * @throws IOException
	 * @throws LineUnavailableException
	 */
	public AudioPlay(String mixerName, AudioSource source)
		throws IOException, LineUnavailableException {
		this(mixerName, source, 0.);
	}
	
	/**
	 * Creates a new AudioPlay object and initializes it. If the desired mixer
	 * is null or can't provide the DataLine, the default mixer is used.
	 * 
	 * @param mixerName name of mixer to use for play back (or null for default)
	 * @param source where to read the data from
	 * @param manualBufferSize 0 for default, milliseconds otherwise
	 * 
	 * @throws IOException
	 * @throws LineUnavailableException
	 */
	public AudioPlay(String mixerName, AudioSource source, double manualBufferSize)
		throws IOException, LineUnavailableException {
		
		this.mixerName = mixerName;
		this.source = source;
		this.manualBufferSize = manualBufferSize;

		initialize();
	}

	/**
	 * Assign a new AudioSource to the player. If the format matches the previous
	 * one, the source is replaced. Otherwise, the whole output system is re-initialized.
	 * 
	 * @param source
	 * @throws IOException
	 * @throws LineUnavailableException
	 */
	public void setAudioSource(AudioSource source) throws IOException, LineUnavailableException {
		if (this.source.getSampleRate() != source.getSampleRate()) {
			line.stop();
			line.close();
			this.source = source;
			initialize();
		} else {
			this.source = source;
		}
	}
	
	/** 
	 * Initialize the play back by setting up the outgoing lines. 
	 * @throws IOException
	 * @throws LineUnavailableException
	 */
	private void initialize() throws IOException, LineUnavailableException {
		// standard linear PCM at 16 bit and the available sample rate
		af = new AudioFormat(source.getSampleRate(), BIT_RATE, 1, true, false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
		
		// No mixer specified, use default mixer
		if (mixerName == null) {
			line = (SourceDataLine) AudioSystem.getLine(info);
		} else {
			// mixerName specified, use this Mixer to write to			
			
			Mixer.Info target = null;
			try {
				target = MixerUtil.getMixerInfoFromName(mixerName, false);
			} catch (Exception e) {
				e.printStackTrace();
			}
						
			// If no target, fall back to default line
			if (target != null)
				line = (SourceDataLine) AudioSystem.getMixer(target).getLine(info);
			else{
				System.err.println("mixer not found: " + mixerName + ". Available mixers:");
								
				for (Mixer.Info m : getMixerList(null))
					System.err.println(m.getName());
				line = (SourceDataLine) AudioSystem.getLine(info);
			}
		}
	
		try {
			line.removeLineListener(this);
			
		} catch (Exception e) {
			System.err.println("warning: could not remove line listener; not present? : " + e.toString());
		}
		line.addLineListener(this);
		
		scale = Math.pow(2, BIT_RATE - 1) - 1;
	}

	/**
	 * Open the line (on demand call)
	 * @throws LineUnavailableException
	 */
	private void openLine() throws LineUnavailableException{
		try{
			if (manualBufferSize != 0.){
				int desiredBufSize = (int) Math.round(manualBufferSize * af.getFrameRate())
				* af.getFrameSize();


				line.open(af, desiredBufSize);

				if (line.getBufferSize() != desiredBufSize){
					System.err.println("could not set desiredBufDur = " + manualBufferSize + 
							" which corresponds to a buffer size of " + manualBufferSize + ". Got bufSize = " + 
							line.getBufferSize());
				}
			} else {
				// otherwise use default buffer size
				line.open(af);
			}
		} catch (IllegalArgumentException e){
			throw new LineUnavailableException(e.getMessage());
		}

		// further handling is done in the LineEvent(OPEN) handler, wait for the line to open
		while (!lineOpened)
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				// outch, only active wait
			}
	}
	
	private static Mixer.Info [] getMixerList(AudioFormat af) {
		return MixerUtil.getMixerList(af, false);
	}
	
	/**
	 * 
	 * @return String of mixerName
	 */
	public String getMixerName(){
		if(mixerName != null)
			return mixerName;
		else
			return "default mixer";
	}

	/**
	 * Close everyting
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	public void tearDown() throws IOException {
		// when tearing down, we need to drain and stop the line before closing 
		// it; no drain means the end of the playback is very likely to be chop

		line.drain();
		
		// this is a workaround for the current linux driver/jdk issues that 
		// truncate the last bit of sound despite the drain; the issue seems to
		// be that the subsequent stops the actual system device, while drain
		// returns once the (java) buffer is empty -- which might be different
		// from the OS buffer
		if (System.getProperty("os.name").toLowerCase().indexOf("linux") != -1) {
			try {
				Thread.sleep(PLAYBACK_LAG);
			} catch (InterruptedException e1) {
				// well, it's a workaround anyways...
			}
		}
	
		line.stop();
		
		// the line is closed on the LineEventHandler
		
		// make a somewhat active wait for the line
		while (lineOpened)
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// if this fails, it's just a real active wait
			}
	}

	/**
	 * write one frame from data array to audioSource (playback)
	 * @return number of bytes played(written to audioSource) or -1 if audiobuffer is empty
	 * @throws IOException
	 * @throws LineUnavailableException 
	 */
	public int write() throws IOException, LineUnavailableException {		
		int bytes;
		
		// open only here, otherwise we block audio device all the time
		if (!lineOpened)
			openLine();

		bytes = byteBuf.length;
		
		int frames = bytes / fs / 2;
		
		int readFrames = source.read(doubleBuf, frames);
		
		int readBytes = readFrames * fs;
		
		// error?
		if (readFrames < 0) {
			tearDown();
			return -1;
		}
		
		// save your breath
		if (readFrames == 0)
			return 0;
		
		// set rest to zero
		if (readFrames < frames)
			for (int i = readFrames; i < frames; ++i)
				doubleBuf[i] = 0;
		
		// double -> short conversion
		ByteBuffer bb = ByteBuffer.wrap(byteBuf);
		bb.order(ByteOrder.LITTLE_ENDIAN);
 
		int i;
		for (i = 0; i < frames; i++)					 
			bb.putShort((short)(doubleBuf[i] * scale));		
		
		readFrames = line.write(byteBuf, 0, readBytes);		
		
		return readFrames;
	}

	protected void finalize() throws Throwable {
		try {
			tearDown();
		} finally {
			super.finalize();
		}
	}

	public static final String SYNOPSIS = 
		"usage: sampled.AudioPlay [-m mixer-name] [-f format-string] [file1 ...]\n" +
		"Play back the listed audio files. If required, use the specified mixer\n" +
		"device. Specify a format string if referring to raw data (e.g. t:ssg/16).\n" +
		"WAV-Format should be automatically detected from the file header.\n" +
		"Speex (*.spx) files must end with .spx.";
	
	/*public static void main(String [] args) throws Exception {
		if (args.length < 1) {
			System.err.println(SYNOPSIS) ;
			System.exit(1);
		}
		
		String mixer = null;
		String format = null;
		
		// scan for arguments
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-m"))
				mixer = args[++i];
			else if (args[i].equals("-f"))
				format = args[++i];
		}
		
		// process files
		for (int i = 0 + (mixer == null ? 0 : 2) + (format == null ? 0 : 2); i < args.length; ++i) {
			String file = args[i];
			System.err.println("Now playing " + file);
			AudioSource reader;
			if (format == null){
				if (file.endsWith(".spx"))
					reader = new SpeexFileReader(new FileInputStream(file));
				else
					reader = new AudioFileReader(file, true);
			}
			else
				reader = new AudioFileReader(file, RawAudioFormat.create(format), true);
			
			reader.setPreEmphasis(false, 1);
			
			AudioPlay play = new AudioPlay(mixer, reader);
			
			// play whole file
			while (play.write() > 0)
				;
		}
	}*/

	public void update(LineEvent event) {
		if (event.getType() == LineEvent.Type.OPEN) {
			// start the line, init the buffer
			line.start();
			
			int bytes = line.getBufferSize();
			byteBuf = new byte [bytes];
			doubleBuf = new double [bytes / af.getFrameSize()];
			fs = af.getFrameSize();
			
			lineOpened = true;
		} else if (event.getType() == LineEvent.Type.START) {
			// nothing to do here
		} else if (event.getType() == LineEvent.Type.STOP) {
			line.close();
		} else if (event.getType() == LineEvent.Type.CLOSE) {
			// only free the line now
			lineOpened = false;
		} else
			System.err.println("Unknown LineEvent: " + event.getType());
		
	}
}