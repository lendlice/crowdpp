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

import java.io.IOException;
import java.util.HashMap;

import javax.sound.sampled.UnsupportedAudioFileException;

import android.media.AudioFormat;
import android.media.AudioTrack;

import de.fau.cs.jstk.exceptions.MalformedParameterStringException;


/**
 * Format holder for (raw) audio file formats including WAV. Note that
 * the Java Sound API rejects Mu-law and A-law, and maybe mistaken on WAV files 
 * with unsigned data (which is pretty-nonstandard anyways). Multi-channel files
 * are NOT supported.
 * 
 * @author sikoried
 *
 */
public class RawAudioFormat {
	/** default WAV header size */
	static int WAV_HEADER_SIZE = 44;
	
	/** header size for non RIFF formats (e.g. WAV ulaw/alaw) */
	static int WAV_HEADER_SIZE2 = 58;
	
	/** default sample rate: 16 kHz */
	static int DEFAULT_SAMPLE_RATE = 16000;
	
	/** default bit rate: 16 bit */
	static int DEFAULT_BIT_RATE = 16;
	
	/** default signedness: true */
	static boolean DEFAULT_SIGNEDNESS = true;
	
	/** default endianess: little */
	static boolean DEFAULT_LITTLE_ENDIAN = true;
	
	/** default frame size: 2 byte */
	static int DEFAULT_FRAME_SIZE = 2;
	
	/** sample rate in Hz (def: 16000) */
	int sr = DEFAULT_SAMPLE_RATE;
	
	public int getSampleRate() { return sr; }
	
	/** bit rate (def: 16) */
	int bd = DEFAULT_BIT_RATE;
	
	public int getBitRate() { return bd; }
	
	/** frame size in byte (def: 2)*/
	int fs = DEFAULT_FRAME_SIZE;
	
	/** signedness (def: true) */
	boolean signed = DEFAULT_SIGNEDNESS;
	
	/** endianess (def: little) */
	boolean littleEndian = DEFAULT_LITTLE_ENDIAN;
	
	/** u-law encoded? */
	boolean ulaw = false;
	
	/** a-law encoded? */
	boolean alaw = false;
	
	/** header size if any */
	int hs = 0;
	
	/** available predefined RawAudioFormats, is initialized on first instanciation */
	private static HashMap<String, RawAudioFormat> predefinedFormats = null;
	
	/** 
	 * Construct a RawAudioFormat from an AudioFormat, assuming a WAV header 
	 * of size WAV_HEADER_SIZE (44) bytes. 
	 * @param af AudioFormat (e.g. from AudioSystem.getAudioFileFormat(File).
	 */
	/*public RawAudioFormat(AudioFormat af) throws IOException {
		sr = (int) af.getFrameRate();
		bd = af.getSampleSizeInBits();
		fs = bd / 8;
		
		if (af.getChannels() > 1)
			throw new IOException("multi-channel files are not supported");
		
		if (af.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
			signed = true; alaw = false; ulaw = false; hs = WAV_HEADER_SIZE;
		}
		if (af.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
			signed = false; alaw = false; ulaw = false; hs = WAV_HEADER_SIZE;
		}
		if (af.getEncoding() == AudioFormat.Encoding.ALAW) {
			alaw = true; signed = true; ulaw = false; hs = WAV_HEADER_SIZE2;
		}
		if (af.getEncoding() == AudioFormat.Encoding.ULAW) {
			ulaw = true; signed = true; alaw = false; hs = WAV_HEADER_SIZE2;
		}
		
	}*/
	/**
	 * Android version of audioformat 
	 * @param af
	 * @throws IOException
	 */
	public RawAudioFormat(AudioTrack at) throws IOException {
		sr = (int) at.getSampleRate();
		bd = at.getAudioFormat();
		fs = bd/8;
		
		if(at.getChannelConfiguration()!=AudioFormat.CHANNEL_OUT_MONO)
			throw new IOException("multi-channel files are not supported");
		signed=true;hs=WAV_HEADER_SIZE;
		
	}
	
	/** 
	 * Construct a RawAudioFormat according to the given arguments.
	 * @param bitDepth
	 * @param frameRate
	 * @param signed
	 * @param littleEndian
	 * @param headerSize Usually 0 for raw files, WAV_HEADER_SIZE for WAV files.
	 */
	public RawAudioFormat(int bitDepth, int frameRate, boolean signed, boolean littleEndian, int headerSize) {
		sr = frameRate;
		bd = bitDepth;
		hs = headerSize;
		fs = bitDepth / 8;
		this.signed = signed;
		this.littleEndian = littleEndian;
	}
	
