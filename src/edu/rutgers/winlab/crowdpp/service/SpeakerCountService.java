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

package edu.rutgers.winlab.crowdpp.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

import edu.rutgers.winlab.crowdpp.audio.SpeakerCount;
import edu.rutgers.winlab.crowdpp.audio.MFCC;
import edu.rutgers.winlab.crowdpp.audio.Yin;
import edu.rutgers.winlab.crowdpp.db.DataBaseHelper;
import edu.rutgers.winlab.crowdpp.sensor.LocationTracker;
import edu.rutgers.winlab.crowdpp.ui.HomeFragment;
import edu.rutgers.winlab.crowdpp.ui.MainActivity;
//import edu.rutgers.winlab.crowdpp.ui.test.MainActivity;
import edu.rutgers.winlab.crowdpp.util.Constants;
import edu.rutgers.winlab.crowdpp.util.FileProcess;
import edu.rutgers.winlab.crowdpp.util.Now;
import edu.rutgers.winlab.crowdpp.util.PhoneStatus;
import edu.rutgers.winlab.crowdpp.R;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * The SpeakerCountService class 
 * @author Chenren Xu, Sugang Li
 */
@SuppressLint("SimpleDateFormat")
public class SpeakerCountService extends Service {
	
	static boolean debug = false;

	private Timer speakerCountTimer;
	private Timer audioRecordTimer;
	
	private DataBaseHelper mDatabase; 
	private SQLiteDatabase mDB;
	
	private LocationTracker loc;

	// Wakelock prevents Crowdpp service from stopping
	private PowerManager.WakeLock wl;
	
	private AmazonS3Client s3Client;
	
	private SharedPreferences settings;
		
	private String wavFile;
	private File serviceDir;

	static boolean recording = false;
	
	// default values when the data is not available
	static int speaker_count = 0;
	static double percentage = -1;
	static double latitude = -1;
	static double longitude = -1;
	
	// read from settings
	static String start_hr, end_hr, interval_min, duration_min, location, upload;
		
