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
import java.util.List;

import edu.rutgers.winlab.crowdpp.util.Maths;

import org.ejml.simple.SimpleMatrix;

/**
 * The Distances class 
 * A collection of distance functions based on different algorithms
 * @author Chenren Xu
 */
public class Distances {
	
	/** @return the cosine distance */
	public static double Cosine(SimpleMatrix a, SimpleMatrix b) {
		double [][]a_norm = new double[a.numRows()][a.numCols()];
		for(int i = 0; i < a.numRows(); i++) {
			for(int j = 0; j < a.numCols(); j++) {
				a_norm[i][j] = a.get(i,j);
			}
		}
		double [][]b_norm = new double[b.numRows()][b.numCols()];
		for(int i = 0; i < b.numRows(); i++) {
			for(int j = 0; j < b.numCols(); j++) {
				b_norm[i][j] = b.get(i,j);
			}
		}
		double deg = Math.toDegrees(Math.acos(Maths.dotProduct(Maths.getColMean(a_norm), Maths.getColMean(b_norm)) / (Maths.getNorm2(Maths.getColMean(a_norm)) * Maths.getNorm2(Maths.getColMean(b_norm)))));
		return deg;
	}
	
	/** @return the normalized cosine distance */
	public static double normalizedCosine(SimpleMatrix a, SimpleMatrix b) {
		int p = a.numCols();
		double []sigma = new double[p]; 
		for(int i = 0; i < p; i++) {
			SimpleMatrix temp_vec = a.extractVector(false, i);
			List<Double> temp_list = new ArrayList<Double>();
			for(int j = 0; j < temp_vec.numRows(); j++) {
				temp_list.add(temp_vec.get(j));
			}
			sigma[i] = Math.sqrt(Maths.getVariance(temp_list));
		}
		SimpleMatrix temp_mat = SimpleMatrix.diag(sigma);
		a = a.mult(temp_mat.invert());
		double [][]a_norm = new double[a.numRows()][a.numCols()];
		for(int i = 0; i < a.numRows(); i++) {
			for(int j = 0; j < a.numCols(); j++) {
				a_norm[i][j] = a.get(i,j);
			}
		}
		for(int i = 0; i < p; i++) {
			SimpleMatrix temp_vec = b.extractVector(false, i);
			List<Double> temp_list = new ArrayList<Double>();
			for(int j = 0; j < temp_vec.numRows(); j++) {
				temp_list.add(temp_vec.get(j));
			}
			sigma[i] = Math.sqrt(Maths.getVariance(temp_list));
		}
		temp_mat = SimpleMatrix.diag(sigma);
		b = b.mult(temp_mat.invert());
		double [][]b_norm = new double[b.numRows()][b.numCols()];
		for(int i = 0; i < b.numRows(); i++) {
			for(int j = 0; j < b.numCols(); j++) {
				b_norm[i][j] = b.get(i,j);
			}
		}
		double deg = Math.toDegrees(Math.acos(Maths.dotProduct(Maths.getColMean(a_norm), Maths.getColMean(b_norm)) / (Maths.getNorm2(Maths.getColMean(a_norm)) * Maths.getNorm2(Maths.getColMean(b_norm)))));
		return deg;
	}	
	
	/** @return the KullbackLeibler distance */
	public static double KullbackLeibler(SimpleMatrix a, SimpleMatrix b) {
		// formula: 0.5 * t(mu_y - mu_x) * (inv(sigma_x) + inv(sigma_y)) * (mu_y - mu_x) + 0.5 tr(inv(sigma_x) * sigma_y + inv(sigma_y) * sigma_x - 2I)
		SimpleMatrix delta_mu = Maths.getColMean(b).minus(Maths.getColMean(a));
		SimpleMatrix sigma_a = Maths.getDiagonalCovariance(a);
		SimpleMatrix sigma_b = Maths.getDiagonalCovariance(b);
		double first_term = 0.5 * (delta_mu.transpose().mult(sigma_a.invert().plus(sigma_b.invert())).mult(delta_mu)).get(0,0);
		double second_term = 0.5 * sigma_a.invert().mult(sigma_b).plus(sigma_b.invert().mult(sigma_a)).minus(SimpleMatrix.identity(sigma_a.numCols())).trace();
		return ((first_term + second_term) / 100000);
	}

	/** @return the Bhattacharyya distance */
	public static double Bhattacharyya(SimpleMatrix a, SimpleMatrix b) {
		// formula: 0.25 * t(mu_y - mu_x) * (sigma_x + sigma_y) * (mu_y - mu_x) + 0.5 * log(det(sigma_a + sigma_b) / (2 * sqrt(det(sigma_a * sigma_b)))
		SimpleMatrix delta_mu = Maths.getColMean(a).minus(Maths.getColMean(b));
		SimpleMatrix sigma_a = Maths.getDiagonalCovariance(a);
		SimpleMatrix sigma_b = Maths.getDiagonalCovariance(b);
		double first_term = 0.25 * (delta_mu.transpose().mult((sigma_a.plus(sigma_b)).invert()).mult(delta_mu)).get(0,0);
		double second_term = 0.5 * Math.log((sigma_a.plus(sigma_b)).determinant() / (2 * Math.sqrt((sigma_a.mult(sigma_b)).determinant())));
		return (first_term + second_term);
	}
	
}
