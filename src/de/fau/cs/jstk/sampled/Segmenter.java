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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * a simple class for speech/silence segmentation based on short-time energy.
 * 
 * in short: 
 * - call processSamples(), e.g. from a recording thread. Should be fast.
 * - call update() as often as you will, from a different thread. Only then, you will see the
 *   changes made by the samples processed in the meantime. update() and processSamples() should be 
 *   synchronized. Care has been taken to minimize blocking time of update(). 
 * 
 * @author hoenig
 */
public class Segmenter {
	
	private int samplingRate;
	double windowDuration;
	int windowSamples;
	int maskRadius;
	boolean doPreemphasis;
	private double minSNR;	
		
	List<Double> energy = new LinkedList<Double>();
	//List<Boolean> isSaturated = new LinkedList<Boolean>();
	List <Double> maxAmp = new LinkedList<Double>();
	boolean [] isSpeech = null;	
	
	double threshold;
	double speech;
	double silence;
	
	double samplesSum = 0.0;
	int samplesNSummed = 0;

	private double baseEnergy;
	private double maxEnergy;

	/**
	 * mutex to control access to tmpEnergy, tmpMaxAmp, leftOverSamples, nSamplesEaten
	 */	
	Semaphore mutex = new Semaphore(1);	
	
	List<Double> tmpEnergy = new LinkedList<Double>();
	List<Double> tmpMaxAmp = new LinkedList<Double>();

	double [] leftOverSamples = null;
	private boolean rawSpeechAtEnd = false;
	
	
	/**
	 * 
	 * @param sampleRate
	 * @param desiredWindowDuration size of the analysis windows in seconds. 
	 * Note: there is no overlap between the windows.
	 * Note: actual window size can be different from desiredWindowDuration. Use getWindowDuration().
	 * @return 
	 */
	public Segmenter(int samplingRate, double desiredWindowDuration, double smoothingLength,
			double minSNR){
		this.setSamplingRate(samplingRate);
		
		windowSamples = (int)Math.round(desiredWindowDuration * samplingRate);
		maskRadius = (int)Math.round(smoothingLength / desiredWindowDuration / 2);
		
		this.windowDuration = (double)windowSamples / (double)samplingRate;
		
		/* ups, pre-emphasis had disastrous effects. */
		this.doPreemphasis = false;
		
		this.setMinSNR(minSNR);
		
		double [] baseSignal = {-1.0/32767.0, 1.0/32767.0, -1.0/32767.0, 1.0/32767.0};		
		baseEnergy = computeEnergy(baseSignal);
	
		double [] maxSignal = {-1.0, 1.0, -1.0, 1.0};		
		maxEnergy = computeEnergy(maxSignal);
		
	}
	
	/**
	 * init with default values
	 * @param samplingRate
	 */
	public Segmenter(int samplingRate){
		this(samplingRate, 0.025, 0.2, 10.0);
	}
	
	/**
	 * @return size of analysis window in seconds
	 */
	public double getWindowDuration(){
		return windowDuration;		
	}
	
	/**
	 * 
	 * @return number of analysis windows
	 */
	public int getNWindows(){
		return energy.size();
	}
	
	/**
	 * @return duration of processed samples in seconds
	 */
	public double getDuration(){
//		
//		System.err.println("samplesProcessed = " + samplesProcessed + " = [sec] " +
//				(double)samplesProcessed / samplingRate +
//				", but returning windowDuration * getNWindows() = " + 
//				windowDuration * getNWindows()
//				);
	
		return windowDuration * getNWindows();		
	}
	
	
	/**
	 * 
	 * @param i
	 * @return whether there's speech at window i
	 */
	public boolean isSpeech(int i){
		return isSpeech[i];		
	}	
	/**
	 * 
	 * @param i
	 * @return @return whether there's silence at window i
	 */
	public boolean isSilence(int i){
		return !isSpeech(i);		
	}
	
	/**
	 * @return whether the latest analysis window contains speech
	 */
	public boolean isSpeechLately(){
		if (getNWindows() == 0)
			return false;
		return isSpeech(getNWindows() - 1);		
	}
	/**
	 * @return whether the latest analysis window contains silence
	 */
	public boolean isSilenceLately(){
		return !isSpeechLately();		
	}
	