	// for database insertion
	static long sys_time;
	static String date, start, end;
	static String curr_hr, curr_min, curr_date;
	public static final int NOTIFICATIN_ID = 100;
	private void showInfo(){ 
        NotificationManager manager = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE); 
                   //get the service of the notification                
               Notification mNotification = new Notification();  
               mNotification.icon = R.drawable.ic_launcher;
               mNotification.flags |=Notification.FLAG_ONGOING_EVENT;// is running           
               Intent intent = new Intent(this,MainActivity.class);
               intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);              
               PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
               mNotification.contentIntent = pendingIntent;
               mNotification.setLatestEventInfo(this, "Crowdpp4.0£º", "The Service is Running", pendingIntent);
           manager.notify(NOTIFICATIN_ID, mNotification);
        }

	@Override 
	public void onCreate() {
		Toast.makeText(this, "Speaker Count Service Started ", Toast.LENGTH_SHORT).show();
		serviceDir = new File(Constants.servicePath);
		showInfo();
		if (!serviceDir.exists() || !serviceDir.isDirectory()) {
			serviceDir.mkdir();
		}
		
		mDatabase = new DataBaseHelper(getApplicationContext());
		mDB = mDatabase.getWritableDatabase();
		
	  loc = new LocationTracker(getApplicationContext());

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "No sleep");
    wl.acquire();
    
    s3Client = new AmazonS3Client(new BasicAWSCredentials(Constants.ACCESS_KEY_ID, Constants.SECRET_KEY));
		s3Client.setRegion(Region.getRegion(Regions.US_WEST_2));		
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    settings 			= this.getSharedPreferences("config", Context.MODE_PRIVATE);
    start_hr 			= settings.getString("start", "");
    end_hr 				= settings.getString("end", ""); 
		interval_min	= settings.getString("interval", "");
		duration_min 	= settings.getString("duration", "");
		location 			= settings.getString("location", "");
		upload	 			=	settings.getString("upload", "");
		
		java.util.Date dt = new java.util.Date();
	  curr_hr = Now.getHour();
		curr_min = Now.getMinute();
		curr_date = Now.getDate();
		
		// immediately start in debug mode
		if (debug) {
			interval_min = "3"; 
			duration_min = "1";
		  int i = Integer.parseInt(curr_min);
		  if (i < 59) {
		  	dt.setHours(Integer.parseInt(curr_hr));		  	
		  	dt.setMinutes(i+1);
		  }
		  else if (i == 59) {
		  	dt.setHours(Integer.parseInt(curr_hr) + 1);
		  	dt.setMinutes(0);
		  }
		}
		else {
		  dt.setHours(Integer.parseInt(curr_hr) + 1);
		  dt.setMinutes(0);
		}
		
		String time = Now.getTime();
		Log.i("Crowd++", "Service will start at " + time);

	  long interval_ms = (long) (Integer.parseInt(interval_min) * 60 * 1000);
		speakerCountTimer = new Timer();
		speakerCountTimer.schedule(new SpeakerCountTask(), dt, interval_ms);
		return START_STICKY;
	}
	
	/** This timer executes every interval_ms in a periodic manner to record audio and count speakers. */
	private class SpeakerCountTask extends TimerTask {
		@Override
		public void run() {	
		  date = Now.getDate();
		  start = Now.getTimeOfDay();
		  curr_hr = Now.getHour();
		  Log.i("SpeakerCountTask", Integer.parseInt(curr_hr) + " is between " + Integer.parseInt(start_hr) + " and " + Integer.parseInt(end_hr) + "?");
			if (Integer.parseInt(curr_hr) >= Integer.parseInt(start_hr) && Integer.parseInt(curr_hr) < Integer.parseInt(end_hr)) {
				Log.i("SpeakerCountTask", "In period.");
				String filename = FileProcess.newFileOnTime("wav");
				wavFile = serviceDir + "/" + filename;
				Bundle mbundle = new Bundle();
				mbundle.putString("audiopath", wavFile);
				recording = true;
				// start audio recording
				Intent audioRecordIntent = new Intent(SpeakerCountService.this, AudioRecordService.class);
				audioRecordIntent.putExtras(mbundle);
				Log.i("SpeakerCountTask", "Recording");
				startService(audioRecordIntent);
			  long duration_ms = (long) (Integer.parseInt(duration_min) * 60 * 1000);
				audioRecordTimer = new Timer();
			  audioRecordTimer.schedule(new AudioRecordTask(), duration_ms);
			}
			else {
				Log.i("SpeakerCountTask", "Out of time period.");
			}
		}
	}

	/** This timer is nested inside the SpeakerCountTask. It is called after "duration_ms" recording and executes speak counting */
	private class AudioRecordTask extends TimerTask {
		@Override
		public void run() {
  		// stop audio recording
			Intent audioRecordIntent = new Intent(SpeakerCountService.this, AudioRecordService.class);
		  stopService(audioRecordIntent);
		  end = Now.getTimeOfDay();
	    recording = false;
	    
  		// get location information
		  if (location.equals("On")) {
		  	loc.getLocation();
			  if (loc.canGetLocation()){
			  	latitude = loc.getLatitude();
			  	longitude = loc.getLongitude();
			  } 
  		  else {
  		  	latitude  = -1;
  		  	longitude = -1;
  		  }			  
			  loc.stopUsingGPS();
		  }
		  
			// generate the MFCC and pitch feature data
			try {
				Yin.writeFile(wavFile);
				Log.i("SpeakerCountTask", "Finish YIN");
				MFCC.writeFile(wavFile);
				Log.i("SpeakerCountTask", "Finish MFCC");				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
			String[] tst_files = new String[2];
			tst_files[0] = wavFile + ".jstk.mfcc.txt";
			tst_files[1] = wavFile + ".YIN.pitch.txt";
			
			// semisupervised speaker counting with owner's calibration data 
	    if (Constants.calibration()) {
				String[] cal_files = new String[2];
				cal_files[0] = HomeFragment.calWavFile + ".jstk.mfcc.txt";
				cal_files[1] = HomeFragment.calWavFile + ".YIN.pitch.txt";
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
	  
			if (!Constants.service_raw_keep) {
				FileProcess.deleteFile(wavFile);
			}
			if (!Constants.service_feature_keep) {
				FileProcess.deleteFile(tst_files[0]);
				FileProcess.deleteFile(tst_files[1]);					
			}
			Log.i("SpeakerCount", Integer.toString(speaker_count));	
			
		  // log the service record 
			if (Constants.log) {
				Intent bIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
				String log	= wavFile + "\tDate:\t" + date + "\tstart:\t" + start + "\tend:\t" + end
										+ "\tspeaker count:\t" + Integer.toString(speaker_count) + "\tspeech percentage:\t" + Double.toString(percentage)
										+ "\tinterval (min):\t" + interval_min + "\tduration (min):\t" + duration_min
										+ "\tlatitude:\t" + Double.toString(latitude) + "\tlongitude:\t" + Double.toString(longitude)
										+ "\tbattery:\t" + Float.toString(PhoneStatus.getBatteryLevel(bIntent)) + "\n";
				File logFile = new File(serviceDir, "/" + "log.txt");
				FileOutputStream fos;
				try {
					fos = new FileOutputStream(logFile, true);
					fos.write(log.getBytes());
					fos.close();
					Log.i("SpeakerCountTask", log);					
				} catch (FileNotFoundException e) {
					e.printStackTrace();				
				} catch (IOException e) {
					e.printStackTrace();					
				}
			}
			
			// insert the record into the social diary table
			sys_time = System.currentTimeMillis();
			mDatabase.insertDiary(mDB, sys_time, date, start, end, speaker_count, percentage, latitude, longitude);
			
			// upload the database after the first speaker counting task done every day
			if (upload.equals("On")) {
				if (debug) {
					Log.i("Upload", "Begin");
			 		new S3PutDataBaseTask().execute();
					Log.i("Upload", "Finish");
			 	}
			 	if (!curr_date.equals(Now.getDate())) {
					Log.i("Upload", "Begin");
			 		new S3PutDataBaseTask().execute();
					Log.i("Upload", "Finish");
			 		curr_date = Now.getDate();
			 	}
			}
		}
	}

	/** Put the database into Amazon S3. */
	private class S3PutDataBaseTask extends AsyncTask<String, String, Integer> {

		protected void onPreExecute() {

		}	
		
		protected Integer doInBackground(String... params) {
			File dbFile = new File(getApplicationContext().getDatabasePath(DataBaseHelper.dbName).toString());
			try {
				PutObjectRequest por = new PutObjectRequest(Constants.calBucket, Constants.dbName, dbFile);  
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
	public void onDestroy()	{
		Log.i("Crowd++", "Service stop...");		
		Toast.makeText(this, "Crowd++ service stop...", Toast.LENGTH_SHORT).show();
		NotificationManager manager2 = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		manager2.cancelAll();
		if (recording) {
			Intent intent = new Intent(SpeakerCountService.this, AudioRecordService.class);
			stopService(intent);
			audioRecordTimer.cancel();
			FileProcess.deleteFile(wavFile);
			Log.i("AudioRecordTask", "Cancel");			
		}		
		speakerCountTimer.cancel();	
		Log.i("SpeakerCountTask", "Cancel");

		wl.release();
		if (mDB != null) {
			mDB.close();
		}
		if (mDatabase != null)	{
			mDatabase.close();
		}
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
}
