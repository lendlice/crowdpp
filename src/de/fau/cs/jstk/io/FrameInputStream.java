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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Read frames from a file or stdin in Frame format.
 * 
 * please FIXME: take an InputStream instead of a File
 * 
 * @author sikoried
 */
public class FrameInputStream implements FrameSource {

	/** input buffer size (1MB) */
	private static final int BUF_SIZE = 1048576;
	
	/** incoming frame size */
	private int fs = 0;
	
	/** input stream to read from */
	private InputStream is = System.in;
	
	/** file, if any */
	private File file = null;
	
	/** read floats? */
	private boolean floats = true;
	
	/**
	 * Initialize a FrameInputStream from STDIN
	 * @throws IOException
	 */
	public FrameInputStream() throws IOException {
		initialize(true);
	}
	
	/**
	 * Initialize a new FrameInputStream on the given file	
	 * @param file
	 * @throws IOException
	 */
	public FrameInputStream(File file) throws IOException {
		this.file = file;
		
		if (file != null)
			is = new BufferedInputStream(new FileInputStream(file), BUF_SIZE);
		
		initialize(true);
	}
	
	/**
	 * Initialize a FrameInputStream on the given file.
	 * @param file
	 * @param floats
	 * @throws IOException
	 */
	public FrameInputStream(File file, boolean floats) throws IOException {
		this.floats = floats;
		this.file = file;
		
		if (file != null)
			is = new BufferedInputStream(new FileInputStream(file), BUF_SIZE); // 1 MB buffer
		
		initialize(true);
	}
	
	/**
	 * Initialize a FrameInputStream on the given file but do not read header
	 * @param file
	 * @param floats
	 * @param fs frame size to assume (0 for frame format)
	 * @throws IOException
	 */
	public FrameInputStream(File file, boolean floats, int fs) throws IOException {
		this.floats = floats;
		this.file = file;
		this.fs = fs;
		
		if (file != null)
			is = new BufferedInputStream(new FileInputStream(file), BUF_SIZE); // 1 MB buffer
		
		initialize(fs == 0);
	}
	
	/**
	 * Will return null.
	 */
	public FrameSource getSource() {
		return null;
	}
	
	/**
	 * Return the underlying file, if any.
	 * @return
	 */
	public File getFile() {
		return file;
	}
	
	/**
	 * Get the associated file name
	 * @param nodir if true, the directory is omitted
	 * @return
	 */
	public String getFileName(boolean nodir) {
		if (file == null)
			return "STDIN";
		if (nodir) {
			String n = file.getAbsolutePath();
			if (n.contains(System.getProperty("file.separator")))
				return n.substring(n.lastIndexOf(System.getProperty("file.separator")) + 1);
			else
				return n;
		} else
			return file.getAbsolutePath();
	}
	
	/**
	 * Read an integer to determine the frame size
	 * @throws IOException
	 */
	private void initialize(boolean header) throws IOException {
		if (header)
			this.fs = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Close the FrameInputStream's input file
	 * @throws IOException
	 */
	public void close() throws IOException {
		is.close();
		is = null;
		eof = true;
	}
	
	/**
	 * Return the size of the output frames
	 */
	public int getFrameSize() {
		return fs;
	}
	
	public String toString() {
		return "FrameInputStream: source=" + (file == null ? "stdin" : file.getAbsolutePath()) + " frame_size=" + fs;
	}
	
	/** indicator for EOF */
	private boolean eof = false;
	
	/**
	 * Read the next frame, convert the raw data to doubles
	 */
	public boolean read(double [] buf) throws IOException {
		if (eof)
			return false;
		
		if (floats)
			eof = !IOUtil.readFloat(is, buf, ByteOrder.LITTLE_ENDIAN);
		else
			eof = !IOUtil.readDouble(is, buf, ByteOrder.LITTLE_ENDIAN);
		
		// no more frames, close file!
		if (eof)
			is.close();
		
		return !eof;
	}
	
	/**
	 * Read the next frame, convert the raw data to doubles
	 */
	public boolean read(float [] buf) throws IOException {
		if (eof)
			return false;
		
		eof = !IOUtil.readFloat(is, buf, ByteOrder.LITTLE_ENDIAN);
		
		// no more frames, close file!
		if (eof)
			is.close();
		
		return !eof;
	}
	
	
	/**
	 * Make sure the File is closed to release the file handle to the OS
	 */
	protected void finalize() throws Throwable {
		try {
			is.close();
		} finally {
			super.finalize();
		}
	}
}
