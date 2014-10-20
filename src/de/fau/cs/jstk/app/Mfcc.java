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
package de.fau.cs.jstk.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.framed.DCT;
import de.fau.cs.jstk.framed.FFT;
import de.fau.cs.jstk.framed.FilterBank;
import de.fau.cs.jstk.framed.FilterBank.Vtln;
import de.fau.cs.jstk.framed.MVN;
import de.fau.cs.jstk.framed.Selection;
import de.fau.cs.jstk.framed.Slope;
import de.fau.cs.jstk.framed.SpectralTransformation;
import de.fau.cs.jstk.framed.Window;
import de.fau.cs.jstk.io.FrameOutputStream;
import de.fau.cs.jstk.io.FrameSource;
import de.fau.cs.jstk.sampled.AudioCapture;
import de.fau.cs.jstk.sampled.AudioFileReader;
import de.fau.cs.jstk.sampled.AudioSource;
import de.fau.cs.jstk.sampled.RawAudioFormat;

/**
 * Feature extraction for ASR and Speaker ID. If you change anything, please 
 * increase FEX_VERSION and update LAST_AUTHOR. Feel free to modify CONTRIBUTORS.
 * If you change defaults, do so using the class variables DEFAULT_*
 * 
 * @author sikoried
 *
 */
public class Mfcc implements FrameSource {
	private static final double FEX_VERSION = 1.2;
	private static final String LAST_AUTHOR = "sikoried";
	private static final String CONTRIBUTORS = "sikoried, bocklet, maier, hoenig, steidl";

	/** default audio format: 16 khz, 16 bit, 2b/frame, signed, linear */
	private RawAudioFormat format = new RawAudioFormat();
	
	/**
	 * public AudioTrack (int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode)
	 */
	//private AndroidAudioTrack track=new AndroidAudioTrack();
	//private AudioFormat androidformat= new AudioFormat();
	
	
	private AudioSource asource = null;
	private FrameSource window = null;
	private FrameSource pspec = null;
	private FrameSource melfilter = null;
	private FrameSource dct = null;
	private FrameSource selection = null;
	private FrameSource deltas = null;
	private MVN mvn = null;
	
	private FrameSource output = null;
	
	private void initializeAudio(String inFile, String parameterString) throws Exception {
		if (parameterString != null)
			format = RawAudioFormat.create(parameterString);
		
		if (inFile == null || inFile.equals("-"))
			asource = new AudioCapture(format.getBitRate(), format.getSampleRate());
		else if (inFile.startsWith("mixer:"))
			asource = new AudioCapture(inFile.substring(6), (inFile.length() == 6), format.getBitRate(), format.getSampleRate(), 0);
		else
			asource = new AudioFileReader(inFile, format, true);
	}
	
	private void initializeWindow(String parameterString) throws Exception {
		window = Window.create(asource, parameterString);
	    output = window;
	}
	
	private void initializePowerSpectrum() throws Exception {
		pspec = new FFT(output, true, true); // we need to set normalization to false if short time energy is desired!!
		output = pspec;
	}
	
	private void initializeMelfilter(String parameterString, Vtln vtln) throws Exception {
		if (parameterString.startsWith("manual:"))
			melfilter = FilterBank.generateFilterBank((SpectralTransformation) output, true, parameterString.substring(7));
		else
			melfilter = FilterBank.generateMelFilterBank((SpectralTransformation) output, parameterString, vtln);
		output = melfilter;
	}
	
	private void initializeDCT() throws Exception {
		dct = new DCT(output, true);
		output = dct;
	}
	
	private void initializeSelection(String parameterString, boolean doShortTimeEnergy) throws Exception {
		if (parameterString == null)
			selection = new Selection(output);
		else
			selection = Selection.create(output, parameterString);
		
		((Selection) selection).setShortTimeEnergy(doShortTimeEnergy);
		
		output = selection;
	}
	
	private void initializeDeltas(String parameterString) throws Exception {
		if (parameterString == null)
			return;
		
		deltas = Slope.create(output, parameterString);
		output = deltas;
	}
	
