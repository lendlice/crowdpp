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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import de.fau.cs.jstk.exceptions.TrainingException;
import de.fau.cs.jstk.stat.Density.Flags;
import de.fau.cs.jstk.stat.MleDensityAccumulator.MleOptions;
import de.fau.cs.jstk.trans.PCA;
import de.fau.cs.jstk.util.Arithmetics;
import de.fau.cs.jstk.util.Distances;


/**
 * Collection of algorithms to initialize a codebook
 * 
 * @author sikoried
 *
 */
public abstract class Initialization {
	public static Logger logger = Logger.getLogger(Initialization.class);
	
	/**
	 * Compute a fast initialization for a mixture density. Available strategies
	 * are "sequential[_N]" (put the next N samples in the next cluster, rotate
	 * through clusters as long as there are samples), "random[_N]" (as 
	 * sequential, just random cluster allocation), "uniform" (as sequential
	 * however N is adjusted so that all data is linearly distributed on the 
	 * clusters), and "uniform_N" (uses sequences of N samples, drawn from positions
	 * distributed linearly over all data) 
	 * @param data
	 * @param nd number of densitites
	 * @param diagonalCovariances use diagonal covariances?
	 * @param method initialization method (see main doc)
	 * @return
	 * @throws TrainingException
	 */
	public static Mixture fastInit(List<Sample> data, int nd, boolean diagonalCovariances, String method) 
		throws TrainingException {
		boolean random = method.startsWith("random");
		
		// compute number of samples per chunk (default: 1)
		int n = 1;
		int max = Integer.MAX_VALUE;
		if (method.startsWith("sequential_"))
			n = Integer.parseInt(method.substring("sequential_".length()));
		else if (method.startsWith("random_"))
			n = Integer.parseInt(method.substring("random_".length()));
		else if (method.equals("uniform"))
			n = data.size() / nd;
		else if (method.startsWith("uniform_")){
			n = data.size() / nd;
			max = Integer.parseInt(method.substring("uniform_".length()));
			System.err.println("maximally using " + max + " samples per cluster");
		}
		
		logger.info("Initialization.fastInit(): This will be a " + method + " initialization; chunk-size=" + n);
		
		// init the lists
		LinkedList<LinkedList<Sample>> lists = new LinkedList<LinkedList<Sample>>();
		for (int i = 0; i < nd; ++i)
			lists.add(new LinkedList<Sample>());
		
		// distribute the data
		int ndx = 0;
		int fd = data.get(0).x.length;
		while (data.size() > 0) {
			int actual = ndx % nd;
			if (random)
				actual = (int)(Math.random() * nd);
			for (int i = 0; i < n && data.size() > 0; ++i){
				Sample sample = data.remove(0);
				if (i < max)
					lists.get(actual).add(sample);
			}
			ndx++;
		}
		
		// ensure that each list is long enough (make that 50 features)
		int norm = 0;
		for (int i = 0; i < nd; ++i) {
			if (lists.get(i).size() < 3)
				throw new TrainingException("Failed in partitioning data (there was a list with less than 3 samples)");
			else 
				norm += lists.get(i).size();
		}
			
		logger.info("Initialization.fastInit(): average number of samples per cluster: " + ((double) norm / nd));
		
		// ML-estimate the components
		logger.info("Initialization.fastInit(): computing ML estimates...");
		Mixture estimate = new Mixture(fd, nd, diagonalCovariances);
		for (int i = 0; i < nd; ++i) {
			estimate.components[i] = Trainer.ml(lists.get(i), diagonalCovariances);
			estimate.components[i].apr = (double) lists.get(i).size() / norm;
		}
		
		return estimate;
	}
	
