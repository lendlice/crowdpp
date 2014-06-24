package edu.rutgers.winlab.crowdpp.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.ejml.simple.SimpleMatrix;

import edu.rutgers.winlab.crowdpp.audio.SpeakerCount;
import edu.rutgers.winlab.crowdpp.util.FileProcess;

public class MicrophoneCalibration {
	
	// compute the self distance and other distance for same length
	public static void crossAsymmetricCalibration(String[] paths) throws java.io.IOException {

		for (int p = 0; p < paths.length; p++) {
			final File directory = new File(paths[p]);
			if (!directory.exists() || !directory.isDirectory()) {
				System.out.println("Directory not found");
			}
			
			ArrayList<String> mfccFilenames = new ArrayList<String>();
	
			for(final String filename : directory.list()){
				if (filename.endsWith("jstk.mfcc.txt")) {
			  	mfccFilenames.add(filename);
			  }
			}
			File self_f = new File(paths[p] + "/distance_self_asymmetric.txt");
			FileOutputStream self_fos = new FileOutputStream(self_f, true);
			File other_f = new File(paths[p] + "/distance_other_asymmetric.txt");
			FileOutputStream other_fos = new FileOutputStream(other_f, true);

			double[] cal_duration_sec = new double[] {30, 45, 60};
			
			for (int j = 0; j < cal_duration_sec.length; j++) {
				int cal_end_id = (int) Math.round(cal_duration_sec[j] / 0.016) - 1;
				// read 45 seconds data from different 
				List<SimpleMatrix> mfcc_cal_list = new ArrayList<SimpleMatrix>();
				for (int k = 0; k < mfccFilenames.size(); k++) {
					double[][] mfcc 	= FileProcess.readFile(paths[p] + "/" + mfccFilenames.get(k));
					SimpleMatrix mfcc_mat = new SimpleMatrix(mfcc);
					mfcc_cal_list.add(mfcc_mat.extractMatrix(0, cal_end_id, 0, 19)); 				
				}
				
				// vary the segment duration from 1 to 8 seconds
				for (int l = 1; l <= 8; l++) {
				  System.out.println ("Segment length:\t" + l);
					ArrayList<ArrayList<SimpleMatrix>> mfcc_matrices = new ArrayList<ArrayList<SimpleMatrix>>();
					for (int k = 0; k < mfccFilenames.size(); k++) {
						double[][] mfcc 	= FileProcess.readFile(paths[p] + "/" + mfccFilenames.get(k));		
						int sample_num 		= mfcc.length;
						double[] time			= new double[sample_num]; 
						time[0] = 0.032;
						for(int i = 1; i < time.length; i++) {
							time[i] = time[i-1] + 0.016;
						}
						
						int end_id = sample_num - 1;
						int seg_num = (int) Math.floor(time[end_id] / l);
						
						int[] lower_id = new int [seg_num];
						int[] upper_id = new int [seg_num];
	
						lower_id[0] = 0;
						int seg_id = 1;
							
						for (int i = 0; i < sample_num; i++) {
							if (time[i] <= (double)(seg_id * l) && time[i+1] > (double)(seg_id * l)) {
								upper_id[seg_id-1] = i;
								seg_id++;
								if (seg_id == seg_num + 1) 
									break;
								lower_id[seg_id-1] = i + 1;
							}
						}
						
						SimpleMatrix mfcc_mat = new SimpleMatrix(mfcc);
						List<SimpleMatrix> mfcc_list = new ArrayList<SimpleMatrix>();
						for(int i = 0; i < seg_num; i++) {
							mfcc_list.add(mfcc_mat.extractMatrix(lower_id[i], upper_id[i], 0, 19)); 
						}
					  System.out.println (mfcc_list.size());
						mfcc_matrices.add((ArrayList<SimpleMatrix>) mfcc_list);
					}
					
					// take segments from 80 second data
					int[] segment_nums = new int[] {80, 40, 25, 20, 16, 10, 10, 10};
					
					// a and c are the indices of speaker id, while b and d are the indices of segment id
					for (int a = 0; a < 10; a++) {
						for (int c = 0; c < 10; c++) {
							for (int d = 0; d < segment_nums[l-1]; d++) {		
								double dist = SpeakerCount.getDistance(mfcc_cal_list.get(a), mfcc_matrices.get(c).get(d));
								String text = Double.toString(cal_duration_sec[j]) + "\t" + Integer.toString(l) + "\t" + Double.toString(dist) + "\n";
								if (a == c) {
									self_fos.write(text.getBytes());
								}
								else if (a != c) {
									other_fos.write(text.getBytes());
								}
							}
						}
					}
				}
			}
			self_fos.close();
			other_fos.close();
		}
		return;
	}	
	
