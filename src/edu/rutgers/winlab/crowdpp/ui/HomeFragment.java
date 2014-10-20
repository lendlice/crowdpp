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

package edu.rutgers.winlab.crowdpp.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

import edu.rutgers.winlab.crowdpp.R;
import edu.rutgers.winlab.crowdpp.audio.SpeakerCount;
import edu.rutgers.winlab.crowdpp.audio.MFCC;
import edu.rutgers.winlab.crowdpp.audio.Yin;
import edu.rutgers.winlab.crowdpp.db.DataBaseHelper;
import edu.rutgers.winlab.crowdpp.sensor.LocationTracker;
import edu.rutgers.winlab.crowdpp.service.AudioRecordService;
import edu.rutgers.winlab.crowdpp.service.SpeakerCountService;
import edu.rutgers.winlab.crowdpp.util.Constants;
import edu.rutgers.winlab.crowdpp.util.FileProcess;
import edu.rutgers.winlab.crowdpp.util.Now;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * The home fragment 
 * @author Chenren Xu, Sugang Li
 */
public class HomeFragment extends Fragment {

	private ToggleButton tb_cal, tb_service, tb_test;
	private TextView tv_cal_content, tv_cal_text, tv_debug, tv_record;
	private Chronometer timer_cal, timer_test;
	private RelativeLayout rl_service, rl_test;
	private File crowdppDir, testDir; 	
	private LocationTracker gps = null;
	
	public static String calWavFile;
	private String testWavFile;
	
	private long sys_time;
	private String date, start, end;
	private int speaker_count; 
	
	// default values when the data is not available
	private double percentage = -1;
	private double latitude = -1;
	private double longitude = -1;	
	
	private String test_log;
	
	private DataBaseHelper mDatabase = null; 
	private Cursor mCursor = null;
	private SQLiteDatabase mDB = null;
	