	/**
	 * Produce a LGB clustering for the cached data, by splitting the largest
	 * cluster first. The resulting Mixture has weights based on the cluster
	 * sizes.
	 * @param data
	 * @param numc number of clusters requested
	 * @param eps value to shift the cluster centroids on split (1.+-eps)*c
	 * @param conv convergence value for centroid reestimation (e.g. 0.0001)
	 * @param diag diagonal or full covariances
	 * @return
	 */
	public static Mixture lbg(List<Sample> data, int numc, double eps, double conv, boolean diag)
		throws ClassNotFoundException {
		// dimension
		int dim = data.get(0).x.length;
		double scale = 1. / (data.size() * dim);
		
		// initial centroid
		DensityDiagonal c0 = (DensityDiagonal) Trainer.ml(data, diag);
		
		// compute diversity
		double d0 = 0.;
		for (Sample s : data)
			d0 += scale * Arithmetics.norm2(-1., s.x, c0.mue);
		
		ArrayList<Density> c = new ArrayList<Density>();
		c.add(c0);
		
		int [] occ = new int [] { data.size() };
		
		// iterate over the splitting procedure
		while (c.size() < numc) {			
			// split the largest cluster
			int p = 0;
			int v = occ[0];
			for (int i = 1; i < occ.length; ++i)
				if (occ[i] > v) { p = i; v = occ[i]; }
						
			// split
			Density ca = c.get(p);
			Arithmetics.smul2(ca.mue, 1. + eps);
			Density cb = (diag 
					? new DensityDiagonal((DensityDiagonal) ca)
					: new DensityFull((DensityFull) ca));
			Arithmetics.smul2(cb.mue, 1. - eps);
			c.add(cb);
			
			double d1 = d0;
			double d2 = 0.;
			
			occ = new int [c.size()];
			
			// keep updating the clusters till they converge
			while (Math.abs((d1 - d2) / d1) > conv) {
				// init accumulators
				MleDensityAccumulator [] mlea = new MleDensityAccumulator [c.size()];
				for (int i = 0; i < mlea.length; ++i)
					mlea[i] = new MleDensityAccumulator(c.get(i).fd, diag ? DensityDiagonal.class : DensityFull.class);
				
				// cluster by distance and accumulate
				for (Sample s : data) {
					int pos = 0;
					double min = Arithmetics.norm2(-1., s.x, c.get(0).mue);
					
					Iterator<Density> it = c.iterator(); it.next();
					int i = 1;
					while (it.hasNext()) {
						double d = Arithmetics.norm2(-1., s.x, it.next().mue);
						if (d < min) {
							min = d;
							pos = i;
						}
						++i;
					}
					
					s.y = (short) pos;
					mlea[pos].accumulate(1.0, s.x);
					occ[pos]++;
				}
				
				// update clusters
				double sum = 0;
				for (int i = 0; i < mlea.length; ++i)
					sum += mlea[i].occ;
				
				for (int i = 0; i < mlea.length; ++i) {
					Density nd = c.get(i).clone();
					MleDensityAccumulator.MleUpdate(c.get(i), MleOptions.pDefaultOptions, Flags.fAllParams, mlea[i], mlea[i].occ / sum, nd);
					c.get(i).fill(nd);
				}
				
				d1 = d2;
				d2 = 0.;
				for (Sample s : data)
					d2 += scale * Arithmetics.norm2(-1., s.x, c.get(s.y).mue);
			}
		}
		
		// construct codebook
		Mixture m = new Mixture(dim, c.size(), diag);
		Iterator<Density> it = c.iterator();
		for (int i = 0; i < c.size(); ++i) {
			Density dd = it.next();
			m.components[i].fill(dd.apr, dd.mue, dd.cov);
		}

		return m;
	}
	
