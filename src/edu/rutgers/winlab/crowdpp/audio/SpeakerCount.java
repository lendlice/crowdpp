/*
 * Copyright (c) 2012-2014 Chenren Xu, Sugang Li
 * Acknowledgments: Yanyong Zhang, Yih-Farn (Robin) Chen, Emiliano Miluzzo, Jun Li
 * Contact: lendlice@winlab.rutgers.edu
 *
 * This file is part of the Crowdpp.
 *
 * Crowdpp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Crowdpp is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with the Crowdpp. If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package edu.rutgers.winlab.crowdpp.audio;

import edu.rutgers.winlab.crowdpp.util.Maths;
import edu.rutgers.winlab.crowdpp.util.Constants;
import edu.rutgers.winlab.crowdpp.util.Distances;
import edu.rutgers.winlab.crowdpp.util.FileProcess;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math;

import org.ejml.simple.SimpleMatrix;

import android.util.Log;

/**
 * The SpeakerCount class 
 * @author Chenren Xu, Sugang Li
 */
public class SpeakerCount {
	
	/** the chosen distance function */
	public static double getDistance(SimpleMatrix a, SimpleMatrix b) {
		return Distances.Cosine(a, b);
	}

	/** gender estimation algorithm */
	public static int getGender(double pitch) {
		// uncertain
		int gender = 0;	
		// male		
		if (pitch < Constants.pitch_male_upper) {
			gender = 1;	
		}
		// female
		else if (pitch > Constants.pitch_female_lower) {
			gender = 2;	
		}
		return gender;
	}
	
	/** identify if the speakers are the same one or not based on gender information  
	 * 	@return 1: same gender 
	 * 					0: different gender
	 * 				 -1: uncertain
	 */
	public static int genderDecision(double pitch_a, double pitch_b) {
		int gender_a = getGender(pitch_a);
		int gender_b = getGender(pitch_b);
		// clear gender identification through voice
		if (gender_a > 0 && gender_b > 0) {
			if (gender_a == gender_b)
				return 1;
			else 
				return 0;
		}
		// leave this job to MFCC
		return -1;
	}
	
	/** calibrating the owner's voice data */
	public static boolean selfCalibration(String path) throws java.io.IOException {
		
		double[][] mfcc 	= FileProcess.readFile(path + ".jstk.mfcc.txt");
		double[][] pitch 	= FileProcess.readFile(path + ".YIN.pitch.txt");			
		
		// compute the number of segments
		int sample_num 		= pitch.length;
		double[] time			= new double[sample_num]; 
		time[0] = 0.032;
		for(int i = 1; i < time.length; i++) {
			time[i] = time[i-1] + 0.016;
		}
		int end_id = sample_num - 1;
		int seg_num = (int) Math.floor(time[end_id] / Constants.seg_duration_sec);
		
		// find the begin and end indices of each segment
		int[] lower_id = new int [seg_num];
		int[] upper_id = new int [seg_num];

		lower_id[0] = 0;
		int seg_id = 1;

		for (int i = 0; i < sample_num; i++) {
			if (time[i] <= (double)(seg_id * Constants.seg_duration_sec) && time[i+1] > (double)(seg_id * Constants.seg_duration_sec)) {
				upper_id[seg_id-1] = i;
				seg_id++;
				if (seg_id == seg_num + 1) 
					break;
				lower_id[seg_id-1] = i + 1;
			}
		}

		// filter out the non-voiced segments		
		SimpleMatrix mfcc_mat = new SimpleMatrix(mfcc);
		List<SimpleMatrix> mfcc_list = new ArrayList<SimpleMatrix>();
		List<Double> pitch_list = new ArrayList<Double>();
		List<Double> pitch_rate = new ArrayList<Double>();		
		List<Double> pitch_mu = new ArrayList<Double>();
		List<Double> pitch_sigma = new ArrayList<Double>();
			
		for(int i = 0; i < seg_num; i++) {
			int c = 0;
			List<Double> temp_pitch = new ArrayList<Double>();
			for(int j = lower_id[i]; j < upper_id[i]; j++) {
				if (pitch[j][0] != -1) {
					c++;
					temp_pitch.add(pitch[j][0]);
				}
			}
			pitch_rate.add((double)c / (upper_id[i] - lower_id[i] + 1));
			pitch_mu.add(Maths.getMean(temp_pitch));
			pitch_sigma.add(Math.sqrt(Maths.getVariance(temp_pitch)));
			
			if (pitch_rate.get(i) >= Constants.pitch_rate_lower 
					&& pitch_mu.get(i) >= Constants.pitch_mu_lower 
					&& pitch_mu.get(i) <= Constants.pitch_mu_upper 
					&& pitch_sigma.get(i) <= Constants.pitch_sigma_upper) {
				mfcc_list.add(mfcc_mat.extractMatrix(lower_id[i], upper_id[i], 0, 19)); 
				pitch_list.add(pitch_mu.get(i));
			}
		}
		
		// calibration failed without enough calibration data
		if (mfcc_list.size() * Constants.seg_duration_sec < Constants.cal_duration_sec_lower) {
			return false;
		}
		// log the calibration feature data 
		else {
			File file_mfcc = new File(path + ".jstk.mfcc.txt");
			FileOutputStream fos_mfcc = new FileOutputStream(file_mfcc, true);
			for (int i = 0; i < mfcc_list.size(); i++) {
				for (int j = 0; j < mfcc_list.get(i).numRows(); j++) {
					for (int k = 0; k < mfcc_list.get(i).numCols(); k++) {
						fos_mfcc.write((Double.toString(mfcc_list.get(i).get(j,k)) + "\t").getBytes());
					}
					fos_mfcc.write("\n".getBytes());
				}
			}
			fos_mfcc.close();
			File file_pitch = new File(path + ".YIN.pitch.txt");
			FileOutputStream fos_pitch = new FileOutputStream(file_pitch, true);
			for (int i = 0; i < pitch_list.size(); i++) {
				fos_pitch.write((Double.toString(pitch_list.get(i)) + "\n").getBytes());
			}
			fos_pitch.close();			
			return true;
		}
	}
	