	private AmazonS3Client s3Client;
	private boolean isMyServiceRunning() {
		//final View view = inflater.inflate(R.layout.home_fragment_layout, container, false);	
	    ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if ("edu.rutgers.winlab.crowdpp.service.SpeakerCountService".equals(service.service.getClassName())) {
	            return true;
	            
	        }
	    }
	    return false;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.home_fragment_layout, container, false);				
		
		tb_cal = (ToggleButton) view.findViewById(R.id.tb_home_calibration);
		tb_service = (ToggleButton) view.findViewById(R.id.tb_home_service);
		tb_test = (ToggleButton) view.findViewById(R.id.tb_home_test);
		tv_cal_content = (TextView) view.findViewById(R.id.tv_home_calibration_content);
		tv_cal_text = (TextView) view.findViewById(R.id.tv_home_calibration_text);
		tv_debug = (TextView) view.findViewById(R.id.tv_home_test_debug);
		tv_record = (TextView) view.findViewById(R.id.tv_home_test_record);
		timer_cal = (Chronometer) view.findViewById(R.id.timer_calibration);		
		timer_test = (Chronometer) view.findViewById(R.id.timer_test);
		rl_service = (RelativeLayout) view.findViewById(R.id.rl_home_service);	    	
		rl_test = (RelativeLayout) view.findViewById(R.id.rl_home_test);	
		if(isMyServiceRunning()==true)
			tb_service.setChecked(true);

    if (Constants.calibration())
    	tv_cal_content.setText("You are all set for the calibration.");
    
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			Toast.makeText(getActivity(), "Can not find SD card ...", Toast.LENGTH_SHORT).show();			
			getActivity().finish();
		}

		crowdppDir = new File(Constants.crowdppPath);
		if (!crowdppDir.exists() || !crowdppDir.isDirectory()) {
			crowdppDir.mkdir();
		} 

		testDir = new File(Constants.testPath);
		if (!testDir.exists() || !testDir.isDirectory()) {
			testDir.mkdir();
		}	
		
		calWavFile = crowdppDir + "/" + Constants.PHONE_ID + ".wav";

		timer_cal.setVisibility(View.INVISIBLE);
		timer_test.setVisibility(View.INVISIBLE);
	  
		mDatabase = new DataBaseHelper(getActivity().getApplicationContext());
		mDB = mDatabase.getWritableDatabase();

		gps = new LocationTracker(getActivity().getApplicationContext());
				
    s3Client = new AmazonS3Client(new BasicAWSCredentials(Constants.ACCESS_KEY_ID, Constants.SECRET_KEY));
		s3Client.setRegion(Region.getRegion(Regions.US_WEST_2));		

		tb_cal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	// prevent the calibration button being pressed when either and test or service is running 
	    	if (tb_service.isChecked()) {
	  			Toast.makeText(getActivity(), "Please turn off the service...", Toast.LENGTH_SHORT).show();		
	  			tb_cal.setChecked(false);
	    	}
	    	else if (tb_test.isChecked()) {
	  			Toast.makeText(getActivity(), "Please turn off the test...", Toast.LENGTH_SHORT).show();		
	  			tb_cal.setChecked(false);	  			
	    	}	
	    	// calibration
	    	else {
		    	if (isChecked) {
		    		tv_cal_text.setText(Constants.cal_text);
		    		rl_service.setVisibility(View.INVISIBLE);
		    		rl_test.setVisibility(View.INVISIBLE);	 
		    		timer_cal.setVisibility(View.VISIBLE);
		    		
		    		AlertDialog dialog = new AlertDialog.Builder(getActivity()).create(); 
		        dialog.setTitle("Calibration");
		        dialog.setMessage(Constants.cal_dialog);
						dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
				    		timer_cal.setBase(SystemClock.elapsedRealtime());
				    		timer_cal.start();
				      	tv_cal_content.setText("Recording your voice...");

				  			Bundle mbundle = new Bundle();
				  			mbundle.putString("audiopath", calWavFile);
				  			Log.i("HomeFragment", "start audio recording service");
				  			
				  			// delete the existing calibration data before the recalibration 
				  			FileProcess.deleteFile(calWavFile);
				  			FileProcess.deleteFile(calWavFile + ".jstk.mfcc.txt");
				  			FileProcess.deleteFile(calWavFile + ".YIN.pitch.txt");
				  			
				  			// start audio recording
				  			Intent recordIntent = new Intent(getActivity(), AudioRecordService.class);
				  			recordIntent.putExtras(mbundle);
				  			getActivity().startService(recordIntent);		    				    		
							}
						});
						dialog.show();        				
		        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(20);		    		
					} 
		    	else {
		    		// stop audio recording
		  			Intent recordIntent = new Intent(getActivity(), AudioRecordService.class);
		  			getActivity().stopService(recordIntent);
		  			timer_cal.stop();
		  			tb_cal.setClickable(false);
		      	tv_cal_content.setText("Calibrating....");
		      	// start calibration
		  			new Calibration().execute();
		  			tb_cal.setClickable(true);
		    		tv_cal_text.setText("");
		    		rl_service.setVisibility(View.VISIBLE);
		    		rl_test.setVisibility(View.VISIBLE);	  	    	
		    		timer_cal.setVisibility(View.INVISIBLE);
					}
	    	}
	    }
		});

		tb_service.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	// prevent the service button being pressed when either and test or calibration is running 	    	
	    	if (tb_cal.isChecked()) {
	  			Toast.makeText(getActivity(), "Please turn off the calibration...", Toast.LENGTH_SHORT).show();		
	  			tb_service.setChecked(false);
	    	}
	    	else if (tb_test.isChecked()) {
	  			Toast.makeText(getActivity(), "Please turn off the test...", Toast.LENGTH_SHORT).show();		
	  			tb_service.setChecked(false);	  			
	    	}
	    	// speaker counting service
	    	else {
					Intent countIntent = new Intent(getActivity(), SpeakerCountService.class);	    	
		    	if (isChecked) {
					  getActivity().startService(countIntent);
					} 
		    	else {
						getActivity().stopService(countIntent);
					}
	    	}
	    }
		});		
		
		tb_test.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	// prevent the test button being pressed when either and service or calibration is running 	    	
	    	if (tb_cal.isChecked()) {
	  			Toast.makeText(getActivity(), "Please turn off the calibration...", Toast.LENGTH_SHORT).show();		
	  			tb_test.setChecked(false);
	    	}
	    	else if (tb_service.isChecked()) {
	  			Toast.makeText(getActivity(), "Please turn off the service...", Toast.LENGTH_SHORT).show();		
	  			tb_test.setChecked(false);	  			
	    	}	 
	    	// perform the speaker counting test
	    	else {
		    	if (isChecked) {
		    		// get location information
		  		  gps.getLocation();
		  		  if (gps.canGetLocation()) {
		  		  	latitude = gps.getLatitude();
		  		  	longitude = gps.getLongitude();
		  		  } 
		  		  else {
		  		  	latitude  = -1;
		  		  	longitude = -1;
		  		  }
		  	    gps.stopUsingGPS();
		  	    
		    		timer_test.setBase(SystemClock.elapsedRealtime());
		    		timer_test.start();
		    		tv_debug.setText("Recording...");
		  			testWavFile = testDir + "/" + FileProcess.newFileOnTime("wav");
		  			// start audio recording
		  			Bundle mbundle = new Bundle();
		  			mbundle.putString("audiopath", testWavFile);
		  			Log.i("HomeFragment", "start audio recording service");
		  			Intent recordIntent = new Intent(getActivity(), AudioRecordService.class);
		  			recordIntent.putExtras(mbundle);
		  			date = Now.getDate();
		  			start = Now.getTimeOfDay();
		  			getActivity().startService(recordIntent);
		  			timer_test.setVisibility(View.VISIBLE);
		    	} 
		    	else {	
		  			// stop audio recording
		  			Intent recordIntent = new Intent(getActivity(), AudioRecordService.class);
		  			getActivity().stopService(recordIntent);
		  			timer_test.stop();
		  			end = Now.getTimeOfDay();
		  			tb_test.setClickable(false);
		  			// start speaker counting test
		  			new Test().execute();
		  			timer_test.setVisibility(View.INVISIBLE);
		  			tb_test.setClickable(true);		  			
		      }
	    	}
	    }
		});
		return view;
	}

	/** Calibration task. */
	class Calibration extends AsyncTask<String, String, Integer> {
		@Override
		protected Integer doInBackground(String... arg0) {
			// generate the MFCC and pitch feature data
			try {
				Yin.writeFile(calWavFile);
				Log.i("SpeakerCountTask", "Finish YIN");
				MFCC.writeFile(calWavFile);
				Log.i("SpeakerCountTask", "Finish MFCC");
				// calibration succeeded with enough audio data
				if (SpeakerCount.selfCalibration(calWavFile)) {
					return 1;
				}
				// calibration failed without enough audio data
				else {
					Log.i("CalibrationTask", "Failed");
					return 0;
				}
		  } catch (IOException e) {
		  	e.printStackTrace();
				return 0;		  	
			} catch (Exception e) {
				e.printStackTrace();
				return 0;		  	
			}
		}
		
    @Override
    protected void onProgressUpdate(String... values) {

    }

    @Override
    protected void onCancelled(Integer result) {
    	
    }
      
    @Override
    protected void onPreExecute() {
    	
    }

    @Override
    protected void onPostExecute(Integer result) {
    	if (result == 1) {
  			Toast.makeText(getActivity(), "Congratulation! You are all set for the calibration.", Toast.LENGTH_SHORT).show();		
      	tv_cal_content.setText("You are all set for the calibration.");
		 		new S3PutDataBaseTask().execute(); 
    	}
    	else if (result == 0) {
  			Toast.makeText(getActivity(), "Calibration data is not sufficient (less than 30 seconds), please do it again...", Toast.LENGTH_SHORT).show();	
      	tv_cal_content.setText("Crowd++ needs your voice to calibrate the system.");
    	}
    }
	}
	
	/** Speaker counting task. */
	class Test extends AsyncTask<String, String, Integer> {
		@Override
		protected Integer doInBackground(String... arg0) {
			// generate the MFCC and pitch feature data
			try {
				Yin.writeFile(testWavFile);
				Log.i("SpeakerCountTask", "Finish YIN");
				MFCC.writeFile(testWavFile);
				Log.i("SpeakerCountTask", "Finish MFCC");				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
			String[] tst_files = new String[2];
			tst_files[0] = testWavFile + ".jstk.mfcc.txt";
			tst_files[1] = testWavFile + ".YIN.pitch.txt";
			
			// semisupervised speaker counting with owner's calibration data 
		  if (Constants.calibration()) {
				String[] cal_files = new String[2];
				cal_files[0] = calWavFile + ".jstk.mfcc.txt";
				cal_files[1] = calWavFile + ".YIN.pitch.txt";
				try {
					double rv[] = SpeakerCount.semisupervised(tst_files, cal_files);
					speaker_count = (int)rv[0];
					percentage = rv[1];	
				} catch (IOException e) {
					e.printStackTrace();
				}
	    }
			// unsupervised speaker counting without calibration data 
			else {
				try {
					speaker_count = SpeakerCount.unsupervised(tst_files);
					percentage = -1;					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		  
		  // log the test record 
			if (Constants.log) {
				String log	= testWavFile + "\tDate:\t" + date + "\tstart:\t" + start + "\tend:\t" + end
										+ "\tspeaker count:\t" + Integer.toString(speaker_count) + "\tspeach percentage:\t" + Double.toString(percentage)
										+ "\tlatitude:\t" + Double.toString(latitude) + "\tlongitude:\t" + Double.toString(longitude) + "\n";			
				
				File sdFile = new File(testDir, "log.txt");
				FileOutputStream fos;
				try {
					fos = new FileOutputStream(sdFile, true);
					fos.write(log.getBytes());
					fos.close();				
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// insert the record into the test table
			sys_time = System.currentTimeMillis();
			mDatabase.insertTest(mDB, sys_time, date, start, end, speaker_count, percentage, latitude, longitude);
			test_log = "Recent ten records:\n";
			mCursor = mDatabase.queryTest(mDB);
			if (mCursor.moveToFirst()) {
				test_log = test_log + "Date\t\t\t\t\t\t\t\tTime\t\t\t\t\t\t\t\t#\t\t\t\t%\n";
				int record_num = 0;
				while (mCursor.isAfterLast() == false) {
					String record = mCursor.getString(0) + "\t\t\t" 
												+ mCursor.getString(1) + " - "  + mCursor.getString(2) + "\t\t\t" 
												+ mCursor.getString(3) + "\t\t\t\t" + mCursor.getString(4) + "\n";
					test_log = test_log + record;						
					mCursor.moveToNext();
					record_num++;
					if (record_num == 10)
						break;
				}					
			}
			Log.i("SpeakerCountTask", "Finish writing file");
			
			if (!Constants.test_raw_keep) {
				FileProcess.deleteFile(testWavFile);
			}
			if (!Constants.test_feature_keep) {
				FileProcess.deleteFile(tst_files[0]);
				FileProcess.deleteFile(tst_files[1]);					
			}
			
			return speaker_count;
		}
		
    @Override
    protected void onProgressUpdate(String... values) {
    	tv_debug.setText("Counting...");
			Log.i("SpeakCountTask", "Counting");
    }

    @Override
    protected void onCancelled(Integer result) {
			Log.i("SpeakCountTask", "Cancelled");
    }
      
    @Override
    protected void onPreExecute() {
			Log.i("SpeakCountTask", "Begin to count");
    }

    @Override
    protected void onPostExecute(Integer result) {
    	tv_debug.setText("Speaker count: " + result);
    	tv_record.setText(test_log);    	
    }
	}

	/** Put the calibration data into Amazon S3. */
	private class S3PutDataBaseTask extends AsyncTask<String, String, Integer> {

		protected void onPreExecute() {

		}	
		
		protected Integer doInBackground(String... params) {
			File wavFile = new File(calWavFile);
			try {
				PutObjectRequest por = new PutObjectRequest(Constants.dbBucket, Constants.PHONE_ID + ".wav", wavFile);  
				s3Client.putObject(por);
			} catch (Exception exception) {
				Log.e("Upload", "Error");
				return 0;
			}
			
			File mfccFile = new File(calWavFile + ".jstk.mfcc.txt");
			try {
				PutObjectRequest por = new PutObjectRequest(Constants.dbBucket, Constants.PHONE_ID + ".jstk.mfcc.txt", mfccFile);  
				s3Client.putObject(por);
			} catch (Exception exception) {
				Log.e("Upload", "Error");
				return 0;
			}
			
			File pitchFile = new File(calWavFile + ".YIN.pitch.txt");
			try {
				PutObjectRequest por = new PutObjectRequest(Constants.dbBucket, Constants.PHONE_ID + ".YIN.pitch.txt", pitchFile);  
				s3Client.putObject(por);
			} catch (Exception exception) {
				Log.e("Upload", "Error");
				return 0;
			}
			return 1;
		}
	
		protected void onPostExecute(Integer result) {	

		}
	}
	
	@Override
	public void onResume() {
	  super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onDestroy() {
		if (mDB != null) {
			mDB.close();
		}
		if (mDatabase != null)	{
			mDatabase.close();
		}
		Log.i("HomeFragment", "Destroying HomeFragment");
		super.onDestroy();		
	}
	
}
