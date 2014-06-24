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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.stat.Density;
import de.fau.cs.jstk.stat.Sample;
import de.fau.cs.jstk.stat.Trainer;
import de.fau.cs.jstk.util.Pair;


/**
 * Perform a mean and variance normalization to the incoming feature vector.
 * 
 * @author sikoried
 */
public class MVN implements FrameSource {
	private static Logger logger = Logger.getLogger(MVN.class);
	
	/** FrameSource to read from */
	private FrameSource source;
	
	public MVN() {
		// nothing to do
	}
	
	public MVN(FrameSource src) {
		setFrameSource(src);
	}
	
	public MVN(FrameSource src, String parameterFile) throws IOException, ClassNotFoundException {
		setFrameSource(src);
		loadFromFile(parameterFile);
	}

	public void setNormalizations(boolean means, boolean variances) {
		this.normalizeMeans = means;
		this.normalizeVars = variances;
	}
	
	public FrameSource getSource() {
		return source;
	}
	
	public void setSource(FrameSource src) {
		if (source != null && source.getFrameSize() != src.getFrameSize())
			throw new RuntimeException("MVN.setSource(): FrameSource dimensions don't match!");
		source = src;
	}
	
	/** number of samples that contributed to the statistics */
	private long samples;
	
	/** mean values to subtract */
	public double [] means;
	
	/** variances */
	public double [] variances;
	
	/** sigmas for normalization (sqrt(var)) */
	public double [] sigmas;

	/**
	 * Return the current frame size
	 */
	public int getFrameSize() {
		return source.getFrameSize();
	}

	/**
	 * Set the FrameSource to read from.
	 * @param src Valid FrameSource instance.
	 */
	public void setFrameSource(FrameSource src) {
		source = src;
	}
	
	/**
	 * Read the next frame from the source, normalize for zero mean and uniform
	 * standard deviation, and output the frame.
	 */
	public boolean read(double[] buf) throws IOException {
		// read, return false if there wasn't any frame to read.
		if (!source.read(buf))
			return false;
		
		// mean and variance normalization
		if (normalizeMeans && normalizeVars) {
			for (int i = 0; i < buf.length; ++i)
				buf[i] = (buf[i] - means[i]) / sigmas[i];
		} else if (normalizeMeans && !normalizeVars) {
			for (int i = 0; i < buf.length; ++i)
				buf[i] = (buf[i] - means[i]);
		} else if (!normalizeMeans && normalizeVars) {
			for (int i = 0; i < buf.length; ++i)
				buf[i] /= sigmas[i];
		}

		return true;
	}
	
	private boolean normalizeMeans = true;
	private boolean normalizeVars = true;
	
	/** 
	 * Reset all internal statistics to clear the normalization parameters.
	 */
	public void resetStatistics() {
		samples = 0;
		means = null;
		variances = null;
		sigmas = null;
	}
	
	public void extendStatistics1(List<double []> data) throws IOException {
		if (data.size() < 1)
			return;
		Density stat = Trainer.ml1(data, true);
		extendStatistics(stat, data.size());
	}
	
	/**
	 * Add samples from the given list to the normalization statistics. Initialize
	 * the parameters if necessary.
	 */
	public void extendStatistics(List<Sample> data) throws IOException {
		if (data.size() < 1)
			return;
		Density stat = Trainer.ml(data, true);
		extendStatistics(stat, data.size());
	}
	
	private void extendStatistics(Density stat, int size) throws IOException {
		if (size < 1)
			return;
		
		if (means == null) {
			// step 2a: set the new statistics
			samples = size;
			means = stat.mue;
			variances = stat.cov;
		} else {
			// step 2b: combine old and new statistics
			if (means.length != stat.fd)
				throw new IOException("frame dimensions do not match: means.length = " + means.length + " input_fs = " + stat.fd);
			
			for (int i = 0; i < stat.fd; ++i) {
				// merge with the new statistics
				double mean_old = means[i];
				
				means[i] = (mean_old * samples + stat.mue[i] * size) / (samples + size);
				
				variances[i] = (
						(variances[i] + mean_old*mean_old) * samples + 
						(stat.cov[i] + stat.mue[i]*stat.mue[i]) * size
						) / (samples + size) - means[i] * means[i];
			}
			
			// don't forget to update the number of samples for these statistics
			samples += size;
		}
		
		// step 3: compute sigmas
		if (sigmas == null)
			sigmas = new double [variances.length];
		
		for (int i = 0; i < variances.length; ++i)
			sigmas[i] = Math.sqrt(variances[i]);
	}
	
