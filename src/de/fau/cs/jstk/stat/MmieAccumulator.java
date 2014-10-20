/*
	Copyright (c) 2011
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import de.fau.cs.jstk.stat.Density.Flags;
import de.fau.cs.jstk.util.Arithmetics;

public final class MmieAccumulator {
	private static Logger logger = Logger.getLogger(MmieAccumulator.class);
	public static final class InvalidIdException extends Exception {
		private static final long serialVersionUID = 1L;
		
	}
	/** Mixture inventory for reestimation */
	public HashMap<Integer, Mixture> inventory = new HashMap<Integer, Mixture>();
	
	/** Sufficient statistics for each registered Mixture */
	public HashMap<Integer, MmieStats> statistics = new HashMap<Integer, MmieStats>();
	
	/**
	 * Container of the MMIE sufficient statistics for an individual Mixture
	 * component.
	 */
	public static final class MmieStats {
		double W;
		double [] ny, dy;
		double [][] nx, nx2;
		double [][] dx, dx2;
		
		MmieStats(int fd, int nd, boolean diagonal, double W) {
			this.W = W;
			ny = new double [nd];
			nx = new double [nd][fd];
			nx2 = (diagonal ? new double [nd][fd] : new double [nd][fd * (fd + 1) / 2]);
			
			dy = new double [nd];
			dx = new double [nd][fd];
			dx2 = (diagonal ? new double [nd][fd] : new double [nd][fd * (fd + 1) / 2]);
		}
		
		public void addNom(MleMixtureAccumulator a) {
			for (int i = 0; i < ny.length; ++i) {
				MleDensityAccumulator da = a.accs[i];
				ny[i] += W * da.occ;
				Arithmetics.vadd6(nx[i], da.mue, W);
				Arithmetics.vadd6(nx2[i], da.cov, W);
			}
		}
		
		public void addDen(MleMixtureAccumulator a, double p) {
			for (int i = 0; i < dy.length; ++i) {
				MleDensityAccumulator da = a.accs[i];
				dy[i] += W * p * da.occ;
				Arithmetics.vadd6(dx[i], da.mue, W * p);
				Arithmetics.vadd6(dx2[i], da.cov, W * p);
			}
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			
			sb.append("ny = " + Arrays.toString(ny) + "\n");
			sb.append("dy = " + Arrays.toString(dy) + "\n");
			
			for (int i = 0; i < ny.length; ++i) {
				sb.append(i + " nx = " + Arrays.toString(nx[i]) + "\n");
				sb.append(i + " nx2 = " + Arrays.toString(nx2[i]) + "\n");
				sb.append(i + " dx = " + Arrays.toString(dx[i]) + "\n");
				sb.append(i + " dx2 = " + Arrays.toString(dx2[i]) + "\n");
			}
			
			return sb.toString();
		}
	}

	public void register(int id, Mixture m, double W) throws InvalidIdException {
		if (statistics.containsKey(id))
			throw new InvalidIdException();
		
		inventory.put(id, m);
		statistics.put(id, new MmieStats(m.fd, m.nd, m.diagonal, W));
	}
	
	public void addSegment(int target, 
			HashMap<Integer, Double> logscores,
			HashMap<Integer, MleMixtureAccumulator> mlestats,			 
			double Kr) {
		
		logger.info("addSegment: Kr=" + Kr);
		
		// get the accumulator for the target class
		MmieStats s = statistics.get(target);
		s.addNom(mlestats.get(target));
		
		// compute segment posteriors
		double sum = 0.0;
		for (Map.Entry<Integer, Double> e : logscores.entrySet()) {
			double p = Math.exp(Kr * e.getValue());
			sum += p;
			e.setValue(p);
		}
		
		for (Map.Entry<Integer, Double> e : logscores.entrySet())
			e.setValue(e.getValue() / sum);
				
		// for all classes & accumulators
		for (Map.Entry<Integer, Double> e : logscores.entrySet()) {
			int id = e.getKey();
			double post = e.getValue();
			MmieStats si = statistics.get(id);
			
			logger.info("adding denominator with p=" + post);
			
			si.addDen(mlestats.get(id), post);
		}
	}
	
	public static final class MmieOptions {
		public static final MmieOptions pDefaultOptions = new MmieOptions(1e-10, 0.001, 10.0, 2.0);
		
		/** component weight (1.0 for standalone) */
		public final double minWeight;
		
		/** variance floor */
		public final double minVariance;
		
		/** minimum occupancy */
		public final double minOcc;

		/** EBW smoothing constant */
		public final double E;
				
		/**
		 * MMIE Options
		 * @param minw minimum component weight
		 * @param minv minimum variance
		 * @param mino minimum occupancy for reestimate
		 * @param e smoothing coefficient E for extBW
		 */
		public MmieOptions(double minw, double minv, double mino, double e) {
			minWeight = minw;
			minVariance = minv;
			minOcc = mino;
			E = e;
		}
		
		public String toString() {
			return "minWeight = " + minWeight + " minVariance = " + minVariance + " minOccupancy = " + minOcc + " E = " + E;
		}
	}
	
	public static boolean EBWUpdate(int i, 
			double D, 
			Flags flags, 
			MmieStats s, 
			Density din, 
			Density dout) {
		
		// compute new mean
		double [] m = new double [din.fd];
		Arithmetics.vadd2(m, s.nx[i]);
		Arithmetics.vadd6(m, s.dx[i], -1.0);
		Arithmetics.vadd6(m, din.mue, D);
		Arithmetics.smul2(m, 1.0 / (s.ny[i] - s.dy[i] + D));
		
		// compute new covariance
		double [] c = new double [din.cov.length];
		Arithmetics.vadd2(c, s.nx2[i]);
		Arithmetics.vadd6(c, s.dx2[i], -1.0);
		Arithmetics.vadd6(c, din.cov, D);
		
		if (m.length != c.length) {
			Arithmetics.vspaddsp(c, din.mue, D);
			Arithmetics.smul2(c, 1.0 / (s.ny[i] - s.dy[i] + D));
			Arithmetics.vspaddsp(c, m, -1.0);
		} else {
			double [] mo2 = Arithmetics.compmul1(din.mue, din.mue);
			double [] mn2 = Arithmetics.compmul1(m, m);
			
			Arithmetics.vadd6(c, mo2, D);
			Arithmetics.smul2(c, 1.0 / (s.ny[i] - s.dy[i] + D));
			Arithmetics.vadd6(c, mn2, -1.0);
		}
		
		// reset if required
		if (!flags.means) 
			System.arraycopy(din.mue, 0, m, 0, m.length);
		if (!flags.vars)
			System.arraycopy(din.cov, 0, c, 0, c.length);
		
		// check if all variances are positive
		double v = (c.length == m.length ? Arithmetics.min(c) : Arithmetics.minsp(c, m.length));
		
		if (v > 0.0) {
			dout.fill(din.apr, m, c);
		}
		
		return (v > 0.0);
	}
	
	public static void MmieUpdate(HashMap<Integer, Mixture> invin, 
			MmieOptions opts, 
			Density.Flags flags, 
			HashMap<Integer, MmieStats> statistics, 
			HashMap<Integer, Mixture> invout) {
		
		if (invout.size() > 0)
			logger.info("The referenced out-inventory was not empty");
		invout.clear();
		
		for (Map.Entry<Integer, Mixture> e : invin.entrySet()) {
			int id = e.getKey();
			Mixture min = e.getValue();
			Mixture mout = min.clone();
			invout.put(id, mout);
			MmieStats s = statistics.get(id);
			
			for (int i = 0; i < min.nd; ++i) {
				double D = opts.E * s.dy[i] / 2.;
				
				int it, maxit = 100;
				for (it = 0; it < maxit; ++it) {
					if (EBWUpdate(i, D, flags, s, min.components[i], mout.components[i])) {
						// success, so double the D value (constraint ii)
						logger.info(i + " success at it=" + it + " D=" + D + " committing with D*2");
						EBWUpdate(i, D * 2, flags, s, min.components[i], mout.components[i]);
						break;
					} else {
						D *= 1.1;
					}
				}
				
				if (it == maxit) {
					logger.warn(" reached maxit (unexpected) -- restoring original density");
					mout.components[i].fill(min.components[i]);
				}
			}
			
			if (flags.weights){
				double kf = s.dy[0] / min.components[0].apr;
				for (int i = 1; i < min.nd; ++i)
					kf = Math.max(kf, s.dy[i] / min.components[i].apr);
				
				double [] k = new double [min.nd];
				Arrays.fill(k, kf);
				
				double [] w = new double [min.nd];
				for (int i = 0; i < w.length; ++i) {
					w[i] = min.components[i].apr;
					k[i] -= s.dy[i] / min.components[i].apr;
				}
				
				for (int i = 0; i < 50; ++i) {
					for (int j = 0; j < w.length; ++j)
						w[j] = s.ny[j] + k[j] * w[j];
				}
				
				Arithmetics.makesumto1(w);
				for (int i = 0; i < w.length; ++i) {
					mout.components[i].apr = w[i] < opts.minWeight ? opts.minWeight : w[i];
					mout.components[i].update();
				}
				
			}
		} 
		
	}
}
