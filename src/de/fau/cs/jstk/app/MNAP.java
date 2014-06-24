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
package de.fau.cs.jstk.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.trans.NAP;

/**
 * Based on NAP, modular NAP computes and applies a transformation to each
 * segment of the data. This is useful if certain subspaces of the data belong
 * together and should not be mixed up. For Example, super-vectors of Mixtures
 * should be transformed in chunks corresponding to the mean values of the 
 * Mixtures.
 * 
 * @author sikoried
 */
public class MNAP implements FrameSource {
	private static Logger logger = Logger.getLogger(MNAP.class);
	
	private FrameSource source;
	
	private int subd;
	
	private double [] sub = null;
	
	private NAP [] naps = null;
	
	public MNAP(InputStream is) throws IOException {
		load(is);
		sub = new double [subd];
	}
	
	public MNAP(int rank, int subdim, File filelf, File dirlf) throws IOException {
		sub = new double [subd];
		subd = subdim;
		compute(rank, filelf, dirlf);
	}
	
	public void setSource(FrameSource source) throws IOException {
		this.source = source;
		
		if (source.getFrameSize() % subd != 0)
			throw new IOException("MNAP: source.frameSize % subd != 0");
		
		logger.info("MNAP.setSource(): source=" + source.toString());
	}
	
	public void load(InputStream is) throws IOException {
		logger.info("MNAP.load(): loading projections...");
		subd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		int n = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		
		naps = new NAP [n];
		for (int i = 0; i < n; ++i)
			naps[i] = new NAP(is);
		
		logger.info("MNAP.load(): ready -- " + toString());
	}
	
	public void save(OutputStream os) throws IOException {
		// write out transformations
		logger.info("MNAP.save(): saving " + naps.length + " transformations");
		IOUtil.writeInt(os, subd, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, naps.length, ByteOrder.LITTLE_ENDIAN);
		for (NAP nap : naps)
			nap.save(os);
	}
	
	public NAP [] getTransformations() {
		return naps;
	}
	
	public void compute(int rank, File filelf, File dirlf) throws IOException {
		logger.info("MNAP.compute() caching data");
		// cache all the data
		LinkedList<Integer> chanLengths = new LinkedList<Integer>();
		LinkedList<LinkedList<float []>> samples = new LinkedList<LinkedList<float[]>>();
		
		float [] buf = null;
		float [] sub = new float [subd];
		
		// read channel list and file list
		LinkedList<String> chans = new LinkedList<String>();
		LinkedList<String> files = new LinkedList<String>();
		BufferedReader br = new BufferedReader(new FileReader(dirlf));
		String line;
		while ((line = br.readLine()) != null) 
			chans.add(line);
		br.close();
		br = new BufferedReader(new FileReader(filelf));
		while ((line = br.readLine()) != null) 
			files.add(line);
		
		// read in channels
		for (int i = 0; i < chans.size(); ++i) {
			String ch = chans.get(i);
			logger.info("MNAP.compute(): reading channel " + ch);
			
			int nf = 0;
			for (String f : files) {
				FrameInputStream fr = new FrameInputStream(new File(ch + System.getProperty("file.separator") + f));
				
				if (buf == null) {
					buf = new float [fr.getFrameSize()];
					if (buf.length % subd != 0)
						throw new IOException("MNAP.compute(): buf.length % subd != 0 => chose a proper subdim!");
					
					for (int j = 0; j < buf.length / subd; ++j)
						samples.add(new LinkedList<float []>());
				} else if (buf.length != fr.getFrameSize())
					throw new IOException("MNAP.compute(): channel " + ch +  System.getProperty("file.separator") + f + " does not match initial feature dimension!");
				
				while (fr.read(buf)) {
					for (int j = 0; j < buf.length / subd; ++j) {
						System.arraycopy(buf, i*subd, sub, 0, subd);
						samples.get(j).add(sub.clone());
					}
					nf++;
				}
			}
			
			chanLengths.add(nf);
		}
		
		// for each subdimension: allocate and transfer data
		naps = new NAP [buf.length / subd];
		float [][] a = null;
		float [][] w = null;
		
		int k = 0;
		for (LinkedList<float []> li : samples) {
			// allocate or verify data matrix
			if (a == null)
				a = new float [li.size()][];
			else if (a.length != li.size())
				throw new IOException("MNAP.compute(): li.size() != prev.li.size()");
			
			Iterator<float []> it = li.iterator();
			for (int i = 0; i < a.length; ++i)
				a[i] = it.next();
			logger.info("MNAP.compute(): cached " + a.length + " frames, dim=" + subd);
			
			logger.info("MNAP.compute(): building up weight matrix...");
		
			// channel compensation: W_ij = 1 for samples of disjoint channel
			if (w == null) {
				w = new float [a.length][a.length];
				int off = 0;
				for (int l : chanLengths) {
					for (int i = off; i < off + l; ++i) {
						if (off > 0)
							Arrays.fill(w[i], 0, off, 1.f);
						
						Arrays.fill(w[i], off, off + l, 0.f);
						
						if (off + l < w.length)
							Arrays.fill(w[i], off + l, w.length, 1.f);
					}
					off += l;
				}
			}
			
			logger.info("MNAP.compute(): computing V...");
			NAP nap = new NAP();
			nap.computeV(a, w, rank);
			
			// store transformation
			naps[k++] = nap;
		}
		
		logger.info("MNAP.compute(): computed " + naps.length + " NAP transformations");
	}
	
