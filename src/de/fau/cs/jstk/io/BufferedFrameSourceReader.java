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


public class BufferedFrameSourceReader implements FrameSource {
	
	private int currentIndex;
	
	private BufferedFrameSource source;
	
	public BufferedFrameSourceReader(BufferedFrameSource source) {
		this.source = source;
		currentIndex = 0;
	}

	@Override
	public boolean read(double[] buf) throws IOException {
		if (currentIndex >= source.getBufferSize()) {
			return false;
		}
		double[] frame = source.get(currentIndex++);
		System.arraycopy(frame, 0, buf, 0, frame.length);
		
		return true;
	}

	@Override
	public int getFrameSize() {
		return source.getFrameSize();
	}

	@Override
	public FrameSource getSource() {
		return source;
	}

}
