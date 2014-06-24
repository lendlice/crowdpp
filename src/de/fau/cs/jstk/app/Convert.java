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
//frame ascii  --in-out-list listfile.txt
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

import de.fau.cs.jstk.io.FrameDestination;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameReader;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.io.FrameWriter;
import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.io.SampleDestination;
import de.fau.cs.jstk.io.SampleInputStream;
import de.fau.cs.jstk.io.SampleOutputStream;
import de.fau.cs.jstk.io.SampleReader;
import de.fau.cs.jstk.io.SampleSource;
import de.fau.cs.jstk.io.SampleWriter;
import de.fau.cs.jstk.stat.Sample;

public class Convert {
	public static final short LABEL_SIZE = 12;
	
	public static final String SYNOPSIS = 
		"Translate between various feature file formats.\n\n" +
		"usage: app.Convert in_format out_format < data_in > data_out [options]\n\n" +
		"formats:\n" +
		"  ufv,dim\n" +
		"    Unlabeled feature data, 4 byte (float) per sample dimension\n" +
		"  lfv,dim\n" +
		"    Labeled feature data; 12 byte label, then 4 byte (float) per sample.\n" +
		"    Labels must be numeric.\n" +
		"  frame, frame_double\n" +
		"    Unlabeled feature data, 4/8 byte (float/double) per sample dimension\n" +
		"  sample_a, sample_b\n" +
		"    Labeled feature data using the stat.Sample class, either (a)scii or\n" +
		"    (b)inary. Format is <short:label> <short:classif-result> <float: feature data>\n" +
		"  ascii\n" +
		"    Unlabeled ASCII data: TAB separated double values, one sample per line.\n\n"+
		"options:\n\n" +
		"  --in-out-list listfile\n" +
		"    the list contains lines \"<in-file> <out-file>\" for batch processing.\n" +
		"    If <out-file> is missing, put everything out to stdout\n\n";
		
	
	public static enum Format {
		UFV,
		LFV,
		FRAME,
		FRAME_DOUBLE,
		SAMPLE_A,
		SAMPLE_B,
		ASCII
	}
	
	public static int fd = 0;
	
	/**
	 * Analyze the format string
	 */
	public static Format determineFormat(String arg) {
		if (arg.startsWith("ufv,")) {
			fd = Integer.parseInt(arg.substring(4));
			return Format.UFV;
		} else if (arg.startsWith("lfv,")) {
			String [] list = arg.split(",");
			fd = Integer.parseInt(list[1]);
			return Format.LFV;
		} else if (arg.equals("frame"))
			return Format.FRAME;
		else if (arg.equals("frame_double"))
			return Format.FRAME_DOUBLE;
		else if (arg.equals("sample_a"))
			return Format.SAMPLE_A;
		else if (arg.equals("sample_b"))
			return Format.SAMPLE_B;
		else if (arg.equals("ascii"))
			return Format.ASCII;
		else
			throw new RuntimeException("invalid format \"" + arg + "\"");
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}

		LinkedList<String> inlist = new LinkedList<String>();
		LinkedList<String> outlist = new LinkedList<String>();
		String listFile = null;
		
		// get input and output formats
		Format inFormat = determineFormat(args[0]);
		Format outFormat = determineFormat(args[1]);		

		// parse other options
		for (int i = 2; i < args.length; ++i) {		
		  if (args[i].equals("--in-out-list")){
			  // already at last argument?
			  if (i == args.length - 1){
				  throw new IllegalArgumentException("missing argument for option " + args[i]);
			  }
			  listFile = args[++i];
		  }
		  else{
			  System.err.println("unknown option " + args[i]);
			  System.exit(1);
		  }
		}
		
		if (listFile == null){
			// null to indicate stdin/stdout
			inlist.add(null);
			outlist.add(null);
		}
		else {
			BufferedReader lr = new BufferedReader(new FileReader(listFile));
			String line = null;
			int i = 1;
			while ((line = lr.readLine()) != null) {			
				String [] help = line.split("\\s+");
				if (help.length == 2){
					inlist.add(help[0]);					
					outlist.add(help[1]);
				}
				else if (help.length == 1){
					inlist.add(help[0]);
					// indicate stdout:
					outlist.add(null);					
				}
				else throw new Exception("file list is broken at line " + i);
				
				i++;
			}
		}
		