	/**
	 * Perform a simple k-means clustering on the data. The number of cluster
	 * needs to be specified in advance. 
	 * @param data
	 * @param nd number of clusters
	 * @return Primitive MixtureDensity
	 * @throws TrainingException
	 */
	public static Mixture kMeansClustering(List<Sample> data, int nd, boolean diagonalCovariances)
			throws TrainingException {

		final long MAX_ITERATIONS = 100;
		final double MIN_ERROR = 1e-5;

		logger.info("Initialization.kMeansClustering(): convergence at MIN_ERROR=" + MIN_ERROR + " or MAX_ITERATIONS=" + MAX_ITERATIONS);
		
		if (data.size() < nd)
			throw new TrainingException("More clusters than samples in data set!");

		int fd = data.get(0).x.length;

		Mixture md1 = new Mixture(fd, nd, diagonalCovariances);
		Mixture md2 = new Mixture(fd, nd, diagonalCovariances);

		LinkedList<LinkedList<Sample>> clusters = new LinkedList<LinkedList<Sample>>();
		for (int i = 0; i < nd; ++i)
			clusters.add(new LinkedList<Sample>());
	
		// randomly distribute the data to the clusters
		LinkedList<Sample> copy = new LinkedList<Sample>(data);
		int z = 0;
		while (copy.size() > 0) {
			int pick = (int)(Math.random()*copy.size());
			clusters.get(z++ % nd).add(copy.remove(pick));
		}
		for (int i = 0; i < nd; ++i)
			md1.components[i] = Trainer.ml(clusters.get(i), diagonalCovariances);
		clusters.clear();

		// run k-means
		double c = 1.;
		long iteration = 0;
		while (c > MIN_ERROR && iteration < MAX_ITERATIONS) {
			logger.info("Initialization.kMeansClustering(): Iteration " + (iteration++) + "/" + MAX_ITERATIONS + " (c=" + c + ")");
			for (int i = 0; i < nd; ++i)
				clusters.add(new LinkedList<Sample>());

			// assign the data to the centroids
			for (Sample s : data)
				clusters.get(assignToCluster(s.x, md1.components)).add(s);

			// as we are seeking a partition, fill empty clusters by splitting the
			// biggest cluster in two parts
			for (int i = 0; i < nd; ++i) {
				if (clusters.get(i).size() == 0) {
					// get largest partition
					int c_i = -1;
					int c_s = -1;
					for (int j = 0; j < clusters.size(); ++j) {
						if (clusters.get(j).size() > c_s) {
							c_i = j;
							c_s = clusters.get(j).size();
						}
					}

					// set the partitions
					LinkedList<Sample> old = clusters.get(c_i);
					LinkedList<Sample> split1 = new LinkedList<Sample>(old.subList(0, c_s / 2));
					LinkedList<Sample> split2 = new LinkedList<Sample>(old.subList(c_s / 2 + 1, old.size() - 1));
					clusters.set(i, split1);
					clusters.set(c_i, split2);
				}
			}

			// compute ML statistics using the assigned sets
			for (int i = 0; i < nd; ++i) {
				md2.components[i] = Trainer.ml(clusters.get(i), diagonalCovariances);
				md2.components[i].apr = (double) clusters.get(i).size() / data.size();
			}

			// compute change
			c = 0;
			for (int i = 0; i < nd; ++i)
				c += Distances.euclidean(md1.components[i].mue,	md2.components[i].mue);

			// set new iteration
			md1 = md2;
			md2 = new Mixture(fd, nd, diagonalCovariances);

			clusters.clear();
		}

		return md1;
	}

	/**
	 * Compute the ID of the nearest centroid.
	 * 
	 * @param x
	 * @param d
	 *            components
	 * @return
	 */
	public static int assignToCluster(double[] x, Density[] d) {
		int id = 0;
		double dist = Distances.euclidean(x, d[0].mue);
		for (int i = 1; i < d.length; ++i) {
			double dist2 = Distances.euclidean(x, d[i].mue);
			if (dist2 < dist) {
				dist = dist2;
				id = i;
			}
		}
		return id;
	}
	
	/**
	 * Compute the ID of the nearest centroid.
	 * 
	 * @param x
	 * @param list 
	 * @return
	 */
	public static int assignToCluster(double[] x, List<Density> list) {
		int id = list.get(0).id;
		double dist = Distances.euclidean(x, list.get(0).mue);
		for (int i = 1; i < list.size(); ++i) {
			double dist2 = Distances.euclidean(x, list.get(i).mue);
			if (dist2 < dist) {
				dist = dist2;
				id = list.get(i).id;
			}
		}
		return id;
	}
	
	private static boolean matchesCluster1(double [] x, double [] c1, double [] c2) {
		double dist1 = Distances.euclidean(x, c1);
		double dist2 = Distances.euclidean(x, c2);
		
		return dist1 < dist2;
	}

