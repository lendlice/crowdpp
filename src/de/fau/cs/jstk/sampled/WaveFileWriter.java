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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * writes wav file from AudioSource. Warning: currently, only linearly 16-bit quantized, signed PCM 
 * is supported.
 * @author hoenig
 *
 */
public class WaveFileWriter {

	private static final int bufSize = 100000;
	
	public static void write(AudioSource source, 
			File outFile) throws IOException{
		
		List<byte[]> arrays = new LinkedList<byte[]>();		
		
		double buffer [] = new double[bufSize];
		
		int read;
		
		while((read = source.read(buffer)) > 0){
			double [] array;
			
			if (read < buffer.length)
				array = Arrays.copyOf(buffer, read);
			else 
				array = buffer;							
		
			arrays.add(Samples.samples2signedLinearLittleShorts(array));			
		}
		
		int length = 0;
		for (byte [] a : arrays)
			length += a.length;
		
		// concatenate
		byte [] allData = new byte[length];
		int i = 0;
		for (byte [] a : arrays){
			System.arraycopy(a, 0, allData, i, a.length);
			i += a.length;
		}
	
		AudioFormat format = new AudioFormat(source.getSampleRate(), 16, 1, true, false);
		
		AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(
					allData), format, allData.length),
					AudioFileFormat.Type.WAVE, outFile);			
	}
}
