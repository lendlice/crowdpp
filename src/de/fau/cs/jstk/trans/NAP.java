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
package de.fau.cs.jstk.trans;


import java.io.BufferedInputStream;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import FJama.EigenvalueDecomposition;
import FJama.Matrix;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.util.Pair;

/**
 * Nuisance Attribute Projection implementation using single precision (float)
 * based on the publications
 * Solomonoff05-AIC (ADVANCES IN CHANNEL COMPENSATION FOR SVM SPEAKER 
 * RECOGNITION, ICASSP 2005) and 
 * Solomonoff07-NAP (NUISANCE ATTRIBUTE PROJECTION, SpeechCommunications 2007)
 * 
 * @author sikoried
 */
public class NAP {
	private static Logger logger = Logger.getLogger(NAP.class);
	
	/** Transformation matrix V, stored as v[eigenvector][feature-dim], which is
	 * transposed compared to Matlab.
	 */
	private float [][] v = null;
	
	private transient float [] vTx = new float [0];
	
	/**
	 * Compute the actual projection matrix P = (1 - vv^t). Mind the memory!!!
	 * @param rank
	 * @return
	 */
	public float [][] computePf(int rank) {
		if (rank == 0 || rank > v.length)
			rank = v.length;
		float [][] P = new float [v[0].length][v[0].length];

		// identity
		for (int i = 0; i < P.length; ++i)
			P[i][i] = 1.f;

		// subtract vv^t 
		for (int i = 0; i < P.length; ++i) {
			float [] Pi = P[i];
			for (int k = 0; k < rank; ++k) {
				float [] vk = v[k];
				float vki = vk[i];
				for (int j = 0; j < P.length; ++j) {
					Pi[j] -= vki * vk[j];
				}
			}
		}
		
		return P;
	}
	
	/**
	 * Compute the actual projection matrix P = (1 - vv^t). Mind the memory!!!
	 * @param rank
	 * @return
	 */
	public double [][] computePd(int rank) {
		if (rank == 0 || rank > v.length)
			rank = v.length;
		double [][] P = new double [v[0].length][v[0].length];

		// identity
		for (int i = 0; i < P.length; ++i)
			P[i][i] = 1.f;

		// subtract vv^t 
		for (int i = 0; i < P.length; ++i) {
			double [] Pi = P[i];
			for (int k = 0; k < rank; ++k) {
				float [] vk = v[k];
				float vki = vk[i];
				for (int j = 0; j < P.length; ++j) {
					Pi[j] -= vki * vk[j];
				}
			}
		}
		
		return P;
	}
	
	/**
	 * Compute the NAP projection matrix for the given data and weight matrix
	 * @param a input observation vectors, dim1 = samples, dim2 = features
	 * @param w weight matrix
	 * @param rank targte co-rank to reduce projection space (use 0 for full rank)
	 * @return projection matrix v
	 */
	public float [][] computeV(float [][] a, float [][] w, int rank) {
		if (rank == 0)
			rank = Math.min(a.length, a[0].length);
		
		if (a.length > a[0].length)
			return computeV_expansion(a, w, rank);
		else
			return computeV_kernel(a, w, rank);
	}
	
