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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.xiph.speex.spi.Speex2PcmAudioInputStream;
import org.xiph.speex.spi.SpeexAudioFileReader;

/**
 * speex-file reader as an AudioSource (*.spx, see http://www.speex.org)
 * @author hoenig
 *
 */
public class SpeexFileReader implements AudioSource{

	private AudioFormat audioFormat;
	
	Speex2PcmAudioInputStream pcmStream;

	private boolean streamClosed;		
	
	private static final int OGG_HEADERSIZE = 27;
	private static final int SPEEX_HEADERSIZE = 80;
	private static final int max_headersize = 3 * OGG_HEADERSIZE + SPEEX_HEADERSIZE + 256 + 256 + 2;
	
	public SpeexFileReader(InputStream inputStream) throws UnsupportedAudioFileException, IOException{
		
		// so that we can expect markSupported() to be true
		inputStream = new BufferedInputStream(inputStream);
		if (!inputStream.markSupported())
			throw new Error("Stream does not support mark");
		
		inputStream.mark(max_headersize);
		
		SpeexAudioFileReader reader = new SpeexAudioFileReader();

		AudioFileFormat af = reader.getAudioFileFormat(inputStream);
		
		System.err.println(af.toString());
		
		// reset to start of stream
		inputStream.reset();
		
		AudioFormat partialFormat = af.getFormat();
		
		pcmStream = new Speex2PcmAudioInputStream(inputStream, partialFormat, 0);
		streamClosed = false;

		
		/*		
		this.audioFormat = new AudioFormat(32000, 16, 1, true, false); 
			new AudioFormat(null, 0, 0, 0, 0, 0, false);
			*/	    
		
		// create new audio format (Speex's getFrameRate() report 50, although samplerate is 32000 (?!))
		
		// relies on nchannels=1, bits=10
		audioFormat = new AudioFormat(partialFormat.getSampleRate(), 16, 1, true, false);
		
				
	}

	public static AudioFormat getSpeexFileAudioFormat(InputStream is) throws UnsupportedAudioFileException, IOException{
		SpeexAudioFileReader reader = new SpeexAudioFileReader();
		AudioFileFormat af = reader.getAudioFileFormat(is);		
		return af.getFormat();
	}
	
	public boolean getPreEmphasis() {	
		return false;
	}

	@Override
	public int getSampleRate() {
		return (int) audioFormat.getFrameRate();
	}


	public int read(double[] buf) throws IOException {
		return read(buf, buf.length);
	}
	
	public int read(double[] buf, int length) throws IOException {
		// relies on nChannels = 1
		byte [] byteBuf = new byte[length * audioFormat.getFrameSize()];
				
		int readBytes = pcmStream.read(byteBuf);
		
		if (readBytes < 1) {
			pcmStream.close();
			streamClosed = true;
			return -1;
		}
		
		ByteBuffer byteBuffer = ByteBuffer.wrap(byteBuf, 0, readBytes);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);		
		
		// relies on sample size multiple of 8
		int readSamples = readBytes / audioFormat.getFrameSize();
		
		int i;
		for (i = 0; i < readSamples; i++){
			//relies on signed samples, size 16 
			short value = byteBuffer.getShort();
			if (value == -32768)
				buf[i] = -1.0;
			else
				buf[i] = (double) value / 32767.0;
		}
		
		return readSamples;
	}
	
	public void setPreEmphasis(boolean applyPreEmphasis, double a) {
		if (applyPreEmphasis)
			throw new Error("preemphasis not supported");		
	}

	@Override
	public void tearDown() throws IOException {
		if (!streamClosed) {
			pcmStream.close();
			streamClosed = true;			
		}
	}
}
