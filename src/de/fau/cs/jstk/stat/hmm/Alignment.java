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

import java.util.Iterator;
import java.util.List;

import de.fau.cs.jstk.exceptions.AlignmentException;

/**
 * An Alignment is an observation sequence with the respective state sequence.
 * 
 * @author sikoried
 */
public class Alignment {
	/** feature sequence */
	public List<double []> observation;
	
	/** state sequence */
	public int [] q = null;
	
	/** underlying hidden Markov model */
	public Hmm model;
	
	/** score of this alignment */
	public double score = 0.;
	
	/**
	 * Create a new alignment instance for a given feature sequence and HMM.
	 * @param model associated HMM
	 * @param observation feature sequence
	 */
	public Alignment(Hmm model, List<double []> observation) {
		this.observation = observation;
		this.model = model;
	}

	/**
	 * Create anew alignment instance for a given feature sequence and HMM and
	 * provide a predefined alignment (used for initialization).
	 * @param observation feature sequence
	 * @param model associated HMM
	 * @param qstar predefined alignment
	 */
	public Alignment(Hmm model, List<double []> observation, int [] qstar)
		throws AlignmentException {
		this(model, observation);
		
		if (observation != null && observation.size() != qstar.length)
			throw new AlignmentException("manual q-star alignment does not match observation length");
		
		q = qstar;
	}
	
	/**
	 * Create a new alignment instance for a given feature sequence and HMM and
	 * provide a predefined alignment (used for initialization).
	 * @param observation feature sequence
	 * @param model associated HMM
	 * @param qstar predefined alignment
	 */
	public Alignment(Hmm model, List<double []> observation, List<Integer> qstar)
		throws AlignmentException {
		this(model, observation);
		
		if (observation != null && observation.size() != qstar.size())
			throw new AlignmentException("manual q-star alignment does not match observation length");
		
		q = new int [qstar.size()];
		for (int i = 0; i < q.length; ++i)
			q[i] = qstar.get(i);
	}
	
	/**
	 * Enforce the given state sequence. Used for initial Viterbi training.
	 * @param qstar desired state sequence
	 */
	public void forceCustomAlignment(int [] qstar) throws AlignmentException {
		if (qstar.length != observation.size())
			throw new AlignmentException("manual q-star alignment does not match observation length");
		q = qstar;
	}
	
	/**
	 * Set a linear left-to-right alignment for the underlying feature
	 * sequence and model.
	 */
	public void forceLinearAlignment() throws AlignmentException {
		if (observation.size() < model.ns)
			throw new AlignmentException("observation shorter than number of states");
		
		q = new int [observation.size()];

		// the actual field width
		double width = (double) q.length / (double) model.ns;
		
		// the slack due to the rounding
		double slack = 0.;
		
		int j = 0;
		for (int i = 0; i < model.ns; ++i) {
			int fields = (int) (width - slack + .5);
			slack = fields - (width - slack);
			for (int k = 0; k < fields; ++k)
				q[j++] = i;
		}
		
		// in case of precision problems, fill with last state
		while (j < q.length)
			q[j++] = model.ns - 1;
	}
	
	/**
	 * Set a random alignment by executing a random walk through the states. 
	 */
	public void forceRandomAlignment() throws AlignmentException {
		if (observation.size() < model.ns)
			throw new AlignmentException("observation shorter than number of states");
		
		q = new int [observation.size()];
		
		for (int i = 0; i < q.length; ++i)
			q[i] = (int) (model.ns * Math.random());
	}
	
	/**
	 * Compute the forced alignment and return the corresponding Viterbi score.
	 * @return Viterbi score
	 */
	public double forcedAlignment() throws AlignmentException {
		return decode(true);
	}
	
	/**
	 * Decode the observation sequence for the given model (Viterbi decoding)
	 * @return Viterbi score
	 */
	public double decode() throws AlignmentException {
		return decode(false);
	}
	
	/**
	 * Compute a Viterbi alignment for the given model and observation sequence.
	 * @param forced true for forced alignment (this only affects the backtracking)
	 * @return alignment score
	 */
	private double decode(boolean forced) throws AlignmentException {
		int T = observation.size();
		short N = model.ns; 
		
		if (forced && T < N)
			throw new AlignmentException("observation shorter than number of states");
		
		float [][] a = model.a;
		
		q = new int [T];
		
		double [][] scores = new double [T][N];
		short [][] trace = new short [T][N];
		
		// maximum value and position
		double mv = -Double.MAX_VALUE;
		short mp = 0;
		
		// use an iterator to move through the observation
		Iterator<double []> iter = observation.iterator();
		double [] x = iter.next();
		
		// initialize, check for possible entry points
		for (int j = 0; j < N; ++j) {
			scores[0][j] = (model.pi[j] == 0. ? 
								-Double.MAX_VALUE :
								Math.log(model.pi[j]) + Math.log(model.s[j].emits(x))
							);
		}
				
		// iterate
		for (int t = 1; iter.hasNext(); ++t) {
			x = iter.next();
			for (int j = 0; j < N; ++j) {
				// compute scores, search max
				for (short i = 0; i < N; ++i) {
					double s = scores[t-1][i] + Math.log(a[i][j]);
					if (mv < s) { mv = s; mp = i; }
				}
				scores[t][j] = mv + Math.log(model.s[j].emits(x));
				trace[t][j] = mp;
				
				// reset max values
				mv = -Double.MAX_VALUE; mp = 0;
			}
		}
		
		// last step
		for (short j = 0; j < N; ++ j)
			if (mv < scores[T-1][j]) { mv = scores[T-1][j]; mp = j; }
		
		// are we supposed to do a forced alignment?
		if (forced) {
			mp = (short) (N - 1);
			mv = scores[T-1][N-1];
		}
		
		q[T-1] = mp;
			
		// backtrack best state sequence
		for (int t = T-2; t >= 0; --t)
			q[t] = trace[t+1][q[t+1]];

		// save and return score
		return (score = mv);
	}
	
	/**
	 * Generate a String representation for debug use only.
	 */
	public String toString() {
		StringBuffer buf = new StringBuffer();
		
		if (q == null) { 
			buf.append("-no alignment-");
		} else {
			buf.append("q = [");
			for (int qq : q)
				buf.append(" " + qq);
			buf.append(" ]\nocc = [");
			int [] stats = new int [model.ns];
			for (int qq : q)
				stats[qq]++;
			for (int ss : stats)
				buf.append(" " + ss);
			buf.append(" ]\n");
		}
		
		return buf.toString();
	}
	
	/**
	 * Produce a MetaAlignment-format representation of the Alignment 
	 * Format is alignment-length [state-sequence]
	 * @return
	 */
	public String pack() {
		StringBuffer sb = new StringBuffer();
		sb.append(model.textualId == null ? "model-id=" + model.id : model.textualId);
		if (q != null) {
			sb.append(" " + q.length);
			for (int qq : q)
				sb.append(" " + qq);
		} else if (observation != null) {
			sb.append(" " + observation.size());
		}
		
		return sb.toString();
	}
}