	private float [][] computeV_expansion(float [][] a, float [][] w, int rank) {
		int n = a.length;
		int m = a[0].length;
		
//		logger.info("A = ");
//		for (float [] ii : a)
//			logger.info(Arrays.toString(ii));
		
		// step 1: A Z(W)
		float [][] az = new float [n][m];
		for (int i = 0; i < n; ++i) {
			// cache current column of Z(W)
			float [] s = new float [n];
			float [] wi = w[i];
			for (int j = 0; j < n; ++j) {				
				s[i] += wi[j];
				s[j] -= wi[j];
			}
			
			// compute rows of A Z(W) using s
			for (int j = 0; j < m; ++j) {
				for (int k = 0; k < n; ++k)
					az[i][j] += a[k][j] * s[k];
			}
		}
		
//		logger.info("A * Z(W) = ");
//		for (float [] ii : az)
//			logger.info(Arrays.toString(ii));
		
		// step 2: compute A Z(W) A^t, enforce symmetry (for faster EV decomp!)
		float [][] azaT = new float [m][m];
		for (int i = 0; i < m; ++i) {
			for (int j = 0; j <= i; ++j) {
				for (int k = 0; k < n; ++k)
					azaT[i][j] += az[k][i] * a[k][j];
				if (i != j)
					azaT[j][i] = azaT[i][j];
			}
		}
		
		az = null;
		
//		logger.info("A * Z(W) A^t = ");
//		for (float [] ii : azaT)
//			logger.info(Arrays.toString(ii));
		
		// step 3: compute EV
		EigenvalueDecomposition eig = new EigenvalueDecomposition(new Matrix(azaT));
		
		// step 4: copy rank most principal EV
		// use transposed access as Matrix uses [row][column] indexing
		float [][] vh = eig.getV().getArray();
		float [] ev = eig.getRealEigenvalues();
		for (int i = 0; i < m; ++i) {
			for (int j = i + 1; j < m; ++j) {
				float h = vh[j][i];
				vh[j][i] = vh[i][j];
				vh[i][j] = h;
			}
		}
		
		// release the decomposition
		eig = null;
		azaT = null;
		
		System.gc();
		
		// sort by eigenvalue
		LinkedList<Pair<float [], Float>> sortedEV = new LinkedList<Pair<float [], Float>>();
		for (int i = 0; i < m; ++i)
			sortedEV.add(new Pair<float [], Float>(vh[i], ev[i]));
		
		// sort strongest EV first
		Collections.sort(sortedEV, new Comparator<Pair<float [], Float>>() {
			public int compare(Pair<float[], Float> o1,
					Pair<float[], Float> o2) {
				return (int) Math.signum(o2.b - o1.b);
			}
		});
		
		v = new float [rank][];
		Iterator<Pair<float [], Float>> it = sortedEV.iterator();
		int num = 0;
		for (int i = 0; i < rank; ++i) {
			Pair<float [], Float> p = it.next();
			if (p.b < 1e-6)
				num++;
			v[i] = p.a;
		}
		
		if (num > 0)
			logger.info("NAP.computeV_expansion(): WARNING -- using " + num + " eigenvectors with eigenvalue < 1e-6! Reduce rank to avoid warning");
		
		// norm the vectors (just in case...)
		for (int i = 0; i < rank; ++i) {
			float sc = 0.f;
			float [] vi = v[i];
			for (float f : vi)
				sc += (f * f);
			sc = (float) Math.sqrt(sc);
			for (int j = 0; j < m; ++j)
				vi[j] /= sc;
		}
		
//		logger.info("V = ");
//		for (float [] ii : v)
//			logger.info(Arrays.toString(ii));
		
		return v;
	}
	
