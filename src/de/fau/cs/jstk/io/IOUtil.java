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
package de.fau.cs.jstk.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Use the IOUtil methods to simplify the binary input/output handling of 
 * byte, int, short, float and double values and arrays
 * 
 * @author sikoried
 */
public final class IOUtil {
	/**
	 * Read a single byte from the InputStream
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte readByte(InputStream is) throws IOException {
		byte [] buf = new byte[1];
		if (is.read(buf) != 1)
			throw new IOException("could not read requested byte");
		return buf[0];
	}
	
	/**
	 * Read a byte array from the InputStream
	 * @param is
	 * @param buf
	 * @return false if the buffer could not be filled
	 * @throws IOException
	 */
	public static boolean readByte(InputStream is, byte [] buf) throws IOException {
		int read = is.read(buf);
		return read == buf.length;
	}
	
	/**
	 * Read len bytes from the InputStream
	 * @param is
	 * @param buf
	 * @param len
	 * @return false if less bytes read than requested
	 * @throws IOException
	 */
	public static boolean readByte(InputStream is, byte [] buf, int len) throws IOException {
		int read = is.read(buf, 0, len);
		return read == len;
	}
	
	/**
	 * Write a single byte to the OutputStream
	 * @param os
	 * @param b
	 * @throws IOException
	 */
	public static void writeByte(OutputStream os, byte b) throws IOException {
		os.write(new byte [] { b });
	}
	
	/**
	 * Write a byte array to the OutputStream
	 * @param os
	 * @param buf
	 * @throws IOException
	 */
	public static void writeByte(OutputStream os, byte [] buf) throws IOException {
		os.write(buf);
	}

	/**
	 * Write len bytes to the OutputStream
	 * @param os
	 * @param buf
	 * @param len
	 * @throws IOException
	 */
	public static void writeByte(OutputStream os, byte [] buf, int len) throws IOException {
		os.write(buf, 0, len);
	}
	
	/**
	 * Read a single short value from the given InputStream using given ByteOrder
	 * @param is
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static short readShort(InputStream is, ByteOrder bo) throws IOException {
		byte [] bbuf = new byte [Short.SIZE / 8];
		int read = is.read(bbuf);

		// complete frame?
		if (read < bbuf.length)
			throw new IOException ("could not read required bytes");

		// decode the double
		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		return bb.getShort();
	}
	
	/**
	 * Read an array of shorts from the given InputStream using given ByteOrder
	 * @param is
	 * @param buf
	 * @param bo
	 * @return false if buffer could not be filled
	 * @throws IOException
	 */
	public static boolean readShort(InputStream is, short [] buf, ByteOrder bo) throws IOException {
		byte [] bbuf = new byte [buf.length * Short.SIZE / 8];
		int read = is.read(bbuf);

		// complete frame?
		if (read < bbuf.length)
			return false;

		// decode the short
		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		for (int i = 0; i < buf.length; ++i)
			buf[i] = bb.getShort();
		
		return true;
	}

	/**
	 * Write a single short to the OutputStream
	 * pointer respectively.
	 * @param os
	 * @param val 
	 * @param bo
	 * @throws IOException
	 */
	public static void writeShort(OutputStream os, short val, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(Short.SIZE/8);
		bb.order(bo);
		bb.putShort(val);
		os.write(bb.array());
	}
	
	/**
	 * Write the given short array to the OutputStream using given ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeShort(OutputStream os, short [] buf, ByteOrder bo) 
		throws IOException {
		writeShort(os, buf, buf.length, bo);
	}
	
	/**
	 * Write the given short array to the OutputStream using given ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeShort(OutputStream os, short [] buf, int length, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(buf.length * Short.SIZE/8);
		bb.order(bo);
		for (int i = 0; i < length; ++i)
			bb.putShort(buf[i]);
		os.write(bb.array());
	}

	/**
	 * Read a single int from the InputStream using given ByteOrder
	 * pointer respectively.
	 * @param is
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static int readInt(InputStream is, ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte [Integer.SIZE / 8];
		int read = is.read(bbuf);

		if (read < bbuf.length)
			throw new IOException ("could not read required bytes");

		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		return bb.getInt();
	}
	
	/**
	 * Read a single int from the InputStream using given ByteOrder
	 * pointer respectively.
	 * @param is
	 * @param buf
	 * @param bo
	 * @return false if frame could not be filled
	 * @throws IOException
	 */
	public static boolean readInt(InputStream is, int [] buf,  ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte [buf.length * Integer.SIZE/8];
		int read = is.read(bbuf);

		if (read < bbuf.length)
			return false;

		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);
		
		for (int i = 0; i < buf.length; ++i)
			buf[i] = bb.getInt();
		
