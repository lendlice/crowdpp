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
import java.io.OutputStream;

/**
 * OutputStream that branches off the data that is being read to another consumer (OutputStream),
 * much like the unix "tee" command.
 * 
 * Useful e.g. when you capture data from the microphone and you have two consumers such as
 * a ByteArrayOutputStream for the actual recording and a client.dod.SoundLevelEstimator for displaying volume 
 * 
 * @author hoenig
 */
public class TeeOutputStream extends OutputStream{
	OutputStream os;
	OutputStream consumer;
	
	public TeeOutputStream(OutputStream os,
			OutputStream consumer){
		this.os = os;
		this.consumer = consumer;		
	}
	
	public void write(byte [] buf, int off, int len) throws IOException{
		os.write(buf, off, len);
		consumer.write(buf, off, len);
		// TODO: ?
		consumer.flush();		
	}
	
	public void write(byte [] buf) throws IOException{
		write(buf, 0, buf.length);
	}
	
	public void flush() throws IOException{
	  consumer.flush();
	}
	
	public void close() throws IOException{
	  flush();

	  os.close();
	  consumer.close();
	}
	
	public void write(int b) throws IOException{
		byte [] buf = new byte[1];
		buf[0] = (byte)b;
		write(buf, 0, 1);		
	}
}
