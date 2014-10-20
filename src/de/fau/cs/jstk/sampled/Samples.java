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
package de.fau.cs.jstk.sampled;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * simple class for converting samples between double and little endian signed 16 bit 
 * @author hoenig
 *
 */
public class Samples {
	public static double [] signedLinearLittleShorts2samples(byte [] buf){
		ByteBuffer bb = ByteBuffer.wrap(buf);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		
		double [] ret = new double[buf.length / 2];
		
		// decode the byte stream
		int i;
		for (i = 0; i < buf.length / 2; i++){
			short value = bb.getShort();
			if (value == 32768)
				value = 32767;
		
			ret[i] = (double) value / 32767.0;
		}
				
		
		return ret;		
	}
	
	public static byte [] samples2signedLinearLittleShorts(double [] buf){
		ByteBuffer bb = ByteBuffer.allocate(buf.length * 2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		for (double d : buf)
			bb.putShort((short)(d * 32767.0));
		
		return bb.array();		
	}

	public static void normalizeSignedLinearLittleShorts(byte [] bytes, boolean subtractDC) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);		
		
		if (bytes.length == 0)
			return;
		if ((bytes.length % 2) != 0)
			throw new IllegalArgumentException("byte count is odd:" +  bytes.length);
		
		int nSamples = bytes.length / 2;
		
		long mean = 0;
		
		int i;
		
		if (subtractDC){
			for (i = 0; i < nSamples; i++)
				mean += bb.getShort(i * 2);
			
			mean /= nSamples;
			
			System.err.println("subtracting DC = " + mean);
			
			for (i = 0; i < nSamples; i++){
				int value = bb.getShort(i * 2);
				value -= mean;
				value = Math.max(Math.min(value, 32767), -32768);

				bb.putShort(i * 2, (short) value);
			}
		}
		
		// search for max
		int max = Math.abs(bb.getShort(0));
		
		for (i = 1; i < nSamples; i++){
			int value = Math.abs(bb.getShort(i * 2));
			if (value > max)
				max = value;
		}

		if (max == 0)
			return;
		
		for (i = 0; i < nSamples; i++){
			int value = bb.getShort(i * 2) * 32767 / max;
			if (value > 32767 || value < -32768)
				throw new AssertionError("value = " + value);
			bb.putShort(i * 2, (short) value);
		}	
		
//		int min = Math.abs(bb.getShort(0));
//		max = Math.abs(bb.getShort(0));
//		
//		for (i = 1; i < nSamples; i++){
//			int value = bb.getShort(i * 2);
//			if (value > max)
//				max = value;
//			if (value < min)
//				min= value;
//		}
//		System.err.println(String.format("new min: %d max: %d", min, max));
		
	}
}
