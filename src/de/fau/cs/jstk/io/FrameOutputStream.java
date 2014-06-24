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
package de.fau.cs.jstk.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

/**
 * Use the FrameOutputStream class to write Frame format data. The format is binary
 * and places a (little endian) int as frame size and subsequent doubles or
 * floats as requested.
 *
 * please FIXME: take an OutputStream instead of a File
 * 
 * @author sikoried
 */
public class FrameOutputStream implements FrameDestination {
	
	/** OutputStream to write to */
	private OutputStream os = System.out;
	
	/** frame size to write */
	private int fs = 0;
	
	/** floats instead of doubles? */
	private boolean floats = true;
	
	/**
	 * Generate a FrameOutputStream that writes a frame size and then floats to STDOUT
	 * @param frameSize size of output frames
	 * @throws IOException
	 */
	public FrameOutputStream(int frameSize) throws IOException {
		fs = frameSize;
		initialize(true);
	}
	
	/**
	 * Generate a FrameOutputStream that writes to stdout
	 * @param frameSize size of output frames
	 * @throws IOException
	 */
	public FrameOutputStream(int frameSize, boolean floats) throws IOException {
		fs = frameSize;
		this.floats = floats;
		initialize(true);
	}
	
	/**
	 * Generate a FrameOutputStream that writes to the given file
	 * @param frameSize
	 * @param file if null, STDOUT is assigned
	 * @throws IOException
	 */
	public FrameOutputStream(int frameSize, File file) throws IOException {
		fs = frameSize;
		if (file != null)
			os = new BufferedOutputStream(new FileOutputStream(file));
		
		initialize(true);
	}
	
	/**
	 * Generate a FrameOutputStream that writes to the given file
	 * @param frameSize
	 * @param file is null, STDOUT is assigned
	 * @param floats 
	 * @throws IOException
	 */
	public FrameOutputStream(int frameSize, File file, boolean floats) throws IOException {
		fs = frameSize;
		this.floats = floats;
		if (file != null)
			os = new BufferedOutputStream(new FileOutputStream(file));
		
		initialize(true);
	}
	
	/**
	 * Generate a FrameOutputStream that writes to the given file
	 * @param frameSize
	 * @param file is null, STDOUT is assigned
	 * @param floats 
	 * @param header write header (frame size as int)
	 * @throws IOException
	 */
	public FrameOutputStream(int frameSize, File file, boolean floats, boolean header) throws IOException {
		fs = frameSize;
		this.floats = floats;
		if (file != null)
			os = new BufferedOutputStream(new FileOutputStream(file));
		
		initialize(header);
	}
	
	/**
	 * Initialize the output stream by writing out the frame size
	 * @throws IOException
	 */
	private void initialize(boolean header) throws IOException {
		if (header)
			IOUtil.writeInt(os, fs, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Write a frame
	 * @param buf
	 * @throws IOException
	 */
	public void write(double [] buf) throws IOException {
		if (floats)
			IOUtil.writeFloat(os, buf, ByteOrder.LITTLE_ENDIAN);
		else
			IOUtil.writeDouble(os, buf, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Write a frame
	 * @param buf
	 * @throws IOException
	 */
	public void write(float [] buf) throws IOException {
		IOUtil.writeFloat(os, buf, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Flush the OutputStream
	 * @throws IOException
	 */
	public void flush() throws IOException {
		
		os.flush();			
		
	}
	
	/**
	 * Flush and close the OutputStream
	 * @throws IOException
	 */
	public void close() throws IOException {
		os.flush();
		if (!(os == System.out || os == System.err)) {			
			os.close();
		}
	}
	
	/**
	 * In the end, close the data file to prevent data loss!
	 */
	public void finalize() throws Throwable{
		//os.flush();
		try {			
			os.close();
		} finally {
			super.finalize();
		}
	}
	
	/**
	 * Return the frame size
	 * @return
	 */
	public int getFrameSize() {
		return fs;
	}
}
