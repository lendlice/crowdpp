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

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.ChunkedDataSet;
import de.fau.cs.jstk.stat.Initialization;
import de.fau.cs.jstk.stat.Initialization.DensityRankingMethod;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.stat.Sample;


public class Initializer {	
	public static final String SYNOPSIS = "Mixture and (single-class) HMM initializer, sikoried 02/2010\n"
			+ "Use this program to initialize both Gaussian mixtures and (single-class)\n"
			+ "hidden Markov models. Both come in different flavors and initialization\n"
			+ "algorithm, so be sure to pick the right one.\n\n"
			+ "NOTA BENE:\n"
			+ "    Initialization is usually slow, well, really slow, and the feature\n"
			+ "    data is cached (some algorithms actually depend on it). So reduce the\n"
			+ "    amount of data to some reasonable subset of your available training\n"
			+ "    data if you want it done by today.\n\n"
			+ "usage: app.Initializer [options]\n"
			+ "--list <list-file>\n"
			+ "  Read training samples from files specified in the referenced <list-file>.\n"
			+ "  This parameter can be used repeatedly to conveniently stack up lists.\n"
			+ "--dir <string>\n"
			+ "  Path to the input files (will be added to the files in the list)\n"
			+ "--file <feature-file>\n"
			+ "  Read training samples from the referenced <feature-file>. This parameter\n"
			+ "  can be used repeatedly to conveniently stack up multiple files to use.\n"
			+ "  Make sure you use the --list parameter to save typing...\n"
			+ "--ufv <dimension>\n"
			+ "  Input format is UFV format with given dimension.\n"
			+ "--gmm out-file\n"
			+ "  Initialize a Gaussian mixture model and save it to 'out-file'. The\n"
			+ "  default strategy produces a mixture of 4 Gaussians with diagonal\n"
			+ "  covariance matrices using the k-nearest neighbour strategy.\n"
			+ "  The following further options are available:\n"
			+ "  -f\n"
			+ "    Use full covariance matrices instead of diagonal ones.\n"
			+ "  -n <number>\n"
			+ "    Initialize <number> clusters instead of 4.\n"
			+ "  -s <strategy>\n"
			+ "    Change the initialization strategy. The following algorithms are\n"
			+ "    available:\n"
			+ "    knn\n"
			+ "      Find the clusters by iteratively distribute the data into the\n"
			+ "      num-cluster clusters, refining the centroid in each step.\n\n"
			+ "    The following options provide hierarchical, statistically driven\n"
			+ "    Gaussian clustering, similar to the LBG algorithm:\n\n"
			+ "    g-none     : split cluster if not normally distributed (no re-ranking)\n"
			+ "    g-cov      : split the cluster with highest covariance\n"
			+ "    g-sum_ev   : split the cluster with the highest sum of eigen values of\n"
			+ "                 the covariance\n"
			+ "    g-diff_ev  : split the cluster with the highest difference in eigen\n"
			+ "                 values\n"
			+ "    g-ad_score : split the cluster with the highest Anderson-Darling\n"
			+ "                 statistics\n"
			+ "    g-ev       : compare densities by the largest EV\n"
			+ "    g-size     : split largest cluster first\n\n"
			+ "    The following initializations provide rather trivial but very fast\n"
			+ "    initializations:\n\n"
			+ "    sequential[_<n>] : Add the next <n> samples to the current Gaussian, then\n"
			+ "                     then switch to the next (and loop once all are visited)\n"
			+ "    random[_<n>]     : Put the next <n> samples to a random Gaussian. If one\n"
			+ "                     Gaussian remains w/o observations, the largest cluster\n"
			+ "                     is split.\n"
			+ "    uniform        : Distribute the samples uniformly, in the sequence read.\n";

	public static void main(String[] args) throws Exception {
		// check arguments
		if (args.length < 1) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}

		BasicConfigurator.configure();
		Logger.getLogger("de.fau.cs.jstk").setLevel(Level.INFO);
		
		// default parameters
		int numberOfDensities = 4;
		boolean diagonalCovariance = true;
		String output = null;
		String gmm_strategy = "knn";
		String inputDir = null;
		String inputList = null;
		
		// use ufv feature files?
		int ufv = 0;

