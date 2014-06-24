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

import edu.rutgers.winlab.crowdpp.audio.AudioRecorder;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * The AudioRecordService class 
 * @author Sugang Li, Chenren Xu
 */
public class AudioRecordService extends Service {
	AudioRecorder extAudioRecorder = null;
	
	@Override 
	public void onCreate()	{
		
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Bundle bundle = intent.getExtras();
		String filename = bundle.getString("audiopath");
		// Uncompressed recording (WAV)
		extAudioRecorder = AudioRecorder.getInstanse(false); 
    extAudioRecorder.setOutputFile(filename);  				
    extAudioRecorder.prepare();
    extAudioRecorder.start();
    Log.i("AudioRecordService", "Start audio recording");		    
		Toast.makeText(this, "Start audio recording...", Toast.LENGTH_SHORT).show();
		return START_STICKY;
	}
	
	@Override
	public void onDestroy()	{
    Log.i("AudioRecordService", "Stop audio recording");				
		Toast.makeText(this, "Stop audio recording...", Toast.LENGTH_SHORT).show();
		extAudioRecorder.stop();
		extAudioRecorder.release();
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
}