	/**
	 * 
	 * @param start
	 * @return whether the signal was saturated after start
	 */
	public boolean hasSaturation(double start) {
		if (getNWindows() == 0)
			return false;
		
		int startFrame = (int)(start / windowDuration);
		int i;
		for (i = startFrame; i < getNWindows(); i++)
			if (isSaturated(i))
				return true;
		return false;
	}
	
	/**
	 * @param start
	 * @return when (in seconds, measured from start of processed samples) the last 
	 * saturated value was observed, counting after start. returns -1 if no
	 * saturation has been observed after start. 
	 */
	public double lastSaturation(double start){
		int startFrame = (int)(start / windowDuration);
		int i;
		for (i = getNWindows() - 1; i>= startFrame; i--)
			if (isSaturated(i))
				return windowDuration * i;
		return -1.0;		
	}
	
	/**
	 * 
	 * @return true if any saturation has been observed
	 */
	public boolean hasSaturation(){
		return hasSaturation(0.0);
	}
	

	private boolean isSaturated(int frame) {
		return maxAmp.get(frame) > 0.999;		
	}
	
	/**
	 * 
	 * @param start
	 * @return whether there's a speech frame after start
	 */
	public boolean hasSpeech(double start){
		int i;
		
		if (getNWindows() == 0)
			return false;		
		
		if (getSNR() < getMinSNR())
			return false;		
		
		int startFrame = (int)(start / windowDuration);
		if (startFrame < 0)
			throw new ArrayIndexOutOfBoundsException("startFrame = " + startFrame);
			//startFrame = 0;
		if (startFrame >= getNWindows()){
			throw new ArrayIndexOutOfBoundsException("startFrame = " + startFrame + " >= getNWindows() = " + getNWindows());
//			if (startFrame > getNWindows())
//				System.err.println("startFrame = " + startFrame + " is nonsense");
//			startFrame = getNWindows() - 1;
		}
		for (i = startFrame; i < getNWindows(); i++)
			if (isSpeech(i))
				return true;
		
		return false;		
	}
	
	/**
	 * whether any speech has been observed since start
	 * @return
	 */
	public boolean hasSpeech(){
		return hasSpeech(0.0);
	}

	/**
	 * eat up any number of samples, update silence segmenting
	 * NOTE (new!): you have to call update() manually before you see any changes!
	 * @param samples
	 */
	public void processSamples(double [] samples){
		
		try {
			mutex.acquire();
		} catch (InterruptedException e) {			
			//e.printStackTrace();
			System.err.println("Segmenter.processSamples: interrupted");
			return;
		}		
		
		if (leftOverSamples != null){
			double [] tmp = new double[samples.length + leftOverSamples.length];
			System.arraycopy(leftOverSamples, 0, tmp, 0,                      leftOverSamples.length);
			System.arraycopy(samples, 0,         tmp, leftOverSamples.length, samples.length);
			samples = tmp;
		}
		int pos = 0;
		
		while(samples.length - pos > windowSamples){		
			double [] window = Arrays.copyOfRange(samples, pos, pos + windowSamples);
			tmpMaxAmp.add(computeMaxAmp(window));
			tmpEnergy.add(computeEnergy(window));	
			pos += windowSamples;		
		}
		
		leftOverSamples = Arrays.copyOfRange(samples, pos, samples.length);		
		
		mutex.release();
		
		// outdated
		//update();		
	}

	private double computeMaxAmp(double[] window) {
		double max = 0;
		for (double d : window){
			double abs = Math.abs(d);
			if (abs > max){
				max = abs;
			}
		}
		return max;
	}

	private double computeEnergy(double[] samples) {

		/*
		 * FIXME: the effect of pre-emphasis depends on the samplingRate.
		 */		
		double alpha = 1.0;
		int i;
		if (doPreemphasis){
			// make a copy of samples, so we don't overwrite the original
			samples = Arrays.copyOf(samples, samples.length);
			
			for (i = samples.length - 1; i > 0; i--)
				samples[i] -= alpha * samples[i - 1];
			samples[0] = samples[1];			
		}		
		
		// update mean (I know, not necessary if doPreemphasis and alpha = 1)
		for (double d : samples){
			samplesSum += d;
			samplesNSummed++;
		}
		double mean = samplesSum / samplesNSummed;

		// energy, after DC subtraction
		
		double energy = 0;
		for (i = 0; i < samples.length; i++){
			double tmp = samples[i] - mean;
			energy += tmp * tmp;
		}
		energy /= samples.length;
		
		return energy;		
	}