	/**
	 * Perform a Gaussian-means (G-means) clustering on the given data set. The
	 * number of mixture components is learned by testing the clusters for
	 * Gaussian distribution and in case splitting clusters violating this
	 * constraint (see also Hamerly03-LTK).
	 * 
	 * @param data
	 * @param alpha significance level in {0.1,0.05,0.025,0.01}
	 * @param maxc maximum number of clusters
	 * @return
	 */
	public static Mixture gMeansClustering(List<Sample> data, double alpha, int maxc,
			boolean diagonalCovariances) throws TrainingException {
		int fd = data.get(0).x.length;
		
		LinkedList<LinkedList<Sample>> clusters = new LinkedList<LinkedList<Sample>>();

		// initialize with only one center
		Mixture md1 = new Mixture(fd, 1, diagonalCovariances);
		Mixture md2 = new Mixture(fd, 1, diagonalCovariances);
		md1.components[0] = Trainer.ml(data, diagonalCovariances);

		while (true) {
			// first, run k-means using current centroids
			double ch = 1.;
			while (ch > 1e-10) {
				// ensure clean clusters
				clusters.clear();
				
				for (int i = 0; i < md1.nd; ++i)
					clusters.add(new LinkedList<Sample>());

				// assign the data to the centroids
				for (Sample s : data)
					clusters.get(assignToCluster(s.x, md1.components)).add(s);

				// compute ML statistics using these sets
				for (int i = 0; i < md1.nd; ++i) {
					md2.components[i] = Trainer.ml(clusters.get(i), diagonalCovariances);
					md2.components[i].apr = (double) clusters.get(i).size() / data.size();
				}

				// compute change
				ch = 0;
				for (int i = 0; i < md2.nd; ++i)
					ch += Distances.euclidean(md1.components[i].mue, md2.components[i].mue);

				// set new iteration
				md1 = md2;
				md2 = new Mixture(fd, md1.nd, diagonalCovariances);
				
				// cluster information is kept for A-D-Test -> no cleanup here!
			}
			
			if (md1.nd >= maxc)
				break;
			
			/* Anderson-Darling-Test to verify the asumption that each cluster
			 * is generated by a normal distribution. The idea is to measure the
			 * difference between a "real" Gaussian distribution and the observed
			 * distribution. Depending on the significance value alpha, the hypothesis
			 * (distribution = gaussian) is accepted or rejected.
			 * 
			 * To split the clusters, the new centroids are computed using the
			 * previous common centroid and adding/substracting the principal component
			 * of the data. This should give the best result.
			 */
			LinkedList<Density> centroids = new LinkedList<Density>();
			for (int i = 0; i < md1.nd; ++i) {
				// copy the affected data
				double[] co = md1.components[i].mue.clone();
				LinkedList<Sample> work = new LinkedList<Sample>();
				for (Sample s : clusters.get(i))
					work.add(new Sample(s));

				// compute the pca
				PCA pca = new PCA(work.get(0).x.length);
				pca.accumulate(work);
				pca.estimate();
				
				// starting from the centroid, follow the principal component
				double[] m = Arithmetics.smul1(pca.getProjection()[0], Math.sqrt(2. * pca.getEigenvalues()[0] / Math.PI));
				double[] c1 = Arithmetics.vadd1(co, m);
				double[] c2 = Arithmetics.vsub1(co, m);
				
				// run basic 2-means
				double [] _c1 = new double [c1.length];
				double [] _c2 = new double [c2.length];
				int _cnt1 = 0, _cnt2 = 0;
				
				double _ch = 1.;
				while (_ch > 1e-10) {
					// compute new means
					for (Sample _s : clusters.get(i)) {
						if (matchesCluster1(_s.x, c1, c2))
							{ Arithmetics.vadd2(_c1, _s.x); _cnt1++; }
						else
							{ Arithmetics.vadd2(_c2, _s.x); _cnt2++; }
					}
					
					// normalize, compute change
					_ch = 0;
					for (int j = 0; j < _c1.length; ++j) {
						_c1[j] /= (double) _cnt1;
						_c2[j] /= (double) _cnt2;
						
						_ch += Math.abs(c1[j] - _c1[j]);
						_ch += Math.abs(c2[j] - _c2[j]);
						
						c1[j] = _c1[j]; _c1[j] = 0;
						c2[j] = _c2[j]; _c2[j] = 0;
					}
					
					_cnt1 = _cnt2 = 0;
				}
				
				/* Reduce the dimension of the features by projecting on the
				 * data onto the principal component and apply the distribution
				 * test to this 'flat' version of the cluster.
				 */
				double[] ps = new double[work.size()];
				double[] v = Arithmetics.vsub1(c1, c2);
				double vn = Arithmetics.norm2(v);
				for (int j = 0; j < ps.length; ++j) 
					ps[j] = Arithmetics.dotp(clusters.get(i).get(j).x, v) / vn;

				if (DistributionTest.andersonDarlingNormal(ps, alpha) || centroids.size() == maxc-1) {
					// cluster seems to be normal distributed
					centroids.add(md1.components[i]);
				} else {
					// cluster needs to be splitted
					Density d1 = diagonalCovariances ? new DensityDiagonal(md1.fd) : new DensityFull(md1.fd);
					Density d2 = diagonalCovariances ? new DensityDiagonal(md1.fd) : new DensityFull(md1.fd);
					
					System.arraycopy(c1, 0, d1.mue, 0, c1.length);
					System.arraycopy(c2, 0, d2.mue, 0, c2.length);
					
					// copy covariance
					
					centroids.add(d1);
					centroids.add(d2);
				}
				
				if (centroids.size() >= maxc)
					break;
			}
			
			// stop criterion: no more new clusters, current md1 will do
			if (md1.components.length == centroids.size())
				break;
			
			// copy the new centroids
			md1 = new Mixture(md1.fd, centroids.size(), diagonalCovariances);
			md2 = new Mixture(md1.fd, centroids.size(), diagonalCovariances);
			for (int i = 0; i < centroids.size(); ++i)
				md1.components[i] = centroids.get(i);
			
			logger.info("retrying with " + centroids.size() + " clusters");
		}

		for (Density d : md1.components)
			d.update();
		
		return md1;
	}
	
