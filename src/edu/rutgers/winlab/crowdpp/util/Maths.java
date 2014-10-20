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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ejml.simple.SimpleMatrix;

/**
 * The Maths class 
 * A collection of used linear algebra and statistics functions
 * @author Chenren Xu
 */

public class Maths {

	/** @return the 2 norm */
	public static double getNorm2(double[] array) {
		double norm = 0;
		for(int i = 0; i < array.length; i++){
			norm = norm + array[i] * array[i];
		}
		return Math.sqrt(norm);	
	}
	
	/** @return the dot product */
	public static double dotProduct(double[] array1, double[] array2) {
		double dot = 0;
		for(int i = 0; i < array1.length; i++){
			dot = dot + array1[i] * array2[i];
		}
		return dot;	
	}

	/** @return the column mean */
	public static double[] getColMean(double[][] array) {
		int cols = array[0].length;
		int rows = array.length;
		double[] colMean = new double[cols];
		for (int i = 0; i < cols; i++) {
			double mean = 0;
			for(int j = 0; j < rows; j++) {
				mean = mean + array[j][i];				
			}
			colMean[i] = mean / rows;
		}
		return (colMean);
	}
	
	/** @return the column mean */
	public static SimpleMatrix getColMean(SimpleMatrix dat) {
		int cols = dat.numCols();
		int rows = dat.numRows();
		double[] colmean = new double[cols];
		for (int i = 0; i < cols; i++) {
			double mean = 0;
			for(int j = 0; j < rows; j++) {
				mean = mean + dat.get(j,i);				
			}
			colmean[i] = mean / rows;
		}
		SimpleMatrix rv = new SimpleMatrix(cols,1,true,colmean);
		return (rv);
	}

	/** @return the mean */
	public static double getMean(List<Double> list) {
		double mean = 0;
		for (int i = 0; i < list.size(); i++) {
			mean = mean + list.get(i);
		}
		return (mean/list.size());
	}

	/** @return the mean */
	public static double getMean(double[] array) {
		double mean = 0;
		for (int i = 0; i < array.length; i++) {
			mean = mean + array[i];
		}
		return (mean/array.length);
	}

	/** @return the median */
	public static double getMedian(List<Double> list) {
		Collections.sort(list);		
		double median = 0;
		if (list.size() % 2 == 1) {
			median = list.get(list.size() / 2);
		}
		else {
			median = (list.get(list.size() / 2 - 1) + list.get(list.size() / 2)) / 2;
		}
		return median;		
	}
	
	/** @return the variance */
	public static double getVariance(List<Double> list) {
		double mean = getMean(list);
		double variance = 0;		
		for (int i = 0; i < list.size(); i++) {
			variance = variance + (list.get(i) - mean) * (list.get(i) - mean);
		}
		return (variance/list.size());
	}

	/** @return the variance */
	public static double getVariance(double[] array) {
		double mean = getMean(array);
		double variance = 0;		
		for (int i = 0; i < array.length; i++) {
			variance = variance + (array[i] - mean) * (array[i] - mean);
		}
		return (variance/array.length);
	}

	/** @return the diagonal covariance */
	public static SimpleMatrix getDiagonalCovariance(SimpleMatrix dat) {
		int p = dat.numCols();
		double []sigma = new double[p]; 
		for(int i = 0; i < p; i++) {
			SimpleMatrix temp_vec = dat.extractVector(false, i);
			List<Double> temp_list = new ArrayList<Double>();
			for(int j = 0; j < temp_vec.numRows(); j++) {
				temp_list.add(temp_vec.get(j));
			}
			sigma[i] = Math.sqrt(getVariance(temp_list));
		}
		SimpleMatrix sigma_mat = SimpleMatrix.diag(sigma);
		return sigma_mat;
	}
	
}
