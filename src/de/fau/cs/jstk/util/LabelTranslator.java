/*
	Copyright (c) 2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer

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

import java.util.Arrays;

/**
 * Use the LabelTranslator to translate byte labels (0-255) into actual label 
 * ids.
 * 
 * @author sikoried
 */
public final class LabelTranslator {
	/** The default LabelTranslator covers the symbols a-z,A-Z,0-9 */
	public static LabelTranslator DEFAULT = 
			new LabelTranslator("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
	
	/** look up table byte-lab -> int lab */
	private int [] lutb2i = new int [256];
	
	/** look up table int-label -> byte-label */
	private int [] luti2b;
	
	/**
	 * Allocate a new LabelTranslator with the given label string
	 * @param labelstring string of character symbols in order
	 */
	public LabelTranslator(String labelstring) {
		byte [] lab = labelstring.getBytes();
		
		luti2b = new int [lab.length];
		// fill the look up table
		Arrays.fill(lutb2i, -1);
		for (int i = 0; i < lab.length; ++i) {
			lutb2i[lab[i]] = i;
			luti2b[i] = lab[i];
		}
	}
	
	public int labelToId(int b) {
		return lutb2i[b];
	}
	
	public int idToLabel(int i) {
		return luti2b[i];
	}
}