	/** 
	 * Abstract DensityRanker: Ranks components ascending by their quality: Lower
	 * score is better quality.
	 * 
	 * @author sikoried
	 */
	static abstract class DensityRanker implements Comparator<Density> {
		HashMap<Integer, Double> scores = new HashMap<Integer, Double>();
		
		/** sorts ascending by "quality" */
		public int compare(Density d1, Density d2) {

			double diff = scores.get(d2.id) - scores.get(d1.id);
			
			// if this instance has lower covariance, it is better!
			if (diff == 0)
				return 0;
			else if (diff < 0.)
				return -1;
			else if (diff > 0.)
				return 1;
			
			return 0;
		}
	}
	
	static class ClusterSizeRanker extends DensityRanker {
		/**
		 * Rank the clusters by number of assigned features. Split the one with
		 * most samples.
		 * 
		 * @param clusters
		 * @param assignment
		 */
		ClusterSizeRanker(List<Density> clusters, HashMap<Integer, LinkedList<Sample>> assignment) {
			logger.info("ClusterSizeRanker: Sorting the clusters by number of assigned samples");
			for (Density d : clusters)
				scores.put(d.id, (double) assignment.get(d.id).size());
		}
	}
	
	/**
	 * Rank components by their sum of (absolute) covariances. Fastest comparator.
	 * 
	 * @author sikoried
	 */
	static class CovarianceRanker extends DensityRanker {
		CovarianceRanker(List<Density> clusters) {
			logger.info("CovarianceRanker: computing scores");
			for (Density d : clusters) {
				double s = 0.;
				for (double c : d.cov)
					if (s < Math.abs(c))
						s = Math.abs(c);
				scores.put(d.id, s);
			}
		}		
	}
	
