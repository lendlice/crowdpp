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
import edu.rutgers.winlab.crowdpp.db.DataBaseHelper;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * The social diary fragment 
 * @author Chenren Xu, Sugang Li
 */
public class SocialDiaryFragment extends Fragment {
	
	Button bt_date;
	TextView tv_record;
	
	DataBaseHelper mDatabase = null; 
	Cursor mCursor = null;
	SQLiteDatabase mDB = null;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.socialdiary_fragment_layout, container, false);				
		bt_date = (Button) view.findViewById(R.id.bt_socialdiary_date);
		tv_record = (TextView) view.findViewById(R.id.tv_socialdiary_record);
		tv_record.setMovementMethod(new ScrollingMovementMethod());
		
		mDatabase = new DataBaseHelper(getActivity().getApplicationContext());
		mDB = mDatabase.getWritableDatabase();
		
		// display the social diary based on the selected date
		bt_date.setOnClickListener(new  OnClickListener() {
			@Override
			public void onClick(View v) {
				final String[] dates = mDatabase.queryDatesInDiary(mDB);
				new AlertDialog.Builder(getActivity())
				.setTitle("Choose date")
				.setSingleChoiceItems(dates, 0, new DialogInterface.OnClickListener() {	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String date[] = new String[] {dates[which]};
						String diary = "Date\t\t\t\t\t\tTime\t\t\t\t\t\t#\t\t%\t\tLat\t\t\t\t\tLong\n";						
						mCursor = mDatabase.queryDiaryByDates(mDB, date);
						
						if (mCursor.moveToFirst()) {
							while (mCursor.isAfterLast() == false) {
								String record = mCursor.getString(0) + "\t\t\t" 
															+ mCursor.getString(1) + " - "  		+ mCursor.getString(2) + "\t\t\t" 
															+ mCursor.getString(3) + "\t\t\t\t" + mCursor.getString(4) + "\n";									
								diary = diary + record;						
								mCursor.moveToNext();
							}
						}
						tv_record.setText(diary);
						dialog.dismiss();
					}
				})
				.show();
			}
		});
		return view;
	}
	
}
