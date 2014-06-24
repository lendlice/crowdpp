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

public final class ChunkedTranscribedData {
	private LinkedList<Chunk> chunks = new LinkedList<Chunk> ();
	private int cind = 0;
	
	/**
	 * A chunk consists of its name and a (ready-to-read) FrameInputStream
	 * @author sikoried
	 *
	 */
	public static final class Chunk {
		/**
		 * Create a new Chunk and prepare the FrameInputStream to read from the given
		 * file.
		 * 
		 * @param fileName
		 * @throws IOException
		 */
		public Chunk(String fileName, String [] words) {
			// prepare the reader
			this.fileName = fileName;
			
			// save the words
			this.words = words;
			
			// get the filename
			name = fileName.substring(fileName.lastIndexOf(System.getProperty("file.separator")) + 1);
		}
		
		public void init() throws IOException {
			if (reader != null)
				reader.close();
			reader = new FrameInputStream(new File(fileName));
		}
		
		public void finalize() {
			try {
				if (reader != null)
					reader.close();
			} catch (IOException e) {
				
			}
		}
		
		/** FrameInputStream */
		public FrameInputStream reader;
		
		/** The word sequence for this training file */
		public String [] words;
		
		/** file name without the path */
		public String name;
		
		public String fileName;
	}
	
	/**
	 * Get the next Chunk from the list.
	 * @return Chunk instance on success, null if there's no more chunks
	 * @throws IOException
	 */
	public synchronized Chunk nextChunk() throws IOException {
		if (cind < chunks.size()) {
			Chunk current = chunks.get(cind++);
			current.init();
			return current;
		}
		return null;
	}
	
	public synchronized void rewind() {
		cind = 0;
	}
	
	/** 
	 * Create a ChunkDataSet using the given list file.
	 * @param fileName path to the list file
	 * @throws IOException
	 */
	public ChunkedTranscribedData(String fileName) 
		throws IOException {
		setChunkList(fileName);
	}
	
	/**
	 * Get the number of Chunks in this data set
	 * @return
	 */
	public int numberOfChunks() {
		return chunks.size();
	}
	
	/**
	 * Load the given chunk list.
	 * @param fileName
	 * @throws IOException
	 */
	public void setChunkList(String fileName) throws IOException {
		chunks.clear();
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		String line;
		while ((line = br.readLine()) != null) {
			String [] split = line.split("\\s+");
			File test = new File(split[0]);
			String [] words = new String [split.length-1];
			System.arraycopy(split, 1, words, 0, words.length);
			if (test.canRead())
				chunks.add(new Chunk(split[0], words));
		}
	}
}