	private void initializeMVN(String parameterFile) throws Exception {
		mvn = new MVN(output, parameterFile);
		output = mvn;
	}
	
	public FrameSource getSource() {
		return output;
	}
	
	/**
	 * Initialize the new MFCC object using the given parameter strings. If a 
	 * parameter String is null, the default constructor is called, or nthe object
	 * is not integrated in the pipe line (deltas, CMS)
	 * @param inFile file name to open
	 * @param pAudio Audio format parameter string, e.g. t:ssg/16
	 * @param pWindow Window function to use, e.g. hamm,25,10
	 * @param pFilterbank Mel filter bank parameters, e.g. 0,8000,-1,.5
	 * @param noDCT Flag if the cepstrum computation should be EXCLUDED
	 * @param doShortTimeEnergy Flag to include the short time band energy (instead of the 0th coefficient)
	 * @param pSelection Perform a selection on the feature vector (usually 0-11)
	 * @param pDeltas Derivatives to compute, e.g. 1:5,2:3
	 * @param mvnParamFile Parameter file for mean and variance normalization (if applicable)
	 * @throws Exception
	 */
	public Mfcc(String inFile, String pAudio, String pWindow, 
			String pFilterbank, boolean noDCT, boolean doShortTimeEnergy, 
			String pSelection, String pDeltas, String mvnParamFile) throws Exception {
		this(inFile, pAudio, pWindow, pFilterbank, noDCT, doShortTimeEnergy, pSelection, pDeltas, mvnParamFile, null);
	}
	
	/**
	 * Initialize the new MFCC object using the given parameter strings. If a 
	 * parameter String is null, the default constructor is called, or nthe object
	 * is not integrated in the pipe line (deltas, CMS)
	 * @param inFile file name to open
	 * @param pAudio Audio format parameter string, e.g. t:ssg/16
	 * @param pWindow Window function to use, e.g. hamm,25,10
	 * @param pFilterbank Mel filter bank parameters, e.g. 0,8000,-1,.5
	 * @param noDCT Flag if the cepstrum computation should be EXCLUDED
	 * @param doShortTimeEnergy Flag to include the short time band energy (instead of the 0th coefficient)
	 * @param pSelection Perform a selection on the feature vector (usually 0-11)
	 * @param pDeltas Derivatives to compute, e.g. 1:5,2:3
	 * @param mvnParamFile Parameter file for mean and variance normalization (if applicable)
	 * @throws Exception
	 */
	public Mfcc(String inFile, String pAudio, String pWindow, 
			String pFilterbank, boolean noDCT, boolean doShortTimeEnergy, 
			String pSelection, String pDeltas, String mvnParamFile, Vtln vtln) 
		throws Exception {
		initializeAudio(inFile, pAudio);
		initializeWindow(pWindow);
		initializePowerSpectrum();
		
		if (pFilterbank != null)
			initializeMelfilter(pFilterbank, vtln);
		
		if (!noDCT)
			initializeDCT();
		
		initializeSelection(pSelection, doShortTimeEnergy);

		if (pDeltas != null)
			initializeDeltas(pDeltas);
		
		if (mvnParamFile != null) 
			initializeMVN(mvnParamFile);
	}
	
	/**
	 * Initialize the new MFCC object using the given parameter strings. If a 
	 * parameter String is null, the default constructor is called, or nthe object
	 * is not integrated in the pipe line (deltas, CMS)
	 * @param inFile file name to open
	 * @param pAudio Audio format parameter string, e.g. t:ssg/16
	 * @param pWindow Window function to use, e.g. hamm,25,10
	 * @param pFilterbank Mel filter bank parameters, e.g. 0,8000,-1,.5
	 * @param noDCT Flag if the cepstrum computation should be EXCLUDED
	 * @param doShortTimeEnergy Flag to include the short time band energy (instead of the 0th coefficient)
	 * @param pSelection Perform a selection on the feature vector (usually 0-11)
	 * @param pDeltas Derivatives to compute, e.g. 1:5,2:3
	 * @param mvnParamFile Parameter file for mean and variance normalization (if applicable)
	 * @throws Exception
	 */
	public Mfcc(InputStream is, String pAudio, String pWindow, 
			String pFilterbank, boolean noDCT, boolean doShortTimeEnergy, 
			String pSelection, String pDeltas, MVN mvn) throws Exception {
		this(is, pAudio, pWindow, pFilterbank, noDCT, doShortTimeEnergy, pSelection, pDeltas, mvn, null);
	}
	