	private float [][] computeV_kernel(float [][] a, float [][] w, int rank) {
		int n = a.length;
		int m = a[0].length;
		
//		logger.info("A = ");
//		for (float [] ii : a)
//			logger.info(Arrays.toString(ii));
		
		// step 1: compute K = A^t A (utilize symmetry)
		float [][] K = new float [n][n];
		for (int i = 0; i < n; ++i) {
			for (int j = 0; j <= i; ++j) {
				for (int k = 0; k < m; ++k)
					K[i][j] += a[i][k] * a[j][k];
				K[j][i] = K[i][j];
			}
		}
		
//		logger.info("K = ");
//		for (float [] ii : K)
//			logger.info(Arrays.toString(ii));
		
		// step 2: Z(W) K
		float [][] ZK = new float [n][n];
		for (int i = 0; i < n; ++i) {
			// cache current row of Z(W)
			float [] s = new float [n];
			float [] wi = w[i];
			for (int j = 0; j < n; ++j) {				
				s[i] += wi[j];
				s[j] -= wi[j];
			}
			
			float [] ZKi = ZK[i];
			for (int j = 0; j < n; ++j) {
				float [] Kj = K[j];
				for (int k = 0; k < n; ++k)
					ZKi[j] += s[k] * Kj[k];
			}
		}
		
		K = null;
		
//		logger.info("ZK = ");
//		for (float [] ii : ZK)
//			logger.info(Arrays.toString(ii));
		
		// step 3: compute EV
		EigenvalueDecomposition eig = new EigenvalueDecomposition(new Matrix(ZK));
		
		// step 4: copy rank most principal EV
		// use transposed access as Matrix uses [row][column] indexing
		float [][] vh = eig.getV().getArray();
		float [] ev = eig.getRealEigenvalues();
		for (int i = 0; i < n; ++i) {
			for (int j = i + 1; j < n; ++j) {
				float h = vh[j][i];
				vh[j][i] = vh[i][j];
				vh[i][j] = h;
			}
		}
		
		// release the decomposition matrix to allow the GC to save space
		eig = null;
		ZK = null;
		
		System.gc();
		
		// sort by eigenvalue
		LinkedList<Pair<float [], Float>> sortedEV = new LinkedList<Pair<float [], Float>>();
		for (int i = 0; i < n; ++i)
			sortedEV.add(new Pair<float [], Float>(vh[i], ev[i]));
		
		// sort strongest EV first
		Collections.sort(sortedEV, new Comparator<Pair<float [], Float>>() {
			public int compare(Pair<float[], Float> o1,
					Pair<float[], Float> o2) {
				return (int) Math.signum(o2.b - o1.b);
			}
		});
		
		float [][] Y = new float [rank][];
		Iterator<Pair<float [], Float>> it = sortedEV.iterator();
		int num = 0;
		for (int i = 0; i < rank; ++i) {
			Pair<float [], Float> p = it.next();
			if (p.b < 1e-6)
				num++;
			Y[i] = p.a;
		}
		
		if (num > 0)
			logger.info("NAP.computeV_kernel(): WARNING -- using " + num + " eigenvectors with eigenvalue < 1e-6! Reduce rank to avoid warning");
		
//		logger.info("Y = ");
//		for (float [] ii : Y)
//			logger.info(Arrays.toString(ii));
		
		// compute V = A * Y
		v = new float [rank][m];
		for (int i = 0; i < rank; ++i) {
			float [] vi = v[i];
			float [] Yi = Y[i];
			
			for (int k = 0; k < n; ++k) {
				float [] ak = a[k];
				float Yik = Yi[k];
				for (int j = 0; j < m; ++j)
					vi[j] += ak[j] * Yik;
			}
		}
		
		// norm the vectors
		for (int i = 0; i < rank; ++i) {
			float sc = 0.f;
			float [] vi = v[i];
			for (float f : vi)
				sc += (f * f);
			sc = (float) Math.sqrt(sc);
			for (int j = 0; j < m; ++j)
				vi[j] /= sc;
		}

//		logger.info("V = ");
//		for (float [] ii : v)
//			logger.info(Arrays.toString(ii));
		
		return v;
	}
	
	/**
	 * Return the computed square eigenvector matrix (sorted by worst EV first)
	 * @return
	 */
	public float [][] getV() {
		return v;
	}
	
	public int getDimension() {
		return v[0].length;
	}
	
	public int getRank() {
		return v.length;
	}
	
	/**
	 * Project the given feature vector and reduce the information with max rank
	 * @param x input/output
	 * @return
	 */
	public void project(double [] x) {
		project(new double [][] { x });
	}
	
	/**
	 * Project the given feature vector and reduce the information
	 * @param x input/output
	 * @param r rank (0 for maximum rank)
	 * @return
	 */
	public void project(double [] x, int r) {
		project(new double [][] { x }, r);
	}
	
	/**
	 * Project the given feature vector and reduce the information with max rank
	 * @param x input/output
	 * @return
	 */
	public void project(double [][] x) {
		project(x, 0);
	}
	