	/**
	 * Rank components by the sum of the eigen values of the covariance matrix.
	 * 
	 * @author sikoried
	 */
	static class SumOfEigenvaluesRanker extends DensityRanker {
		SumOfEigenvaluesRanker(List<Density> clusters) {
			logger.info("SumOfEigenvaluesRanker: computing scores");
			long timestamp = System.currentTimeMillis();
			
			for (Density d : clusters) {
				int fd = d.fd;
				Jama.Matrix cm = new Jama.Matrix(fd, fd);
				if (d instanceof DensityDiagonal) {
					for (int i = 0; i < fd; ++i)
						cm.set(i, i, d.cov[i]);
				} else {
					// lower triangular matrix...
					int k = 0;
					for (int i = 0; i < fd; ++i)
						for (int j = 0; j <= i; ++j) {
							cm.set(i, j, d.cov[k]);
							cm.set(j, i, d.cov[k]);
							k++;
						}
				}
				
				double [] ev = cm.eig().getRealEigenvalues();
				
				double sum = 0.;
				
				// sum over the eigen values
				for (int i = 0; i < fd; ++i) 
					sum += ev[i];
					
				scores.put(d.id, sum);
			}
			logger.info("SumOfEigenvaluesRanker: " + clusters.size() + " scores in " + String.format("%.2f", (System.currentTimeMillis() - timestamp) / 1000.) + " sec");
		}
	}
	
	/**
	 * Rank components by comparing (largest_ev - smallest_ev) of the covariance
	 * 
	 * @author sikoried
	 */
	static class EigenvalueDifferenceRanker extends DensityRanker {
		EigenvalueDifferenceRanker(LinkedList<Density> clusters) {
			logger.info("EigenvalueDifferenceRanker: computing scores");
			long timestamp = System.currentTimeMillis();
			
			for (Density d : clusters) {
				int fd = d.fd;
				Jama.Matrix cm = new Jama.Matrix(fd, fd);
				if (d instanceof DensityDiagonal) {
					for (int i = 0; i < fd; ++i)
						cm.set(i, i, d.cov[i]);
				} else {
					// lower triangular matrix...
					int k = 0;
					for (int i = 0; i < fd; ++i)
						for (int j = 0; j <= i; ++j) {
							cm.set(i, j, d.cov[k]);
							cm.set(j, i, d.cov[k]);
							k++;
						}
				}
				
				double [] ev = cm.eig().getRealEigenvalues();
				
				double sum = ev[fd-1] - ev[0];
					
				scores.put(d.id, sum);
			}
			logger.info("EigenvalueDifferenceRanker: " + clusters.size() + " scores in " + String.format("%.2f", (System.currentTimeMillis() - timestamp) / 1000.) + " sec");
		}
	}
	
	/**
	 * Rank components by comparing (largest_ev - smallest_ev) of the covariance
	 * 
	 * @author sikoried
	 */
	static class EigenvalueRanker extends DensityRanker {
		EigenvalueRanker(List<Density> clusters) {
			logger.info("EigenvalueRanker: computing scores");
			long timestamp = System.currentTimeMillis();
			
			for (Density d : clusters) {
				int fd = d.fd;
				Jama.Matrix cm = new Jama.Matrix(fd, fd);
				if (d instanceof DensityDiagonal) {
					for (int i = 0; i < fd; ++i)
						cm.set(i, i, d.cov[i]);
				} else {
					// lower triangular matrix...
					int k = 0;
					for (int i = 0; i < fd; ++i)
						for (int j = 0; j <= i; ++j) {
							cm.set(i, j, d.cov[k]);
							cm.set(j, i, d.cov[k]);
							k++;
						}
				}
				
				double [] ev = cm.eig().getRealEigenvalues();
					
				scores.put(d.id, ev[fd-1]);
			}
			logger.info("EigenvalueRanker: " + clusters.size() + " scores in " + String.format("%.2f", (System.currentTimeMillis() - timestamp) / 1000.) + " sec");
		}
	}
	
