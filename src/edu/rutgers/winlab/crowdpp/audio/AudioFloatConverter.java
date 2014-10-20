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

package edu.rutgers.winlab.crowdpp.audio;

/**
 * The AudioFloatInputStream class 
 * convert the byte array to float array 
 * @author Sugang Li, Chenren Xu
 */
public abstract class AudioFloatConverter {
	
	/** hardcode the format: 16 bits, unsigned and little endian */
	private static class AudioFloatConversion16SL extends AudioFloatConverter {
    @Override
		public float[] toFloatArray(byte in_buff[], int in_offset, float out_buff[], int out_offset, int out_len) {
    	int ix = in_offset;
      int len = out_offset + out_len;
      for (int ox = out_offset; ox < len; ox++) {
      	out_buff[ox] = (short)(in_buff[ix++] & 0xff | in_buff[ix++] << 8) * 3.051851E-05F;
      }
      return out_buff;
    }

    @Override
		public byte[] toByteArray(float in_buff[], int in_offset, int in_len, byte out_buff[], int out_offset) {
    	int ox = out_offset;
      int len = in_offset + in_len;
      for (int ix = in_offset; ix < len; ix++) {
      	int x = (int)(in_buff[ix] * 32767D);
        out_buff[ox++] = (byte)x;
        out_buff[ox++] = (byte)(x >>> 8);
      }
      return out_buff;
    }
	}

  // Hardcode the wav read format
  public static AudioFloatConverter getConverter() {
  	return new AudioFloatConversion16SL();
  }

  public abstract float[] toFloatArray(byte abyte0[], int i, float af[], int j, int k);

  public float[] toFloatArray(byte in_buff[], float out_buff[], int out_offset, int out_len) {
  	return toFloatArray(in_buff, 0, out_buff, out_offset, out_len);
  }

  public float[] toFloatArray(byte in_buff[], int in_offset, float out_buff[], int out_len) {
    return toFloatArray(in_buff, in_offset, out_buff, 0, out_len);
  }

  public float[] toFloatArray(byte in_buff[], float out_buff[], int out_len) {
    return toFloatArray(in_buff, 0, out_buff, 0, out_len);
  }

  public float[] toFloatArray(byte in_buff[], float out_buff[]) {
    return toFloatArray(in_buff, 0, out_buff, 0, out_buff.length);
  }

  public abstract byte[] toByteArray(float af[], int i, int j, byte abyte0[], int k);

  public byte[] toByteArray(float in_buff[], int in_len, byte out_buff[], int out_offset) {
    return toByteArray(in_buff, 0, in_len, out_buff, out_offset);
  }

  public byte[] toByteArray(float in_buff[], int in_offset, int in_len, byte out_buff[]) {
    return toByteArray(in_buff, in_offset, in_len, out_buff, 0);
  }

  public byte[] toByteArray(float in_buff[], int in_len, byte out_buff[]) {
    return toByteArray(in_buff, 0, in_len, out_buff, 0);
  }

  public byte[] toByteArray(float in_buff[], byte out_buff[]) {
    return toByteArray(in_buff, 0, in_buff.length, out_buff, 0);
  }

}