	/**
	 * Project the given feature vectors and reduce the information using the
	 * specified rank. This block-wise transformation is recommended (fast).
	 * @param x input/output
	 * @param r rank (0 for maximum rank)
	 * @return
	 */
	public void project(double [][] x, int r) {
		if (r == 0 || r > v.length)
			r = v.length;
		for (int i = 0; i < x.length; ++i) {
			double [] xi = x[i];
		
			if (vTx.length != r)
				vTx = new float [r];
			
			// R = V^T X
			for (int j = 0; j < r; ++j) {
				vTx[j] = 0.f;
				
				for (int k = 0; k < xi.length; ++k)
					vTx[j] += v[j][k] * xi[k];
			}
			
			// Y = X - V R
			for (int k = 0; k < r; ++k) {
				float [] vk = v[k];
				float vTxk = vTx[k];
				 for (int j = 0; j < xi.length; ++j)
					xi[j] -= vk[j] * vTxk;
			}
		}
	}
	
	
	/**
	 * Project the given feature vectors and reduce the information using the
	 * max possible rank. This block-wise transformation is recommended (fast).
	 * @param x input/output
	 * @return
	 */
	public void project(float [] x) {
		project(new float [][] { x });
	}
	
	/**
	 * Project the given feature vectors and reduce the information using the
	 * specified rank. This block-wise transformation is recommended (fast).
	 * @param x input/output
	 * @param r rank (0 for maximum rank)
	 * @return
	 */
	public void project(float [] x, int r) {
		project(new float [][] { x }, r);
	}
	
	/**
	 * Project the given feature vectors and reduce the information using the
	 * max possible rank. This block-wise transformation is recommended (fast).
	 * @param x input/output
	 * @return
	 */
	public void project(float [][] x) {
		project(x, 0);
	}
	
	/**
	 * Project the given feature vectors and reduce the information using the
	 * specified rank. This block-wise transformation is recommended (fast).
	 * @param x input/output
	 * @param r rank (0 for maximum rank)
	 * @return
	 */
	public void project(float [][] x, int r) {
		if (r == 0 || r > v.length)
			r = v.length;
		for (int i = 0; i < x.length; ++i) {
			float [] xi = x[i];
		
			if (vTx.length != r)
				vTx = new float [r];
			
			// R = V^T X
			for (int j = 0; j < r; ++j) {
				vTx[j] = 0.f;
				
				for (int k = 0; k < xi.length; ++k)
					vTx[j] += v[j][k] * xi[k];
			}
			
			// Y = X - V R
			for (int k = 0; k < r; ++k) {
				float [] vk = v[k];
				float vTxk = vTx[k];
				 for (int j = 0; j < xi.length; ++j)
					xi[j] -= vk[j] * vTxk;
			}
		}
	}
	
	/**
	 * Generate a new NAP object and load the projection from the given stream.
	 * @param is
	 * @throws IOException
	 */
	public NAP(InputStream is) throws IOException {
		load(is);
	}
	
	public NAP() {
		// nothing to do here
	}
	
	/**
	 * Save the (square) eigenvector matrix V
	 * @param os
	 * @throws IOException
	 */
	public void save(OutputStream os) throws IOException {
		if (v == null)
			throw new IOException("NAP.save(): no projection matrix available!");
		
		IOUtil.writeInt(os, v.length, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, v[0].length, ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < v.length; ++i)
			IOUtil.writeFloat(os, v[i], ByteOrder.LITTLE_ENDIAN);
	}
	
	/**
	 * Load the (square) eigenvector matrix V
	 * @param is
	 * @throws IOException
	 */
	public void load(InputStream is) throws IOException {
		int r = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		int d = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		logger.info("reading NAP transformation " + r + "x" + d);
		v = new float [r][d];
		for (int i = 0; i < r; ++i)
			if (!IOUtil.readFloat(is, v[i], ByteOrder.LITTLE_ENDIAN))
				throw new IOException("Could not read row " + i);
	}
	