	/**
	 * Initialize the new MFCC object using the given parameter strings. If a 
	 * parameter String is null, the default constructor is called, or nthe object
	 * is not integrated in the pipe line (deltas, CMS)
	 * @param inFile file name to open
	 * @param pAudio Audio format parameter string, e.g. t:ssg/16
	 * @param pWindow Window function to use, e.g. hamm,25,10
	 * @param pFilterbank Mel filter bank parameters, e.g. 0,8000,-1,.5
	 * @param noDCT Flag if the cepstrum computation should be EXCLUDED
	 * @param doShortTimeEnergy Flag to include the short time band energy (instead of the 0th coefficient)
	 * @param pSelection Perform a selection on the feature vector (usually 0-11)
	 * @param pDeltas Derivatives to compute, e.g. 1:5,2:3
	 * @param mvnParamFile Parameter file for mean and variance normalization (if applicable)
	 * @throws Exception
	 */
	public Mfcc(InputStream is, String pAudio, String pWindow, 
			String pFilterbank, boolean noDCT, boolean doShortTimeEnergy, 
			String pSelection, String pDeltas, MVN mvn, Vtln vtln) 
		throws Exception {
		
		format = RawAudioFormat.create(pAudio);
		asource = new AudioFileReader(is, format, true);

		initializeWindow(pWindow);
		initializePowerSpectrum();
		
		if (pFilterbank != null)
			initializeMelfilter(pFilterbank, vtln);
		
		if (!noDCT)
			initializeDCT();
		
		initializeSelection(pSelection, doShortTimeEnergy);

		if (pDeltas != null)
			initializeDeltas(pDeltas);
		
		if (mvn != null) {
			mvn.setSource(output);
			output = mvn;
		}
	}

	public String describePipeline() {
		StringBuffer buf = new StringBuffer();
		LinkedList<String> reverse = new LinkedList<String>();
		FrameSource fs = output;
		while (fs != null) {
			reverse.add(fs.toString());
			fs = fs.getSource();
		}
		for (int i = reverse.size() - 1; i >= 0; --i)
			buf.append(reverse.get(i) + "\n");
		return asource.toString() + "\n" + buf.toString();
	}
	
	public void tearDown() throws IOException {
		asource.tearDown();
	}
	
	public void setVarianceNormalization(boolean flag) {
		if (mvn != null)
			mvn.setNormalizations(true, flag);
	}
	
	private long nframes = 0;
	
	public boolean read(double [] buf) throws IOException {
		boolean val = output.read(buf);
		
		// a last numerical check!
		for (int i = 0; i < buf.length; ++i) {
			if (Double.isInfinite(buf[i]))
				throw new IOException("bin.Mfcc.read(): Faulty frame! infinity at frame #" + nframes + "[" + i + "]!");
			if (Double.isNaN(buf[i]))
				throw new IOException("bin.Mfcc.read(): Faulty frame! not a number at frame #" + nframes + "[" + i + "]!");
		}
		
		nframes++;
		
		return val;
	}
	
	public int getFrameSize() {
		return output.getFrameSize();
	}

	/** 8kHz, 16bit, signed, little endian, linear */
	public static String DEFAULT_AUDIO_FORMAT = "t:ssg/8";
	
	/** Hamming window of 16ms, 10ms shift */
	public static String DEFAULT_WINDOW = "hamm,32,16";
	
