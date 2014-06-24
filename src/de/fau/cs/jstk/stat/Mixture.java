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
package de.fau.cs.jstk.stat;


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
import java.util.LinkedList;
import java.util.Scanner;

import de.fau.cs.jstk.app.MNAP;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.trans.NAP;
import de.fau.cs.jstk.util.Arithmetics;
import de.fau.cs.jstk.util.Pair;

/**
 * A Gaussian mixture density using either diagonal or full covariance matrices.
 * 
 * @author sikoried
 *
 */
public final class Mixture {
//	private static Logger logger = Logger.getLogger(Mixture.class);
	
	/** number of densities */
	public int nd;
	
	/** feature dimension */
	public int fd;
	
	/** mixture id */
	public int id;
	
	/** score after evaluation (including priors, or course) */
	public transient double score;	

	/** log(score) after evaluation, but computed numerically more stable; 
	 * might contain useful values even when score = Infinity */
	public transient double logscore;
	
	/** offset that was added after the the application of the log */
	public transient double logoffset;
	
	/** helper to avoid allocating memory in each call of evaluate */
	private transient double [] logscoreHelp = null;	
	
	/** log likelihood accumulator. currently unused/not implemented */
	public transient double llh = 0.;
	
	/** component densities */
	public Density [] components;
	
	/** is this mixture using diagonal covariances? */
	public boolean diagonal;
	
	/** last seen feature vector */
	private transient double [] last = null;
	
	/**
	 * Create a new MixtureDensity.
	 * @param featureDimension feature dimension
	 * @param numberOfDensities number of densities
	 * @param diagonalCovariances
	 */
	public Mixture(int featureDimension, int numberOfDensities, boolean diagonalCovariances) {
		this.nd = numberOfDensities;
		this.fd = featureDimension;
		this.last = new double [fd];
		components = new Density [nd];
		diagonal = diagonalCovariances;
		
		for (int i = 0; i < nd; ++i) {
			components[i] = diagonalCovariances ? new DensityDiagonal(fd) : new DensityFull(fd);
			components[i].apr = 1. / nd;
			components[i].id = i;
		}
	}
	
	public Mixture(Mixture copy) {
		this.nd = copy.nd;
		this.fd = copy.fd;
		this.last = new double [fd];
		this.diagonal = copy.diagonal;
		
		components = new Density [nd];
		
		for (int i = 0; i < nd; ++i)
			components[i] = copy.components[i].clone();
	}
	
