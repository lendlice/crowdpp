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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Yin class
 * An implementation of the YIN pitch tracking algorithm.
 * See <a href="http://recherche.ircam.fr/equipes/pcm/cheveign/ps/2002_JASA_YIN_proof.pdf">the YIN paper.</a>
 * Implementation originally based on <a href="http://aubio.org">aubio</a>
 * @author Joren Six
 */

public class Yin {

	/** Used to start and stop real time annotations. */
	private static Yin yinInstance;

	/** The YIN threshold value (see paper) */
	private final double threshold = 0.15;

	private final int bufferSize;
	private final int overlapSize;
	private final float sampleRate;
	
	/**
	 * A boolean to start and stop the algorithm.
	 * Practical for real time processing of data.
	 */
	private volatile boolean running;

	/** The original input buffer */
	private final float[] inputBuffer;

	/**
	 * The buffer that stores the calculated values.
	 * It is exactly half the size of the input buffer.
	 */
	private final float[] yinBuffer;

	private Yin(float sampleRate) {
		this.sampleRate = sampleRate;
		bufferSize = 256; // 1s = 8000 * 2 bytes = 8000 float, so 32 ms = 8 * 32 = 256
		overlapSize = bufferSize / 2;
		running = true;
		inputBuffer = new float[bufferSize];
		yinBuffer = new float[bufferSize/2];
	}

	/** Implements the difference function as described in step 2 of the YIN paper */
	private void difference(){
		float delta;
		for(int tau = 0; tau < yinBuffer.length; tau++) {
			yinBuffer[tau] = 0;
		}
		// yinBuffer.length
		for(int tau = 1 ; tau <yinBuffer.length ; tau++) {
			for(int j = 0 ; j <yinBuffer.length ; j++) {
				delta = inputBuffer[j] - inputBuffer[j+tau];
				yinBuffer[tau] += delta * delta;
			}
		}
	}

	/**
	 * The cumulative mean normalized difference function as described in step 3 of the YIN paper
	 * <br><code>
	 * yinBuffer[0] == yinBuffer[1] = 1
	 * </code>
	 */
	private void cumulativeMeanNormalizedDifference() {
		yinBuffer[0] = 1;
		//Very small optimization in comparison with AUBIO
		//start the running sum with the correct value:
		//the first value of the yinBuffer
		float runningSum = yinBuffer[1];
		yinBuffer[1] = 1;
		for(int tau = 2 ; tau < yinBuffer.length ; tau++) {
			runningSum += yinBuffer[tau];
			yinBuffer[tau] *= tau / runningSum;
		}
	}

	/** Implements step 4 of the YIN paper */
	private int absoluteThreshold(){
		//Uses another loop construct than the AUBIO implementation
		for(int tau = 1; tau < yinBuffer.length; tau++){
			if (yinBuffer[tau] < threshold){
				while(tau + 1 < yinBuffer.length && yinBuffer[tau+1] < yinBuffer[tau]) {
					tau++;
				}
				return tau;
			}
		}
		return -1;
	}

	/**
	 * Implements step 5 of the YIN paper. 
	 * It refines the estimated tau value using parabolic interpolation. 
	 * This is needed to detect higher frequencies more precisely.
	 * @param tauEstimate the estimated tau value.
	 * @return a better, more precise tau value.
	 */
	private float parabolicInterpolation(int tauEstimate) {
		float s0, s1, s2;
		int x0 = (tauEstimate < 1) ? tauEstimate : tauEstimate - 1;
		int x2 = (tauEstimate + 1 < yinBuffer.length) ? tauEstimate + 1 : tauEstimate;
		if (x0 == tauEstimate)
			return (yinBuffer[tauEstimate] <= yinBuffer[x2]) ? tauEstimate : x2;
		if (x2 == tauEstimate)
			return (yinBuffer[tauEstimate] <= yinBuffer[x0]) ? tauEstimate : x0;
		s0 = yinBuffer[x0];
		s1 = yinBuffer[tauEstimate];
		s2 = yinBuffer[x2];
		return tauEstimate + 0.5f * (s2 - s0 ) / (2.0f * s1 - s2 - s0);
	}

	/**
	 * The main flow of the YIN algorithm. 
	 * @return a pitch value in Hz or -1 if no pitch is detected.
	 */
	private float getPitch() {
		int tauEstimate = -1;
		float pitchInHertz = -1;

		//step 2
		difference();

		//step 3
		cumulativeMeanNormalizedDifference();

		//step 4
		tauEstimate = absoluteThreshold();

		//step 5
		if (tauEstimate != -1) {
			 float betterTau = parabolicInterpolation(tauEstimate);
			 
			pitchInHertz = sampleRate/betterTau;
		}
		return pitchInHertz;
	}
	
	public static void writeFile(String fileName) throws IOException {
		InputStream is = new FileInputStream(new File(fileName));
		AudioFloatInputStream afis = AudioFloatInputStream.getInputStream(is);
		Yin.processStream(afis, fileName);
	}

	public static void processStream(AudioFloatInputStream afis, String filename) throws IOException {
		float sampleRate = 8000;
		yinInstance = new Yin(sampleRate);
		int bufferStepSize = yinInstance.bufferSize - yinInstance.overlapSize;

		// read full buffer
		boolean hasMoreFloats = afis.read(yinInstance.inputBuffer, 0, yinInstance.bufferSize) != -1;
		File sdFile = new File(filename + ".YIN.pitch.txt");
		FileOutputStream fos = new FileOutputStream(sdFile, true);
				
		while(hasMoreFloats && yinInstance.running) {
			float pitch = yinInstance.getPitch();
			String text = pitch + "\n";			
			fos.write(text.getBytes());
			// slide buffer with predefined overlap
			for(int i = 0 ; i < bufferStepSize ; i++) {
				yinInstance.inputBuffer[i] = yinInstance.inputBuffer[i+yinInstance.overlapSize];
			}
			hasMoreFloats = afis.read(yinInstance.inputBuffer, yinInstance.overlapSize, bufferStepSize) != -1;
		}
		fos.close();
	}

	/** Stops real time annotation. */
	public static void stop() {
		if (yinInstance != null)
			yinInstance.running = false;
	}

}
