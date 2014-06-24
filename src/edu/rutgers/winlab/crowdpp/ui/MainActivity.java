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
import edu.rutgers.winlab.crowdpp.util.Constants;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * The entry of Crowdpp app 
 * @author Chenren Xu, Sugang Li
 */
public class MainActivity extends FragmentActivity implements ActionBar.TabListener{

	AppSectionsPagerAdapter mAppSectionsPagerAdapter;
	
	ViewPager mViewPager;
	
	Constants mConst;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	  setContentView(R.layout.main_activity_layout);

	  // Create the adapter that will return a fragment for each of the three primary sections of the app.
	  mAppSectionsPagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());

	  // Set up the action bar.
	  final ActionBar actionBar = getActionBar();

	  // Specify that the Home/Up button should not be enabled, since there is no hierarchical parent.
	  actionBar.setHomeButtonEnabled(false);

	  // Specify that we will be displaying tabs in the action bar.
	  actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

	  // Set up the ViewPager, attaching the adapter and setting up a listener for when the user swipes between sections.
	  mViewPager = (ViewPager) findViewById(R.id.pager);
	  mViewPager.setAdapter(mAppSectionsPagerAdapter);
	  mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
	  	@Override
	  	public void onPageSelected(int position) {
	  		// When swiping between different app sections, select the corresponding tab.
	  		// We can also use ActionBar.Tab#select() to do this if we have a reference to the Tab.
	  		actionBar.setSelectedNavigationItem(position);
	  	}
	  });

	  // For each of the sections in the app, add a tab to the action bar.
	  for (int i = 0; i < mAppSectionsPagerAdapter.getCount(); i++) {
	  	// Create a tab with text corresponding to the page title defined by the adapter.
	    // Also specify this Activity object, which implements the TabListener interface, as the listener for when this tab is selected.
	    actionBar.addTab(actionBar.newTab().setText(mAppSectionsPagerAdapter.getPageTitle(i)).setTabListener(this));
	  }
	  
		SharedPreferences settings = this.getSharedPreferences("config", Context.MODE_PRIVATE);;
		SharedPreferences.Editor editor = settings.edit();
    
		// load the default parameters into SharedPreferences for the first time launch 
    int ct = settings.getInt("count", 0);
    if (ct == 0) {
    	editor.putString("start", "9"); 
    	editor.putString("end", "21");   	
    	editor.putString("interval", "15");   	
    	editor.putString("duration", "5");   	
    	editor.putString("location", "On");
    	editor.putString("upload", "On");
    	TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    	editor.putString("IMEI", tm.getDeviceId());
    	editor.putString("brand", Build.BRAND);
    	editor.putString("model", Build.MODEL); 
    	String phone_type = Build.BRAND + "_" + Build.MODEL;
    	
    	// motoX
    	if (phone_type.equals("motorola_XT1058")) {
      	editor.putString("mfcc_dist_same_semi", "13");
      	editor.putString("mfcc_dist_diff_semi", "18");
      	editor.putString("mfcc_dist_same_un", "13");
      	editor.putString("mfcc_dist_diff_un", "18");
    	}
    	// nexus 4
    	else if (phone_type.equals("google_Nexus 4")) {
      	editor.putString("mfcc_dist_same_semi", "17");
      	editor.putString("mfcc_dist_diff_semi", "22");
      	editor.putString("mfcc_dist_same_un", "17");
      	editor.putString("mfcc_dist_diff_un", "22");
    	}
    	// s2
    	else if (phone_type.equals("samsung_SAMSUNG-SGH-I727")) {
      	editor.putString("mfcc_dist_same_semi", "18");
      	editor.putString("mfcc_dist_diff_semi", "25");
      	editor.putString("mfcc_dist_same_un", "18");
      	editor.putString("mfcc_dist_diff_un", "25");
    	}
    	// s3 
    	else if (phone_type.equals("samsung_SAMSUNG-SGH-I747")) {
      	editor.putString("mfcc_dist_same_semi", "16");
      	editor.putString("mfcc_dist_diff_semi", "21");
      	editor.putString("mfcc_dist_same_un", "16");
      	editor.putString("mfcc_dist_diff_un", "21");
    	}
    	// s4
    	else if (phone_type.equals("samsung_SAMSUNG-SGH-I337")) {
      	editor.putString("mfcc_dist_same_semi", "14");
      	editor.putString("mfcc_dist_diff_semi", "24");
      	editor.putString("mfcc_dist_same_un", "14");
      	editor.putString("mfcc_dist_diff_un", "24");
    	}
    	// other devices
    	else {
      	editor.putString("mfcc_dist_same_semi", "15.6");
      	editor.putString("mfcc_dist_diff_semi", "21.6");
      	editor.putString("mfcc_dist_same_un", "15.6");
      	editor.putString("mfcc_dist_diff_un", "21.6");
    		Toast.makeText(this, "Your device is not recognized and the result might not be accurate...", Toast.LENGTH_SHORT).show();
    	}
  	  Log.i("Crowd++", "First time launched");

  		AlertDialog dialog = new AlertDialog.Builder(this).create(); 
      dialog.setTitle("Welcome to Crowd++");
      dialog.setMessage(Constants.hello_msg);
  		dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Close", new DialogInterface.OnClickListener() {
  			@Override
  			public void onClick(DialogInterface dialog, int which) {
  				
  			}
  		});
  		dialog.show();        				
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(20);  	  
    }
    
		editor.putInt("count", ++ct);
		editor.commit();
	  Log.i("Launched Count", Integer.toString(ct));
	  mConst = new Constants(this);
	  if (!Constants.calibration())
  		Toast.makeText(this, "You haven't calibrated the system.", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onTabReselected(Tab arg0, FragmentTransaction arg1) {

	}

	@Override
	public void onTabSelected(Tab arg0, FragmentTransaction arg1) {
		mViewPager.setCurrentItem(arg0.getPosition());	
	}

	@Override
	public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {

	}
	
	public static class AppSectionsPagerAdapter extends FragmentPagerAdapter {

		public AppSectionsPagerAdapter(FragmentManager fm) {
			super(fm);
    }

    @Override
    public Fragment getItem(int i) {
    	switch (i) {
      	case 0:
      		return new HomeFragment();
        case 1:
         	return new SocialDiaryFragment();
        case 2:
          return new SettingsFragment();
        default:
          return new HomeFragment();
      }
    }

    @Override
    public int getCount() {
    	return 3;
    }

    @Override
    public CharSequence getPageTitle(int position) {
    	switch(position){
      	case 0:
      		return "Home";
        case 1:
        	return "Social Diary";
        case 2:
        	return "Settings";
        default:
        	return "More";
      }
    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.control_activity_menu, menu);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {  
    switch (item.getItemId()) {  
    	// exit the app
    	case R.id.exit:
  			Toast.makeText(this, "Closing...", Toast.LENGTH_SHORT).show();
    		//System.exit(0);
				this.finish();
				break;
			// show the FAQ 
    	case R.id.help:
    		AlertDialog dialog = new AlertDialog.Builder(this).create(); 
        dialog.setTitle("FAQ");
        dialog.setMessage(Constants.FAQ);
				dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Close", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				dialog.show();        				
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(20);
    		break;
      default:  
      	return super.onOptionsItemSelected(item);  
    }
		return true;
	}
	
	// exit the app with backpressed
	@Override  
	public void onBackPressed() {
		super.onBackPressed();   
		Toast.makeText(this, "Closing...", Toast.LENGTH_SHORT).show();
		this.finish();
	}
	
}