	@SuppressWarnings("rawtypes")
	/** segment the conversation testing data */	
	public static List[] segmentation(String[] args) throws java.io.IOException {

		double[][] mfcc 	= FileProcess.readFile(args[0]);
		double[][] pitch 	= FileProcess.readFile(args[1]);			
		
		// compute the number of segments
		int sample_num 		= pitch.length;
		double[] time			= new double[sample_num]; 
		time[0] = 0.032;
		for(int i = 1; i < time.length; i++) {
			time[i] = time[i-1] + 0.016;
		}
		
		int end_id = sample_num - 1;
		int seg_num = (int) Math.floor(time[end_id] / Constants.seg_duration_sec);

		if (seg_num == 0)
			return null;
		
		// find the begin and end indices of each segment
		int[] lower_id = new int [seg_num];
		int[] upper_id = new int [seg_num];

		lower_id[0] = 0;
		int seg_id = 1;
			
		for (int i = 0; i < sample_num; i++) {
			if (time[i] <= (double)(seg_id * Constants.seg_duration_sec) && time[i+1] > (double)(seg_id * Constants.seg_duration_sec)) {
				upper_id[seg_id-1] = i;
				seg_id++;
				if (seg_id == seg_num + 1) 
					break;
				lower_id[seg_id-1] = i + 1;
			}
		}
			
		// filter out the non-voiced segments		
		SimpleMatrix mfcc_mat = new SimpleMatrix(mfcc);
		List<SimpleMatrix> mfcc_list = new ArrayList<SimpleMatrix>();
		List<Double> pitch_list = new ArrayList<Double>();
		List<Double> pitch_rate = new ArrayList<Double>();		
		List<Double> pitch_mu = new ArrayList<Double>();
		List<Double> pitch_sigma = new ArrayList<Double>();
			
		for (int i = 0; i < seg_num; i++) {
			int c = 0;
			List<Double> temp_pitch = new ArrayList<Double>();
			for (int j = lower_id[i]; j < upper_id[i]; j++) {
				if (pitch[j][0] < Constants.pitch_mu_lower || pitch[j][0] > Constants.pitch_mu_upper) 
					pitch[j][0] = -1;				
				if (pitch[j][0] != -1) {
					c++;
					temp_pitch.add(pitch[j][0]);
				}
			}
			pitch_rate.add((double)c / (upper_id[i] - lower_id[i] + 1));
			pitch_mu.add(Maths.getMean(temp_pitch));
			pitch_sigma.add(Math.sqrt(Maths.getVariance(temp_pitch)));
			if (pitch_rate.get(i) >= Constants.pitch_rate_lower 
					&& pitch_mu.get(i) >= Constants.pitch_mu_lower 
					&& pitch_mu.get(i) <= Constants.pitch_mu_upper 
					&& pitch_sigma.get(i) <= Constants.pitch_sigma_upper) {
				mfcc_list.add(mfcc_mat.extractMatrix(lower_id[i], upper_id[i], 0, 19)); 
				pitch_list.add(pitch_mu.get(i));
			}
		}
		
		// no voiced data
		if (pitch_list.size() == 0) {
			return null;
		}
		
		// iteratively pre-cluster the neighbor segments until no merging happens 
		int last_size, p, q;
		while (true) {
			last_size = mfcc_list.size();
			p = 0; q = 1;
			while (q < mfcc_list.size()) {
		  	if (getDistance(mfcc_list.get(p), mfcc_list.get(q)) <= Constants.mfcc_dist_same_un && genderDecision(pitch_list.get(p), pitch_list.get(q)) == 1) {	
		  		mfcc_list.set(p, mfcc_list.get(p).combine(mfcc_list.get(p).numRows(), 0, mfcc_list.get(q)));
		      pitch_list.set(p, (pitch_list.get(p) + pitch_list.get(q)) / 2);
		      mfcc_list.remove(q);
		      pitch_list.remove(q);
		    }
		    else {
		    	p = q; q++;
		    }
		  }
		  if (last_size == mfcc_list.size()) {
		  	break;
		  }
		}	
			
		return new List[]{mfcc_list, pitch_list};
	}
	
