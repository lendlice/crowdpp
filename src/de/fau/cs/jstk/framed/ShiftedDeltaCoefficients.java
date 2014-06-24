/*
	Copyright (c) 2009-2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer
		Tobias Bocklet
		Florian Hoenig
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

/**
 * The ShiftedDeltaCoefficients (SDC) are a set of deltas computed over a certain
 * interval and context. They are characterized by 4 parameters N, D, P, K. <br/>
 * N is the input frame size and is indirectly specified by the referenced source.<br/>
 * D is the number of interleaved frames for the delta computation; in contrast
 * to most literature, this is actually the number of frames within the two
 * frames used for each delta and not a spread allowing only odd distances 
 * (1, 3, 5, ...). <br/>
 * P is the offset of the indivudual deltas w.r.t. the first frame in the context.<br/>
 * K is the number of deltas to compute.<br/>
 * The output dimension is N times K, the internal ringbuffer is of size N times ((K-1)*P + D + 2)
 * 
 * @author sikoried
 *
 */
public class ShiftedDeltaCoefficients implements FrameSource {
	/** FrameSource to read from */
	private FrameSource source;
	
	/** input frame size */
	private int fs_in;
	
	/** spread */
	private int d;
	
	/** gap between deltas */
	private int p;
	
	/** number of deltas */
	private int k;
	
	/** ring buffer */
	private double [][] ringbuf;
	
	/** read index */
	private int pr;
	
	/** write index */
	private int pw;
	
	/** copy the original input data */
	private boolean copy;
	
	/**
	 * Extract shifted delta coefficients (SDC) from the given FrameSource, 
	 * extracted as sdc(t) = ((c(t+d+1)-c(t), c(t+p+d+1)-c(t+p), c(t+2p+d+1)-c(t+2p)-, ...). <br/>
	 * Note that using SDC implicates loosing border frames as there is no
	 * padding! Also: We use a slightly different definition of spread: d is the
	 * number of interleaved frames, it may thus be zero for neighbouring frames!
	 * @param source FrameSource to read from (indirectly specifies SDC parameter n)
	 * @param d spread (typically 1)
	 * @param p gap size (typically 3)
	 * @param k number of deltas to extract
	 * @param copy copy the central frame of the input context to the output vector?
	 */
	public ShiftedDeltaCoefficients(FrameSource source, int d, int p, int k, boolean copy) {
		this.source = source;
		this.d = d;
		this.p = p;
		this.k = k;
		
		this.fs_in = source.getFrameSize();
		this.copy = copy;
		
		ringbuf = new double [(k-1)*p + d + 2][fs_in];
		
		pr = pw = 0;
	}
	
	/**
	 * Extract shifted delta coefficients (SDC) from the given FrameSource, 
	 * extracted as sdc(t) = ((c(t+d+1)-c(t), c(t+p+d+1)-c(t+p), c(t+2p+d+1)-c(t+2p)-, ...). <br/>
	 * Note that using SDC implicates loosing border frames as there is no
	 * padding! Also: We use a slightly different definition of spread: d is the
	 * number of interleaved frames, it may thus be zero for neighbouring frames!
	 * The (central) input vector is copied and placed first in the output vector
	 * @param source FrameSource to read from (indirectly specifies SDC parameter n)
	 * @param d spread (typically 1)
	 * @param p gap size (typically 3)
	 * @param k number of deltas to extract
	 */
	public ShiftedDeltaCoefficients(FrameSource source, int d, int p, int k) {
		this(source, d, p, k, true);
	}

	public boolean read(double [] buf) throws IOException {
		// initial read, fill ring buffer and set read index!
		if (pw == pr) {
			while (pw < ringbuf.length - 1) {
				if (!source.read(ringbuf[pw++]))
					return false;
			}
		}
		
		// regular read
		if (!source.read(ringbuf[pw]))
			return false;
		
		// copy original?
		int offset = 0;
		if (copy) {
			System.arraycopy(ringbuf[(pr + (ringbuf.length - 1)/2) % ringbuf.length], 0, buf, 0, fs_in);
			offset = 1;
		}
			
		// construct SDCs
		for (int i = 0; i < k; ++i) {
			int ndx1 = (pr + i*p + d + 1) % ringbuf.length;
			int ndx2 = (pr + i*p) % ringbuf.length;
						
			double [] v1 = ringbuf[ndx1];
			double [] v2 = ringbuf[ndx2];
			for (int j = 0; j < fs_in; ++j)
				buf[(i+offset)*fs_in + j] = v1[j] - v2[j];
		}
		
		// advance read and write position
		pr = (pr + 1) % ringbuf.length;
		pw = (pw + 1) % ringbuf.length;
		
		return true;
	}

	public int getFrameSize() {
		return fs_in * k + (copy ? fs_in : 0);
	}

	public FrameSource getSource() {
		return source;
	}
	
	/**
	 * Get the ring buffer size. If used within the stream concept, the SDC will
	 * shorten the (end of the) stream by ringbufsize-1 frames, as no padding
	 * is performed.
	 * @return
	 */
	public int getRingBufferSize() {
		return ringbuf.length;
	}
	
	public String toString() {
		return "framed.ShiftedDeltaCoefficients n=" + fs_in + " d=" + d + " p=" + p + " k=" + k + " ringbuf.length=" + ringbuf.length + " copy=" + copy;
	}
	
	public static final String SYNOPSIS = 
		"framed.ShiftedDeltaCoefficients d p k list out-dir [in-dir]\n" +
		"Compute SDCs from the given input frame files. While the parameter n is implicitly\n" +
		"by the input data, a typical setting for d-p-k is 1-3-7. The input directory is\n" +
		"optional.";
	
	public static void main(String [] args) throws Exception {
		if (args.length < 5 || args.length > 6) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		int d = Integer.parseInt(args[0]);
		int p = Integer.parseInt(args[1]);
		int k = Integer.parseInt(args[2]);
		
		String listf = args[3];
		String outd = args[4] + System.getProperty("file.separator");
		String ind = (args.length == 6 ? args[5] + System.getProperty("file.separator") : ""); 
		
		
		LinkedList<Pair<String, String>> iolist = new LinkedList<Pair<String, String>>();
		
		BufferedReader lr = new BufferedReader(new FileReader(listf));
		String line = null;
		while ((line = lr.readLine()) != null)
				iolist.add(new Pair<String, String>(ind + line, outd + line));
		
		if (iolist.size() == 0)
			throw new IOException("Found no input files!");
		
		while (iolist.size() > 0) {
			Pair<String, String> pp = iolist.remove(0);
			FrameSource fs = new FrameInputStream(new File(pp.a));
			ShiftedDeltaCoefficients sdc = new ShiftedDeltaCoefficients(fs, d, p, k);
			
			FrameOutputStream fw = new FrameOutputStream(sdc.getFrameSize(), new File(pp.b));
			double [] buf = new double [sdc.getFrameSize()];
			
			while (sdc.read(buf))
				fw.write(buf);
			
			fw.close();
		}
	}
}