	/**
	 * Rank components by their Anderson Darling statistics. The larger this 
	 * value is, the more likely the data for the estimate is normally distributed.
	 * 
	 * @author sikoried
	 */
	static class AndersonDarlingRanker extends DensityRanker {
		/** 
		 * We need to cache the Anderson Darling statistics: reference the clusters
		 * and their assigned samples.
		 * 
		 * @param clusters
		 * @param assignments
		 */
		AndersonDarlingRanker(List<Density> clusters, HashMap<Integer, LinkedList<Sample>> assignment) {
			logger.info("AndersonDarlingRanker: computing AD statistics");
			
			long timestamp = System.currentTimeMillis();
			
			// compute the AD statistic for each cluster
			for (Density d : clusters) {
				// copy the data
				LinkedList<Sample> work = new LinkedList<Sample>();
				for (Sample s : assignment.get(d.id))
					work.add(new Sample(s));
				
				if (work.size() == 0) {
					scores.put(d.id, 0.);
					continue;
				}
				
				// do the pca
				PCA pca = new PCA(work.get(0).x.length);
				pca.accumulate(work);
				pca.estimate();
				
				// transform all samples
				double [] transformedData = new double [work.size()];
				double [] xt = new double [1];
				for (int i = 0; i < transformedData.length; ++i) {
					pca.transform(work.get(i).x, xt);
					transformedData[i] = xt[0];
				}
				
				double ad = DistributionTest.andersonDarlingNormalCriticalValue(transformedData);
				
				scores.put(d.id, ad);
			}
			
			logger.info("AndersonDarlingRanker: " + clusters.size() + " scores in " + String.format("%.2f", (System.currentTimeMillis() - timestamp) / 1000.) + " sec");
		}
	}
	
	public static enum DensityRankingMethod {
		/** sum of the (absolute) covariance values -- fastest */
		COVARIANCE,
		
		/** sum of the eigen values of the covariance matrix */
		SUM_EIGENVALUE,
		
		/** compare (largest EV - smallest EV) */
		EV_DIFFERENCE,
		
		/** compare the largest EV only */
		EV,
		
		/** Compare by Anderson-Darling statistics */
		AD_STATISTIC,
		
		/** compare by number of assigned samples */
		NUM_SAMPLES
	}
	
