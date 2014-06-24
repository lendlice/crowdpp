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
package de.fau.cs.jstk.framed;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import de.fau.cs.jstk.io.FrameSource;

/**
 * Use the SimulatedFrameReader to generate a FrameSource with a predefined
 * sequence of frame values. Usefull for debugging and testing.
 * 
 * @author sikoried
 */
public class SimulatedFrameSource implements FrameSource {
	/** frame size */
	private int fs = 0;

	/** simulated sample rate */
	private int sampleRate = 16000;
	
	private int blockSize = 512;
	
	/** manually dequeue elements from this source? */
	private boolean manualDequeue = false;
	
	/** simulated data */
	private List<double []> data = new LinkedList<double []>();
	
	/**
	 * Generate an artificial sequence of frames
	 * @param data
	 */
	public SimulatedFrameSource(double [][] data) {
		for (double [] d : data)
			this.data.add(d);
		
		fs = data[0].length;
	}

	/**
	 * Generate an artificial sequence of frames based on the given list.
	 * @param list
	 */
	public SimulatedFrameSource(List<double []> list) {
		this.data = list;
		fs = list.get(0).length;
	}
	
	/**
	 * Generate an artificial sequence of frames by caching the given 
	 * FrameSource
	 * @param fs
	 */
	public SimulatedFrameSource(FrameSource source) throws IOException {
		fs = source.getFrameSize();
		double [] buf = new double [fs];
		while (source.read(buf))
			data.add(buf.clone());
	}
	
	public FrameSource getSource() {
		return null;
	}
	
	public void setSampleInfo(int rate, int blockSize) {
		sampleRate = rate;
		this.blockSize = blockSize;
	}
	
	public int getSampleRate() {
		return sampleRate;
	}
	
	public int getBlockSize() {
		return blockSize;
	}
	
	/** 
	 * Set the manual dequeue value to true if you wish to manually remove
	 * frames from the source.
	 * 
	 * @param value
	 */
	public void setManualDequeue(boolean value) {
		manualDequeue = value;
	}
	
	public int getFrameSize() {
		return fs;
	}

	/**
	 * Read the next frame from the data array
	 */
	public boolean read(double[] buf) throws IOException {
		if (data.size() == 0)
			return false;
		
		if (manualDequeue)
			return peek(buf);
		
		// copy data, advance pointer
		double [] out = data.remove(0);
		System.arraycopy(out, 0, buf, 0, fs);
		
		return true;
	}
	
	/**
	 * Retrieve the next frame without removing it from the queue.
	 * @param buf
	 * @return
	 * @throws IOException
	 */
	public boolean peek(double [] buf) throws IOException {
		if (data.size() == 0)
			return false;
		
		System.arraycopy(data.get(0), 0, buf, 0, fs);
		
		return true;
	}
	
	/**
	 * Remove the front-most element.
	 */
	public void dequeue() {
		data.remove(0);
	}
	
	/**
	 * Adds a frame to the simulated frame source.
	 * @param frame the referenced frame will be copied!
	 */
	public void appendFrame(double [] frame) {
		data.add(frame.clone());
	}
}