	/**
	 * Construct a RawAudioFormat for u-law or a-law encoded files (these are 8bit).
	 * @param frameRate 
	 * @param alawInsteadUlaw Use a-law instead of u-law
	 * @param headerSize Size of the header, if any.
	 */
	public RawAudioFormat(int frameRate, boolean alawInsteadUlaw, int headerSize) {
		sr = frameRate;
		hs = headerSize;
		bd = 8;
		fs = 1;
		signed = true;
		if (alawInsteadUlaw) {
			alaw = true; ulaw = false;
		} else {
			alaw = false; ulaw = true;
		}
	}

	/**
	 * Construct a RawAudioFormat for signed, little endian audio w/o header (default SSG)
	 * @param bitRate Bit rate, usually 16
	 * @param frameRate
	 */
	public RawAudioFormat(int bitRate, int frameRate) {
		this(bitRate, frameRate, true, true, 0);
	}
	
	/**
	 * Default: 16kHz, 16bit, signed, little endian, no header
	 */
	public RawAudioFormat() {
		// nothing to do here
	}
	
	/**
	 * Set the default header size (WAV_HEADER_SIZE)
	 */
	public void setWavHeader() {
		hs = WAV_HEADER_SIZE;
	}
	
	public String toString() {
		return "RawAudioFormat: " 
			+ sr + "Hz " 
			+ bd + "bit "
			+ (alaw ? "a-law " : "") 
			+ (ulaw ? "u-law " : "") 
			+ (signed ? "signed " : "unsigned ")
			+ (littleEndian ? "little_" : "big_") + "endian "
			+ "frame_size=" + fs
			+ " header_size=" + hs;
	}
	
	/**
	 * Query common (headerless) RawAudioFormats: <br/>
	 * 		ssg/16: signed, little endian, 16kHz, 16bit <br/>
	 * 		ssg/8: signed, little endian, 8kHz, 16bit <br/>
	 * 		ulaw/16: Mu-law compressed, little endian, 16kHz <br/> 
	 * 		ulaw/8: Mu-law compressed, little endian, 8kHz <br/>
	 * 		alaw/16: A-law compressed, little endian, 16kHz <br/>
	 * 		alaw/8: A-law compressed, little endian, 8kHz <br/>
	 * Use RawAudioFormat.setWavHeader() to add the default WAV header; access
	 * RawAudioFormat.hs to set header size manually.
	 * @param key Key from list above
	 * @return requested RawAudioFormat
	 * @throws UnsupportedAudioFileException
	 */
	public static RawAudioFormat getRawAudioFormat(String key) 
		//throws UnsupportedAudioFileException 
	{
		//if (predefinedFormats == null) 
			//initializePredefinedFormats();
		//if (!predefinedFormats.containsKey(key))
			//throw new UnsupportedAudioFileException();
		return predefinedFormats.get(key);
	}

	/**
	 * Generate the predefined formats
	 */
	private static void initializePredefinedFormats() {
		if (predefinedFormats != null)
			return;
		
		// list of predefined formats
		predefinedFormats = new HashMap<String, RawAudioFormat>();
		predefinedFormats.put("ssg/16", new RawAudioFormat(16, 16000));
		predefinedFormats.put("ssg/8", new RawAudioFormat(16, 8000));
		predefinedFormats.put("ulaw/16", new RawAudioFormat(16000, false, 0));
		predefinedFormats.put("ulaw/8", new RawAudioFormat(8000, false, 0));
		predefinedFormats.put("alaw/16", new RawAudioFormat(16000, true, 0));
		predefinedFormats.put("alaw/8", new RawAudioFormat(8000, true, 0));
		predefinedFormats.put("sphere/8", new RawAudioFormat(8000, false, 1024));
		predefinedFormats.put("wav/44", new RawAudioFormat(16, 44100, true, true, 44));
		predefinedFormats.put("wav/32", new RawAudioFormat(16, 32000, true, true, 44));
		predefinedFormats.put("wav/16", new RawAudioFormat(16, 16000, true, true, 44));
		predefinedFormats.put("wav/8", new RawAudioFormat(16, 8000, true, true, 44));
	}
	
	/**
	 * Return a (String) list of predefined audio formats
	 * @return String containing all predefined audio formats
	 */
	public static String getPredefinedAudioFormats() {
		if (predefinedFormats == null)
			initializePredefinedFormats();
		StringBuffer sb = new StringBuffer();
		for (String k : predefinedFormats.keySet()) 
			sb.append("\"" + k + "\": " + predefinedFormats.get(k) + "\n");
		return sb.toString();
	}
	