	/**
	 * Perform a hierarchical Gaussian clustering: Beginning with only one density,
	 * the worst density according to the Ranker is split.
	 * 
	 * @param data List of data samples
	 * @param maxc Maximum number of clusters
	 * @param diagonalCovariances
	 * @return ready-to-use MixtureDensity
	 * @throws TrainingException
	 */
	public static Mixture hierarchicalGaussianClustering(List<Sample> data, 
			int maxc, boolean diagonalCovariances, 
			DensityRankingMethod rank) throws TrainingException {
		LinkedList<Density> clusters = new LinkedList<Density>();
		HashMap<Integer, LinkedList<Sample>> assignment = new HashMap<Integer, LinkedList<Sample>>();
		HashSet<Integer> unsplittable = new HashSet<Integer>();
		
		// id for the next cluster
		int nextId = 1;
		
		// remember the number of samples
		int num_samples = data.size();
		int fd = data.get(0).x.length;
		
		// initialization
		clusters.add(diagonalCovariances ? new DensityDiagonal(fd) : new DensityFull(fd));
		assignment.put(0, new LinkedList<Sample>());
		
		// iterate until desired number of clusters is reached
		while (clusters.size() < maxc) {
			
			// step 1: distribute the samples to the clusters
			for (LinkedList<Sample> ll : assignment.values())
				ll.clear();
			for (Sample s : data)
				assignment.get(assignToCluster(s.x, clusters)).add(s);
			
			// step 2: estimate components
			for (Density d : clusters) {
				logger.info("cluster " + d.id + " has " + assignment.get(d.id).size() + " samples");
				Density tmp = Trainer.ml(assignment.get(d.id), diagonalCovariances);
				d.fill(tmp.apr, tmp.mue, tmp.cov);
			}
			
			// generate a ranker: first splitting candidate is first then!
			DensityRanker ranker = null;
			switch (rank) {
			case COVARIANCE: 
				ranker = new CovarianceRanker(clusters); break;
			case SUM_EIGENVALUE: 
				ranker = new SumOfEigenvaluesRanker(clusters); break;
			case EV_DIFFERENCE:
				ranker = new EigenvalueDifferenceRanker(clusters); break;
			case AD_STATISTIC:
				ranker = new AndersonDarlingRanker(clusters, assignment); break;
			case EV:
				ranker = new EigenvalueRanker(clusters); break;
			case NUM_SAMPLES:
				ranker = new ClusterSizeRanker(clusters, assignment); break;
			}

			// rank the estimates by quality
			logger.info("Initialization.hierarchicalGaussianClustering(): sorting clusters by quality...");
			Collections.sort(clusters, ranker);
			
			// remove the worst cluster for splitting (if not unsplittable)
			int ind;
			Density split = null;
			for (ind = 0; ind < clusters.size(); ind++) {
				split = clusters.get(ind);
				if (!unsplittable.contains(split.id)) {
					if (ranker instanceof AndersonDarlingRanker) {
						if (ranker.scores.get(split.id) > 2.)
							break;
					} else
						break;
				}					
				else
					split = null;
			}
			
			// any candidate?
			if (split == null) {
				logger.info("Initialization.hierarchicalGaussianClustering(): no splittable cluster remaining -- stop");
				break;
			}
			
			logger.info("Initialization.hierarchicalGaussianClustering(): splitting cluster_" + split.id + "...");
			
			// get a copy of the data to do the PCA
			LinkedList<Sample> work = new LinkedList<Sample>();
			for (Sample s : assignment.get(split.id))
				work.add(new Sample(s));
			
			PCA pca = new PCA(work.get(0).x.length);
			pca.accumulate(work);
			pca.estimate();
			
			// we need lists for these new components
			LinkedList<Sample> ass1 = new LinkedList<Sample>();
			LinkedList<Sample> ass2 = new LinkedList<Sample>();
			
			// starting from the old mean, follow the principal component
			double[] shift = Arithmetics.smul1(pca.getProjection()[0], Math.sqrt(2. * pca.getEigenvalues()[0] / Math.PI));
			double[] c1 = Arithmetics.vadd1(split.mue, shift);
			double[] c2 = Arithmetics.vsub1(split.mue, shift);
			
			// distributer the cluster to the splits
			for (Sample _s : assignment.get(split.id)) {
				if (matchesCluster1(_s.x, c1, c2))
					ass1.add(_s);
				else
					ass2.add(_s);
			}
			
			// check if we produced a too small cluster
			if (ass1.size() < 50  || ass2.size() < 50) {
				unsplittable.add(split.id);
				continue;
			}
			
			// compute the ML estimate using the assignments
			logger.info("Initialization.hierarchicalGaussianClustering(): estimate new clusters...");
			Density d1 = Trainer.ml(ass1, diagonalCovariances);
			Density d2 = Trainer.ml(ass2, diagonalCovariances);
			
			// remove the splitted cluster and its assignments
			clusters.remove(ind);
			assignment.remove(split.id);
			
			// IDs for the new components
			int id1 = split.id;
			int id2 = nextId++;
			
			d1.id = id1;
			d2.id = id2;

			clusters.add(d1);
			clusters.add(d2);
			
			assignment.put(id1, ass1);
			assignment.put(id2, ass2);
			
			logger.info("Initialization.hierarchicalGaussianClustering(): num_samples_cluster_" + id1 + " " + ass1.size());
			logger.info("Initialization.hierarchicalGaussianClustering(): num_samples_cluster_" + id2 + " " + ass2.size());
						
			logger.info("Initialization.hierarchicalGaussianClustering(): number_of_clusters = " + clusters.size());
		}
		
		// build mixture density, estimate priors from assignments
		Mixture md = new Mixture(fd, clusters.size(), diagonalCovariances);
		clusters.toArray(md.components);
		for (Density d : md.components) {
			d.apr = (double) assignment.get(d.id).size() / num_samples;
			d.update();
		}
		
		return md;
	}
}
