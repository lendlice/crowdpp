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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.ChunkedDataSet;
import de.fau.cs.jstk.io.FrameInputStream;


/**
 * A parallel implementation of the EM algorithm. Uses an initial mixture and
 * a ChunkedDataSet to update the parameters.
 * 
 * @author sikoried
 *
 */
public final class ParallelEM {
	private static Logger logger = Logger.getLogger(ParallelEM.class);
	
	/** number of threads (= CPUs) to use */
	private int numThreads = 0;
	
	/** data set to use; do not forget to rewind if required! */
	private ChunkedDataSet data = null;
	
	/** previous estimate */
	public Mixture previous = null;
	
	/** current estimate */
	public Mixture current = null;
	
	/** number of components */
	private int nd;
	
	/** feature dimension */
	private int fd;
	
	/** number of iterations performed by this instance */
	public int ni = 0;
	
	private MleDensityAccumulator.MleOptions opts;
	
	private Density.Flags flags;
	
	/**
	 * Generate a new Estimator for parallel EM iterations.
	 * 
	 * @param initial Initial mixture to start from (DATA IS MODIFIED)
	 * @param data data set to use
	 * @param numThreads number of threads (= CPUs)
	 * @throws IOException
	 */
	public ParallelEM(Mixture initial, ChunkedDataSet data, int numThreads) 
		throws IOException {
		this(initial, data, MleDensityAccumulator.MleOptions.pDefaultOptions, Density.Flags.fAllParams, numThreads);
	}
		
	public ParallelEM(Mixture initial, ChunkedDataSet data, int numThreads, Density.Flags flags)  
			throws IOException {
			this(initial, data, MleDensityAccumulator.MleOptions.pDefaultOptions, flags, numThreads);
	}
	
	public ParallelEM(Mixture initial, ChunkedDataSet data, 
			MleDensityAccumulator.MleOptions opts, Density.Flags flags, 
			int numThreads) throws IOException {
		this.data = data;
		this.numThreads = numThreads;
		this.current = initial;
		this.fd = initial.fd;
		this.nd = initial.nd;
		this.opts = opts;
		this.flags = flags;
	}
	
	/**
	 * Set the data set to work on
	 */
	public void setChunkedDataSet(ChunkedDataSet data) {
		this.data = data;
	}
	
	/**
	 * Set the number of threads for the next iteration
	 */
	public void setNumberOfThreads(int num) {
		numThreads = num;
	}
	
	/**
	 * Perform a number of EM iterations
	 * @param iterations
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void iterate(int iterations) throws ClassNotFoundException, IOException, InterruptedException {
		while (iterations-- > 0)
			iterate();
	}
	
	/**
	 * Perform one EM iteration
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void iterate() throws ClassNotFoundException, IOException, InterruptedException {
		logger.info("ParallelEM.iterate(): BEGIN iteration " + (++ni));
		
		// each thread an individual working copy of the current estiamte
		Mixture [] workingCopies = new Mixture[numThreads];
		MleMixtureAccumulator accus [] = new MleMixtureAccumulator [numThreads];
		
		
		// save the old mixture, put zeros in the current
		previous = current.clone();
		
		ExecutorService e = Executors.newFixedThreadPool(numThreads);
		
		// BEGIN EM PART1: accumulate the statistics
		CountDownLatch latch = new CountDownLatch(numThreads);
	
		MleMixtureAccumulator mlea = new MleMixtureAccumulator(
				current.fd, 
				current.nd, 
				current.diagonal() ? DensityDiagonal.class : DensityFull.class);
		
		for (int i = 0; i < numThreads; ++i)
			e.execute(new Worker(
				workingCopies[i] = current.clone(), 
				accus[i] = new MleMixtureAccumulator(mlea), latch)
			);
		
		// wait for all jobs to be done
		latch.await();
		
		// make sure the thread pool is done
		e.shutdownNow();
		
		// rewind the list 
		data.rewind();
		
		// BEGIN EM PART2: combine the partial estimates by combining the accus
		for (int i = 0; i < numThreads; ++i)
			mlea.propagate(accus[i]);
		
		MleMixtureAccumulator.MleUpdate(previous, opts, flags, mlea, current);

		logger.info("ParallelEM.iterate(): END");
	}
	
	/**
	 * First part of the EM: Accumulate posteriors, prepare priors and mean
	 */
	private class Worker implements Runnable {
		Mixture m;
		MleMixtureAccumulator a;
		CountDownLatch latch;
		
		/** feature buffer */
		double [] f;
		
		/** posterior buffer */
		double [] p;
		
		/** number of chunks processed by this thread */
		int cnt_chunk = 0;
		
		/** number of frames processed by this thread */
		int cnt_frame = 0;
		
		Worker(Mixture m, MleMixtureAccumulator a, CountDownLatch latch) {
			this.latch = latch;
			this.m = m;
			this.a = a;
			
			// init the buffers
			f = new double [fd];
			p = new double [nd];
			
			// just to be sure...
			a.flush();
		}
		
		/**
		 * Main thread routine: read as there are chunks, compute posteriors,
		 * update the accus
		 */
		public void run() {
			try {
				ChunkedDataSet.Chunk chunk;
				
				// as long as we have chunks to do... NB: data is (synchronized) from ParallelEM!
				while ((chunk = data.nextChunk()) != null) {
					FrameInputStream source = chunk.getFrameReader();
						
					while (source.read(f)) {
						m.evaluate(f);
						m.posteriors(p);
						a.accumulate(p, f);

						cnt_frame++;
					}
					
					cnt_chunk++;
				}
				
				logger.info("ParallelEM.Worker#" + Thread.currentThread().getId() + ".run(): processed " + cnt_frame + " in " + cnt_chunk + " chunks");
			
			} catch (IOException e) {
				logger.info("ParallelEM.Worker#" + Thread.currentThread().getId() + ".run(): IOException: " + e.toString());
			} finally {
				// notify the main thread
				latch.countDown();
			}
		}
	}
}
