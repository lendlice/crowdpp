/*
	Copyright (c) 2009-2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Stefan Steidl

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

import java.io.IOException;


public class BufferedFrameSource implements FrameSource {

	private FrameSource source;
	private int readIndex;
	private double[][] buffer;
	private int size;
	
	protected BufferedFrameSource(BufferedFrameSource copyFrom) {
		source = copyFrom.source;
		int fs = source.getFrameSize();
		size = copyFrom.buffer.length;
		readIndex = 0;
		buffer = new double[size][];
		for (int i = 0; i < size; i++) {
			buffer[i] = new double[fs];
			System.arraycopy(copyFrom.buffer[i], 0, buffer[i], 0, fs);			
		}
	}
	
	public BufferedFrameSource(FrameSource source) throws IOException {
		this.source = source;
		readIndex = 0;
		buffer = null;
		size = 0;
		if (source != null) {
			bufferAllFrames();
		}
	}
	
	private void bufferAllFrames() throws IOException {
		int init = 128;
		int frameSize = source.getFrameSize();
		buffer = new double[init][];
		double[] frame = new double[frameSize];
		
		while (source.read(frame)) {
			if (size >= buffer.length) {
				double[][] oldBuffer = buffer;
				buffer = new double[oldBuffer.length + init][];
				System.arraycopy(oldBuffer, 0, buffer, 0, oldBuffer.length);
			}
			
			buffer[size++] = frame;
			frame = new double[frameSize];
		}
	}
	
	public int getBufferSize() {
		if (buffer == null) {
			return 0;
		}
		return size;
	}
	
	public double[] get(int index) {
		if ((index < 0) || (index >= size)) {
			return null;
			/*
			double[] frame = new double[source.getFrameSize()];
			for (int i = 0; i < frame.length; i++) {
				frame[i] = 0.0;
			}
			return frame;
			*/
		}
		return buffer[index];
	}
	
	@Override
	public boolean read(double[] buf) throws IOException {
		if (readIndex < size) {
			System.arraycopy(buffer[readIndex], 0, buf, 0, buffer[readIndex].length);
			readIndex++;
			return true;
		}
		
		return false;
	}

	@Override
	public int getFrameSize() {
		return source.getFrameSize();
	}

	@Override
	public FrameSource getSource() {
		return source;
	}
	
	public BufferedFrameSourceReader getReader() {
		return new BufferedFrameSourceReader(this);
	}
	
}
