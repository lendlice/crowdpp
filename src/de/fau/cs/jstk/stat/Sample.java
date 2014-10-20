/*
	Copyright (c) 2009-2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer
		Tobias Bocklet

	This file is part of the Java Speech Toolkit (JSTK).

	The JSTK is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	The JSTK is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with the JSTK. If not, see <http://www.gnu.org/licenses/>.
*/
package de.fau.cs.jstk.stat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.fau.cs.jstk.util.Arithmetics;


/** 
 * A more sophisticated way of storing a Sample. Includes label and data,
 * provides some utility functions.
 * 
 * @author sikoried
 *
 */
public class Sample implements Serializable {
	private static final long serialVersionUID = 1L;

	/** correct label */
	public short c;
	
	/** classified label */
	public short y;
	
	/** data vector */
	public double [] x;
	
	public Sample(Sample s) {
		x = new double [s.x.length];
		y = s.y;
		c = s.c;
		System.arraycopy(s.x, 0, x, 0, s.x.length);
	}
	
	/**
	 * Generate a new sample with the correct label c
	 * @param c correct label
	 * @param x data
	 */
	public Sample(short c, double [] x) {
		this.x = x.clone();
		this.c = c;
	}
	
	public Sample(short c, short y, double [] x) {
		this.x = x.clone();
		this.c = c;
		this.y = y;
	}
	
	/**
	 * Generate a new (empty) sample
	 * @param c correct label
	 * @param dim feature dimension
	 */
	public Sample(short c, int dim) {
		this.c = c;
		x = new double [dim];
	}
	
	/**
	 * Return a String representation including the assigned label
	 */
	public String toClassifiedString() {
		String val = "" + c + " " + y;
		for (double d : x)
			val += " " + d;
		return val;
	}
	
	public String toString() {
		String val = "" + c;
		for (double d : x)
			val += " " + d;
		return val;
	}
	
	/**
	 * Create an ArrayList of Samples from a double array; one sample per row
	 * @param data rows will be samples
	 * @return ArrayList of Samples
	 */
	public static List<Sample> unlabeledArrayListFromArray(double [][] data) {
		LinkedList<Sample> n = new LinkedList<Sample>();
		for (double [] x : data)
			n.add(new Sample((short) 0, x));
		return n;
	}
	
	/**
	 * Remove all data from a list which is not of label id
	 * @param data data set
	 * @param id target class
	 * @return
	 */
	public static List<Sample> reduceToClass(List<Sample> data, int id) {
		LinkedList<Sample> n = new LinkedList<Sample>();
		for (Sample s : data)
			if (s.c == id)
				n.add(s);
		return n;
	}
	
	/**
	 * Remove samples of a certain class from the data set
	 * @param data data set
	 * @param id target class
	 * @return
	 */
	public static List<Sample> removeClass(List<Sample> data, int id) {
		ArrayList<Sample> n = new ArrayList<Sample>();
		for (Sample s : data)
			if (s.c != id)
				n.add(s);
		return n;
	}
	
	/**
	 * Subtract the mean value from all samples
	 * @param data
	 * @return mean value
	 */
	public static Sample meanSubstract(List<Sample> data) {
		Sample mean = new Sample(data.get(0));
		
		for (int i = 1; i < data.size(); ++i)
			Arithmetics.vadd2(mean.x, data.get(i).x);
		
		Arithmetics.sdiv2(mean.x, data.size());
		
		for (Sample s : data)
			Arithmetics.vsub2(s.x, mean.x);
		
		return mean;
	}
}
