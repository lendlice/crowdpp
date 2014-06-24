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
import java.util.ArrayList;

import de.fau.cs.jstk.exceptions.MalformedParameterStringException;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;

public class Selection implements FrameSource {

	/** FrameSource to read from */
	private FrameSource source = null;
	
	private FFT fft = null;
	
	/** indices of the feature dimensions to select; default: MFCC0-11 */
	private int [] indices;
	
	/** internal read buffer */
	private double [] buf = null;
	
	/** incoming frame size */
	private int fs_in;
	
	/** outbound frame size */
	private int fs_out;
	
	private boolean ste = false;
	
	/**
	 * Generate a default feature selection: dimensions 0-11 (standard mfcc), no
	 * short time energy.
	 * @param source
	 */
	public Selection(FrameSource source) {
		this(source, false);
	}
	
	public Selection(FrameSource source, boolean doShortTimeEnergy) {
		this(source, new int [] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,12,13,14,15,16,17,18 }, doShortTimeEnergy);
	}
	
	public Selection(FrameSource source, int [] indices) {
		this(source, indices, false);
	}
	
	/**
	 * Apply a selection to the incoming feature frame
	 * @param source
	 * @param indices a implicit mapping, place the indices of the desired dimensions here
	 */
	public Selection(FrameSource source, int [] indices, boolean doShortTimeEnergy) {
		this.source = source;
		this.indices = indices;
		fs_in = source.getFrameSize();
		fs_out = indices.length;
		buf = new double [fs_in];
		
		this.setShortTimeEnergy(doShortTimeEnergy);
	}
	
	public void setShortTimeEnergy(boolean doShortTimeEnergy) {
		if (doShortTimeEnergy) {
			ste = true;
			
			// search the appropriate source for STE
			FrameSource tmp = this;
			while ((tmp = tmp.getSource()) != null) {
				if (tmp instanceof FFT) {
					fft = (FFT) tmp;
					break;
				} 
			}
			
			if (fft == null)
				throw new RuntimeException("framed.Selection.setShortTimeEnergy(): No source for STE found!");
		} else {
			ste = false;
			fft = null;
		}
	}
	
	public FrameSource getSource() {
		return source;
	}
	
	/** 
	 * Return the outgoing frame size 
	 */
	public int getFrameSize() {
		return fs_out;
	}
	
	public boolean does0thCoefficient() {
		for (int i : indices)
			if (i == 0)
				return true;
		return false;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("selection fs_in=" + fs_in + " fs_out=" + fs_out + " mapping_in_out=[");
		boolean steInThisFrame = false;
		for (int i = 0; i < fs_out; ++i) {
			if (ste && indices[i] == 0 && !steInThisFrame) {
				buf.append(" STE->" + i);
				steInThisFrame = true;
			} else
				buf.append(" " + indices[i] + "->" + i);
		}
		buf.append(" ]");

		return buf.toString();
	}

	/**
	 * Read the next frame and transfer the features to the outgoing buffer
	 * according to the indices
	 */
	public boolean read(double[] buf) throws IOException {
		if (!source.read(this.buf))
			return false;
		
		// copy; go the long way, there might be re-ordering!
		boolean steInThisFrame = false;
		for (int i = 0; i < buf.length; ++i) {
			if (indices[i] == 0 && ste && !steInThisFrame) {
				if (fft != null)
					buf[0] = Math.log(fft.getRawSpectralEnergy() + FilterBank.EPSILON);
			
				steInThisFrame = true;
			} else
				buf[i] = this.buf[indices[i]];
		}
		
		return true;
	}
	
	/** 
	 * Create a Selection object according to the parameter string and
	 * attach it to the source.
	 * @param source framesource to read from
	 * @param formatString comma separated list of indices or ranges (e.g. "0,1,4-8")
	 * @return ready-to-use Selection
	 */
	public static Selection create(FrameSource source, String formatString)
		throws MalformedParameterStringException {
		ArrayList<Integer> indices = new ArrayList<Integer>();
		String [] parts = formatString.split(",");
		
		try {
			for (String i : parts) {
				if (i.indexOf("-") > 0) {
					String [] range = i.split("-");
					int start = Integer.parseInt(range[0]);
					int end = Integer.parseInt(range[1]);
					for (int j = start; j <= end; ++j)
						indices.add(j);
				} else {
					indices.add(Integer.parseInt(i));
				}
			}
		} catch (Exception e) {
			// something went wrong analyzing the parameter string
			throw new MalformedParameterStringException(e.toString());
		}
		
		if (indices.size() < 1)
			throw new MalformedParameterStringException("No indices in format string!");
		
		// generate the final indices array
		int [] ind = new int [indices.size()];
		for (int i = 0; i < ind.length; ++i)
			ind[i] = indices.get(i);
		
		// generate the Selection object
		return new Selection(source, ind);
	}
	
	public static String synopsis = 
		"usage: framed.Selection [--ufv-in dim] [--ufv-out | --ascii-out] -s <ParameterString> < infile > outfile\n" +
		"ParameterString: comma separated list of individual digits (0,1,...) or ranges (4-12), e.g. \"0,1,4-8\"\n";
	
	/**
	 * main program; for usage see synopsis
	 * @param args
	 * @throws IOException
	 * @throws IOException, MalformedParameterStringException
	 */
	public static void main(String [] args) throws IOException, MalformedParameterStringException {
		if (args.length < 1) {
			System.err.println(synopsis);
			System.exit(1);
		}
		
		boolean ascii = false;
		boolean ufvout = false;
		boolean ufvin = false;
		
		int ufvdim = 0;
		
		String parameter = null;
		
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("--ascii-out"))
				ascii = true;
			else if (args[i].equals("--ufv-out"))
				ufvout = true;
			else if (args[i].equals("--ufv-in")) {
				ufvin = true;
				ufvdim = Integer.parseInt(args[++i]);
			} else if (args[i].equals("-s"))
				parameter = args[++i];
		}
		
		if ((ascii && ufvout) || parameter == null) {
			System.err.println(synopsis);
			System.exit(1);
		}
		
		// get a STDIN frame reader
		FrameInputStream fr = (ufvin ? new FrameInputStream(null, true, ufvdim) : new FrameInputStream());
		
		// attach to selection
		FrameSource selection = Selection.create(fr, parameter);
		
		double [] buf = new double [selection.getFrameSize()];
		
		// get a STDOUT frame writer if required
		FrameOutputStream fw = null;
		if (!ascii) {
			fw = (ufvout ? new FrameOutputStream(buf.length, true) : new FrameOutputStream(buf.length));
		}
		
		// read the frames, select
		while (selection.read(buf)) {
			if (ascii) {
				// ascii?
				for (int i = 0; i < buf.length - 1; ++i)
					System.out.print(buf[i] + " ");
				System.out.println(buf[buf.length-1]);
			} else {
				// binary, writer takes care of UFV
				fw.write(buf);
			}
		}
	}
}
