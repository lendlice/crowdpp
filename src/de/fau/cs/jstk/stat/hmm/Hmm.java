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
package de.fau.cs.jstk.stat.hmm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.stat.Mixture;
import de.fau.cs.jstk.util.Arithmetics;


/**
 * The Hmm class implements hidden Markov models. It contains the transition
 * probabilities and a number of states. Currently, there are states describing
 * discrete, semi-continuous and continuous emission probabilities. </br> 
 * The general training procedure is to initialize and accumulate the statistics
 * over a certain number of observations, and then to reestimate the model 
 * parameters in the end.
 * 
 * @author sikoried
 */
public final class Hmm {
	private static Logger logger = Logger.getLogger(Hmm.class);
	
	/** unique id */
	public int id;
	
	/** textual representation, usually a token.toString() */
	public transient String textualId;
	
	/** number of states */
	public short ns;

	/** states */
	public State [] s;
	
	/** entry probabilities */
	public float [] pi = null;
	
	/** transition probabilities [from][to] */
	public float [][] a = null;
	
	/** statistics accumulator */
	public transient Accumulator accumulator = null;
	
	/** 
	 * Create a meta (left-to-right) HMM constructed from the referenced HMM array
	 * @param hmms sequence of HMM to represent
	 */
	public Hmm(Hmm [] hmms) {
		// collect model data
		ns = 0;
		LinkedList<State> states = new LinkedList<State>();
		for (Hmm m : hmms) {
			ns += m.ns;
			for (State ss : m.s)
				states.add(ss);
		}
		
		// join models
		s = states.toArray(new State [ns]);
		pi = new float [ns];
				
		// this must be left-to-right topology, so we start left-most!
		pi[0] = 1.f;
		
		// build up joint transition matrix
		a = new float [ns][ns];
		int disp = 0;
		for (Hmm m : hmms) {
			// copy transition matrix
			for (int i = 0; i < m.ns; ++i)
				for (int j = 0; j < m.ns; ++j)
					a[i+disp][j+disp] = m.a[i][j];
			
			// enforce a transition to the next model with acoustic probability
			if (disp > 0 && disp < ns) { 
				a[disp-1][disp]   = .5f;
				a[disp-1][disp-1] = .5f;
			}
			
			disp += m.ns;
		}
	}
	
	/**
	 * Create a new HMM with a unique id and a certain number of states of the
	 * requested kind.
	 * @param id unique id
	 * @param ns number of states
	 * @param templateState example instance of states to generate
	 */
	public Hmm(int id, short ns, State templateState) {
		this.id = id;
		this.ns = ns;
		
		// easy stuff
		s = new State [ns];
		pi = new float [ns];
		a = new float [ns][ns];
		
		// generate the states
		if (templateState instanceof DState) {
			DState ts = (DState) templateState;
			for (int i = 0; i < ns; ++i)
				s[i] = new DState(ts);
		} else if (templateState instanceof CState) {
			CState ts = (CState) templateState;
			for (int i = 0; i < ns; ++i)
				s[i] = new CState(ts);
		} else if (templateState instanceof SCState) {
			SCState ts = (SCState) templateState;
			for (int i = 0; i < ns; ++i)
				s[i] = new SCState(ts);
		} else 
			throw new RuntimeException("Hmm(): Unsupported state type " + templateState.getClass().getCanonicalName());
	}
	
	/**
	 * Create a new HMM and initialize from the InputStream
	 * @param is
	 * @param shared Use these shared densities for the initialization of SCStates
	 * @throws IOException
	 */
	public Hmm(InputStream is, HashMap<Integer, Mixture> shared) 
		throws IOException {
			
		id = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		ns = IOUtil.readShort(is, ByteOrder.LITTLE_ENDIAN);
		
		pi = new float [ns];
		if (!IOUtil.readFloat(is, pi, ByteOrder.LITTLE_ENDIAN))
			throw new IOException("could not read entry probs");
		
		a = new float [ns][ns];
		for (int i = 0; i < ns; ++i)
			if (!IOUtil.readFloat(is, a[i], ByteOrder.LITTLE_ENDIAN))
				throw new IOException("could not read transition probs");
		
		// read in the states individually
		s = new State [ns];
		for (int i = 0; i < ns; ++i)
			s[i] = State.read(is, shared);
	}
	
