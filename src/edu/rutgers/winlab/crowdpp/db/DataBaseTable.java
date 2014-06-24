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

import android.provider.BaseColumns;

/**
 * The DataBaseTable class 
 * @author Chenren Xu
 */
public class DataBaseTable {
	
	/** Constructor */
  private DataBaseTable() {}

	/** The database table for social diary */
  public static final class DiaryTable implements BaseColumns {
  	// CREATE TABLE Diary (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, date TEXT, start TEXT, end TEXT, count INTEGER, percentage REAL, latitude REAL, longitude REAL)
    private DiaryTable() {}

    public static final String TABLE_NAME = "Diary";
    
    public static final String SYS_TIME		= "time";
    public static final String DATE 			= "date";
    public static final String START 			= "start";
    public static final String END 				= "end";
    public static final String COUNT 			= "count";
    public static final String PCT 				= "percentage";
    public static final String LAT 				= "latitude";
    public static final String LONG 			= "longitude";

    public static final String ORDER 			= "time ASC";
  }
  
	/** The database table for test */
  public static final class TestTable implements BaseColumns {
  	// CREATE TABLE Test (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, date TEXT, start TEXT, end TEXT, count INTEGER, percentage REAL, latitude REAL, longitude REAL)
    private TestTable() {}
      
    public static final String TABLE_NAME = "Test";
    
    public static final String SYS_TIME 	= "time";
    public static final String DATE 			= "date";
    public static final String START 			= "start";
    public static final String END 				= "end";
    public static final String COUNT 			= "count";
    public static final String PCT 				= "percentage";
    public static final String LAT 				= "latitude";
    public static final String LONG 			= "longitude";

    public static final String ORDER 			= "time DESC";
  }
  
}
