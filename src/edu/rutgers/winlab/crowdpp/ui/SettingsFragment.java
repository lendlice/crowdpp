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

import edu.rutgers.winlab.crowdpp.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The settings fragment 
 * @author Chenren Xu, Sugang Li
 */
public class SettingsFragment extends ListFragment {

	private SharedPreferences settings;
	SharedPreferences.Editor editor;

	String[] 		title, content, choice;
	String[] 		hours_arr				= new String[] 		{ "0:00", "1:00", "2:00", "3:00", "4:00", "5:00", 
																									"6:00", "7:00", "8:00", "9:00", "10:00", "11:00", 
																									"12:00", "13:00", "14:00", "15:00", "16:00", "17:00", 
																									"18:00", "19:00", "20:00", "21:00", "22:00", "23:00", "24:00"}; 
  String[] 		interval_arr 		= new String[] 		{	"10 Minutes", "15 Minutes", "20 Minutes", "30 Minutes", "60 Minutes"};
  String[] 		duration_arr 		= new String[] 		{	"2 Minutes", "3 Minutes", "5 Minutes", "8 Minutes"};
  String[][]	duration_arrs		= new String[][] {{	"2 Minutes", "3 Minutes", "4 Minutes", "5 Minutes"},
  																							{	"3 Minutes", "4 Minutes", "5 Minutes", "8 Minutes"},
  																							{	"3 Minutes", "5 Minutes", "8 Minutes", "10 Minutes"},
  																							{	"5 Minutes", "8 Minutes", "10 Minutes", "15 Minutes"},
  																							{	"10 Minutes", "15 Minutes", "20 Minutes", "30 Minutes"}};
  String[] 		location_enable	= new String[] 		{	"On", "Off"};
  String[] 		upload_enable  	= new String[] 		{	"On", "Off"};
  
  String temp_str;
  int temp_hour;
  int temp_interval;
  
	TextView tv_peroid, tv_interval, tv_duration, tv_location, tv_upload;
  
  @Override  
  public void onListItemClick(ListView l, final View v, int position, long id) {	
  	switch((int)id){
			case 0:   	
				new AlertDialog.Builder(getActivity())
				.setTitle("Start time")
				.setSingleChoiceItems(Arrays.copyOfRange(hours_arr, 0, hours_arr.length - 1), 0, new DialogInterface.OnClickListener() {	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						tv_peroid = (TextView) v.findViewById(R.id.tv_settings_choice);
						temp_str = hours_arr[which].substring(0, hours_arr[which].indexOf(':'));
						editor.putString("start", temp_str); 
						temp_str.concat(" - ");
						temp_hour = which;
						dialog.dismiss();
						final String[] temp_hours = Arrays.copyOfRange(hours_arr, temp_hour + 1, hours_arr.length);
						new AlertDialog.Builder(getActivity())
						.setTitle("End time")
						.setSingleChoiceItems(temp_hours, 0, new DialogInterface.OnClickListener() {	
							@Override
							public void onClick(DialogInterface dialog, int which) {
								editor.putString("end", temp_hours[which].substring(0, temp_hours[which].indexOf(':')));
								temp_str = temp_str.concat(" to ").concat(temp_hours[which].substring(0, temp_hours[which].indexOf(':')));
								tv_peroid.setText(temp_str);																		
								dialog.dismiss();
							  editor.commit();
						  	Toast.makeText(getActivity(), "You need to restart the service to apply these changes", Toast.LENGTH_SHORT).show();  																			
							}
						})
						.show();
					}
				})
				.show();
				break;

			case 1:
				new AlertDialog.Builder(getActivity())
				.setTitle(title[(int)id])
				.setSingleChoiceItems(interval_arr, 0, new DialogInterface.OnClickListener() {	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						temp_interval = which;
						tv_interval = (TextView) v.findViewById(R.id.tv_settings_choice);						
						temp_str = interval_arr[which].substring(0, interval_arr[which].indexOf(' '));
						tv_interval.setText(temp_str.concat(" Min"));
					  Log.i("Debug", temp_str.concat(" Min"));						
						editor.putString("interval", temp_str); 						
					  editor.commit();						
						dialog.dismiss();
				  	Toast.makeText(getActivity(), "You need to restart the service to apply these changes", Toast.LENGTH_SHORT).show();  																	
					}
				})
				.show(); 									
				break;
				
