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

package edu.rutgers.winlab.crowdpp.db;

import edu.rutgers.winlab.crowdpp.db.DataBaseTable.DiaryTable;
import edu.rutgers.winlab.crowdpp.db.DataBaseTable.TestTable;
import edu.rutgers.winlab.crowdpp.util.Constants;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * The DataBaseHelper class 
 * @author Chenren Xu
 */
public class DataBaseHelper extends SQLiteOpenHelper {
	public final static String dbName = Constants.dbName;
	public final static int dbVersion = 2;	
	
	public DataBaseHelper(Context context) {
		super(context, dbName, null, dbVersion);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " 	+ DiaryTable.TABLE_NAME 
																+ " ("
																+ DiaryTable._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
																+ DiaryTable.SYS_TIME + " INTEGER, "
																+ DiaryTable.DATE + " TEXT, "
																+ DiaryTable.START + " TEXT, "
																+ DiaryTable.END + " TEXT, "
																+ DiaryTable.COUNT + " INTEGER, "
																+ DiaryTable.PCT + " REAL, "
																+ DiaryTable.LAT + " REAL, "
																+ DiaryTable.LONG + " REAL"
																+ ");");
		
		db.execSQL("CREATE TABLE " 	+ TestTable.TABLE_NAME 
																+ " ("
																+ TestTable._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
																+ TestTable.SYS_TIME + " INTEGER, "
																+ TestTable.DATE + " TEXT, "
																+ TestTable.START + " TEXT, "
																+ TestTable.END + " TEXT, "
																+ TestTable.COUNT + " INTEGER, "
																+ TestTable.PCT + " REAL, "
																+ TestTable.LAT + " REAL, "
																+ TestTable.LONG + " REAL"
																+ ");");
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		String sql = "drop table if exists " + DiaryTable.TABLE_NAME;
		db.execSQL(sql);		
		sql = "drop table if exists " + TestTable.TABLE_NAME;
		db.execSQL(sql);
		onCreate(db);
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
	}
	
	public long insertDiary(SQLiteDatabase db, long sys_time, String date, String start, String end, int count, double percentage, double latitude, double longitude) {
		ContentValues cv = new ContentValues();
		db.beginTransaction(); 
		try {
			cv.put(DiaryTable.SYS_TIME, sys_time);
			cv.put(DiaryTable.DATE, date);
			cv.put(DiaryTable.START, start);
			cv.put(DiaryTable.END, end);
			cv.put(DiaryTable.COUNT, count);
			cv.put(DiaryTable.PCT, percentage);
			cv.put(DiaryTable.LAT, latitude);
			cv.put(DiaryTable.LONG, longitude);
			db.setTransactionSuccessful();
		} 
		finally {
			db.endTransaction();
		}
		return db.insert(DiaryTable.TABLE_NAME, null, cv);	
	}
	
	public String[] queryDatesInDiary(SQLiteDatabase db) {
		String query = "SELECT DISTINCT " + DiaryTable.DATE + " FROM " + DiaryTable.TABLE_NAME + " ORDER BY " + DiaryTable.ORDER + ";";
		Cursor cursor = db.rawQuery(query, null);
		int dates_count = cursor.getCount();
	  Log.i("Dates count", Integer.toString(dates_count));
		String[] dates = new String[dates_count];
		if (cursor.moveToFirst()) {
			for(int i = 0; i < dates_count; i++) {
				dates[i] = cursor.getString(0);
				cursor.moveToNext();
			  Log.i("Dates", dates[i]);
			}	
		}
		return dates;
	}

	public Cursor queryDiaryByDates(SQLiteDatabase db, String[] date) {
		String tb = DiaryTable.TABLE_NAME;
		String[] cols = new String[] {DiaryTable.DATE, 
																	DiaryTable.START, DiaryTable.END, 
																	DiaryTable.COUNT,TestTable.PCT,
																	DiaryTable.LAT,TestTable.LONG};
		String sel = DiaryTable.DATE.concat("=?");
		String order = DiaryTable.ORDER;
		Cursor cursor = db.query(tb, cols, sel, date, null, null, order);
		return cursor;
	}
	
	public long insertTest(SQLiteDatabase db, long sys_time, String date, String start, 
			String end, int count, double percentage, double latitude, double longitude) {
		ContentValues cv = new ContentValues();
		db.beginTransaction(); 
		try {
			cv.put(TestTable.SYS_TIME, sys_time);
			cv.put(TestTable.DATE, date);
			cv.put(TestTable.START, start);
			cv.put(TestTable.END, end);
			cv.put(TestTable.COUNT, count);
			cv.put(TestTable.PCT, percentage);
			cv.put(TestTable.LAT, latitude);
			cv.put(TestTable.LONG, longitude);
			db.setTransactionSuccessful();
		} 
		finally {
			db.endTransaction();
		}
		return db.insert(TestTable.TABLE_NAME, null, cv);	
	}

	public Cursor queryTest(SQLiteDatabase db){
		String tb = TestTable.TABLE_NAME;
		String[] cols = new String[] {TestTable.DATE, 
																	TestTable.START, TestTable.END, 
																	TestTable.COUNT, TestTable.PCT,
																	TestTable.LAT, TestTable.LONG};
		String order = TestTable.ORDER;
		Cursor cursor = db.query(tb, cols, null, null, null, null, order);
		return cursor;
	}

}
