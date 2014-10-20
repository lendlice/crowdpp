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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.util.Arithmetics;

public final class MleDensityAccumulator {
	private static Logger logger = Logger.getLogger(MleDensityAccumulator.class);
	
	/** Host class of this accumulator */
	private Class<? extends Density> host;
	
	public int fd;
	
	/** number of observations */
	public long n = 0;
	
	/** occupancy (weight accumulator) */
	public double occ = 0;
	
	/** mean value accumulator */
	public double [] mue;
	
	/** covariance accumulator */
	public double [] cov;
	
	/**
	 * Allocate a new MLE accumulator for the requested type of Density
	 * @param fd
	 * @param host
	 * @throws ClassNotFoundException
	 */
	public MleDensityAccumulator(int fd, Class<? extends Density> host)
			throws ClassNotFoundException {
		this.fd = fd;
		this.host = host;
		
		this.mue = new double [fd];
		
		if (host.equals(DensityDiagonal.class))
			cov = new double [fd];
		else if (host.equals(DensityFull.class))
			cov = new double [fd * (fd + 1) / 2];
		else
			throw new ClassNotFoundException("MleDensityAccumulator not implemented for " + host.toString());
	}
	
	public MleDensityAccumulator(MleDensityAccumulator copy) {
		this.fd = copy.fd;
		this.host = copy.host;
		
		this.n   = copy.n;
		this.occ = copy.occ;
		this.mue = copy.mue.clone();
		this.cov = copy.cov.clone();
	}
	
	public MleDensityAccumulator(InputStream is) throws IOException {
		read(is);		
	}
	
	public static final int HOST_DIAGONAL = 0;
	public static final int HOST_FULL = 1;
	
	public void read(InputStream is) throws IOException {
		int htype = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		if (htype == HOST_DIAGONAL)
			host = DensityDiagonal.class;
		else if (htype == HOST_FULL)
			host = DensityFull.class;
		else
			throw new IOException("Unknown host-type " + htype);
		
		fd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		n = IOUtil.readLong(is, ByteOrder.LITTLE_ENDIAN);
		occ = IOUtil.readDouble(is, ByteOrder.LITTLE_ENDIAN);
		
		mue = new double [fd];
		cov = new double [htype == HOST_DIAGONAL ? fd : fd * (fd + 1) / 2];
		
		if (!IOUtil.readDouble(is, mue, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("Could not read x stats");
		
		if (!IOUtil.readDouble(is, cov, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("Could not read x2 stats");
	}
	
	public void write(OutputStream os) throws IOException {
		if (host.equals(DensityDiagonal.class))
			IOUtil.writeInt(os, HOST_DIAGONAL, ByteOrder.LITTLE_ENDIAN);
		else
			IOUtil.writeInt(os, HOST_FULL, ByteOrder.LITTLE_ENDIAN);
		
		IOUtil.writeInt(os, fd, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeLong(os, n , ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, occ, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, mue, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeDouble(os, cov, ByteOrder.LITTLE_ENDIAN);
		
		os.flush();
	}
	
	public void accumulate(double gamma, double [] x) {
		n++;
		
		// save your breath
		if (gamma == 0.)
			return;
		
		occ += gamma;
		
		if (host.equals(DensityDiagonal.class)) {
			
			for (int k = 0; k < fd; ++k) {
				double t = gamma * x[k];
				mue[k] += t;
				cov[k] += t * x[k];
			}
		} else {
			for (int k = 0; k < fd; ++k)
				mue[k] += gamma * x[k];
			
			int m = 0;
			for (int k = 0; k < fd; ++k)
				for (int l = 0; l <= k; ++l)
					cov[m++] += gamma * x[k] * x[l];
		}
	}
	
	/**
	 * Add the referenced accumulator's scores to this one's.
	 * @param source
	 */
	void propagate(MleDensityAccumulator source) {
		if (source.n == 0)
			return;
		
		// observations
		n += source.n;
		
		// stats
		occ += source.occ;
		Arithmetics.vadd2(mue, source.mue);
		Arithmetics.vadd2(cov, source.cov);
	}
	
	void interpolate(MleDensityAccumulator source, double weight) {
		if (source.n == 0)
			return;
		
		occ = weight * source.occ + (1. - weight) * occ;
		Arithmetics.interp1(mue, source.mue, weight);
		Arithmetics.interp1(cov, source.cov, weight);
	}
	
	/**
	 * Scale the accumulated statistics
	 * @param tau
	 */
	void scale(double tau) {
		occ *= tau;
		Arithmetics.smul2(mue, tau);
		Arithmetics.smul2(cov, tau);
	}
	
	/**
	 * Flush the accumulator by setting all values to zero
	 */
	void flush() {
		n = 0;
		occ = 0;
		Arrays.fill(mue, 0.0);
		Arrays.fill(cov, 0.0);
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("MleDensityAccumulator n=" + n + " occ=" + occ + "\n");
		sb.append("xstat = " + Arrays.toString(mue) + "\n");
		sb.append("x2stat = " + Arrays.toString(cov) + "\n");
		
		return sb.toString();		 
	}
	
	/**
	 * Options for the MLE reestimation of a single density.
	 * 
	 * @author sikoried
	 */
	public static final class MleOptions {
		public static final MleOptions pDefaultOptions = new MleOptions(1e-10, 0.001, 10.0);
		
		/** component weight (1.0 for standalone) */
		public final double minWeight;
		
		/** variance floor */
		public final double minVariance;
		
		/** minimum occupancy */
		public final double minOcc;

		public MleOptions(double minw, double minv, double mino) {
			minWeight = minw;
			minVariance = minv;
			minOcc = mino;
		}
		
		public String toString() {
			return "minWeight = " + minWeight + " minVariance = " + minVariance + " minOccupancy = " + minOcc;
		}
	}
	
	/**
	 * Update a given Density according to the given options using the suff.
	 * statistics in the accumulator.
	 * 
	 * @param din
	 * @param opt
	 * @param acc
	 * @param weight
	 * @param dout
	 */
	public static void MleUpdate(Density din, MleOptions opt, 
			Density.Flags flags, MleDensityAccumulator acc, 
			double weight, Density dout) {
		
		if  (acc.occ < opt.minOcc) {
			logger.info("occ < minOcc : No update of density " + din.id);
			return;
		}
		
		// normalize statistics
		Arithmetics.sdiv2(acc.mue, acc.occ);
		Arithmetics.sdiv2(acc.cov, acc.occ);
		
		// conclude covariance computation by subtracting new mean
		if (din instanceof DensityDiagonal) {
			for (int i = 0; i < acc.fd; ++i) {
				acc.cov[i] -= acc.mue[i] * acc.mue[i];
				if (acc.cov[i] < opt.minVariance)
					acc.cov[i] = opt.minVariance;
			}
		} else {
			int l = 0;
			for (int j = 0; j < acc.fd; ++j) {
				for (int k = 0; k <= j; ++k) {
					double cc = acc.cov[l] - acc.mue[j] * acc.mue[k];
					if (Math.abs(cc) < opt.minVariance)
						cc = opt.minVariance * Math.signum(cc);
					acc.cov[l++] = cc;
				}
			}
		}
		
		// see if we we need to do an update
		if (!flags.weights)
			weight = din.apr;
		
		double [] m = (flags.means ? acc.mue : din.mue);
		double [] c = (flags.vars  ? acc.cov : din.cov);
		
		// update statistics
		dout.fill(weight, m, c);
	}
}