		while (inlist.size() > 0) {
			// get next file
			String inFileName = inlist.remove(0);
			String outFileName = outlist.remove(0);
			
			InputStream in = null;
			
			// FIXME: remove as soon as FrameInputStream takes InputStreams
			File inFile = null;
			File outFile = null;
			
			OutputStream out = null;
			if (inFileName == null){
				in = System.in;
			}
			else {
				in = new FileInputStream(inFileName);

				inFile = new File(inFileName);
			}
			
			if (outFileName == null){
				out = System.out;
			}
			else {
				out = new FileOutputStream(outFileName);
				
				outFile = new File(outFileName);
			}

			// possible readers
			FrameSource fsource = null;
			SampleSource ssource = null;

			// possible writers
			FrameDestination fdest = null;
			SampleDestination sdest = null;

			switch (inFormat) {
			case SAMPLE_A: ssource = new SampleReader(new InputStreamReader(in)); break;
			case SAMPLE_B: ssource = new SampleInputStream(in); break;
			case FRAME: fsource = new FrameInputStream(inFile); fd = fsource.getFrameSize(); break;
			case FRAME_DOUBLE: fsource = new FrameInputStream(inFile, false); fd = fsource.getFrameSize(); break;
			case ASCII: fsource = new FrameReader(new InputStreamReader(in)); fd = fsource.getFrameSize(); break;
			}

			double [] buf = new double [fd];

			// read until done...
			while (true) {
				Sample s = null;
				short label = 0;

				// try to read...
				switch (inFormat) {
				case FRAME:
				case FRAME_DOUBLE:
				case ASCII:
					if (fsource.read(buf))
						s = new Sample((short) 0, buf);
					break;
				case SAMPLE_A:
				case SAMPLE_B:
					s = ssource.read();
					break;
				case LFV:
					byte [] bl = new byte [LABEL_SIZE];
					if (!IOUtil.readByte(in, bl))
						break;
					String textual = new String(bl);
					try {
						label = Short.parseShort(textual);
					} catch (NumberFormatException e) {
						throw new IOException("Invalid label '" + textual + "' -- only numeric labels allowed!");
					}
				case UFV:
					if (!IOUtil.readFloat(in, buf, ByteOrder.LITTLE_ENDIAN)) 
						break;

					s = new Sample(label, buf);
					break;
				}

				// anything read?
				if (s == null)
					break;

				// write out...
				switch (outFormat) {
				case SAMPLE_A:
					if (sdest == null)
						sdest = new SampleWriter(new OutputStreamWriter(out));
					sdest.write(s);
					break;
				case SAMPLE_B:
					if (sdest == null)
						sdest = new SampleOutputStream(out, s.x.length);
					sdest.write(s);
					break;
				case FRAME:
					if (fdest == null)
						fdest = new FrameOutputStream(s.x.length, outFile);
					fdest.write(s.x);
					break;
				case FRAME_DOUBLE:
					if (fdest == null)
						fdest = new FrameOutputStream(s.x.length, outFile, false);
					fdest.write(s.x);
					break;
				case LFV:
					byte [] outlabel1 = new byte [LABEL_SIZE];
					byte [] outlabel2 = Integer.toString(s.c).getBytes();
					for (int i = 0; i < LABEL_SIZE; ++i) {
						if (i < outlabel2.length)
							outlabel1[i] = outlabel2[i];
						else
							outlabel1[i] = 0;
					}
					out.write(outlabel1);
				case UFV:
					ByteBuffer bb = ByteBuffer.allocate(buf.length * Float.SIZE/8);

					// UFVs are little endian!
					bb.order(ByteOrder.LITTLE_ENDIAN);

					for (double d : s.x) {
						bb.putFloat((float) d);
					}

					out.write(bb.array());
					break;
				case ASCII:
					if (fdest == null) {
						fdest = new FrameWriter(new OutputStreamWriter(out));
					}
					double [] d = new double [s.x.length];
					for (int i = 0; i < d.length; ++i) {
						int j = (int) Math.round(s.x[i] * 1000);
						d[i] = (double) j / 1000;
					}
					fdest.write(d);
					//fdest.write(s.x);
					break;
				}
			}

			// be nice, close everything? No, just flush (for the benefit of multiple files to stdout)
			if (fdest != null)
				fdest.flush();
			if (sdest != null)
				sdest.flush();
		
			out.flush();
		}
	}
}