			case 2:
				new AlertDialog.Builder(getActivity())
				.setTitle(title[(int)id])
				.setSingleChoiceItems(duration_arrs[temp_interval], 0, new DialogInterface.OnClickListener() {	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						tv_duration = (TextView) v.findViewById(R.id.tv_settings_choice);
						temp_str = duration_arrs[temp_interval][which].substring(0, duration_arrs[temp_interval][which].indexOf(' '));
						tv_duration.setText(temp_str.concat(" Min"));
					  Log.i("Debug", temp_str.concat(" Min"));

						editor.putString("duration", temp_str); 						
					  editor.commit();							
						dialog.dismiss();	
				  	Toast.makeText(getActivity(), "You need to restart the service to apply these changes", Toast.LENGTH_SHORT).show();  											
					}
				})
				.show();			
				break;
				
			case 3:
				new AlertDialog.Builder(getActivity())
				.setTitle(title[(int)id])
				.setSingleChoiceItems(location_enable, 0, new DialogInterface.OnClickListener() {	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						tv_location = (TextView) v.findViewById(R.id.tv_settings_choice);
						temp_str = location_enable[which];
						tv_location.setText(temp_str);
					  Log.i("Debug", temp_str);
						editor.putString("location", temp_str); 						
					  editor.commit();
						dialog.dismiss();
				  	Toast.makeText(getActivity(), "You need to restart the service to apply these changes", Toast.LENGTH_SHORT).show();  																	
					}
				})
				.show();									
				break;	
				
			case 4:
				new AlertDialog.Builder(getActivity())
				.setTitle(title[(int)id])
				.setSingleChoiceItems(upload_enable, 0, new DialogInterface.OnClickListener() {	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						tv_upload = (TextView) v.findViewById(R.id.tv_settings_choice);
						temp_str = upload_enable[which];
						tv_upload.setText(temp_str);
						editor.putString("upload", temp_str); 						
					  editor.commit();							
						dialog.dismiss();		
				  	Toast.makeText(getActivity(), "You need to restart the service to apply these changes", Toast.LENGTH_SHORT).show();  																	
					}
				})
				.show();									
				break;			
			
			case 5:
				Intent webIntent = new Intent("android.intent.action.VIEW", Uri.parse("https://github.com/lendlice/crowdpp"));  
				startActivity(webIntent);  
				break;

			case 6:
				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { "chenren.xu@gmail.com" });
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Crowd++ bug report");
				sendIntent.setType("message/rfc822");
				startActivity(Intent.createChooser(sendIntent, "Send via"));
				break;
				
			default:
				break;
  	}
  }  

  @Override  
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { 
    settings = getActivity().getSharedPreferences("config", Context.MODE_PRIVATE);
    editor = settings.edit();

    title 	= new String[] {"Period", 
    												"Interval",
    												"Duration",
    												"Location",
    												"Upload",
    												"Project page",
    												"Contact me",
    												"Version"								};
    
    content	= new String[] {"Specify when you want the service run every day.", 
  		  										"Specify how frequently you want the service to run.", 
  		  										"Specify how long you want to record every time.",
  		  										"Specify if you want the location data be collected.",
  		  										"Specify if you want to contribute the data to the cloud.",
  		  										"Brings you to the project page. Please leave a comment.",
  		  										"Report a bug or send your comment via email.",
  		  										"1.0"};
    
    choice 	= new String[] {settings.getString("start", "").concat(" to ").concat(settings.getString("end", "")), 
    												settings.getString("interval", "").concat(" Min"),
    												settings.getString("duration", "").concat(" Min"),
    												settings.getString("location", ""),
    												settings.getString("upload", ""),	
    												"",
    												"",
    												""};

  	ArrayList<HashMap<String, String>> listItem = new ArrayList<HashMap<String, String>>();

  	for (int i = 0; i < title.length; i++) { 	
    	HashMap<String, String> map = new HashMap<String, String>();  
    	map.put("tv_settings_title", title[i]);  
      map.put("tv_settings_content", content[i]);
      map.put("tv_settings_choice", choice[i]);
      listItem.add(map);
    }
  	
  	// bind the listview adapter with the setting content
    SimpleAdapter mSimpleAdapter = new SimpleAdapter(inflater.getContext(), listItem, R.layout.settings_item,
        new String[] {"tv_settings_title", "tv_settings_content", "tv_settings_choice"},   
        new int[] {R.id.tv_settings_title, R.id.tv_settings_content, R.id.tv_settings_choice}  
    );

    setListAdapter(mSimpleAdapter);

   	return super.onCreateView(inflater, container, savedInstanceState);  
  }  

}