	/** Filter bank 188Hz-6071Hz, 226.79982mel band width, 50% filter overlap */
	public static String DEFAULT_MELFILTER = "188,6071,226.79982,0.5";
	
	/** Deltas to compute (null = none) */
	public static String DEFAULT_DELTAS = null;
	
	/** Static features to select after DCT */
	public static String DEFAULT_SELECTION = "0-18";
	
	/** Program synopsis */
private static final String SYNOPSIS = 
		"mfcc feature extraction v " + FEX_VERSION + "\n" +
		"last author: " + LAST_AUTHOR + "\n" +
		"contributors: " + CONTRIBUTORS + "\n\n" +
		"usage: app.Mfcc [options]\n\n" +
		"file options:\n\n" +
		"-i in-file\n" +
		"  use the given file for input; use \"-i -\" for default microphone\n" +
		"  input, or \"mixer:mixer-name\" for (and only for) a specific mixer\n" +
		"-o out-file\n" +
		"  use the given file for output (header + double frames; default: STDOUT)\n" +
		"--in-out-list listfile\n" +
		"  the list contains lines \"<in-file> <out-file>\" for batch processing\n" +
		"--in-list listfile directory\n" +
		"  contains lines \"<file>\" for input; strips directory from input files,\n" +
		"  and stores them in <directory>\n" +
		"--dir <directory>\n" +
		"  use this to specify a directory where the audio files are located.\n"+
		"\n" +
		"audio format options:\n\n" +
		"-f <format-string>\n" +
		"  \"f:path-to-file-with-header\": load audio format from file\n" +
		"  \"t:template-name\": use an existing template (ssg/[8,16], alaw/[8,16], ulaw/[8,16]\n" +
		"  \"r:bit-rate,sample-rate,signed(0,1),little-endian(0,1)\": specify raw format (no-header)\n" +
		"  default: \"" + DEFAULT_AUDIO_FORMAT + "\"\n" +
		"\n" +
		"feature extraction options:\n\n" +
		"-w \"<hamm|hann|rect>,<length-ms>,<shift-ms>\"\n" +
		"  window function (Hamming, Hann, Rectangular), length of window and \n" +
		"  shift time (in ms)\n" +
		"  default: \"" + DEFAULT_WINDOW + "\"\n" +
		"--no-filterbank\n" +
		"  Do NOT apply a filterbank at all\n" +
		"-b \"<startfreq-hz>,<endfreq-hz>,<bandwidth-mel>,<val>\"\n" +
		"  mel filter bank; val < 1. : minimum overlap <val>; val > 1 : minimum\n" +
		"  number of filters; if you wish to leave a certain field at default,\n" +
		"  put a value below 0.\n" +
		"  default: \"" + DEFAULT_MELFILTER + "\"\n" +
		"-b manual:filter0:filter1:filter2:...\n" +
		"  where filter := <type>,<shape>,<start>,<end>\n" +
		"  Generate the filterbank manually by specifying every filter individually\n" +
		"  type  : [m]el or [f]req\n" +
		"  shape : [r]ectangular or [t]riangular\n" +
		"  start : start frequency of the filter (related to type)\n" +
		"  end   : end frequency of the filter (related to type)\n" +
		"--no-ste\n" +
		"  Do NOT compute the short time energy (STE) and use instead the 0th cepstral coefficient.\n " +
		"  If you want both STE and 0th, put \"0,0-11\" (or similar) to your selection.\n" +
		"--only-spectrum\n" +
		"  Do NOT apply DCT after the filtering\n" +
		"-s <selection-string>\n" +
		"  Select the static features to use and in which order, e.g. \"0,3-8,1\"\n" +
		"  default: \"" + DEFAULT_SELECTION + "\"\n" +
		"-m <mvn-file>\n" +
		"  use statistics saved in <mvn-file> for mean and variance normalization (MVN)\n" +
		"--generate-mvn-file <mvn-file>\n" +
		"  computes mean and variance statistics on the given file(list) and saves\n" +
		"  it to <mvn-file>\n" +
		"--turn-wise-mvn\n" +
		"  Apply MVN to each turn; this is an individual offline mean and variance\n" +
		"  normalization\n" +
		"--novar\n" +
		"  No variance normalization\n" +
		"-d \"[tirol,]context:order[:scale][,context:order[:scale]]+\"\n" +
		"  compute oder <order> derivatives over context <context>, and optionally\n" +
		"  scale by <scale>, separate multiple derivatives by comma; deltas are\n" +
		"  concatenated to static features in the same order as specified in the\n" +
		"  argument; setting tirol results in a smoothing of the static variables\n" + 
		"  default: \"" + DEFAULT_DELTAS + "\"\n\n" +
		"-h | --help\n" +
		"  display this help text\n\n" +
		"--show-pipeline\n" +
		"  initialize and print feature pipeline to STDERR\n";
	