	/**
	 * Add samples from the given source to the normalization statistics. Initialize
	 * the parameters if necessary.
	 * @param src
	 * @throws IOException
	 */
	public void extendStatistics(FrameSource src) throws IOException {
		double [] buf = new double [src.getFrameSize()];
		LinkedList<Sample> data = new LinkedList<Sample>();
		
		while (src.read(buf))
			data.add(new Sample((short) 0, buf));
		
		extendStatistics(data);
	}
	
	/**
	 * Read the normalization parameters from the referenced file.
	 * @param fileName
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public void loadFromFile(String fileName) throws IOException {
		InputStream is = new FileInputStream(fileName);
		read(is);
		is.close();
	}
	
	/**
	 * Read the normalization parameters from the given InputStream
	 * @param is
	 * @throws IOException
	 */
	public void read(InputStream is) throws IOException {
		samples = IOUtil.readLong(is, ByteOrder.LITTLE_ENDIAN);
		
		int fd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		
		means = new double [fd];
		variances = new double [fd];
		sigmas = new double [fd];
		
		if (!IOUtil.readDouble(is, means, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("Could not read mean values");
		if (!IOUtil.readDouble(is, variances, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("Could not read mean variances");
		if (!IOUtil.readDouble(is, sigmas, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("Could not read mean sigmas");
	}
	
	/**
	 * Save the normalization parameters to the referenced file.
	 * @param fileName
	 * @throws IOException
	 */
	public void saveToFile(String fileName) throws IOException {
		OutputStream os = new FileOutputStream(new File(fileName));
		write(os);		
		os.close();
	}
	
	/**
	 * Save the normalization parameters to the given OutputStream
	 * @param os
	 * @throws IOException
	 */
	public void write(OutputStream os) throws IOException {
		IOUtil.writeLong(os, samples, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, means.length, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, means, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, variances, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, sigmas, ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Generate a String represenation of the normalization parameters
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		
		ret.append("framed.MVN samples = " + samples + "\n");
		ret.append("  m = [");
		for (double m : means)
			ret.append(" " + m);
		ret.append(" ]\n  v = [");
		for (double v : variances)
			ret.append(" " + v);
		ret.append(" ]\n");
		
		return ret.toString();
	}
	
	public static final String synopsis = 
		"sikoried, 12-4-2009\n" +
		"Compute a mean and variance normalization for each feature file individually.\n" +
		"Optionally, the normalization parameters can be estimated on all referenced\n" +
		"files (cumulative) or loaded from file. See the options for more details.\n" +
		"\n" +
		"usage: framed.MVN [options]\n" +
		"  --io in-file out-file\n" +
		"    Use the given files for in and output. This option may be used multiple\n" +
		"    times.\n" +
		"  --in-out-list list-file\n" +
		"    Use a list containing lines \"<in-file> <out-file>\" for batch processing.\n" +
		"    This option may be used multiple times.\n" +
		"  --in-list list-file directory\n" +
		"    Read all files contained in the list and save the output to the given\n" +
		"    directory. This option may be used multiple times.\n" +
		"  --dir <input-dir>\n" +
		"    Expect the input files in the given directory. MUST BE PLACED BEFORE --in-list!!!\n" +
		"\n" +
		"  --cumulative\n" +
		"    Estimate the MVN parameters on ALL files instead of individual MVN.\n" +
		"  --save-parameters file\n" +
		"    Save the CMVN parameters. This can only be used for single files or in\n" +
		"    combination with --cumulative. In case of --online, the parameters after\n" +
		"    are saved after processing all data.\n" +
		"  --load-parameters file\n" +
		"    Use the CMVN parameters from the given file instead of individual or\n" +
		"    cumulative estimates.\n" +
		"  --simulate\n" +
		"    Only compute the normalization parameters but no data normalization!\n" +
		"  --no-variance\n" +
		"    Do not do variance normalization\n" +
		"  -v\n" +
		"    Be verbose\n" +
		"\n" +
		"  -h | --help\n" +
		"    Display this help text.\n";
	
	public static void main(String[] args) throws Exception, IOException {
		if (args.length < 2) {
			System.err.println(synopsis);
			System.exit(1);
		}
		
		logger.setLevel(Level.WARN);
		
		boolean cumulative = false;
		boolean simulate = false;
		boolean novar = false;
		
		String parameterOutputFile = null;
		String parameterInputFile = null;
		
		String inDir = null;
		
		// store all files to be processed in a list
		ArrayList<Pair<String, String>> iolist = new ArrayList<Pair<String, String>>();
		
		// parse the command line arguments
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-h") || args[i].equals("--help")) {
				System.err.println(synopsis);
				System.exit(1);
			} else if (args[i].equals("--simulate"))
				simulate = true;
			else if (args[i].equals("--cumulative"))
				cumulative = true;
			else if (args[i].equals("--no-variance"))
				novar = true;
			else if (args[i].equals("--load-parameters"))
				parameterInputFile = args[++i];
			else if (args[i].equals("--save-parameters"))
				parameterOutputFile = args[++i];
			else if (args[i].equals("--io")) {
				// add single file pair
				iolist.add(new Pair<String, String>(args[i+1], args[i+2]));
				i += 2;
			} else if (args[i].equals("-v"))
				logger.setLevel(Level.ALL);
			else if (args[i].equals("--dir"))
				inDir = args[++i];
			else if (args[i].equals("--in-list")) {
				BufferedReader lr = new BufferedReader(new FileReader(args[++i]));
				
				// validate output directory
				File outDir = new File(args[++i]);
				if (!outDir.canWrite())
					throw new IOException("Cannot write to directory " + outDir.getAbsolutePath());
				
				// read in the list
				String line = null;
				int lineCnt = 1;
				while ((line = lr.readLine()) != null) {
					String inf = (inDir == null ? line : inDir + System.getProperty("file.separator") + line);
					String ouf = outDir + System.getProperty("file.separator") + line;
					
					// check file
					if (!(new File(inf)).canRead())
						throw new IOException(args[i-1] + "(" + lineCnt + "): Cannot read input file " + line);
						
					iolist.add(new Pair<String, String>(inf, ouf));
					lineCnt++;
				}
			} else if (args[i].equals("--in-out-list")) {
				BufferedReader lr = new BufferedReader(new FileReader(args[++i]));
				String line = null;
				int lineCnt = 1;
				while ((line = lr.readLine()) != null) {
					String [] help = line.split("\\s+");
					
					if (help.length != 2)
						throw new IOException(args[i] + "(" + lineCnt + "): invalid line format");
		
					if (!(new File(help[0])).canRead())
						throw new IOException(args[i] + "(" + lineCnt + "): Cannot read input file " + line);
					
					iolist.add(new Pair<String, String>(help[0], help[1]));
					lineCnt++;
				}
			} else {
				throw new Exception("unknown parameter: " + args[i]);
			}
		}
		
		// check some parameters -- not all combinations make sense!
		if (cumulative == false && iolist.size() > 1 && parameterOutputFile != null)
			throw new Exception("cannot save CMVN parameters for more than 1 file (use --cumulative)");
		
		if (cumulative == true && parameterInputFile != null)
			throw new Exception("cumulative and parameterInputFile are exclusive!");
		
		// system summary
		logger.info("cumulative: " + cumulative);
		logger.info("simulate  : " + simulate);
		logger.info("params-in : " + (parameterInputFile == null ? "none" : parameterInputFile));
		logger.info("params-out: " + (parameterOutputFile == null ? "none" : parameterOutputFile));
		logger.info("list-size : " + iolist.size());
		
		MVN work = new MVN();
		
		if (parameterInputFile != null) {
			work.loadFromFile(parameterInputFile);
			
			if (novar)
				work.setNormalizations(true, false);
			
			logger.info(work.toString());
		}
		
		if (cumulative) {
			// read all data
			for (Pair<String, String> p : iolist) {
				FrameInputStream fr = new FrameInputStream(new File(p.a));
				work.extendStatistics(fr);
			}
			
			// save the parameter if required
			if (parameterOutputFile != null)
				work.saveToFile(parameterOutputFile);
			
			if (simulate)
				System.exit(0);
			
			if (novar)
				work.setNormalizations(true, false);
		}
		
		for (Pair<String, String> p : iolist) {
			// for individual CMVN, we need to process the data first -- if not read from file
			if (!cumulative && parameterInputFile == null) {
				work.resetStatistics();
				work.extendStatistics(new FrameInputStream(new File(p.a)));
				
				if (parameterOutputFile != null)
					work.saveToFile(parameterOutputFile);
			}
			
			if (simulate)
				continue;
			
			work.setFrameSource(new FrameInputStream(new File(p.a)));
			FrameOutputStream fw = new FrameOutputStream(work.getFrameSize(), new File(p.b));
			double [] buf = new double [work.getFrameSize()];
			
			// read and normalize all samples
			while (work.read(buf))
				fw.write(buf);
			
			fw.close();
		}
	}
}
