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
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.exceptions.MalformedParameterStringException;
import de.fau.cs.jstk.exceptions.TrainingException;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.sampled.AudioFileReader;
import de.fau.cs.jstk.sampled.AudioSource;
import de.fau.cs.jstk.sampled.RawAudioFormat;
import de.fau.cs.jstk.stat.Initialization;
import de.fau.cs.jstk.stat.Initialization.DensityRankingMethod;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.stat.Sample;
import de.fau.cs.jstk.stat.Trainer;
import de.fau.cs.jstk.util.Pair;


/**
 * The EnergyDetector object reads from a FramesSources, typically a window, and
 * returns a window if the energy is higher than a certain threshold
 * 
 * @author bocklet
 * 
 */
public class EnergyDetector implements FrameSource {
	private static Logger logger = Logger.getLogger(EnergyDetector.class);
	
	private FrameSource source;
	private double threshold;
	private int fs;

	public EnergyDetector(FrameSource source) {
		this.source = source;
	}

	public FrameSource getSource() {
		return source;
	}
	
	/**
	 * Initializes the EnergyDector
	 * 
	 * @param source FrameSource to read from
	 * @param threshold threshold for voice activity decision
	 */
	public EnergyDetector(FrameSource source, double threshold) {

		this.source = source;
		fs = source.getFrameSize();
		this.threshold = threshold;
	}

	/**
	 * Return the outgoing frame size
	 */
	public int getFrameSize() {
		return source.getFrameSize();
	}

	/**
	 * Read from the given source as long as a window is found, that is higher
	 * than the specified threshold
	 * 
	 * @param buf
	 *            buffer to save the signal frame
	 * @return true on success, false if the audio stream terminated before a
	 *         window with sufficient energy could be found
	 */
	public boolean read(double[] buf) throws IOException {
		while (source.read(buf)) {
			// calculate energy for the entire window
			double energy = 0.0;
			for (int i = 0; i < fs; i++)
				energy += Math.abs(buf[i]);

			// pass on valid frame, otherwise continue reading
			if (energy > threshold)
				return true;
		}
		return false;
	}

	public String toString() {
		return "framed.EnergyDetector thres=" + threshold;
	}
	
	public enum ThresholdStrategy {
		MEAN,
		CLUSTER,
		EM1,
		EM5
	}
	
	/**
	 * Estimate the energy threshold for the given data. If used on representative
	 * silence, ThresholdStrategy.MEAN should be used. Use this function on data
	 * containing both speech and silence.
	 * @param data frame-wise energy values
	 * @param strategy requested strategy
	 * @return energy threshold
	 * @throws TrainingException
	 */
	public static double estimateThreshold(List<Sample> data, ThresholdStrategy strategy) throws TrainingException {
		if (data.size() < 2) {
			logger.info("EnergyDetector.estimateThreshold(): data.size() < 2!");
			return 0.;
		}
		
		Mixture m = null;
		
		if (strategy == ThresholdStrategy.MEAN){
			double t = 0.;
			for (Sample s : data)
				t += s.x[0];
			return t / data.size();
		} else if (strategy == ThresholdStrategy.CLUSTER
					|| strategy == ThresholdStrategy.EM1
					|| strategy == ThresholdStrategy.EM5) {
			m = Initialization.hierarchicalGaussianClustering(data, 2, true, DensityRankingMethod.COVARIANCE);
			
			// we need to have exactly 2 clusters, also for small data sets
			if (m.nd != 2) {
				logger.info("re-initializing with kMeans");
				m = Initialization.kMeansClustering(data, 2, true);
			}
		} else
			throw new RuntimeException("EnergyDetector.estimateThreshold(): Invalid strategy!");
		
		if (strategy == ThresholdStrategy.EM1 || strategy == ThresholdStrategy.EM5)
			m = Trainer.em(m, data);
		
		if (strategy == ThresholdStrategy.EM5)
			m = Trainer.em(m, data, 4);

		return decisionBoundary(m);
	}
	
	/**
	 * Compute the (EER) decision boundary for GMM consisting of 2 Gaussians. Samples
	 * 10k steps between the two mean values.
	 * @param m
	 * @return estimated decision boundary
	 */
	public static double decisionBoundary(Mixture md) {
		if (md.components.length != 2)
			throw new RuntimeException("EnergyDetector.decisionBoundary(): Mixture must be of 2 Gaussians!");
		if (md.fd != 1)
			throw new RuntimeException("EnergyDetector.decisionBounrady(): Mixture feature dim must be 1!");
		
		// sample decision boundary: do 10k steps
		double [] x = { md.components[0].mue[0] };
		double [] p = new double [2];
		
		int n = 10000;
		double delta = (md.components[1].mue[0] - md.components[0].mue[0]) / n;
		while (n-- > 0) {
			md.evaluate(x);
			md.posteriors(p);
			
			// well now we have VA!
			if (p[1] > p[0])
				break;
			else
				x[0] += delta;
		}
		
		return x[0];
	}
	public static double comptueThresholdFromFile(String fileName, String sFormat, String sWindow, ThresholdStrategy strat) {
		return computeThresholdFromFile(fileName, sFormat, sWindow, strat, null);
	}
	
