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
 * Use the Pair class to group two values together.
 * 
 * @author sikoried
 */
public class Pair<T1, T2> {
	public Pair(T1 a, T2 b) {
		this.a = a;
		this.b = b;
	}
	public T1 a;
	public T2 b;
	public boolean equals(Object p) {
		if (!(p instanceof Pair<?, ?>)) 
			return false;
		Pair <?, ?> pair = (Pair<?, ?>) p;
		return pair.a.equals(a) && pair.b.equals(b);
	}
	public int hashCode() {
		return a.hashCode() + b.hashCode();
	}
	public String toString() {
		return a.toString() + " " + b.toString();
	}
}
