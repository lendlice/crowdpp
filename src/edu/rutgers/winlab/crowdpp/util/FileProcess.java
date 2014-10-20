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

package edu.rutgers.winlab.crowdpp.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.StringTokenizer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;

/**
 * The FileProcess class 
 * @author Chenren Xu
 */
public class FileProcess {
	
	@SuppressLint("SimpleDateFormat")
	/** @return the file with the timestamp as the filename */
	public static String newFileOnTime(String strExtension)   {
		java.util.Date dt = new java.util.Date(System.currentTimeMillis());
	  SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS");
	  String fileName = fmt.format(dt);
	  fileName = fileName + "." + strExtension;
	  return fileName;
	}

	/** @return the SD card path */
	public static String getSdPath() {
		return  Environment.getExternalStorageDirectory().getPath();
	}
	
	/** delete the file */
	public static void deleteFile(String filename) {
		File file = new File(filename);
		file.delete();
	}
	
	/** write to the file under SD card path */
	public static void writeToSd(String text, String filename) {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			File sdCardDir = Environment.getExternalStorageDirectory();
			File sdFile = new File(sdCardDir, filename);
			try {
				FileOutputStream fos = new FileOutputStream(sdFile, true);
				text = text + "\n";
				fos.write(text.getBytes());
				fos.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return;
	}
	
	/** write to the file */
	public static void writeFile(String text, String filename) {
		try {
			FileWriter fw = new FileWriter(filename,true);
			text = text + "\n";
		  fw.write(text);
		  fw.close();
		}	catch (IOException e) {
			e.printStackTrace();
		}
		return;
	}
	
	/** read the file into a matrix */
	public static double[][] readFile(String filename) throws IOException {
		BufferedReader br = new BufferedReader (new FileReader (filename));
		String s1, s2;
		int rows = 0;
		int cols = 0;
		if ((s1 = br.readLine()) != null) {
			++rows;
			StringTokenizer st = new StringTokenizer (s1);
			while (st.hasMoreTokens())
			{
				s2 = st.nextToken();
			    ++cols;
			}
		}
		while ((s1 = br.readLine()) != null) {
			++rows;
		}
	  br.close();
		
	  double [][] dat = new double [rows][cols];
	  br = new BufferedReader (new FileReader (filename));

	  int i = 0;
		int j = 0;
		while ((s1 = br.readLine()) != null) {
			StringTokenizer st = new StringTokenizer (s1);
			j = 0;
			while (st.hasMoreTokens()) {
			  s2 = st.nextToken();
			  dat[i][j] = Double.valueOf(s2);	 
			  j++;
			} 
			i++;
		}
		return dat;
	}
	
}
