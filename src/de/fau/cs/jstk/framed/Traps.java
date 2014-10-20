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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.util.Pair;
import edu.emory.mathcs.jtransforms.dct.DoubleDCT_1D;

/**
 * Compute TRAPS, features representing the temporal structure of individual
 * features using DCT (instead of linear regression as in framed.Slope).
 * 
 * @author sikoried
 */
public class Traps implements FrameSource {

	/** FrameSource to read from */
	private FrameSource source = null;
	
	/** ring buffer size */
	private int rbs;
	
	/** source frame size */
	private int fs_in;
	
	private double [] buf_dct = null;
	
	/** ring buffer to save the temporal context */
	private double [][] ringbuf = null;
	
	/** index within ringbuf for next write */
	private int ndx = 0;
	
	/** scale the DCT output? */
	private boolean scale = true;
	
	private boolean dodct = true;
	
	private DoubleDCT_1D dct = null;
	
	/**
	 * Create a TRAPS instance that reads from the given FrameSource (usually 
	 * a FrameInputStream on precomputed MFB files), context length in frames. 
	 * Enables scaled DCT.
	 * @param source FrameSource to read from
	 * @param context number of frames to consider
	 */
	public Traps(FrameSource source, int context) {
		this(source, context, true, true);
	}
	
	/**
	 * Create a TRAPS instance that reads from the given FrameSource (usually 
	 * a FrameInputStream on precomputed MFB files), context length in frames and
	 * the scale parameter for DCT
	 * @param source FrameSource to read from
	 * @param context number of frames to consider
	 * @param scale scale the dct coefficients?
	 */
	public Traps(FrameSource source, int context, boolean scale, boolean dodct) {
		this.source = source;
		this.rbs = context;
		this.scale = scale;
		this.dodct = dodct;
		
		fs_in = source.getFrameSize();
		dct = new DoubleDCT_1D(fs_in);
	}
	
	public int getFrameSize() {
		return fs_in * rbs;
	}

	public FrameSource getSource() {
		return source;
	}
	
	public boolean read(double [] buf) throws IOException {
		// stage1: beginning-of-stream; initialize and fill ring buffer
		if (ringbuf == null) {
			// init buffers 
			buf_dct = new double [rbs];
			ringbuf = new double [rbs][fs_in];
			
			// fill buffer
			while (ndx < rbs && source.read(ringbuf[ndx]))
				ndx++;
			
			// validate that we have read at least one full context
			if (ndx != rbs)
				return false;
			
			// advance/reset the index
			ndx = 0;
		} else {
			// we only work on full contexts
			if (!source.read(ringbuf[ndx]))
				return false;
			
			// advance index
			ndx = (ndx + 1) % rbs;
		}
		
		// ring buffer is up to date, oldest observation at ndx; process all bands
		for (int b = 0; b < fs_in; ++b) {
			// extract the current band
			for (int i = 0; i < rbs; ++i)
				buf_dct[i] = ringbuf[(ndx + i) % rbs][b];
			
			// forward DCT
			if (dodct)
				dct.forward(buf_dct, scale);
			
			// copy to out-buffer
			System.arraycopy(buf_dct, 0, buf, b*fs_in, fs_in);
		}
		
		return true;
	}
	
	public static final String SYNOPSIS =
		"sikoried, 1/27/2011\n" +
		"(Raw) TRAPS computation (long context temporal structure features).\n\n" +
		"usage: framed.Traps [parameters]\n" +
		"  -c num\n" +
		"    Construct the temporal context from <num> frames\n" +
		"  -l list out-dir [in-dir]\n" +
		"    Read files from list (and optional in-dir) and store result in out-dir\n" +
		"  --mvn\n" +
		"    Apply turn-wise mean and variance normalization.\n" +
		"  --no-scale\n" +
		"    Do not apply DCT scaling.\n" +
		"  --no-dct\n" +
		"    Do not apply DCT at all.";
	
	public static void main(String [] args) throws IOException {
		if (args.length < 5) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		LinkedList<Pair<String, String>> iolist = new LinkedList<Pair<String, String>>();
		
		int c = 0;
		boolean doMvn = false;
		boolean scale = true;
		boolean dodct = true;
		
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-c"))
				c = Integer.parseInt(args[++i]);
			else if (args[i].equals("-l")) {
				String lif = args[++i];
				String outd = args[++i] + System.getProperty("file.separator");
				String ind = (i+1 < args.length && !args[i+1].startsWith("-")) ? args[++i] + System.getProperty("file.separator") : "";
				BufferedReader lr = new BufferedReader(new FileReader(lif));
				String line = null;
				while ((line = lr.readLine()) != null)
					iolist.add(new Pair<String, String>(ind + line, outd + line));
				
			} else if (args[i].equals("--mvn"))
				doMvn = true;
			else if (args[i].equals("--no-scale"))
				scale = false;
			else if (args[i].equals("--no-dct"))
				dodct = false;
		}
		
		if (iolist.size() == 0)
			throw new IOException("Found no input files!");
		
		while (iolist.size() > 0) {
			Pair<String, String> p = iolist.remove(0);
			FrameSource fs = new FrameInputStream(new File(p.a));
			
			Traps traps = new Traps(fs, c, scale, dodct);
			
			if (doMvn) {
				// build up the MVN stats
				MVN mvn = new MVN();
				mvn.extendStatistics(traps);
				
				// re-init the traps and attach them to the MVN
				traps = new Traps(new FrameInputStream(new File(p.a)), c, scale, dodct);
				mvn.setSource(traps);
				
				fs = mvn;
			} else
				fs = traps;
			
			FrameOutputStream fw = new FrameOutputStream(fs.getFrameSize(), new File(p.b));
			double [] buf = new double [fs.getFrameSize()];
			
			while (fs.read(buf))
				fw.write(buf);
			
			fw.close();
		}
	}
}