		// shared stuff
		LinkedList<File> files = new LinkedList<File>();

		// read arguments
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("--file")) {
				if (!(new File(args[++i])).canRead())
					throw new IOException("Couldn't access " + args[i]
							+ " for reading.");
				files.add(new File(args[i]));
			} else if (args[i].equals("--list")) {
				inputList = args[++i];
			} else if (args[i].equals("--dir"))
				inputDir = args[++i];
			else if (args[i].equals("-f"))
				diagonalCovariance = false;
			else if (args[i].equals("-n"))
				numberOfDensities = Integer.parseInt(args[++i]);
			else if (args[i].equals("-s"))
				gmm_strategy = args[++i];
			else if (args[i].equals("--gmm"))
				output = args[++i];
			else if (args[i].equals("--ufv"))
				ufv = Integer.parseInt(args[++i]);
			else {
				System.err.println("Unknown argument: "+ args[i]);
				System.exit(-1);
			}
		}

		if (inputList != null) {
			BufferedReader br = new BufferedReader(new FileReader(inputList));
			String buf;
			while ((buf = br.readLine()) != null) {
				if (inputDir != null) {
					buf = inputDir + System.getProperty("file.separator") + buf;
				}				
				if (!(new File(buf)).canRead())
					throw new IOException("Couldn't access " + inputList + ":"
							+ buf + " for reading.");
				files.add(new File(buf));
			}
			br.close();
		}

		if (files.size() == 0) {
			System.err.println("Initializer.main(): No feature files provided! Exitting...");
			System.exit(1);
		}
	
		// read all the data into the memory
		ChunkedDataSet ds = new ChunkedDataSet(files, ufv);
		List<Sample> data = ds.cachedData();

		System.err.println("Initializer.main(): Cached " + data.size() + " samples");

		Mixture estimate = null;

		System.err.println("Initializer.main(): Starting clustering...");

		if (gmm_strategy.equals("knn"))
			estimate = Initialization.kMeansClustering(data,
					numberOfDensities, diagonalCovariance);
		else if (gmm_strategy.equals("g-none"))
			estimate = Initialization.gMeansClustering(data, 0.1,
					numberOfDensities, diagonalCovariance);
		else if (gmm_strategy.equals("g-cov"))
			estimate = Initialization.hierarchicalGaussianClustering(data,
					numberOfDensities, diagonalCovariance,
					DensityRankingMethod.COVARIANCE);
		else if (gmm_strategy.equals("g-sum_ev"))
			estimate = Initialization.hierarchicalGaussianClustering(data,
					numberOfDensities, diagonalCovariance,
					DensityRankingMethod.SUM_EIGENVALUE);
		else if (gmm_strategy.equals("g-diff_ev"))
			estimate = Initialization.hierarchicalGaussianClustering(data,
					numberOfDensities, diagonalCovariance,
					DensityRankingMethod.EV_DIFFERENCE);
		else if (gmm_strategy.equals("g-ad_score"))
			estimate = Initialization.hierarchicalGaussianClustering(data,
					numberOfDensities, diagonalCovariance,
					DensityRankingMethod.AD_STATISTIC);
		else if (gmm_strategy.equals("g-ev"))
			estimate = Initialization.hierarchicalGaussianClustering(data,
					numberOfDensities, diagonalCovariance,
					DensityRankingMethod.EV);
		else if (gmm_strategy.equals("g-size"))
			estimate = Initialization.hierarchicalGaussianClustering(data, 
					numberOfDensities, diagonalCovariance, 
					DensityRankingMethod.NUM_SAMPLES);
		// trivial strategies can be handled right here
		else if (gmm_strategy.startsWith("sequential")
				|| gmm_strategy.startsWith("random")
				|| gmm_strategy.startsWith("uniform")) {
			
			estimate = Initialization.fastInit(data, numberOfDensities, diagonalCovariance, gmm_strategy);
		} else {
			System.err.println("Initializer.main(): unknown strategy '" + gmm_strategy + "'");
			System.exit(1);
		}

		// write the estimated parameters
		for (int i = 0; i < estimate.nd; ++i)
			estimate.components[i].id = i;
		
		System.err.println("Initializer.main(): Writing parameters to " + output);
		
		estimate.writeToFile(new File(output));
	}
}