	public static final String SYNOPSIS =
		"sikoried, 9/24/2010\n" +
		"Compute or apply Nuisance Attribute Projection (NAP) to reduce channel\n" +
		"effects on features. Note that all feature data needs to be cached, so be\n" +
		"considerate...\n\n" +
		"usage: transformations.NAP [options] [-c|-C|-a|-d ...]\n\n" +
		"options:\n" +
		"-v\n" +
		"  Be verbose (needs to be first argument!).\n" +
		"-w <strategy>\n" +
		"  Weighting strategy, choose one:\n" +
		"  isvc : speaker session variability compensation;\n" +
		"         W_ij = 1 for corresponding frames of different channels. This\n" +
		"         requires the channels to be of same length!\n" +
		"  cc   : channel compensation; W_ij = 1 for frames of different channels\n" +
		"         This is the default strategy.\n" +
		"\n" +
		"commandos:\n" +
		"-c file rank chan1 chan2 [chan...]\n" +
		"  Read in the channel data from the given files.\n" +
		"-C file rank list-file list-dir\n" +
		"  Same as -c, but use a lists of files directories, assuming each file\n" +
		"  of the list in each channel directory.\n" +
		"-a file rank list out-dir [in-dir]\n" +
		"  Apply the projection stored in file; use 0 rank for max rank. Resulting files are\n" +
		"  saved to out-dir, if required specify the proper in-dir where the input data\n" +
		"  are located.\n\n" +
		"-d file\n" +
		"  Display the projection matrix.\n";
		
	private static enum Mode { COMPUTE1, COMPUTE2, APPLY, DISPLAY };
	private static enum Strategy { ISVC, CC };
	
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		BasicConfigurator.configure();
		logger.setLevel(Level.FATAL);
		
		Mode mode = null;
		Strategy strat = Strategy.CC;
		
		int argOffset = 0;
		
		String outf = null;
		String outd = null;
		String list = null;
		String inDir = null;
		int rank = 0;
		
		String liFiles = null;
		String liDirs = null;
		
		// options first
		int z;
		for (z = 0; z < args.length; ++z) {
			if (args[z].equals("-v"))
				logger.setLevel(Level.INFO);
			else if (args[z].equals("-w")) {
				++z;
				if (args[z].equals("cc"))
					strat = Strategy.CC;
				else if (args[z].equals("isvc"))
					strat = Strategy.ISVC;
				else
					throw new RuntimeException("NAP.main(): Unknown strategy " + args[z]);
			} else
				break;
		}
		
		// command
		if (args[z].equals("-c")) {
			mode = Mode.COMPUTE1;
			outf = args[++z];
			rank = Integer.parseInt(args[++z]);
			argOffset = ++z;
		} else if (args[z].equals("-C")) { 
			mode = Mode.COMPUTE2;
			outf = args[++z];
			rank = Integer.parseInt(args[++z]);
			liFiles = args[++z];
			liDirs = args[++z];
		} else if (args[z].equals("-a")) {
			mode = Mode.APPLY;
			outf = args[++z];
			rank = Integer.parseInt(args[++z]);
			list = args[++z];
			outd = args[++z];
			inDir = z < args.length-1 ? args[++z] : null;
		} else if (args[z].equals("-d")) {
			mode = Mode.DISPLAY;
			outf = args[++z];
		} else 
			throw new IOException("NAP.main(): illegal argument " + args[z]);
		