	/**
	 * TODO: description of what is happening 
	 * 
	 * @return current estimate of signal-to-noise ratio. Any NaN value is substituted by 0.
	 */
	public double getSNR(){
		if (getNWindows() == 0)
			throw new ArrayIndexOutOfBoundsException("no windows observed yet");
		double snr = energyToDB(speech) - energyToDB(silence);
		if (Double.isNaN(snr))
			return 0;
		return snr;
	}
	
	public double getSpeechEnergy(){
		if (getNWindows() == 0)
			throw new ArrayIndexOutOfBoundsException("no windows observed yet");
		return speech;
	}
	
	public double getTerminalSilence(){
		return getTerminalSilence(0.0);
	}
	
	/**
	 * 
	 * @param start
	 * @return duration of silence at end of utterance (but not counting before @start)
	 */
	public double getTerminalSilence(double start){
		if (!hasSpeech(start))
			return getDuration();
		
		int i;
		int n = 0;
		int startFrame = (int)(start / windowDuration);		
		
		for (i = getNWindows() - 1; i >= startFrame; i--){
			if (isSpeech(i))
				break;
			n++;
		}
		return (double) n * getWindowDuration();		
	}

	/**
	 * 
	 * @param start
	 * @return duration of silence after @start
	 */
	public double getInitialSilence(double start){
		if (!hasSpeech(start))
			return getDuration();
		
		int i;
		int n = 0;
		int startFrame = (int)(start / windowDuration);
		
		for (i = startFrame; i < getNWindows() - 1; i++){
			if (isSpeech(i))
				break;
			n++;
		}
		return (double) n * getWindowDuration();		
	}
	
	public double getInitialSilence(){
		return getInitialSilence(0.0);
	}

	
	public double getEnergy() {
		return getEnergy((getNWindows() - 1) * windowDuration);
	}
	
	public double getMaxEnergy() {
		if (getNWindows() == 0)
			return 0.0;		
		double max = getEnergy(0);
		int i;
		for (i = 0; i < getNWindows(); i++)
			if (energy.get(i) > max)
				max = energy.get(i);
		return max;
	}
	
	/**
	 * 
	 * @param time in seconds
	 * @return energy (in [0;1]) at @time
	 */
	public double getEnergy(double time) {
		int frame = (int)Math.round(time / windowDuration + 0.001);
		if (frame < 0)
			frame = 0;

		return energy.get(frame) / maxEnergy;	
	}	

	public double getMaxAmp() {
		return getMaxAmp((getNWindows() - 1) * windowDuration);
	}
	
	/**
	 * 
	 * @param time in seconds
	 * @return energy (in [0;1]) at @time
	 */
	public double getMaxAmp(double time) {
		
		int frame = (int)Math.round(time / windowDuration + 0.001);
		
		return maxAmp.get(frame);	
	}
	
	private double energyToDB(double energy){
		return 10.0 * Math.log10(energy + baseEnergy);
	}
	
	public void update() throws InterruptedException{		
		
		// fetch new data, synchronized
//		try {
			mutex.acquire();
//		} catch (InterruptedException e) {			
//			e.printStackTrace();
//			return;
//		}		
		
		energy.addAll(tmpEnergy);
		tmpEnergy.clear();
		maxAmp.addAll(tmpMaxAmp);
		tmpMaxAmp.clear();
		
		mutex.release();
		
		// actual updating, can happen unsynchronized
		
		computeLevels();
		
		if (getNWindows() > 0)
			rawSpeechAtEnd = isSpeech(getNWindows() - 1);			
		
		smooth();
		/*
		System.err.print(String.format("silence %f, speech %f\n",
				silence, speech));
				*/
	}
	
	private void smooth(){
		//int i;
					
		
		/* smoothing operations */
		
        		
		//erosion(isSpeech, 2);
		//dilation(isSpeech, 3);
		
		
		closing(isSpeech, maskRadius);
		opening(isSpeech, maskRadius);

	}


	private void opening(boolean [] v, int radius){
		erosion(v, radius);
		dilation(v, radius);		
	}
	
	private void closing(boolean [] v, int radius){		
		dilation(v, radius);
		erosion(v, radius);
	}
	