	public static double computeThresholdFromFile(String fileName, String sFormat, String sWindow, ThresholdStrategy strat, List<Boolean> byframe) {
		double thres = 0.;
		
		try {
			AudioSource as = new AudioFileReader(fileName, RawAudioFormat.create(sFormat), true);
			Window wnd = Window.create(as, sWindow);
			
			double [] buf = new double [wnd.getFrameSize()];
			
			LinkedList<Sample> list = new LinkedList<Sample>();
			
			while (wnd.read(buf))
				list.add(new Sample((short) 0, new double [] { Window.energy(buf) }));
		
			if (list.size() == 0){
				System.err.println("No frames in file " + fileName);
				return -1.;
			}
			
			thres = estimateThreshold(list, strat);
			
			if (byframe != null) {
				for (Sample s : list) 
					byframe.add(s.x[0] > thres);
			}
		} catch (IOException e) {
			System.err.println(e.toString());
			thres = -1.;
		} catch (MalformedParameterStringException e) {
			System.err.println(e.toString());
			thres = -2.;
		} catch (UnsupportedAudioFileException e) {
			System.err.println(e.toString());
			thres = -3.;
		}
		
		return thres;
	}
	
	public static final String SYNOPSIS = 
		"sikoried, 4/26/2010\n" +
		"Compute a reasonable energy threshold for voice activity for the files of\n" +
		"the given list for use with EnergyDetector (e.g. in bin.Mfcc). If the list\n" +
		"contains file pairs, the frame-wise VAD decision is saved (in frame format).\n\n" +
		"usage: framed.EnergyDetector <format-string> <window-string> <list-file> [strategy]\n" +
		"strategy may be:\n" +
		"  mean    : mean energy value determined from the current file (default)\n" +
		"  cluster : decision boundary of two gaussian clusters\n" +
		"  em1     : decision boundary after 1 EM iteration (recommended)\n" +
		"  em5     : decision boundary after 5 EM iterations\n";
	
	public static void main(String [] args) throws Exception {
		BasicConfigurator.configure();
		
		if (args.length < 3 || args.length > 4) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		logger.setLevel(Level.FATAL);
		Initialization.logger.setLevel(Level.FATAL);
		Trainer.logger.setLevel(Level.FATAL);
		
		String sFormat = args[0];
		String sWindow = args[1];
		String inFile = args[2];
		
		ThresholdStrategy strat = ThresholdStrategy.MEAN;
		
		if (args.length == 4) {
			if (args[3].equals("mean"))
				strat = ThresholdStrategy.MEAN;
			else if (args[3].equals("cluster"))
				strat = ThresholdStrategy.CLUSTER;
			else if (args[3].equals("em1"))
				strat = ThresholdStrategy.EM1;
			else if (args[3].equals("em5"))
				strat = ThresholdStrategy.EM5;
			else
				throw new RuntimeException("EnergyDetector.main(): invalid thresholding strategy");
		}
		
		LinkedList<Pair<String, String>> files = new LinkedList<Pair<String, String>>();
		BufferedReader br = new BufferedReader(new FileReader(inFile));
		String sbuf;		
		while ((sbuf = br.readLine()) != null) {
			Pair<String, String> pair;
			if (sbuf.split("\\s+").length > 1)
				pair = new Pair<String, String>(sbuf.split("\\s+")[0], sbuf.split("\\s+")[1]);
			else
				pair = new Pair<String, String>(sbuf, null);
			if (!(new File(pair.a)).canRead())
				throw new IOException("Couldn't access " + inFile + ":"
						+ pair.a + " for reading.");
			files.add(pair);
		}
		br.close();
		
		for (Pair<String, String> file : files) {
			
			AudioSource as = new AudioFileReader(file.a, RawAudioFormat.create(sFormat), true);
			Window wnd = Window.create(as, sWindow);
				
			double [] buf = new double [wnd.getFrameSize()];
			
			LinkedList<Sample> list = new LinkedList<Sample>();
			
			while (wnd.read(buf))
				list.add(new Sample((short) 0, new double [] { Window.energy(buf) }));
		
			if (list.size() == 0){
				System.err.println("No frames in file " + file);
				continue;
			}

			double thres = estimateThreshold(list, strat);
			System.out.println(thres);
			
			// we need to output a binary decision for VAD
			if (file.b != null) {
				StringBuffer sb = new StringBuffer();
				for (Sample s : list)
					sb.append(s.x[0] > thres ? "1" : "0");
				FileWriter fw = new FileWriter(file.b);
				fw.write(sb.toString());
				fw.close();
			}
		}
	}
}