		if (mode == null) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		if (mode == Mode.COMPUTE1 || mode == Mode.COMPUTE2) {
			LinkedList<Integer> chanLengths = new LinkedList<Integer>();
			LinkedList<float []> samples = new LinkedList<float []>();
			
			float [] buf = null;
			
			// A: read in channels from files
			if (mode == Mode.COMPUTE1) {
				for (int f = argOffset; f < args.length; ++f) {
					logger.info("NAP.main(): reading channel " + args[f]);
	
					FrameInputStream fr = new FrameInputStream(new File(args[f]));
					
					// allocate or check buffer
					if (buf == null)
						buf = new float [fr.getFrameSize()];
					else if (buf.length != fr.getFrameSize())
						throw new IOException("NAP.main(): channel " + args[f] + " does not match initial feature dimension!");
					
					int nf = 0;
					while (fr.read(buf)) {
						samples.add(buf.clone());
						nf++;
					}
					
					// remember channel length
					chanLengths.add(nf);
				}
			} 
			// B: read in channels using list and directories
			else {
				LinkedList<String> chans = new LinkedList<String>();
				LinkedList<String> files = new LinkedList<String>();
				BufferedReader br = new BufferedReader(new FileReader(liDirs));
				String line;
				while ((line = br.readLine()) != null) 
					chans.add(line);
				br.close();
				br = new BufferedReader(new FileReader(liFiles));
				while ((line = br.readLine()) != null) 
					files.add(line);
				
				// read in channels
				for (int i = 0; i < chans.size(); ++i) {
					String ch = chans.get(i);
					logger.info("NAP.main(): reading channel " + ch);
					
					int nf = 0;
					for (String f : files) {
						FrameInputStream fr = new FrameInputStream(new File(ch + System.getProperty("file.separator") + f));
						
						if (buf == null)
							buf = new float [fr.getFrameSize()];
						else if (buf.length != fr.getFrameSize())
							throw new IOException("NAP.main(): channel " + ch +  System.getProperty("file.separator") + f + " does not match initial feature dimension!");
						
						while (fr.read(buf)) {
							samples.add(buf.clone());
							nf++;
						}
					}
					
					chanLengths.add(nf);
				}
			}
			
			// allocate and transfer data
			float [][] a = new float [samples.size()][];
			Iterator<float []> it = samples.iterator();
			for (int i = 0; i < a.length; ++i)
				a[i] = it.next();
			logger.info("NAP.main(): cached " + a.length + " frames, dim=" + buf.length);
			
			logger.info("NAP.main(): building up weight matrix...");
			float [][] w = new float [a.length][a.length];
			
			if (strat == Strategy.CC) {
				// channel compensation: W_ij = 1 for samples of disjoint channel
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
			} else if (strat == Strategy.ISVC) {
				// inter-session variability compensation: W_ij = 1 for 
				// corresponding samples of different channels
				
				int l = chanLengths.get(0);
				int nc = chanLengths.size();
				
				// validate channel length
				for (int i = 1; i < nc; ++i) {
					if (l != chanLengths.get(i))
						throw new RuntimeException("NAP.main(): length of channel " + i + " does not match first channel!");
					
					for (int j = 0; j < i; ++j) {
						// set W_ij = 1 for corresponding frames
						for (int k = 0; k < l; ++k) {
							w[i*l + k][j*l + k] = 1.f;
							w[j*l + k][i*l + k] = 1.f;
						}
					}
				}
			} else
				throw new RuntimeException("NAP.main(): unsupported NAP strategy!");
			
			logger.info("NAP.main(): computing V...");
			NAP nap = new NAP();
			nap.computeV(a, w, rank);
			
			logger.info("NAP.main(): saving projection to " + outf);
			FileOutputStream os = new FileOutputStream(outf);
			nap.save(os);
			os.close();
		} else if (mode == Mode.APPLY){
			logger.info("NAP.main(): loading projection from " + outf);
			InputStream is = new BufferedInputStream(new FileInputStream(outf), 10485760);
			NAP nap = new NAP(is);
			is.close();
			
			logger.info("NAP.main(): applying blockwise NAP with rank=" + rank);
			BufferedReader br = new BufferedReader(new FileReader(list));
			while ((outf = br.readLine()) != null) {
				// read in file
				FrameInputStream fr = new FrameInputStream(new File(inDir == null ? outf : inDir + System.getProperty("file.separator") + outf));
				LinkedList<float []> xx = new LinkedList<float []>();
				float [] buf = new float [fr.getFrameSize()];
				while (fr.read(buf))
					xx.add(buf.clone());
				fr.close();
								
				// build data array
				float [][] x = new float [xx.size()][];
				Iterator<float []> it = xx.iterator();
				for (int i = 0; i < x.length; ++i)
					x[i] = it.next();
				
				// project
				nap.project(x, rank);
					
				// write out
				FrameOutputStream fw = new FrameOutputStream(nap.getDimension(), new File(outd + System.getProperty("file.separator") + outf));
				for (float [] px : x)
					fw.write(px);
				fw.close();
			}
		} else if (mode == Mode.DISPLAY) {
			logger.info("NAP.main(): loading projection from " + outf);
			InputStream is = new BufferedInputStream(new FileInputStream(outf), 10485760);
			NAP nap = new NAP(is);
			is.close();
			
			float [][] v = nap.getV();
			System.err.println("size(V) = " + v.length + " x " + v[0].length);
			System.out.println("V = ");
			for (float [] vv : v)
				System.out.println(Arrays.toString(vv));
		}
	}
}