	// compute the self distance and other distance for same length
	public static void crossSymmetricCalibration(String[] paths) throws java.io.IOException {

		for (int p = 0; p < paths.length; p++) {
			final File directory = new File(paths[p]);
			if (!directory.exists() || !directory.isDirectory()) {
				System.out.println("Directory not found");
			}
			
			ArrayList<String> mfccFilenames = new ArrayList<String>();
	
			for(final String filename : directory.list()){
				if (filename.endsWith("jstk.mfcc.txt")) {
			  	mfccFilenames.add(filename);
			  }
			}
			File self_f = new File(paths[p] + "/distance_self_symmetric.txt");
			FileOutputStream self_fos = new FileOutputStream(self_f, true);
			File other_f = new File(paths[p] + "/distance_other_symmetric.txt");
			FileOutputStream other_fos = new FileOutputStream(other_f, true);

			// vary the segment duration from 1 to 8 seconds
			for (int l = 1; l <= 8; l++) {
			  System.out.println ("Segment length:\t" + l);
				ArrayList<ArrayList<SimpleMatrix>> mfcc_matrices = new ArrayList<ArrayList<SimpleMatrix>>();
				for(int k = 0; k < mfccFilenames.size(); k++) {
					double[][] mfcc 	= FileProcess.readFile(paths[p] + "/" + mfccFilenames.get(k));		
					int sample_num 		= mfcc.length;
					double[] time			= new double[sample_num]; 
					time[0] = 0.032;
					for(int i = 1; i < time.length; i++) {
						time[i] = time[i-1] + 0.016;
					}
					
					int end_id = sample_num - 1;
					int seg_num = (int) Math.floor(time[end_id] / l);
					
					int[] lower_id = new int [seg_num];
					int[] upper_id = new int [seg_num];

					lower_id[0] = 0;
					int seg_id = 1;
						
					for (int i = 0; i < sample_num; i++) {
						if (time[i] <= (double)(seg_id * l) && time[i+1] > (double)(seg_id * l)) {
							upper_id[seg_id-1] = i;
							seg_id++;
							if (seg_id == seg_num + 1) 
								break;
							lower_id[seg_id-1] = i + 1;
						}
					}
					
					SimpleMatrix mfcc_mat = new SimpleMatrix(mfcc);
					List<SimpleMatrix> mfcc_list = new ArrayList<SimpleMatrix>();
					for(int i = 0; i < seg_num; i++) {
						mfcc_list.add(mfcc_mat.extractMatrix(lower_id[i], upper_id[i], 0, 19)); 
					}
				  System.out.println (mfcc_list.size());
					mfcc_matrices.add((ArrayList<SimpleMatrix>) mfcc_list);
				}
				
				// take segments from 80 second data
				int[] segment_nums = new int[] {80, 40, 25, 20, 16, 10, 10, 10}; 				
				
				// a and c are the indices of speaker id, while b and d are the indices of segment id
				for(int a = 0; a < 10; a++) {
					for(int b = 0; b < segment_nums[l-1]; b++) {
						for(int c = 0; c < 10; c++) {
							for(int d = 0; d < segment_nums[l-1]; d++) {
								double dist = SpeakerCount.getDistance(mfcc_matrices.get(a).get(b), mfcc_matrices.get(c).get(d));
								String text = Integer.toString(l) + "\t" + Double.toString(dist) + "\n";
								if (a == c && b != d) {
									self_fos.write(text.getBytes());
								}
								else if (a != c) {
									other_fos.write(text.getBytes());
								}
							}
						}
					}
				}
			}
			self_fos.close();
			other_fos.close();
		}
		return;
	}
	
}
