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
package de.fau.cs.jstk.util;

/**
 * This package provides commonly used distance measures, e.g. euclid, cityblock etc.
 * 
 * @author sikoried
 */
public abstract class Distances {
	
	public static double euclidean(double [] a, double [] b) {
		double dist = 0; 
		for (int i = 0; i < a.length; ++i)
			dist += Math.pow(a[i]-b[i], 2);
		return Math.sqrt(dist);
	}
	
	public static double manhattan(double [] a, double [] b)
		throws Exception {
		double dist = 0; 
		for (int i = 0; i < a.length; ++i)
			dist += Math.abs(a[i]-b[i]);
		return dist;
	}
}