	/**
	 * Try to query the RawAudioFormat from the header in the given file
	 * @param fileName
	 * @return extracted RawAudioFormat
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	public static RawAudioFormat getRawAudioFormatFromFile(String fileName)
		//throws UnsupportedAudioFileException, IOException 
	{
		/*
		RawAudioFormat fmt = null;
		
		try {
			// try to determine wav header
			File f = new File(fileName);	
			int wavLength = (int)(f.length()/2);
			short[] music = new short[wavLength];
			if (!f.exists()) {
				throw new IOException("File '" + fileName + "' does not exist");
			}
			if (!f.canRead()) {
				throw new IOException("Cannot read file '" + fileName + "'");
			}
			fmt = new RawAudioFormat(AudioSystem.getAudioFileFormat(f).getFormat());
			
		} catch (UnsupportedAudioFileException e) {
			// maybe it's a SPHERE header?
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			
			// check header magic
			String ln = br.readLine();
			if (!ln.startsWith("NIST_"))
				throw new UnsupportedAudioFileException();
					
			fmt = new RawAudioFormat();
			
			// determine header length
			fmt.hs = Integer.parseInt(br.readLine().trim());
			
			// determine attributes
			while ((ln = br.readLine()) != null) {
				String [] p = ln.toLowerCase().split(" ");
				
				// header end?
				if (ln.equals("end_head"))
					break;
				
				// skip empty lines
				if (p.length == 0)
					continue;
				
				if (p[0].equals("channel_count")) {
					if (Integer.parseInt(p[2]) != 1) {
						throw new UnsupportedAudioFileException("SPHERE: only 1 channel supported!");
					}
				} else if (p[0].equals("sample_rate")) {
					fmt.sr = Integer.parseInt(p[2]);					
				} else if (p[0].equals("sample_coding")) {
					if (p[2].equals("pcm") || p[2].equals("pcm-2")) {
						fmt.bd = 16; fmt.fs = 2;
					} else if (p[2].equals("pcm-1")) {
						fmt.bd = 8; fmt.fs = 1;
					} else if (p[2].equals("ulaw")) {
						fmt.ulaw = true; fmt.alaw = false;
					} else if (p[2].equals("ulaw,embedded-shorten-v2.00")) {
						fmt.ulaw = true; fmt.alaw = false;
					} else if (p[2].equals("alaw")) {
						fmt.alaw = true; fmt.ulaw = false;
					}
					else
						throw new UnsupportedAudioFileException("SPHERE: unsupported sample coding \"" + p[2] + "\"");
				} else if (p[0].equals("sample_byte_format")) {
					if (p[2].equals("01") || p[2].equals("1") || p[2].equals("N"))
						fmt.littleEndian = true;
					else if (p[2].equals("10"))
						fmt.littleEndian = false;
					else
						throw new UnsupportedAudioFileException("SPHERE: unsupported sample byte format");
				} else if (p[0].equals("sample_n_bytes")) {
					fmt.fs =  Integer.parseInt(p[2]);
					fmt.bd = fmt.fs * 8;
				}
				
				// ignore other parameters
			}
		}
		return fmt;*/
		return new RawAudioFormat(16,8000);
	}
	
		
	/**
	 * Generate a RawAudioFormat from the given parameterString. Possible strings 
	 * are: 
	 * "f:path-to-file": extract format from file
	 * "t:template-name": get format from template: ssg/[8,16], alaw/[8,16], ulaw/[8,16]
	 * "r:bit-rate,sample-rate,signed(0|1),little-endian(0|1)": raw as specified (noheader)
	 * 
	 * @param parameterString parameter string (f:filename, t:template, r:raw-params)
	 * @return ready-to-use format instance
	 * 
	 * @throws MalformedParameterStringException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * 
	 * @see getAudioFormat
	 */
	public static RawAudioFormat create(String parameterString)
		//throws MalformedParameterStringException, UnsupportedAudioFileException, IOException
	{
		
		/*if (parameterString.startsWith("f:"))
			return getRawAudioFormatFromFile(parameterString.substring(2));
		else if (parameterString.startsWith("t:"))
			return getRawAudioFormat(parameterString.substring(2));
		else if (parameterString.startsWith("r:")) {
			String [] h = parameterString.substring(2).split(",");
			if (h.length == 4) {
				return new RawAudioFormat(Integer.parseInt(h[0]), 
											Integer.parseInt(h[1]),
											h[2].equals("1"),
											h[3].equals("1"),
											0);
			} else
				throw new MalformedParameterStringException("wrong raw format");
		} else
			throw new MalformedParameterStringException("wrong format");*/
		return getRawAudioFormatFromFile(parameterString);
		
	}

	/*public static void main(String [] args) {
		System.out.println("predefined sampled.RawAudioFormat values:");
		System.out.print(getPredefinedAudioFormats());
	}*/
}
