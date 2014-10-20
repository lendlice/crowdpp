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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * InputStream that branches off the data that is being read to another consumer (OutputStream),
 * much like the unix "tee" command.
 * 
 * Useful e.g. when you read audio data from a stream and you have two consumers such as
 * a AudioInputStream for actual playback and a client.dod.SoundLevelEstimator for displaying volume 
 * 
 * @author hoenig
 */
public class TeeInputStream extends InputStream{
	InputStream is;
	OutputStream consumer;
	
	public TeeInputStream(InputStream is,
			OutputStream consumer){
		this.is = is;
		this.consumer = consumer;		
	}
	
	public int read(byte [] buf, int off, int len) throws IOException{
		int readBytes = is.read(buf, off, len);
//		System.err.println(String.format("TeeInputStream.read: buf.length = %d, off = %d, len = %d, readBytes = %d",
//				buf.length, off, len, readBytes));
		if (readBytes > 0)
			consumer.write(buf, off, readBytes);
		else if (readBytes < 0){
			consumer.close();
			return readBytes;
		}
		// TODO: ?
		consumer.flush();
		return readBytes;
	}
	
	public int read(byte [] buf) throws IOException{
		return read(buf, 0, buf.length);
	}
	
	public void flush() throws IOException{
	  consumer.flush();
	}
	
	public void close() throws IOException{
	  flush();

	  is.close();
	  consumer.close();
	}
	
	public int read() throws IOException{
		byte [] buf = new byte[1];
		read(buf, 0, 1);
		return buf[0];
	}
}