	/** unsupervised speaker counting algorithm without owner's calibration data */	
	public static int unsupervisedAlgorithm(List<SimpleMatrix> mfcc, List<Double> pitch) {

	  List<SimpleMatrix> new_mfcc = new ArrayList<SimpleMatrix>();
	  // admit the first segment as speaker 1
	  new_mfcc.add(mfcc.get(0));
	  List<Double> new_pitch = new ArrayList<Double>();
	  new_pitch.add(pitch.get(0));
		int speaker_count = 1;

	  for (int i = 1; i < mfcc.size(); i++) {
	  	int diff_count = 0;
	    for (int j = 0; j < speaker_count; j++) {
	    	// for each audio segment i, compare it with the each admitted audio segment j
	    	double mfcc_dist = getDistance(mfcc.get(i), new_mfcc.get(j));
	    	// different gender
	      if (genderDecision(pitch.get(i), new_pitch.get(j)) == 0) { 
	      	diff_count = diff_count + 1;
	      } 
	      // mfcc distance is larger than a threshold
	      else if (mfcc_dist >= Constants.mfcc_dist_diff_un) {
	      	diff_count = diff_count + 1;            	
	      }
	      // same speaker
	      else {
	      	if (mfcc_dist <= Constants.mfcc_dist_same_un && genderDecision(pitch.get(i), new_pitch.get(j)) == 1) {
		        new_mfcc.set(j, new_mfcc.get(j).combine(new_mfcc.get(j).numRows(), 0, mfcc.get(i))); // merge
						break;
		      }
	      }
	    }
	    // admit as a new speaker if different from all the admitted speakers.
	    if (diff_count == speaker_count) {
	    	speaker_count = speaker_count + 1;
	      new_mfcc.add(mfcc.get(i));
	      new_pitch.add(pitch.get(i));
	    }
	  }
	  return speaker_count;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	/** unsupervised speaker counting algorithm wrapper function */	
	public static int unsupervised(String[] test_files) throws java.io.IOException {

		List[] features = segmentation(test_files);
		
		if (features == null) {
			Log.i("SpeakerCount", "No enough audio data");
			return 0;
		}
		else {
			return unsupervisedAlgorithm(features[0], features[1]);
		}
	}

	/** semisupervised speaker counting algorithm with owner's calibration data */	
	public static double[] semisupervisedAlgorithm(SimpleMatrix trn_mfcc, double trn_pitch, List<SimpleMatrix> tst_mfcc, List<Double> tst_pitch) {
		
	  List<SimpleMatrix> new_mfcc = new ArrayList<SimpleMatrix>();
	  new_mfcc.add(trn_mfcc);
	  List<Double> new_pitch = new ArrayList<Double>();
	  new_pitch.add(trn_pitch);
		double speaker_count = 1;
		double speech_percentage = 0;
		double length = 0;
		String log;
		Log.i("Testing MFCC segments", Integer.toString(tst_mfcc.size()));
	  for (int i = 0; i < tst_mfcc.size(); i++) {
	  	int diff_count = 0;
	    for (int j = 0; j < speaker_count; j++) {
	    	// for each audio segment i, compare it with the each admitted audio segment j
	    	double mfcc_dist = getDistance(tst_mfcc.get(i), new_mfcc.get(j));
	  		Log.i("MFCC distance", Integer.toString(i) + "," + Integer.toString(j) + "," + Double.toString(mfcc_dist));
      	log = Integer.toString(i) + "," + Double.toString(tst_pitch.get(i)) + "," + Integer.toString(j) + "," + Double.toString(new_pitch.get(j));
      	Log.i("Pitch information", log);
	    	// different gender
	      if (genderDecision(tst_pitch.get(i), new_pitch.get(j)) == 0) { 
	      	diff_count++;
	    		Log.i("Different speaker", "based on gender");
	      } 
	      // mfcc distance is larger than a threshold
	      else if ( (j == 0 && mfcc_dist >= Constants.mfcc_dist_diff_semi) || (j > 0 && mfcc_dist >= Constants.mfcc_dist_diff_un) ) {
	      	diff_count++;
	    		Log.i("Different speaker", "based on MFCC");
	      }
	      // same speaker
	      else {
	        log = Integer.toString(j) + "," + mfcc_dist + "," + Constants.mfcc_dist_same_semi + "," + Double.toString(tst_pitch.get(i));
	    		Log.i("Maybe same speaker", log);
	      	if ( ((j == 0 && mfcc_dist <= Constants.mfcc_dist_same_semi) || (j > 0 && mfcc_dist <= Constants.mfcc_dist_same_un) ) && genderDecision(tst_pitch.get(i), new_pitch.get(j)) == 1) {
		        new_mfcc.set(j, new_mfcc.get(j).combine(new_mfcc.get(j).numRows(), 0, tst_mfcc.get(i))); // merge
		        log = Integer.toString(j) + "," + Integer.toString(tst_mfcc.get(i).numRows());
		    		Log.i("Merging", log);
						break;
		      }
	      }
	    }
	    // admit as a new speaker if different from all the admitted speakers.
	    if (diff_count == speaker_count) {
	    	speaker_count++;
	      new_mfcc.add(tst_mfcc.get(i));
	      new_pitch.add(tst_pitch.get(i));
	    }
      length += tst_mfcc.get(i).numRows();
	  }
	  
	  // don't count the owner if there is no voice from the owner in the conversation testing data
	  if (new_mfcc.get(0).numRows() == trn_mfcc.numRows())
	  	speaker_count--;
	  
		Log.i("Training length", Integer.toString(trn_mfcc.numRows()));
		Log.i("Testing length", Integer.toString(new_mfcc.get(0).numRows()));
		Log.i("Total length", Double.toString(length));

	  speech_percentage = 100 * (double) (new_mfcc.get(0).numRows() - trn_mfcc.numRows()) / length;
		Log.i("Speech percentage", Double.toString(speech_percentage));

	  return new double[] {speaker_count, speech_percentage};
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	/** semisupervised speaker counting algorithm wrapper function */	
	public static double[] semisupervised(String[] test_files, String[] cal_files) throws java.io.IOException {

		List[] tst_features = segmentation(test_files);
		
		if (tst_features == null) {
			Log.i("SpeakerCount", "No enough audio data");
			return new double[] {0, -1};
		}
		else {
			SimpleMatrix trn_mfcc = new SimpleMatrix(FileProcess.readFile(cal_files[0]));
			double trn_pitch = Maths.getColMean(FileProcess.readFile(cal_files[1]))[0];
			return semisupervisedAlgorithm(trn_mfcc, trn_pitch, tst_features[0], tst_features[1]);
		}
	}	
	
}