		return true;
	}
	
	/**
	 * Write an int to the OutputStream and advance the stream
	 * pointer respectively.
	 * @param os
	 * @param val
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static void writeInt(OutputStream os, int val, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE/8);
		bb.order(bo);
		bb.putInt(val);
		os.write(bb.array());
	}
	
	/**
	 * Write the given int array to the OutputStream using given ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeInt(OutputStream os, int [] buf, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(buf.length * Integer.SIZE/8);
		bb.order(bo);
		for (int d : buf) 
			bb.putInt(d);
		os.write(bb.array());
	}

	/**
	 * Read a single long from the InputStream using given ByteOrder
	 * pointer respectively.
	 * @param is
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static long readLong(InputStream is, ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte [Long.SIZE / 8];
		int read = is.read(bbuf);

		if (read < bbuf.length)
			throw new IOException ("could not read required bytes");

		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		return bb.getLong();
	}
	
	/**
	 * Read a long array from the InputStream using given ByteOrder
	 * pointer respectively.
	 * @param is
	 * @param buf
	 * @param bo
	 * @return false if frame could not be filled
	 * @throws IOException
	 */
	public static boolean readLong(InputStream is, long [] buf,  ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte [buf.length * Long.SIZE/8];
		int read = is.read(bbuf);

		if (read < bbuf.length)
			return false;

		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);
		
		for (int i = 0; i < buf.length; ++i)
			buf[i] = bb.getLong();
		
		return true;
	}
	
	/**
	 * Write an int to the OutputStream and advance the stream
	 * pointer respectively.
	 * @param os
	 * @param val
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static void writeLong(OutputStream os, long val, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(Long.SIZE/8);
		bb.order(bo);
		bb.putLong(val);
		os.write(bb.array());
	}
	
	/**
	 * Write the given long array to the OutputStream using given ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeLong(OutputStream os, long [] buf, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(buf.length * Long.SIZE/8);
		bb.order(bo);
		for (long d : buf) 
			bb.putLong(d);
		os.write(bb.array());
	}
	
	/**
	 * Read a single Float from the InputStream using given ByteOrder
	 * @param is
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static float readFloat(InputStream is, ByteOrder bo) throws IOException {
		byte [] bbuf = new byte[Float.SIZE / 8];
		int read = is.read(bbuf);

		// complete frame?
		if (read < bbuf.length)
			throw new IOException("could not read required bytes");

		// decode the double
		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		return bb.getFloat();
	}
	
	/**
	 * Reads float array from a InputStream using given ByteOrder
	 * 
	 * @param is binary InputStream
	 * @param buf (output) buffer
	 * @return true on success, false else.
	 * @throws IOException
	 */
	public static boolean readFloat(InputStream is, float [] buf, ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte[buf.length * Float.SIZE / 8];
		int read = is.read(bbuf);

		// complete frame?
		if (read < bbuf.length)
			return false;

		// decode the double
		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		for (int i = 0; i < buf.length; ++i)
			buf[i] = bb.getFloat();
		
		return true;
	}

	/**
	 * Reads floats from a binary InputStream and store them in the double buffer
	 * using given ByteOrder
	 * @param is binary InputStream
	 * @param buf (output) buffer
	 * @return true on success, false else.
	 * @throws IOException
	 */
	public static boolean readFloat(InputStream is, double [] buf, ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte[buf.length * Float.SIZE / 8];
		int read = is.read(bbuf);

		// complete frame?
		if (read < bbuf.length)
			return false;

		// decode the double
		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		for (int i = 0; i < buf.length; ++i)
			buf[i] = bb.getFloat();
		
		return true;
	}
	
	/**
	 * Reads float array from ASCII stream
	 * 
	 * @param br allocated BufferedReader 
	 * @param buf (output) buffer
	 * @return true on success, false else.
	 * @throws IOException
	 */
	public static boolean readFloat(BufferedReader br, float [] buf)
			throws IOException {
		String line = br.readLine();
		
		if (line == null)
			return false;
		
		String [] tokens = line.trim().split("\\s+");
		
		if (tokens.length != buf.length)
			return false;
		
		for (int i = 0; i < tokens.length; ++i)
			buf[i] = Float.valueOf(tokens[i]);

		return true;
	}
	
	/**
	 * Read a float array from the stream without knowing its size ahead of time
	 * @param br
	 * @return float array
	 */
	public float [] readFloat(BufferedReader br) throws IOException {
		String line = br.readLine();
		
		if (line == null)
			return null;
		
		String [] tokens = line.trim().split("\\s+");
		
		float [] buf = new float [tokens.length];
		
		for (int i = 0; i < tokens.length; ++i)
			buf[i] = Float.valueOf(tokens[i]);
		
		return buf;
	}
	
	/** 
	 * Write a float to the OutputStream
	 * pointer respectively.
	 * @param os
	 * @param val
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static void writeFloat(OutputStream os, float val, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(Float.SIZE/8);
		bb.order(bo);
		bb.putFloat(val);
		os.write(bb.array());
	}
	
	/**
	 * Write the given float array to the OutputStream using the specified 
	 * ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeFloat(OutputStream os, float [] buf, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(buf.length * Float.SIZE/8);
		bb.order(bo);
		for (float f : buf) 
			bb.putFloat(f);
		os.write(bb.array());
	}
	
	/**
	 * Write the given double array as floats to the OutputStream using the 
	 * specified ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeFloat(OutputStream os, double [] buf, ByteOrder bo)
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(buf.length * Float.SIZE/8);
		bb.order(bo);
		for (double d : buf) {
			// truncate to 3 digits
			int i = (int) Math.round(d * 1000);
			d = (double) i / 1000;
			bb.putFloat((float) d);
		}
		os.write(bb.array());
	}
	
	/**
	 * Write a given float array to the ASCII stream
	 * @param bw
	 * @param buf
	 * @throws IOException
	 */
	public static void writeFloat(BufferedWriter bw, float [] buf)
		throws IOException {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < buf.length - 1; ++i)
			sb.append(Float.toString(buf[i]) + " ");
		sb.append(Float.toString(buf[buf.length-1]) + "\n");
		bw.append(sb.toString());
	}
	
	/**
	 * Read a single Double from the InputStream
	 * @param is
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static double readDouble(InputStream is, ByteOrder bo) throws IOException {
		byte [] bbuf = new byte[Double.SIZE / 8];
		int read = is.read(bbuf);

		if (read < bbuf.length)
			throw new IOException("could not read required bytes");

		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		return bb.getDouble();
	}
	
	/**
	 * Reads double array from a binary InputStream
	 * 
	 * @param is binary InputStream
	 * @param buf (output) buffer
	 * @return true on success, false else.
	 * @throws IOException
	 */
	public static boolean readDouble(InputStream is, double [] buf, ByteOrder bo) 
		throws IOException {
		byte [] bbuf = new byte[buf.length * Double.SIZE / 8];
		int read = is.read(bbuf);

		if (read < bbuf.length)
			return false;

		ByteBuffer bb = ByteBuffer.wrap(bbuf);
		bb.order(bo);

		for (int i = 0; i < buf.length; ++i)
			buf[i] = bb.getDouble();
		
		return true;
	}

	/**
	 * Reads doubles from ASCII stream
	 * 
	 * @param br allocated BufferedReader 
	 * @param buf (output) buffer
	 * @return true on success, false else.
	 * @throws IOException
	 */
	public static boolean readDouble(BufferedReader br, double [] buf)
			throws IOException {
		String line = br.readLine();
		
		if (line == null)
			return false;
		
		String [] tokens = line.trim().split("\\s+");
		
		if (tokens.length != buf.length)
			return false;
		
		for (int i = 0; i < tokens.length; ++i)
			buf[i] = Double.valueOf(tokens[i]);

		return true;
	}

	/**
	 * Read a double array from the stream without knowing its size ahead of time
	 * @param br
	 * @return
	 */
	public double [] readDouble(BufferedReader br) throws IOException {
		String line = br.readLine();
		
		if (line == null)
			return null;
		
		String [] tokens = line.trim().split("\\s+");
		
		double [] buf = new double [tokens.length];
		
		for (int i = 0; i < tokens.length; ++i)
			buf[i] = Double.valueOf(tokens[i]);
		
		return buf;
	}
	
	/** 
	 * Write a double to the OutputStream
	 * pointer respectively.
	 * @param os
	 * @param val
	 * @param bo
	 * @return
	 * @throws IOException
	 */
	public static void writeDouble(OutputStream os, double val, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(Double.SIZE/8);
		bb.order(bo);
		bb.putDouble(val);
		os.write(bb.array());
	}
	
	/**
	 * Write the given double array to the OutputStream using the specified 
	 * ByteOrder
	 * @param os
	 * @param buf
	 * @param bo
	 * @throws IOException
	 */
	public static void writeDouble(OutputStream os, double [] buf, ByteOrder bo) 
		throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(buf.length * Double.SIZE/8);
		bb.order(bo);
		for (double d : buf) {
			// truncate to 3 digits
			int i = (int) Math.round(d * 1000);
			d = (double) i / 1000;
			bb.putDouble(d);
		}
		os.write(bb.array());
	}
	
	/**
	 * Write a given double array to the ASCII stream
	 * @param bw
	 * @param buf
	 * @throws IOException
	 */
	public static void writeDouble(BufferedWriter bw, double [] buf) 
		throws IOException {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < buf.length - 1; ++i)
			sb.append(Double.toString(buf[i]) + " ");
		sb.append(Double.toString(buf[buf.length-1]) + "\n");
		bw.append(sb.toString());
	}
}