	/**
	 * Write the HMM parameters to the given OutputStream. Note that the the
	 * shared codebooks, if any, will not be written.
	 * 
	 * @param os
	 */
	public void write(OutputStream os) throws IOException {
		IOUtil.writeInt(os, id, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeShort(os, ns, ByteOrder.LITTLE_ENDIAN);
		
		IOUtil.writeFloat(os, pi, ByteOrder.LITTLE_ENDIAN);
		
		for (int i = 0; i < ns; ++i)
			IOUtil.writeFloat(os, a[i], ByteOrder.LITTLE_ENDIAN);
		
		for (State ss : s)
			ss.write(os);
	}
	
	/**
	 * Set the shared codebook for all states (will throws exceptions when used
	 * for any other state type than SCState).
	 * @param cb
	 */
	public void setSharedCodebook(Mixture cb) {
		for (State ss : s) {
			((SCState) ss).cb = cb;
		}
	}
	
	public Mixture getSharedCodebook() {
		return ((SCState) s[0]).cb;
	}
	
	/**
	 * Returns true if both HMMs have the same unique ID.
	 */
	public boolean equals(Object o) {
		if (!(o instanceof Hmm))
			return false;
		
		return ((Hmm) o).id == id;
	}
	
	/**
	 * Initialize the statistics (and remove old ones!)
	 */
	public void init() {
		if (accumulator != null)
			logger.warn("replacing existing Accumulator!");
		
		accumulator = new Accumulator();
		for (State si : s)
			si.init();
	}
	
	/**
	 * Discard any accumulated statistics. Recommended after re-estimation.
	 */
	public void discard() {
		accumulator = null;
		for (State si : s)
			si.discard();
	}

	/**
	 * Re-estimate the model parameters from the accumulators.
	 */
	public void reestimate() {
		double sum1 = 0., sum2;
		
		// re-estimate the entry and transition probabilities
		for (int i = 0; i < ns; ++i) {
			sum1 += accumulator.pi[i];
			                       
			sum2 = 0.;
			for (int j = 0; j < ns; ++j) 
				sum2 += accumulator.a[i][j];
			
			// see if there was any activity
			if (sum2 > 0.) {
				// re-estimate the transition probs
				for (int j = 0; j < ns; ++j)
					a[i][j] = (float) (accumulator.a[i][j] / sum2);
				
				// re-estimate the state
				s[i].reestimate();
			} else
				logger.warn("hmm(" + id + ")[" + i + "] no transition weight, no re-estimation of a[" + i + "][] and s[" + i + "]");
		}
		
		// divisions by zero are nasty...
		if (sum1 > 0.) {
			for (int i = 0; i < ns; ++i)
				pi[i] = (float) (accumulator.pi[i] / sum1);
		} else 
			logger.warn("hmm(" + id + ") no entries logged => no re-estimation of pi");
	}
	
	/**
	 * Propagate the sufficient statistics of another HMM instance to the local
	 * accumulator. The number of states (and their type) must match.
	 * @param source
	 */
	public void propagate(Hmm source) {
		if (source.ns != ns)
			throw new RuntimeException("HMM.propagate(): Source HMM has different number of states!");
		
		// absorb the state accumulators
		for (int i = 0; i < ns; ++i)
			s[i].propagate(source.s[i]);
		
		// absorb the transition accumulator
		accumulator.propagate(source.accumulator);
	}
	
	public void interpolate(Hmm source, double rho) {
		if (source.ns != ns)
			throw new RuntimeException("HMM.interpolate(): Source HMM has different number of states!");
		
		// interpolate HMM specific accumulators
		accumulator.interpolate(source.accumulator, rho);
		
		// interpolate state accumulators
		for (int i = 0; i < ns; ++i)
			s[i].interpolate(source.s[i], rho);
	}
	
	/**
	 * Interpolate the local parameters (!) with the referenced ones.
	 * @param wt this = wt * source + (1 - wt) * this
	 * @param source
	 */
	public void pinterpolate(double wt, Hmm source) {
		if (ns != source.ns)
			throw new RuntimeException("Hmm.pinterpolate(): different numbers of states");
		
		for (int i = 0; i < ns; ++i) {
			pi[i] = (float) (wt * source.pi[i] + (1. - wt) * pi[i]);
			Arithmetics.interp1(a[i], source.a[i], (float) wt);
			Arithmetics.makesumto1(a[i]);
			
			s[i].pinterpolate(wt, source.s[i]);
		}
		
		// don't forget the entry probs
		Arithmetics.makesumto1(pi);
	}
	
	/** 
	 * Accumulator class to hold statistics for transition and entry probabilities 
	 */
	public final class Accumulator {
		double [][] a = new double [ns][ns];
		double [] pi = new double [ns];
		long segments = 0;
		long frames = 0;
		
		void propagate(Accumulator source) {
			// no frames -- no gains ;)
			if (source.frames == 0)
				return;
			
			segments += source.segments;
			frames += source.frames;
			
			for (int i = 0; i < a.length; ++i) {
				pi[i] += source.pi[i];
				Arithmetics.vadd2(a[i], source.a[i]);
			}
		}
		
		void interpolate(Accumulator source, double rho) {
			for (int i = 0; i < a.length; ++i) {
				double r = rho / (rho + s[i].gamma());
				pi[i] = r * source.pi[i] + (1. - r) * pi[i];
				Arithmetics.interp1(a[i], source.a[i], r);
			}
		}
		
		public String toString() {
			return "HMM.Accumulator nseq=" + segments + " nfrm=" + frames;
		}
	}
	
	/** 
	 * Increment the Baum-Welch statistics by the given observation
	 * @param observation
	 */
	public void incrementBW(List<double []> observation) {
		int no = observation.size();
		
		if  (no < ns)
			logger.info("HMM.incrementBW(): WARNING -- observation sequence (" + no + ") shorter than model length (" + ns + ")!");
		
		if (no == 0)
			return;
		
		// increment the counters
		accumulator.segments++;
		accumulator.frames += no;
		
		Iterator<double []> o = observation.iterator();
		double [] x = null;
		
		// compute forward backward probabilities
		double [][] alpha = new double [no][ns];
		double [][] beta = new double [no][ns];
		
		// scales[t] = sum_j alpha[t][j]
		double [] scales = new double [no];
		
		// cached emission probabilities
		double [][] ep = new double [no][ns]; 
		
		// scaled alpha computation
		x = o.next();		
		
		for (int j = 0; j < ns; ++j) {
			ep[0][j] = s[j].emits(x);
			alpha[0][j] = pi[j] * ep[0][j];
			scales[0] += alpha[0][j];
		}
		
		for (int j = 0; j < ns; ++j)
			alpha[0][j] /= scales[0];
		
		for (int t = 1; t < no; ++t) {
			x = o.next();
			
			// compute alpha for each state
			for (int j = 0; j < ns; ++j) {
				// cache emission probability
				ep[t][j] = s[j].emits(x);
				
				// sum up over all previous states
				for (int i = 0; i < ns; ++i)
					alpha[t][j] += alpha[t-1][i] * a[i][j];
				
				alpha[t][j] *= ep[t][j];
				scales[t] += alpha[t][j];
			}
			
			// normalize
			for (int j = 0; j < ns; ++j)
				alpha[t][j] /= scales[t];
		}
		
		// scaled beta computation
		for (int i = 0; i < ns; ++i)
			beta[no-1][i] = 1. / scales[no-1];
		
		for (int t = no-2; t >= 0; --t) {
			for (int i = 0; i < ns; ++i) {
				for (int j = 0; j < ns; ++j)
					beta[t][i] += a[i][j] * ep[t+1][j] * beta[t+1][j];
				
				// normalize
				beta[t][i] /= scales[t];
			}
		}
		
		// update states
		o = observation.iterator();
		x = o.next();
		double [] gamma = new double[ns];		
		double sum = 0.;
		
		for (int t = 0; t < no; ++t) {
			// compute state posterior gamma
			// due to the normalization and the design of the scales, the scale
			// factor gets cancelled out!
			sum = 0.;
			for (int i = 0; i < ns; ++i) {
				gamma[i] = alpha[t][i] * beta[t][i];
				sum += gamma[i];
			}
			
			// for first observation, increase the entry prob accumulators
			if (t == 0) {
				for (int i = 0; i < ns; ++i)
					accumulator.pi[i] += gamma[i] / sum;
			}
			
			// for all observations, increase the statistics accumulator
			for (int i = 0; i < ns; ++i)
				s[i].accumulate(gamma[i] / sum, x);
			
			// no transition accumulation at last observation
			if (t == no - 1)
				break;
			
			for (int i = 0; i < ns; ++i)
				for (int j = 0; j < ns; ++j)
					accumulator.a[i][j] += alpha[t][i] * a[i][j] * ep[t+1][j] * beta[t+1][j];
			
			x = o.next();
		}		
	}
	
	/**
	 * Incremet the Viterbi statistics using the given alignment. If the actual
	 * path is not yet computed, the Viterbi decoding of the provided alignment
	 * is called.
	 * @param a Alignment containing 
	 */
	public void incrementVT(Alignment a) {
		// make sure we have the right model
		if (!a.model.equals(this))
			throw new RuntimeException("HMM[" + id + "].incrementVT(): Alignment.model and this model do not match.");
		
		// maybe we need an alignment
		if (a.q == null)
			throw new RuntimeException("HMM[" + id + "].incrementVT(): No state alignment present.");
		
		incrementVT(a.observation, a.q);
	}
	
	/**
	 * Increment the Viterbi statistics using the given observation and
	 * corresponding state sequence.
	 * @param observation feature vectors
	 * @param q state sequence
	 */
	public void incrementVT(List<double []> observation, int [] q) {
		if (observation.size() != q.length) {
			logger.fatal("HMM.incrementVT(): observation.size() != q.length");
			throw new RuntimeException("HMM.incrementVT(): observation.size() != q.length");
		}
		
		if  (observation.size() < ns)
			logger.info("HMM.incrementVT(): WARNING -- observation sequence (" + observation.size() + ") shorter than model length (" + ns + ")!");
		
		// nothing to do?
		if (q.length == 0)
			return;
		
		accumulator.segments++;
		accumulator.frames += q.length;
		
		Iterator<double []> o = observation.iterator();
		
		// see initial state
		accumulator.pi[q[0]] += 1.;
		
		// rest of observation
		for (int t = 0; t < q.length; ++t) {
			// accumulate for the target state
			s[q[t]].accumulate(1., o.next());

			// at the last observation, there's no transition update!
			if (t == q.length - 1)
				break;
			
			accumulator.a[q[t]][q[t+1]] += 1.;
		}
	}
	
	/**
	 * Generate a String representation of this HMM and its parameters.
	 */
	public String toString() {
		StringBuffer buf = new StringBuffer();
		
		buf.append("hmm.HMM id=" + id + " ns=" + ns + (textualId != null ? textualId : "") + "\n");
		buf.append("pi = [");
		for (int i = 0; i < ns; ++i)
			buf.append(" " + pi[i]);
		buf.append(" ]\na = [\n");
		for (int i = 0; i < ns; ++i) {
			for (int j = 0; j < ns; ++j)
				buf.append(" " + a[i][j]);
			buf.append("\n");
		}
		buf.append("]\n");
		
		for (int i = 0; i < ns; ++i)
			buf.append("s[" + i + "] " + s[i] + "\n");
		
		return buf.toString();
	}
	
	/**
	 * The HMM topology sets the possible entry and transition probabilities.
	 * @author sikoried
	 */
	public static enum Topology {
		/** 
		 * The linear model enters at the left most state and allows only
		 * transitions to the current state and the next subsequent state.
		 */
		LINEAR,
		
		/**
		 * The Bakis model enters at the left most state and allows transitions
		 * to self and 2 subsequent states.
		 */
		BAKIS,
		
		/**
		 * The left-to-right model enters at the left-most state and allows all 
		 * transitions to self and all subsequent states.
		 */
		LEFT_TO_RIGHT,
		
		/**
		 * The ergodic model may enter at any state and allows all state 
		 * transitions. This is the most general topology and should only be 
		 * applied to non-time dependent problems.
		 */
		ERGODIC
	}
	
	/**
	 * Set the entry and transition probabilities according to the specified 
	 * Topology
	 * @param topo
	 */
	public void setTransitions(Topology topo) {
		// reset the transitions
		for (int i = 0; i < ns; ++i) {
			pi[i] = 0.f;
			for (int j = 0; j < ns; ++j)
				a[i][j] = 0.f;
		}
		
		switch (topo) {
		case LINEAR: {
			pi[0] = 1.f;
			for (int i = 0; i < ns-1; ++i) {
				a[i][i] = .5f;
				a[i][i+1] = .5f;
			}
			a[ns-1][ns-1] = 1.f;
			break;
		}
		case ERGODIC: {
			for (int i = 0; i < ns; ++i) {
				pi[i] = (float) (1. / ns);
				for (int j = 0; j < ns; ++j)
					a[i][j] = (float) (1. / ns);
			}
		}
		default:
			logger.info("HMM.setTransitions(): requested topology not implemented (yet)");
		}
	}
}