	public static void main(String[] args) throws Exception {
		
		boolean displayHelp = false;
		boolean showPipeline = false;
		
		boolean generateMVNFile = false;
		
		boolean turnwisemvn = false;
		boolean noFilterbank = false;
		boolean onlySpectrum = false;
		boolean doShortTimeEnergy = true;

		boolean novar = false;
		
		String inFile = null;
		String outFile = null;
		String outDir = null;
		String listFile = null;
		String mvnParamFile = null;
		String audioDir = null;
		
		String audioFormatString = DEFAULT_AUDIO_FORMAT;
		String windowFormatString = DEFAULT_WINDOW;
		String filterFormatString = DEFAULT_MELFILTER;
		String selectionFormatString = DEFAULT_SELECTION;
		String deltaFormatString = DEFAULT_DELTAS;
		
		Vtln vtln = null;
		
		if (args.length > 1) {
			// process arguments
			for (int i = 0; i < args.length; ++i) {
				
				// file options
				if (args[i].equals("--in-out-list"))
					listFile = args[++i];
				else if (args[i].equals("-i"))
					inFile = args[++i];
				else if (args[i].equals("-o"))
					outFile = args[++i];
				else if (args[i].equals("--in-list")) {
					listFile = args[++i];
					outDir = args[++i];
				}
				
				// prefix for audio input directory
				else if(args[i].equals("--audioinputdir") || args[i].equals("--dir"))
					audioDir = args[++i];
				
				// audio format options
				else if (args[i].equals("-f"))
					audioFormatString = args[++i];
				
				// window options
				else if (args[i].equals("-w")) 
					windowFormatString = args[++i];
				
				// mel filter bank options
				else if (args[i].equals("-b"))
					filterFormatString = args[++i];
				
				// selection
				else if (args[i].equals("-s"))
					selectionFormatString = args[++i];
				
				// mean options
				else if (args[i].equals("-m"))
					mvnParamFile = args[++i];
				else if (args[i].equals("--generate-mvn-file")) {
					generateMVNFile = true;
					mvnParamFile = args[++i];
				} else if (args[i].equals("--turn-wise-mvn")) 
					turnwisemvn = true;
				else if (args[i].equals("--no-filterbank"))
					noFilterbank = true;
				else if (args[i].equals("--only-spectrum"))
					onlySpectrum = true;
				else if (args[i].equals("--no-ste"))
					doShortTimeEnergy = false;
				else if (args[i].equals("--vtln")) {
					vtln = new Vtln(Double.parseDouble(args[++i]), Double.parseDouble(args[++i]), Double.parseDouble(args[++i]));
				}
				else if (args[i].equals("--novar")) {
					novar = true;
				}
				// deltas
				else if (args[i].equals("-d")) {
					deltaFormatString = args[++i];
					if (deltaFormatString.equals("")) {
						deltaFormatString = null;
					}
				}
				
				// help
				else if (args[i].equals("-h") || args[i].equals("--help"))
					displayHelp = true;
				
				// show pipeline
				else if (args[i].equals("--show-pipeline"))
					showPipeline = true;
				
				else
					System.err.println("ignoring argument " + i + ": " + args[i]);
			}
		} else {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		// help?
		if (displayHelp) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		// turn off debug
		Logger.getLogger("de.fau.cs.jstk").setLevel(Level.FATAL);
		
		// consistency checks
		if (listFile != null && (inFile != null || outFile != null))
			throw new Exception("-l and (-i,-o) are exclusive!");
		if (turnwisemvn && mvnParamFile != null)
			throw new Exception("--generate-mvn-file, -m and --turnwise-mvn are exclusive");
		
		LinkedList<String> inlist = new LinkedList<String>();
		LinkedList<String> outlist = new LinkedList<String>();
		
		// read list
		if (listFile == null) {
			inlist.add(inFile);
			outlist.add(outFile);
		} else {
			BufferedReader lr = new BufferedReader(new FileReader(listFile));
			String line = null;
			int i = 1;
			while ((line = lr.readLine()) != null) {
				if (outDir == null) {
					String [] help = line.split("\\s+");
					if (help.length != 2)
						throw new Exception("file list is broken at line " + i);
					inlist.add(help[0]);
					outlist.add(help[1]);
				} else {
					String inf = (audioDir == null ? line : audioDir + System.getProperty("file.separator") + line);
					String ouf = outDir + System.getProperty("file.separator") + line;
					
					inlist.add(inf);
					outlist.add(ouf);
				}
				i++;
			}
			lr.close();
		}
		
		Mfcc mfcc = null;
		
		if (generateMVNFile) {
			MVN mvn = new MVN();
			
			while (inlist.size() > 0) {
				inFile = inlist.remove();
				
				mfcc = new Mfcc(inFile, audioFormatString, windowFormatString, 
						noFilterbank ? null : filterFormatString, onlySpectrum, 
						doShortTimeEnergy, selectionFormatString, deltaFormatString, null, vtln);
				
				mvn.extendStatistics(mfcc);
			}
			
			System.out.println("Mfcc.main(): saving mean and variance statistics to " + mvnParamFile);
			mvn.saveToFile(mvnParamFile);
			System.exit(0);
		}
		
		// if we do a turn-wise cms, we need a temporary file
		File tf = null;
		if (turnwisemvn) 
			tf = File.createTempFile(Long.toString(System.currentTimeMillis()) + Double.toString(Math.random()), ".mvn");
		
		// Do the actual feature computation and write out
		while (inlist.size() > 0) {
			// get next file
			inFile = inlist.remove(0);
			outFile = outlist.remove(0);
			
			// if there is turn-wise MVN, we need to compute the statistics first!
			if (turnwisemvn) {
				mfcc = new Mfcc(inFile, audioFormatString, windowFormatString, 
						noFilterbank ? null : filterFormatString, 
						onlySpectrum, doShortTimeEnergy, selectionFormatString, 
						deltaFormatString, null, vtln);
				
				MVN mvn = new MVN();
				mvn.extendStatistics(mfcc);
				mvn.saveToFile(tf.getCanonicalPath());
			} 

			// regular processing: if there's (temporal) MVN data, it's applied
			mfcc = new Mfcc(inFile, audioFormatString, windowFormatString, 
					noFilterbank ? null : filterFormatString, 
					onlySpectrum, doShortTimeEnergy, selectionFormatString, 
					deltaFormatString, turnwisemvn ? tf.getCanonicalPath() : mvnParamFile, vtln);
			
			if (novar)
				mfcc.setVarianceNormalization(false);
			
			// output pipeline
			if (showPipeline) {
				System.err.print(mfcc.describePipeline());
				showPipeline = false; // show it only once!
			}
			
			double [] buf = new double [mfcc.getFrameSize()];
			
			// output a text file
			FrameOutputStream writer = new FrameOutputStream(buf.length, new File(outFile));
			while (mfcc.read(buf))
				writer.write(buf);
			writer.close();
			
			// clean up the temporary file
			if (turnwisemvn)
				tf.delete();
		}
	}
}
