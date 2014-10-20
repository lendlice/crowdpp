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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import de.fau.cs.jstk.stat.Sample;


/**
 * Use the ChunkedDataSet if you have a list of files containing framed data and
 * want to read that in sequencially.
 * @author sikoried
 *
 */
public class ChunkedDataSet {
	private LinkedList<File> validFiles = new LinkedList<File> ();
	private int cind = 0;

	private int fs = 0;  // frame size for all chunks
	
	/**
	 * A chunk consists of its name and a (ready-to-read) FrameInputStream
	 */
	public class Chunk {
		/** file to work on */
		private File file;
		
		/**
		 * Create a new Chunk and prepare the FrameInputStream to read from the given
		 * file.
		 * 
		 * @param fileName
		 * @throws IOException
		 */
		public Chunk(File file) {
			this.file = file;
		}
		
		/** FrameInputStream allocated on demand */
		private FrameInputStream reader = null;
		
		/**
		 * Get the initialized FrameInputStream
		 */
		public FrameInputStream getFrameReader() throws IOException {
			if (reader == null)
				reader = new FrameInputStream(file, true, fs);
			
			return reader;
		}
	}
	
	/**
	 * Get the next Chunk from the list.
	 * @return Chunk instance on success, null if there's no more chunks
	 * @throws IOException
	 */
	public synchronized Chunk nextChunk() throws IOException {
		if (validFiles.size() == 0)
			return null;
		if (cind < validFiles.size())
			return new Chunk(validFiles.get(cind++));
		return null;
	}
	
	/**
	 * Rewind the chunk list and start again from the first.
	 */
	public synchronized void rewind() {
		cind = 0;
	}
	
	/** 
	 * Create a ChunkDataSet using the given list file.
	 * @param listFile path to the list file
	 * @throws IOException
	 */
	public ChunkedDataSet(File listFile) throws IOException {
		this(listFile, null, 0);
	}
	
	/**
	 * Create a ChunkedDataSet using the given list file and consider them UFV
	 * @param listFile
	 * @param fs frame size to expect (no-header), 0 for Frame format
	 * @throws IOException
	 */
	public ChunkedDataSet(File listFile, int fs) throws IOException {
		this(listFile, null, fs);
	}
	
	/**
	 * Create a ChankDataSet using a given list file and the directory where the
	 * feature files are located.
	 * @param listFile
	 * @param dir input directory
	 * @param fs frame size (set > 0 for UFV)
	 * @throws IOException
	 */
	public ChunkedDataSet(File listFile, String dir, int fs) throws IOException {
		this.fs = fs;
		setChunkList(listFile, dir);
	}
	
	/**
	 * Create a ChunkedDataSet using the given list of files and a UFV frame
	 * size if necessary.
	 * @param fileNames
	 * @param fs 0 for Frame format, other for frame size (no-header)
	 */
	public ChunkedDataSet(List<File> files, int fs) throws IOException {
		this.fs = fs;
		
		// validate and add files
		for (File file : files) {
			if (file.canRead())
				validFiles.add(file);
			else
				throw new IOException("Could not read file " + file);
		}
	}
	
	/**
	 * Get the number of Chunks in this data set
	 * @return
	 */
	public int numberOfChunks() {
		return validFiles.size();
	}
	
	/**
	 * Load the given chunk list. If the parameter <dir> contains a String, this String is appended to the filename
	 * @param fileName
	 * @param dir
	 * @throws IOException
	 */
	public void setChunkList(File listFile, String dir) throws IOException {
		validFiles.clear();
		BufferedReader br = new BufferedReader(new FileReader(listFile));
		String name;
		while ((name = br.readLine()) != null) {
			if(dir != null)
				name = dir + System.getProperty("file.separator") + name;
			File test = new File(name);
			if (test.canRead())
				validFiles.add(test);
			else
				throw new IOException("Could not read file " + name);
		}
	}
	
	/**
	 * Cache all chunks into a List<Sample> for easier (single-core) access
	 * @return
	 * @throws IOException
	 */
	public synchronized List<Sample> cachedData() throws IOException {
		// remember old index
		int oldInd = cind;
		cind = 0;
		
		LinkedList<Sample> data = new LinkedList<Sample>();
		Chunk chunk;
		while ((chunk = nextChunk()) != null) {
			double [] buf = new double [chunk.getFrameReader().getFrameSize()];
			while (chunk.reader.read(buf))
				data.add(new Sample((short) 0, buf));
		}
		
		// restore old index
		cind = oldInd;
		
		return data;
	}
}