	/**
	 * Read a new MixtureDensity from the given ObjectInputStream
	 * @param is
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public Mixture(InputStream is) throws IOException {

		id = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		fd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		nd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		this.last = new double [fd];
		
		diagonal = (IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN) == 0);
		
		components = new Density [nd];
		for (int i = 0; i < nd; ++i) {
			try {
				if (diagonal) {
					components[i] = new DensityDiagonal(fd);
					components[i].fill(is);
				} else {
					components[i] = new DensityFull(fd);
					components[i].fill(is);
				}
			} catch (Exception e) {
				throw new IOException("MixtureDensity.MixtureDensity(): Error reading Density " + i);
			}
		}
	}
	
	/**
	 * Write the MixtureDensity parameters to the given ObjectOutputStream
	 * @param os
	 * @throws IOException
	 */
	public void write(OutputStream os) throws IOException {
		IOUtil.writeInt(os, id, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, fd, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, nd, ByteOrder.LITTLE_ENDIAN);
		
		IOUtil.writeInt(os, diagonal ? 0 : 1, ByteOrder.LITTLE_ENDIAN);
		
		for (Density d : components)
			d.write(os);
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof Mixture))
			return false;
		Mixture md = (Mixture) o;
		boolean eq = true;
		
		eq &= (id == md.id);
		eq &= (nd == md.nd);
		eq &= (fd == md.fd);
		
		return eq;
	}
	
	/**
	 * Determine if this mixture uses diagonal covariances
	 * @return
	 */
	public boolean diagonal() {
		return diagonal;
	}
	
	/**
	 * Return a deep copy of this instance
	 */
	public Mixture clone() {
		return new Mixture(this);
	}

	/**
	 * Evaluate the GMM
	 * @param x feature vector
	 * @return probability of that mixture
	 */
	public double evaluate(double [] x) {
		score = 0.;
		logscore = 0.;
		
		if (x.length != fd)
			throw new IllegalArgumentException("x.length = " + x.length + " != codebook dim = " + fd);
		
		
		if (logscoreHelp == null)
			logscoreHelp = new double[components.length];		
		int i = 0;
		
		for (Density d : components) {
			score += d.evaluate(x);
			logscoreHelp[i++] = d.lh;
		}
		
		/* compute log(score), avoiding Infinity */
		
		/* - find maximum */
		logoffset = logscoreHelp[0];
		for (double d : logscoreHelp)
			if (d > logoffset)
				logoffset = d;
				
		/* - subtract max, sum up exponents */			
		double tmp = 0.;
		for (i = 0; i < logscoreHelp.length; i++)
			tmp += Math.exp(logscoreHelp[i] - logoffset);
		
		llh += (logscore = logoffset + Math.log(tmp));
				
		return score;
	}
	
	/**
	 * Evaluate the GMM but do not evaluate if this is a subsequent call on the 
	 * last feature vector (this will be used in SCState for HMM).
	 * @param x
	 * @return
	 */
	public double evaluate2(double [] x) {
		boolean eq = true;
		int i = 0;
		while (eq && i < fd) {
			eq &= (x[i] == last[i]);
			i++;
		}
		
		// if all values are equal, we can skip the computation
		if (eq) {
			return score;
		} else {
			System.arraycopy(x, 0, last, 0, fd);
			return evaluate(x);
		}
	}
	
	/**
	 * Return the index of the highest scoring density (without the prior or exponentiation!)
	 * @param x
	 * @return
	 */
	public int classify(double [] x, boolean withPriors) {
		// evaluate all densities
		evaluate(x);
		
		// find the maximum one 
		double max = components[0].score;
		int maxid = 0;
		for (int i = 1; i < nd; ++i) {
			double p = components[i].score;
			if (p > max) {
				max = p;
				maxid = i;
			}
		}
		return maxid;
	}
	
	/** 
	 * Normalize the component scores to posteriors (call evaluate first!)
	 * @param p container to save the posteriors to
	 */
	public void posteriors(double [] p) {
		for (int i = 0; i < nd; ++i)
			p[i] = components[i].ascore / score;
	}
	
	/**
	 * Normalize the component scores to posteriors using external mixture
	 * weights. Call evaluate first!
	 * @param p container to save the posteriors to
	 * @param c mixture weights to use
	 */
	public void posteriors(double [] p, double [] c) {
		double sum = 0.;
		for (int i = 0; i < nd; ++i) {
			p[i] = c[i] * components[i].score;
			sum += p[i];
		}
		for (int i = 0; i < nd; ++i)
			p[i] /= sum;
	}
	
	/**
	 * Set all the elements of the components to zero
	 */
	public void clear() {
		llh = 0.;
		for (Density d : components)
			d.clear();
	}
	
	/**
	 * Interpolate the local parameters (!) with the referenced ones
	 * 
	 * @param weight this = weight * source + (1 - weight) * this
	 * @param source
	 */
	public void pinterpolate(double weight, Mixture source) {
		double sum = 0.;
		for (int i = 0; i < nd; ++i) {
			components[i].pinterpolate(weight, source.components[i]);
			sum += components[i].apr;
		}
		
		for (Density d : components)
			d.apr /= sum;
	}
	
	/**
	 * Generate a super vector for GMM-SVM use. The generated vector contains 
	 * (in that order) all priors, mean values and variances (if requested).
	 * @param priors include prior probabilities
	 * @param means include mean vectors
	 * @param variances include variances (diagonal covariance)
	 * @return super vector [apr1 apr2 ... mue1 mue2 ... cov1 cov2 ...]
	 */
	public double [] superVector(Density.Flags flags) {
		// determine the size
		int size = 0;
		if (flags.weights)
			size += 1;
		if (flags.means)
			size += fd;
		if (flags.vars)
			size += fd;
		
		double [] sv = new double [size * nd];
		
		// copy values
		int i = 0;
		for (Density d : components)
			System.arraycopy(d.superVector(flags), 0, sv, size * (i++), size);
		
		return sv;
	}
	
	/**
	 * Return a String representation of the mixture
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("fd = " + fd + " nd = " + nd + " diagonal: " + diagonal() + "\n");
		for (int i = 0; i < nd; ++i)
			sb.append(components[i].toString() + "\n");
		return sb.toString();
	}
	
	/**
	 * Construct a MixtureDensity from an ASCII InputStream
	 * @param is
	 */
	public Mixture(Scanner scanner) throws IOException {
		while (!scanner.hasNextInt())
			scanner.next();
		fd = scanner.nextInt();
		
		while (!scanner.hasNextInt())
			scanner.next();
		nd = scanner.nextInt();
		
		while (!scanner.hasNextBoolean())
			scanner.next();
		diagonal = scanner.nextBoolean();
		
		if (diagonal) {
			components = new DensityDiagonal [nd];
			for (int i = 0; i < nd; ++i)
				components[i] = new DensityDiagonal(fd, scanner);
		} else {
			components = new DensityFull [nd];
			for (int i = 0; i < nd; ++i)
				components[i] = new DensityFull(fd, scanner);
		}
	}
	
	public String info() {
		return "fd = " + fd + " nd = " + nd + " diagonal: " + diagonal() + "\n";
	}
	
	/**
	 * Write the mixture density to the given file or stdout.
	 * @param file file to write to, or null for stdout
	 * @throws IOException
	 */
	public void writeToFile(File file) throws IOException {
		OutputStream os = (file == null ? System.out : new FileOutputStream(file));
		write(os);
		os.flush();
		os.close();
	}
	
	/**
	 * Read a mixture density object from the given file or stdin.
	 * @param file file to read from, null for stdin
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Mixture readFromFile(File file) 
		throws IOException, ClassNotFoundException {
		
		InputStream is = null;
		if (file == null)
			is = System.in;
		else
			is = new FileInputStream(file);
		
		Mixture md = new Mixture(is);
		
		is.close();
		return md;
	}
	
	/**
	 * Compute the approximate Kullback-Leibler statistics using this mixture as
	 * a background model for priors and variance.
	 * @param ma mean vectors of density a
	 * @param mb mean vectors of density b
	 * @return
	 */
	public double akl(double [] ma, double [] mb) {
		double dist = 0.;
		
		for (int i = 0; i < nd; ++i) {
			double p = components[i].apr;
			double [] c = components[i].cov;
			int ci = 0;
			for (int j = 0; j < fd; ++j) {
				double d = ma[i*fd + j] - mb[i*fd + j];
				dist += p * d * d / c[ci];
								
				// mind the lower triangular matrix for full covariance
				if (diagonal)
					ci++;
				else
					ci += (j + 1);
			}
		}
		
		return dist;
	}
	
	public Mixture marginalize(boolean [] keep) {
				
		if (keep.length != fd)
			throw new IllegalArgumentException("dimension mismatch (keep.length = " + keep.length + " != fd = " + fd);
		
	    int dim = 0;
	    for (boolean value : keep)
	    	if (value)
	    		dim++;	    		
	
		Mixture m = new Mixture(dim, nd, diagonal);
		
		int i;
		for (i = 0; i < nd; i++)
			m.components[i] = components[i].marginalize(keep);		
		
		return m;
	}
	
	/**
	 * return a marginalized version of this density;
	 * marginalized over all dimensions from first...last 
	 * (or all dimensions *except* first...last, if keepInterval is true)
	 * 
	 * @param first
	 * @param last
	 * @param keepInterval
	 * @return the new density
	 */
	public Mixture marginalizeInverval(int first, int last, boolean keepInterval){
		boolean [] keep = new boolean[fd];
		
		Arrays.fill(keep, false);
			
		int i;
		
		if (keepInterval){
			for (i = first; i <= last; i++)
				keep[i] = true;			
		}
		else{
			for (i = 0; i < first; i++)
				keep[i] = true;
			for (i = last + 1; i < fd; i++)
				keep[i] = true;
		}
		return marginalize(keep);
		
	}
	
	
	/**
	 * Compute the approximate Kullback-Leibler statistics using this mixture as
	 * a background model for priors and variance.
	 * @param ma mean vectors of density a
	 * @param mb mean vectors of density b
	 * @return
	 */
	public double aklkernel(double [] ma, double [] mb) {
		double dist = 0.;
		
		for (int i = 0; i < nd; ++i) {
			double p = components[i].apr;
			double [] c = components[i].cov;
			int ci = 0;
			for (int j = 0; j < fd; ++j) {
				dist += p * ma[i*fd + j] * mb[i*fd + j] / c[ci];
								
				// mind the lower triangular matrix for full covariance
				if (diagonal)
					ci++;
				else
					ci += (j + 1);
			}
		}
		
		return dist;
	}
	
	public static final String SYNOPSIS = 
		"sikoried, 2/16/2010\n" +
		"Use this tool to (d)display, (c)onstruct and (e)valuate Gaussian mixture densities.\n" +
		"usage: statistics.MixtureDensity <mode> [parameters]\n" +
		"Available modes:\n" +
		"  d <code-book1> [code-book2 ...]\n" +
		"    Print an ASCII representation of the given codebook files.\n" +
		"  C <out-file> [ascii-in | < STDIN]\n" +
		"    Similar to 'c', create a MixtureDensity from ASCII representation either in file or \n" +
		"    from STDIN\n" +
		"  e <codebook> [in-out-list]\n" +
		"    Evaluate a mixture density for given features. If you want to evaluate a single\n" +
		"    feature file, use the pipe operators ( < in > out), if you want to process multiple\n" +
		"    mixtures, use an in/out list containing lines with input and output file separated by\n" +
		"    whitespace. Each output frame consists of the overall mixture score followed by the\n" +
		"    individual component scores without the priors.\n" +
		"  l <codebook> [in-out-list]\n" +
		"    like e, but just write the total log-likelihood for the whole mixture.\n" +
		"  lm first last <codebook> [in-out-list]\n" +
		"    write the total log-likelihood for the whole mixture for: all dimensions,\n" +
		"    dimensions first through last, and all dimensions except first through last.\n" +
		"  E <codebook> <wt-ascii> [in-out-list]\n" + 
		"    Same as 'e', but use mixture weights in ascii file\n" +
		"  s <pmc> [in-out-list | in-list out-file | [< in > out]]\n" +
		"    Transform mixture densities to supervectors. Use the pipe operators ( < in > out) for\n" +
		"    a single transformation. If you want to process multiple mixtures, use an in/out list\n" +
		"    containing lines with input and output file separated by whitespace.\n" +
		"    'p' includes priors, 'm' includes mean vectors, 'c' includes covariances. Use bin.Concat\n" +
		"    to concatenate feature files.\n" +
		"  t proj rank [in-out-list | [< in > out]]\n" +
		"    Transform the means of the mixture density using the MNAP projection matrix; use 0 for full\n" +
		"    rank projection.\n" +
		"  m <first> <last> < in-codebook > out-codebook\n" +
		"    Marginalize codebook over all dimensions from <first> to <last>.\n" +
		"  M <first> <last> < in-codebook > out-codebook\n" +
		"    Marginalize codebook over all dimensions except <first> to <last>.";
	
	
	public static enum Mode { DISPLAY, CONSTRUCT, FROMASCII, EVALUATE, EVALUATE_MARGINALS, SV, MNAP, AKL, AKL2, MARGINALIZE};
	
	public static void main(String [] args) throws Exception {
		if (args.length < 1) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		String smode = args[0];
		
		Mode mode = Mode.DISPLAY;
		String weightfile = null;
		boolean justLogAcc = false;
		boolean marginalizeKeepInterval = false;
		
		if (smode.equals("d"))
			mode = Mode.DISPLAY;
		else if (smode.equals("c"))
			mode = Mode.CONSTRUCT;
		else if (smode.equals("C"))
			mode = Mode.FROMASCII;
		else if (smode.equals("e"))
			mode = Mode.EVALUATE;
		else if (smode.equals("l")){
			mode = Mode.EVALUATE;
			justLogAcc = true;
		}
		else if (smode.equals("lm")){
			mode = Mode.EVALUATE_MARGINALS;
		}
		else if (smode.equals("E")) {
			mode = Mode.EVALUATE;
			weightfile = args[1];
		} else if (smode.equals("s"))
			mode = Mode.SV;
		else if (smode.equals("t"))
			mode = Mode.MNAP;
		else if (smode.equals("a"))
			mode = Mode.AKL;
		else if (smode.equals("A"))
			mode = Mode.AKL2;
		else if (smode.equals("m"))
			mode = Mode.MARGINALIZE;
		else if (smode.equals("M")){
			mode = Mode.MARGINALIZE;
			marginalizeKeepInterval = true;
		}
		else {
			System.err.println("MixtureDensity.main(): Unknown mode \"" + smode + "\"");
			System.exit(1);
		}
		
		switch (mode) {
		case DISPLAY: {
			for (int i = 1; i < args.length; ++i)
				System.out.println(Mixture.readFromFile(new File(args[i])).toString());
			break; 
		}
		case FROMASCII: {
			InputStream is = System.in;
			if (args.length == 3)
				is = new FileInputStream(args[2]);
			else if (args.length > 3)
				throw new IOException("Invalid arguments, check help!");
			
			Mixture md = new Mixture(is);
			md.writeToFile(new File(args[1]));
			break;
		}
		case EVALUATE: {
			// read codebook
			Mixture cb = Mixture.readFromFile(new File(args[1]));
			
			// load weigths
			if (weightfile != null) {
				Scanner sc = new Scanner(new File(weightfile));
				double sum = 0.;
				for (Density d : cb.components) {
					d.apr = sc.nextDouble();
					sum += d.apr;
				}
				
				for (Density d : cb.components) {
					d.apr /= sum;
					d.update();
				}
			}
			
			// buffers
			double [] x = new double [cb.fd];
			double [] p = new double [cb.nd + 1];
			double [] l = new double [1];
			
			LinkedList<String> inlist = new LinkedList<String>();
			LinkedList<String> outlist = new LinkedList<String>();
			
			if ((weightfile == null && args.length == 2) || (weightfile != null && args.length == 3)) {
				// read and write from stdin
				inlist.add(null);
				outlist.add(null);
			} else if ((weightfile == null && args.length == 3) || (weightfile != null && args.length == 4)) {
				// read list
				BufferedReader lr = new BufferedReader(new FileReader(args[2]));
				String line = null;
				int i = 1;
				while ((line = lr.readLine()) != null) {
					String [] help = line.split("\\s+");
					if (help.length != 2)
						throw new Exception("list file is broken at line " + i);
					inlist.add(help[0]);
					outlist.add(help[1]);
					i++;
				}
			} else {
				System.err.println("MixtureDensity.main(): Invalid number of parameters (" + args.length + ")!");
				System.exit(1);
			}
			
			// process files
			while (inlist.size() > 0) {
				FrameInputStream reader = new FrameInputStream(new File(inlist.remove()));
				FrameOutputStream writer;
				if (justLogAcc)
					writer = new FrameOutputStream(1, new File(outlist.remove()));
				else
					writer = new FrameOutputStream(cb.nd + 1, new File(outlist.remove()));
				
				// read, evaluate, write
				while (reader.read(x)) {
										
					double totalProb = cb.evaluate(x);
					
					if (justLogAcc){
						l[0] = cb.logscore;
						
						writer.write(l);
					}
					else{
						p[0] = totalProb;
						for (int i = 1; i < p.length; ++i)
							p[i] = cb.components[i-1].score;
						writer.write(p);
					}					
					
					
				}
				
				reader.close();
				writer.close();
			}
			
			break;
		}
		case EVALUATE_MARGINALS: {
			// read codebook
			if (args.length != 4 && args.length != 5){
				throw new IllegalArgumentException("MixtureDensity.main(): Invalid number of parameters (" + args.length + ")!");
			}
			
			int first = Integer.parseInt(args[1]);
			int last = Integer.parseInt(args[2]);
//			System.err.println("first = " + first);
//			System.err.println("last = " + last);
			Mixture cb = Mixture.readFromFile(new File(args[3]));

			// marginalized mixtures:
			// - remove first through last
			Mixture cb_marg = cb.marginalizeInverval(first, last, false);
			// - remove all except first through last
			Mixture cb_marg_complement = cb.marginalizeInverval(first, last, true);
			
			// buffers
			double [] x = new double [cb.fd];
			double [] x_marg_complement = new double [last - first + 1];
			double [] x_marg = new double [cb.fd - (x_marg_complement.length)];						
			double [] l = new double [3];
			
			LinkedList<String> inlist = new LinkedList<String>();
			LinkedList<String> outlist = new LinkedList<String>();
			
			if (args.length == 4){
				// read and write from stdin
				inlist.add(null);
				outlist.add(null);
			} else if (args.length == 5) {
				// read list
				BufferedReader lr = new BufferedReader(new FileReader(args[4]));
				String line = null;
				int i = 1;
				while ((line = lr.readLine()) != null) {
					String [] help = line.split("\\s+");
					if (help.length != 2)
						throw new Exception("list file is broken at line " + i);
					inlist.add(help[0]);
					outlist.add(help[1]);
					i++;
				}
			}
			
			// process files
			while (inlist.size() > 0) {
				FrameInputStream reader = new FrameInputStream(new File(inlist.remove()));
				FrameOutputStream writer;
				
				writer = new FrameOutputStream(3, new File(outlist.remove()));				
				
				// read, evaluate, write
				while (reader.read(x)) {
					
					// marginalize feature vectors:
					// - first to last
					System.arraycopy(x, first, x_marg_complement, 0, x_marg_complement.length);
					// - complement:
					System.arraycopy(x, 0, x_marg, 0, first);
					System.arraycopy(x, last + 1, x_marg, first, x.length - (last + 1));

					cb.evaluate(x);
					l[0] = cb.logscore;

					cb_marg_complement.evaluate(x_marg_complement);
					l[1] = cb_marg_complement.logscore;
					
					cb_marg.evaluate(x_marg);
					l[2] = cb_marg.logscore;

					writer.write(l);

				}
				
				reader.close();
				writer.close();
			}
			
			break;
		}
		case SV: {
			if (args.length < 2) {
				System.err.println("MixtureDensity.main(): no pmc var set!");
				System.exit(1);
			}
			
			boolean p = false, m = false, c = false;
			
			LinkedList<String> inlist = new LinkedList<String>();
			LinkedList<String> outlist = new LinkedList<String>();
			
			String paramString = args[1].toLowerCase();
			String singleOutFile = null;
			
			if (!paramString.matches("[pmc]{1,3}")) {
				System.err.println("MixtureDensity.main(): Malformed inclusion string \"" + paramString + "\": use only p,m and c!");
				System.exit(1);
			}
			
			if ((new File(args[1])).exists()) {
				System.err.println("MixtureDensity.main(): Disambiguity between file \"" + args[1] + "\" and inclusion string!");
				System.exit(1);
			}
			
			if (paramString.indexOf("p") >= 0)
				p = true;
			if (paramString.indexOf("m") >= 0)
				m = true;
			if (paramString.indexOf("c") >= 0)
				c = true;
			
			if (!p && !m && !c) {
				System.err.println("MixtureDensity.main(): You need to include at least p, m or c!");
				System.exit(1);
			}
			
			if (args.length == 2) {
				inlist.add(null);
				outlist.add(null);
			} else if (args.length == 3){
				// read list
				BufferedReader lr = new BufferedReader(new FileReader(args[2]));
				String line = null;
				int i = 1;
				while ((line = lr.readLine()) != null) {
					String [] help = line.split("\\s+");
					if (help.length != 2)
						throw new Exception("list file is broken at line " + i);
					inlist.add(help[0]);
					outlist.add(help[1]);
					i++;
				}
			} else if (args.length == 4) {
				// read list
				BufferedReader lr = new BufferedReader(new FileReader(args[2]));
				String line = null;
				while ((line = lr.readLine()) != null) {
					inlist.add(line.trim());
				}
				singleOutFile = args[3];
			} else {
				System.err.println("MixtureDensity.main(): Invalid number of parameters!");
				System.exit(1);
			}
			
			if (singleOutFile == null) {
				while (inlist.size() > 0) {
					String inf = inlist.remove();
					Mixture md = Mixture.readFromFile(inf == null ? null : new File(inf));
					double [] sv = md.superVector(new Density.Flags(p, m, c));
					String ouf = outlist.remove();
					FrameOutputStream fw = new FrameOutputStream(sv.length, ouf == null ? null : new File(ouf));
					fw.write(sv);
					fw.close();
				}
			} else {
				FrameOutputStream fw = null;
				while (inlist.size() > 0) {
					String inf = inlist.remove();
					Mixture md = Mixture.readFromFile(inf == null ? null : new File(inf));
					double [] sv = md.superVector(new Density.Flags(p, m, c));
					if (fw == null)
						fw = new FrameOutputStream(sv.length, new File(singleOutFile));
					else if (fw.getFrameSize() != sv.length)
						throw new RuntimeException("MixtureDensity dimensions are inconsistent!");
					fw.write(sv);
				}
				if (fw != null)
					fw.close();
			}
				
			break;
		}
		case AKL: {
			Mixture ubm = new Mixture(new FileInputStream(args[1]));
			Mixture m1 = new Mixture(new FileInputStream(args[2]));
			Mixture m2 = new Mixture(new FileInputStream(args[3]));
			double [] mm1 = m1.superVector(Density.Flags.fOnlyMeans);
			double [] mm2 = m2.superVector(Density.Flags.fOnlyMeans);
			System.out.println("dotp()       = " + Arithmetics.dotp(mm1, mm2));
			System.out.println("akl-kernel() = " + ubm.aklkernel(mm1, mm2));
			System.out.println("akl()        = " + ubm.akl(mm1, mm2));
			
			break;
		}
		case AKL2: {
			Mixture ubm = new Mixture(new FileInputStream(args[1]));
			FrameInputStream fis1 = new FrameInputStream(new File(args[2]));
			FrameInputStream fis2 = new FrameInputStream(new File(args[3]));
			double [] m1 = new double [fis1.getFrameSize()];
			double [] m2 = new double [fis2.getFrameSize()];
			if (fis1.read(m1) && fis2.read(m2) && m1.length == m2.length && m1.length == ubm.fd * ubm.nd) {
				System.out.println("dotp()       = " + Arithmetics.dotp(m1, m2));
				System.out.println("akl-kernel() = " + ubm.aklkernel(m1, m2));
				System.out.println("akl()        = " + ubm.akl(m1, m2));
			} else
				System.out.println("incompatible data");
			break;
		}
		case MNAP: {
			
			if (args.length < 3) {
				System.err.println(SYNOPSIS);
				System.exit(1);
			}
			
			MNAP mnap = new MNAP(new FileInputStream(args[1]));
			NAP [] naps = mnap.getTransformations();
			int rank = Integer.parseInt(args[2]);
			
			LinkedList<Pair<String, String>> inout = new LinkedList<Pair<String, String>>();
			if (args.length == 3)
				inout.add(new Pair<String, String>(null, null));
			else {
				BufferedReader lr = new BufferedReader(new FileReader(args[3]));
				String line = null;
				int i = 1;
				while ((line = lr.readLine()) != null) {
					String [] help = line.split("\\s+");
					if (help.length != 2)
						throw new Exception("list file is broken at line " + i);
					inout.add(new Pair<String, String>(help[0], help[1]));
					i++;
				}
			}
						
			while (inout.size() > 0) {
				Pair<String, String> p = inout.remove();
				Mixture md = new Mixture(p.a == null ? System.in : new FileInputStream(p.a));
				
				if (md.nd != naps.length)
					throw new IOException("MNAP.naps.length != mixture.nd");
				
				for (int i = 0; i < md.nd; ++i)
					naps[i].project(md.components[i].mue, rank);
				
				md.write(p.b == null ? System.out : new FileOutputStream(p.b));
			}
			
			break;
		}
		case MARGINALIZE:
			if (args.length < 3)
				throw new IllegalArgumentException("mode m needs two arguments!");
			int first = Integer.parseInt(args[1]);
			int last = Integer.parseInt(args[2]);
			
			Mixture old = new Mixture(System.in);
			Mixture m = old.marginalizeInverval(first, last, marginalizeKeepInterval);
			
			m.write(System.out);			
			
			break;
		}
	}
}
