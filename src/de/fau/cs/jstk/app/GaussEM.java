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


import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.ChunkedDataSet;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.stat.Density;
import de.fau.cs.jstk.stat.Density.Flags;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.stat.ParallelEM;
import de.fau.cs.jstk.stat.Sample;
import de.fau.cs.jstk.stat.Trainer;

public class GaussEM {
	private static Logger logger = Logger.getLogger(GaussEM.class);
	
	public static final String SYNOPSIS = 
		"Estimate Gaussian mixture densities using an initial estimate and a\n" + 
		"(large) data set.\n\n" +
		"usage: app.GaussEM <options>\n" +
		"  -i initial-model\n" +
		"    Initial estimate of the mixture density. See bin.Initializer for\n" +
		"    possible starts.\n" +
		"  -n iterations\n" +
		"    Number of EM iterations to compute.\n" +
		"  -o output-model\n" +
		"    File to write the final estimate to.\n" +
		"  -l listfile\n" +
		"    Use a list file to specify the files to read from.\n" +
		"  -d <directory>\n"+
		"    Specifies the path where the inputfiles are located\n"+
		"  -p num\n" +
		"    Parallelize the EM algorithm on num cores (threads). Use 0 for \n" +
		"    maximum available number of cores. NB: -p 1 is different from -s as\n" +
		"    it doesn't cache the entire data set.\n" +
		"  --update [wmv]\n" +
		"    Update selected parameters: [w]eights [m]eans and [v]ariances\n" +
		"  -s\n" +
		"    Do a standard single-core EM with a complete caching of the data.\n" +
		"    This might be faster than -p for small problems with less files.\n" +
		"  --save-partial-estimates\n" +
		"    Write out the current estimate after each iteration (to output-model.*)\n" +
		"\n" +
		"default: -n 10 -p 0\n";
	
	public static void main(String[] args) throws IOException, Exception {
		BasicConfigurator.configure();
		
		if (args.length < 6) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		String inf = null;
		String ouf = null;
		String lif = null;
		String inDir = null;
		
		int ufv = 0;
		
		boolean savePartialEstimates = false;
		
		// number of iterations
		int n = 10;
		
		// number of cores
		int c = Runtime.getRuntime().availableProcessors();
		
		Density.Flags flags = Flags.fAllParams;
		
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-i"))
				inf = args[++i];
			else if (args[i].equals("-o"))
				ouf = args[++i];
			else if (args[i].equals("-p")) {
				int tc = Integer.parseInt(args[++i]);
//				if (tc > c)
//					throw new RuntimeException("too many cores requested!");
				if (tc > 0)
					c = tc;
			} else if (args[i].equals("-s"))
				c = -1;
			else if (args[i].equals("-n"))
				n = Integer.parseInt(args[++i]);
			else if (args[i].equals("-l"))
				lif = args[++i];
			else if (args[i].equals("-d"))
				inDir = args[++i];
			else if (args[i].equals("--update")) {
				String arg = args[++i].toLowerCase();
				flags = new Density.Flags(arg.contains("w"), arg.contains("m"), arg.contains("v"));
			} else if (args[i].equals("--save-partial-estimates")) {
				savePartialEstimates = true;
			} else if (args[i].equals("--ufv")) {
				ufv = Integer.parseInt(args[++i]);
				
			} else {
				System.err.println("Unknown argument: "+ args[i]);
				System.exit(-1);
			}
				
		}
		
		if (inf == null) {
			System.err.println("no input file specified");
			System.exit(1);
		}
		
		if (ouf == null) {
			System.err.println("no output file specified");
			System.exit(1);
		}
		
		if (lif == null) {
			System.err.println("no list file specified");
			System.exit(1);
		}
		
		logger.info("Reading from " + inf + "...");
		Mixture initial, estimate;
		initial = Mixture.readFromFile(new File(inf));
		
		if (savePartialEstimates)
			initial.writeToFile(new File(ouf + ".0"));
		
		if (c == -1) {
			logger.info("Caching feature data...");
			LinkedList<Sample> data = new LinkedList<Sample>();
			ChunkedDataSet set = new ChunkedDataSet(new File(lif), inDir, ufv);
			ChunkedDataSet.Chunk chunk;
			while ((chunk = set.nextChunk()) != null) {
				FrameInputStream r = chunk.getFrameReader();
				double [] buf = new double [r.getFrameSize()];
				while (r.read(buf))
					data.add(new Sample((short) 0, buf));
			}
			logger.info(data.size() + " samples cached");
			
			logger.info("Starting " + n + " EM iterations: single-core, cached data");			
			
			estimate = initial;
			for (int i = 0; i < n; ++i) {
				estimate = Trainer.em(estimate, data);
				if (savePartialEstimates)
					estimate.writeToFile(new File(ouf + "." + (i+1)));
			}
		} else {
			logger.info("Starting " + n + " EM iterations on " + c + " cores");

			ParallelEM pem = new ParallelEM(initial, new ChunkedDataSet(new File(lif), inDir, ufv), c, flags);

			for (int i = 0; i < n; ++i) {
				pem.iterate();
				if (savePartialEstimates)
					pem.current.writeToFile(new File(ouf + "." + (i+1)));
			}
			estimate = pem.current;
		}
		
		logger.info("Saving new estimate...");
		estimate.writeToFile(new File(ouf));
	}
}