	public boolean read(double [] buf) throws IOException {
		if (!source.read(buf))
			return false;
		
		for (int i = 0; i < buf.length / subd; ++i) {
			System.arraycopy(buf, i*subd, sub, 0, subd);
			naps[i].project(sub);
			System.arraycopy(sub, 0, buf, i*subd, subd);
		}
		
		return true;
	}
	
	public int getFrameSize() {
		return source.getFrameSize();
	}
	
	public FrameSource getSource() {
		return source;
	}
	
	public String toString() {
		return "app.MNAP num_proj=" + naps.length + " subd=" + subd;
	}
	
	public static final String SYNOPSIS = 
		"sikoried, 3/1/2011\n" +
		"Compute a modular NAP on subdimensions of the data. Useful for Mixture\n" +
		"supervectors\n" +
		"\n" +
		"usage: app.MNAP [options]\n" +
		"-c file rank subdim file-list dir-list\n" +
		"  Compute modular NAP projections on (subdim) dimensions and max (rank)\n" +
		"  and save the projections to the given (single) file. Files in file-list\n" +
		"  are expected in every directory in the dir-list\n" +
		"-a file rank list outdir [indir]\n" +
		"  Apply the modular NAP projectios from (file) with the given (rank) to\n" +
		"  the files in the list and put save them to (outdir). If necessary, specify\n" +
		"  a (indir) directory where to find the input files.\n" +
		"-v\n" +
		"  Be verbose.";
	
	public static void main(String [] args) throws Exception {
		BasicConfigurator.configure();
		Logger.getLogger("de.fau.cs.jstk").setLevel(Level.FATAL);
		
		if (args.length < 5) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		// scan for verbose flag
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-v")) 
				Logger.getLogger("de.fau.cs.jstk").setLevel(Level.INFO);
		}
		
		// actions
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-c")) {
				compute(new File(args[i+1]), Integer.parseInt(args[i+2]), Integer.parseInt(args[i+3]), new File(args[i+4]), new File(args[i+5]));
			} else if (args[i].equals("-a")) {
				apply(new File(args[i+1]), Integer.parseInt(args[i+2]), new File(args[i+3]), args[i+4], (i+5 < args.length ? args[i+5] : null));
			} else if (args[i].equals("-v")) {
				Logger.getLogger("de.fau.cs.jstk").setLevel(Level.INFO);
			} 
		}
	}
	
	/**
	 * Compute a new modular NAP and save it to the given file. Strategy is NAP.CC
	 * @param outf file to store the transformations
	 * @param rank desired rank (0 for max rank)
	 * @param subdim subdimensions to split the input data ( dim % subdim =!= 0 )
	 * @param filelf list with files
	 * @param dirlf list with directories
	 * @throws Exception
	 */
	public static void compute(File outf, int rank, int subdim, File filelf, File dirlf) throws Exception {
		logger.info("MNAP.compute(): outf=" + outf + " rank=" + rank + " subd=" + subdim + " files=" + filelf + " dirs=" + dirlf);
		MNAP mnap = new MNAP(rank, subdim, filelf, dirlf);
		FileOutputStream fos = new FileOutputStream(outf);
		mnap.save(fos);
		fos.close();
	}
	
	/**
	 * Apply a pre-computed modular NAP to the files in the list file.
	 * @param inf mnap projection file
	 * @param rank desired rank
	 * @param listf list file
	 * @param outd output directory
	 * @param ind input directory (null for none)
	 * @throws Exception
	 */
	public static void apply(File inf, int rank, File listf, String outd, String ind) throws Exception {
		logger.info("MNAP.apply(): inf=" + inf + " rank=" + rank + " files=" + listf + " outd=" + outd + " ind=" + (ind == null ? "null" : ind));
		MNAP mnap = new MNAP(new FileInputStream(inf));
		
		logger.info("MNAP.apply(): applying " + mnap.toString() + " with rank=" + rank);
		BufferedReader br = new BufferedReader(new FileReader(listf));
		String outf;
		while ((outf = br.readLine()) != null) {
			// read in file
			FrameInputStream fr = new FrameInputStream(new File(ind == null ? outf : ind + System.getProperty("file.separator") + outf));
			mnap.setSource(fr);
			FrameOutputStream fw = new FrameOutputStream(fr.getFrameSize(), new File(outd + System.getProperty("file.separator") + outf));
			double [] buf = new double [fr.getFrameSize()];
			while (mnap.read(buf))
				fw.write(buf);
			fr.close();
			fw.close();
		}
	}
}
