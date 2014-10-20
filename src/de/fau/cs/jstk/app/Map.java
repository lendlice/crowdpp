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
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import android.annotation.SuppressLint;
import de.fau.cs.jstk.io.ChunkedDataSet;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.stat.Sample;
import de.fau.cs.jstk.stat.Trainer;
import de.fau.cs.jstk.util.Pair;


@SuppressLint("DefaultLocale")
public class Map {
	private static Logger logger = Logger.getLogger(Map.class);
	
	public static final String SYNOPSIS = 
		"MAP adaption for mixture densities, bocklet & sikoried 07/2009\n\n" +
		"Adapt an initial mixture density using the given feature data. If\n" +
		"num-iterations is specified, the MAP step is repeated.\n\n" +
		"usage: app.Map [patameters]\n" + 
		"-i <initial>\n" +
		"-o <adapted>\n" +
		"-a <mode>\n" +
		"   Specify parameters to adapt: (p)riors, (m)eans, (c)ovariances; default 'pmc'\n" +
		"-r <relevance>\n" +
		"   Relevance factor >= 0 to weight new statistics against old model. The higher, the more weight the old model gets. Default 16\n" +
		"-n num-iterations\n" +
		"   Number of MAP iterations; default 1\n" +
		"-l list\n" +
		"   Read adaptation data from files in list\n" +
		"-f file\n" +
		"   Read adaptation data from given file\n" +
		"-L <in-out-list>\n" +
		"   Use in-out-list for large number of adaptation tasks\n" +
		"--ufv <frameSize>\n" +
		"-v\n" +
		"   Be verbose\n";

	public static void main(String[] args) throws IOException,
			ClassNotFoundException {
		if (args.length < 3) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}

		logger.setLevel(Level.WARN);
		
		int i = 0;

		Mixture initial = null;
		String outfile = null;
		String infile = null;
		String mode = "pmc";
		int numiters = 1;

		int ufv = 0;

		double r = 16.;

		LinkedList<File> dataFiles = new LinkedList<File>();
		LinkedList<Pair<File, File>> inout = new LinkedList<Pair<File, File>>();
		
		for (i=0; i < args.length; ++i) {
			if (args[i].equals("-i"))
				infile = args[++i];
			else if (args[i].equals("-o"))
				outfile = args[++i];
			else if (args[i].equals("-a"))
				mode = args[++i].toLowerCase();
			else if (args[i].equals("-r"))
				r = Double.parseDouble(args[++i]);
			else if (args[i].equals("-l")) {
				BufferedReader br = new BufferedReader(
						new FileReader(args[++i]));
				String buf;
				while ((buf = br.readLine()) != null)
					dataFiles.add(new File(buf));
				br.close();
			} else if (args[i].equals("-v"))
				logger.setLevel(Level.ALL);
			else if (args[i].equals("-f")) {
				dataFiles.add(new File(args[++i]));
			} else if (args[i].equals("-L")) { 
				BufferedReader br = new BufferedReader(new FileReader(args[++i]));
				String sbuf;		
				int ln = 0;
				while ((sbuf = br.readLine()) != null) {
					ln++;
					Pair<File, File> pair;
					if (sbuf.split("\\s+").length > 1)
						pair = new Pair<File, File>(new File(sbuf.split("\\s+")[0]), new File(sbuf.split("\\s+")[1]));
					else
						throw new IOException("list file broken at line " + ln);
					if (!pair.a.canRead())
						throw new IOException("Couldn't access " + args[i] + ":"
								+ pair.a + " for reading.");
					inout.add(pair);
				}
				br.close();
			} else if (args[i].equals("--ufv")) {
				ufv = Integer.parseInt(args[++i]);
			} else {
					System.err.println("Unknown argument: "+ args[i]);
					System.exit(-1);
			}	
		}

		if (infile == null) {
			System.err.println("Map.main(): no input file specified");
			System.exit(1);
		}


		// any data?
		if (inout.size() > 0 && dataFiles.size() > 0) {
			System.err.println("-L and [-l, -f] are exclusive!");
			System.exit(1);
		} else if (inout.size() == 0 && dataFiles.size() == 0) {
			System.err.println("Map.main(): No data files provided! Exitting...");
			System.exit(1);
		}
		
		if (dataFiles.size() > 0) {			
			if (outfile == null) {
				System.err.println("Map.main(): no output file specified");
				System.exit(1);
			}
			
			// read initial density
			logger.info("Map.main(): reading initial model...");
			initial = Mixture.readFromFile(new File(infile));
	
			// read all the data into the memory
			logger.info("Map.main(): Reading feature data...");
			ChunkedDataSet ds = new ChunkedDataSet(dataFiles, ufv);
			List<Sample> data = ds.cachedData();
			logger.info("Map.main(): " + data.size() + " samples read");
	
			logger.info("Map.main(): performing " + numiters + " MAP steps (r = " + r + ", mode=" + mode + ")");
			Mixture adapted = Trainer.map(initial, data, r, numiters, mode);
	
			logger.info("Map.main(): writing adapted mixture to " + outfile + "...");
			adapted.writeToFile(new File(outfile));
		} else {
			// read initial density
			logger.info("Map.main(): reading initial model...");
			initial = Mixture.readFromFile(new File(infile));
	
			// read all the data into the memory
			for (Pair<File, File> p : inout) {
				logger.info("Map.main(): " + infile + " <- " + p.a + " => " + p.b);
				logger.info("Map.main(): Reading feature data...");
				
				FrameInputStream fr = new FrameInputStream(p.a, true, ufv);
				List<Sample> data = new LinkedList<Sample>();
				double [] buf = new double [fr.getFrameSize()];
				while (fr.read(buf))
					data.add(new Sample((short) 0, buf));
				fr.close();
				
				logger.info("Map.main(): " + data.size() + " samples read");
		
				logger.info("Map.main(): performing " + numiters + " MAP steps (r = " + r + ", mode=" + mode + ")");
				Mixture adapted = Trainer.map(initial, data, r, numiters, mode);
		
				logger.info("Map.main(): writing adapted mixture to " + outfile + "...");
				adapted.writeToFile(p.b);
			}
		}
	}
}