	// helper for dilation/erosion
	private static void smearRight(boolean [] v, int radius, boolean smearTrue){
		int i;
		int num = -1;
		for (i = 0; i < v.length; i++){
			if (v[i] == smearTrue)
				num = radius;
			if (num < 0)
				v[i] = !smearTrue;
			else
				v[i] = smearTrue;
			num--;
		}
	}
	
	// helper for dilation/erosion
	private static void smearLeft(boolean [] v, int radius, boolean smearTrue){
		int i;
		int num = -1;
		for (i = v.length - 1; i >= 0; i--){
			if (v[i] == smearTrue)
				num = radius;
			if (num < 0)
				v[i] = !smearTrue;
			else
				v[i] = smearTrue;
			num--;
		}
	}
	
	private static void dilation(boolean [] v, int radius){
		/* left to right */
		smearRight(v, radius, true);
		
		/* right to left */
		smearLeft(v, radius, true);
	}
	
	private void erosion(boolean [] v, int radius){
		/* left to right */
		smearRight(v, radius, false);
		
		/* right to left */
		smearLeft(v, radius, false);		
	}

	private void computeLevels() {
		
		int i;			
		
		/* threshold: lower envelope by exp(mean(log(energies)))*/
		double threshold = 0.0;		
		for (Double d : energy){			
			threshold += Math.log(d + baseEnergy);			
		}				
		threshold = Math.exp(threshold / getNWindows());
		this.threshold = threshold;		
		
		this.isSpeech = computeSpeech();		
		
		/* speech level */
		double speech = 0.0;
		int nSpeech = 0;			
		for (i = 0; i < getNWindows(); i++){
			if (!isSpeech(i))
				continue;
			speech += energy.get(i);
			nSpeech++;
		}
		speech /= nSpeech;
		this.speech = speech;
		
		/* silence level: lower envelope of silence energies by exp(mean(log(silence energies)))*/
		double silence = 0.0;
		int nSilence = 0;
		
		for (i = 0; i < getNWindows(); i++){
			if (!isSilence(i))
				continue;
			silence += Math.log(energy.get(i) + baseEnergy);
			nSilence++;
		}
		silence = Math.exp(silence / nSilence);
		this.silence = silence;
		
		// fixes possible NaN: 
		if (nSpeech == 0)
			this.speech = this.silence;
		else if (nSilence == 0)
			this.silence = this.speech;
	}

	private boolean[] computeSpeech() {
		/* silence/speech */
		int i;
		boolean [] isSpeech = new boolean[getNWindows()];	
		for (i = 0; i < getNWindows(); i++)
			isSpeech[i] = energy.get(i) >= threshold;	
		
		return isSpeech;
	}
	
	public String toString(){
		String out = "";
		int i;
		for (i = 0; i < getNWindows(); i++)		
			out += String.format("%g %g %g %s\n",
					energy.get(i),					
					threshold,
					isSilence(i) ? silence : speech,
					isSilence(i));
		
		return out;
	}
	
	public static final String SYNOPSIS = 
		"usage: sampled.Segmenter wav-file > gnuplot-output.txt\n" +
		"(attempt to) segment into speech and silence.";
	
	public static void main(String [] args) throws Exception {
		if (args.length < 1) {
			System.err.println(SYNOPSIS) ;
			System.exit(1);
		}
		
		AudioSource source = new AudioFileReader(args[0], null, true);
		System.err.println("samplingRate = " + source.getSampleRate());
		Segmenter segmenter = new Segmenter(source.getSampleRate(), 0.025, 0.1,
				5);
		
		double [] buf = new double[1000];
		int read;
		while((read = source.read(buf)) > 0){
			double [] usedBuffer;
			if (read < buf.length)
				usedBuffer = Arrays.copyOf(buf, read);
			else
				usedBuffer = buf;
			segmenter.processSamples(usedBuffer);			
		}		
		System.out.print(segmenter);
		System.err.println("snr = " + segmenter.getSNR());
	}

	public void setSamplingRate(int samplingRate) {
		this.samplingRate = samplingRate;
	}

	public int getSamplingRate() {
		return samplingRate;
	}

	public void setMinSNR(double minSNR) {
		this.minSNR = minSNR;
	}

	public double getMinSNR() {
		return minSNR;
	}


	/**
	 * whether the last frame, *unsmoothed*, is speech 
	 * @return
	 */
	public boolean isRawSpeechAtEnd() {
		return rawSpeechAtEnd;
	}


}
