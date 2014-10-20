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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioFileListReader implements AudioSource {

	private AudioFileReader current = null;
	
	private RawAudioFormat format = null;
	
	private String fileList = null;
	
	/** list of files, FIFO */
	private Queue<String> list = new LinkedList<String>();
	
	/** use buffered reader? */
	private boolean cache = true;
	
	/** apply pre-emphasis? */
	private boolean preemphasize = false;
	
	/** pre-emphasis factor */
	private double a = AudioFileReader.DEFAULT_PREEMPHASIS_FACTOR;
	
	/** 
	 * Generate a new AudioFileListReader using given file list, RawAudioFormat
	 * and cache indicator. 
	 * @param fileList
	 * @param format
	 * @param cache
	 * @throws IOException
	 * 
	 * @see RawAudioFormat.create
	 */
	public AudioFileListReader(String fileList, RawAudioFormat format, boolean cache) throws UnsupportedAudioFileException, IOException {
		this.fileList = fileList;
		this.format = format;
		this.cache = cache;
		initialize();
	}
	
	/**
	 * Read the given list file line-by-line, check if they exist, and add them
	 * to the file list
	 * @throws IOException
	 */
	private void initialize() throws UnsupportedAudioFileException, IOException {
		
		// read the file list
		BufferedReader br = new BufferedReader(new FileReader(fileList));
		String buf = null;
		while ((buf = br.readLine()) != null) {
			try {
				File f = new File(buf);
				
				// check file
				if (!f.exists())
					throw new FileNotFoundException();
				if (!f.canRead())
					throw new IOException();
				
				// add to list
				list.add(buf);
			} catch (FileNotFoundException e) {
				System.err.println("skipping file '" + buf + "': file not found");
			} catch (IOException e) {
				System.err.println("skipping file '" + buf + "': permission denied");
			} 			
		}
		
		if (list.size() == 0)
			throw new IOException("file list was empty!");
		
		// load the first file
		current = new AudioFileReader(list.poll(), format, cache);
		current.setPreEmphasis(preemphasize, a);
	}
	
	public boolean getPreEmphasis() {
		if (current != null)
			return getPreEmphasis();
		else
			return preemphasize;
	}
	
	public void setPreEmphasis(boolean applyPreEmphasis, double a) {
		preemphasize = applyPreEmphasis;
		this.a = a;
		if (current != null)
			current.setPreEmphasis(applyPreEmphasis, a);
	}
	
	public String toString() {
		return "AudioFileListReader: " + fileList + " (" + list.size() + " valid files\n" + format.toString();
	}

	public int read(double[] buf) throws IOException {
		return read(buf, buf.length);
	}
	
	public int read(double[] buf, int length) throws IOException {
		if (current == null)
			return 0;
		
		int read = current.read(buf, length);		
		
		if (read == length) {
			// enough samples read
			return read;
		}
		else if (read < 0) {
			// no samples read, load next file if possible
			if (list.size() == 0)
				return -1;
			
			// load next file
			current.tearDown();
			try {
				current = new AudioFileReader(list.remove(), format, cache);
			} catch (UnsupportedAudioFileException e) {
				throw new IOException(e.toString());
			}
			current.setPreEmphasis(preemphasize, a);
			
			// read frame
			return current.read(buf);
		} else {
			// early EOF, pad with zeros, load next file
			for (int i = read; i < length; ++i)
				buf[i] = 0.;
			
			if (list.size() > 0) {
				try {
					current = new AudioFileReader(list.remove(), format, cache);
				} catch (UnsupportedAudioFileException e) {
					throw new IOException(e.toString());
				}
				current.setPreEmphasis(preemphasize, a);
			}
			
			return length;
		}
	}
	
	public void tearDown() throws IOException {
		// nothing to do here
	}

	public int getSampleRate() {
		if (current != null)
			return current.getSampleRate();
		else
			return 0;
	}

}
