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


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import FJama.Matrix;
import FJama.SingularValueDecomposition;
import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.FrameSource;

/**
 * The CachedPCA caches all the data and solves the Eigendecomposition of the 
 * covariance matrix by applying SVD. This is requires much more memory than
 * the regular PCA but is able to solve implicit EV problem for problems where
 * dim >> num_samples.
 * 
 * @author sikoried
 *
 */
public class SparseDataPCA extends Projection {

	/** data cache */
	private List<float []> cache = new LinkedList<float []>();
	
	private double [] evals = null;
	
	public SparseDataPCA(int fd) {
		super(fd);
		mean = new double [fd];
	}
	
	/**
	 * Accumulate a copy of the referenced sample.
	 * @param x
	 */
	public void accumulate(double [] x) {
		float [] buf = new float [x.length];
		for (int i = 0; i < x.length; ++i) {
			buf[i] = (float) x[i];
			mean[i] += x[i];
		}
		cache.add(buf);
	}
	
	/**
	 * Accumulate all frames of the given FrameSource
	 * @param source
	 * @throws IOException
	 */
	public void accumulate(FrameSource source) throws IOException {
		double [] buf = new double [source.getFrameSize()];
		while (source.read(buf))
			accumulate(buf);
	}
	
	/**
	 * Estimate the implicit Eigenvectors of the covariance using SVD
	 */
	public void estimate() {
		// finalize mean computation
		int ns = cache.size();
		for (int i = 0; i < mean.length; ++i)
			mean[i] /= ns;
		
		// transpose the data, subtract mean
		float [][] A = new float [fd][ns];
		Iterator<float []> it = cache.iterator();
		for (int i = 0; it.hasNext(); ++i) {
			float [] h = it.next();
			for (int j = 0; j < h.length; ++j)
				A[j][i] = (h[j] - (float) mean[j]);
		}
		
		// A = U S V', where U = eig(AA')
		SingularValueDecomposition svd = new SingularValueDecomposition(new Matrix(A));
		proj = new double [ns][fd];
		evals = new double [ns];
		float [][] U = svd.getU().getArray();
		float [] S = svd.getSingularValues();
		for (int i = 0; i < proj.length; ++i) {
			evals[i] = (double) (S[i] * S[i]);
			for (int j = 0; j < fd; ++j)
				proj[i][j] = U[j][i];
		}
	}
	
	public double [] getEigenvalues() {
		return evals;
	}
	
	public static final String SYNOPSIS = 
		"sikoried, 2/2/2011\n" +
		"The sparse data PCA is designed for data with num_samples << dim. \n" +
		"It solves the Eigenvalue decomposition of the covariance matrix implicitly\n" +
		"via SVD and automatically reduces the dimension to the number of non-zero " +
		"Eigenvalues (for data with rank deficiency).\n" +
		"The CachedPCA uses single precision arithmetics (float) to accomodate for large\n" +
		"data sets (e.g. SpeakerID).\n" +
		"usage: transformations.SparsePCA proj list [indir]" +
		"  proj  : output file for projection (Frame format)\n" +
		"  list  : file list (files need to be Frame format)\n" +
		"  indir : (optional) directory where the input files are located\n";
	
	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 3) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		String outf = args[0];
		String listf = args[1];
		String indir = args.length == 3 ? args[2] + System.getProperty("file.separator") : "";
		
		SparseDataPCA pca = null;
		
		BufferedReader br = new BufferedReader(new FileReader(listf));
		String line;
		while ((line = br.readLine()) != null) {
			FrameInputStream fr = new FrameInputStream(new File(indir + line));
			
			if (pca == null)
				pca = new SparseDataPCA(fr.getFrameSize());
			
			pca.accumulate(fr);
		}
		br.close();

		pca.estimate();

		pca.save(new File(outf));
	}
}
