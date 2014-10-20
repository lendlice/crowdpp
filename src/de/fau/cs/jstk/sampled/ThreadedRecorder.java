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

import android.annotation.SuppressLint;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;




@SuppressLint("DefaultLocale")
public class ThreadedRecorder implements Runnable {
	
	/**
	 * A StateListener can be used to get notified by the ThreadedRecorder
	 * to react on its state changes without actively polling the recorder's 
	 * state.
	 * 
	 * @author sikoried
	 */
	public static interface StateListener {
		public void recorderStarted(ThreadedRecorder instance);
		public void recorderPaused(ThreadedRecorder instance);
		public void recorderStopped(ThreadedRecorder instance);
	}
	
	private List<StateListener> dependents = new LinkedList<StateListener>();
	
	private Thread thread;
	
	/** AudioCapture to read from, needs to be fully initialized */
	private AudioCapture source;
	
	/** is the recorder paused? */
	private volatile boolean paused = false;
	
	/** is a stop requested? */
	private volatile boolean stopRequested = false;
	
	/** is the capture finished? indicator to the recording thread */
	private volatile boolean finished = true;
	
	/** output stream to write to */
	private OutputStream os = null;
	
	/**
	 * Create a new ThreadedRecorder using an initialized AudioCapture object.
	 * Once start is called, the raw data (matching the AudioCapture format) will
	 * be saved to a file.
	 * @param source
	 */
	public ThreadedRecorder(AudioCapture source) {
		this.source = source;
	}
	
	public RawAudioFormat getCurrentFormat() {
		return new RawAudioFormat(source.getBitRate(), source.getSampleRate(), true, true, 0);
	}
	
	/**
	 * Set the AudioCapture device. The ThreadedRecorder must be stopped and 
	 * removeAudioCapture() must be called.
	 * @param source
	 * @throws IOException
	 */
	public void setAudioCapture(AudioCapture source) throws IOException {
		if (os != null || thread != null)
			throw new IOException("ThreadedRecorder.setAudioCapture(): recorder is recording!");
		
		if (this.source != null)
			throw new IOException("ThreadedRecorder.setAudioCapture(): call removeAudioCapture() first!");
		
		
		
		this.source = source;
	}
	
	/**
	 * Remove and free the currently used AudioCapture device.
	 * @throws IOException
	 */
	public void removeAudioCapture() throws IOException {
		if (os != null || thread != null)
			stop();
		
		// free the device
		if (source != null)	
			source.tearDown();

		
		source = null;
	}
	
	/**
	 * 
	 * @return source, mainly for calling getActualBufDur() on it
	 */
	public AudioCapture getAudioCapture() {
		return source;
	}
	
	/**
	 * Start the recording and save to the given file. File will be overwritten!
	 * @param fileName absolute path to target file; if null, a ByteArrayOutputStream is used.
	 * @return the output stream used for this recording; either BufferedOuputStream or ByteArrayOutputStream
	 */
	public OutputStream start(String fileName) throws IOException {
		// make sure we don't cancel a recording
		if (os != null || thread != null)
			throw new IOException("Recorder already running!");
		
		if (fileName == null)
			os = new ByteArrayOutputStream();
		else
			os = new BufferedOutputStream(new FileOutputStream(fileName));

		// yeah, start off
		thread = new Thread(this);
		thread.start();
		
		return os;
	}
	
	/** 
	 * (un)pause the recording; on pause, the recording continues, but is not
	 * saved to the file.
	 */
	public void pause() {		
		if (!paused)
			notifyPause();
		else
			notifyStart();
		
		paused = !paused;
	}
	
	/***
	 * Stop the recording. This method blocks until the recording thread died.
	 */
	public void stop() {
		stopRequested = true;
		try {
			if (thread != null){
				System.err.println("stop(): thread.join() ...");
				thread.join();
				System.err.println("stop(): thread.join() done.");
			}
			thread = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/** 
	 * Internal thread function, takes care of the actual recording
	 */
	public void run() {
		// enable internal buffer
		notifyStart();
		source.enableInternalBuffer(0);
		finished = false;
		
		//if (true) throw new Error("sr = " + source.getSampleRate());
		
		try {
			// run as long as we want...
			while (!stopRequested) {				
				source.read(null); // read to internal buffer
				if (!paused)
					synchronized(os){
						os.write(source.getRawBuffer());
					}				
			}

			// close and cleanup
			os.close();
		} catch (IOException e) {
			System.err.println("ThreadedRecorder.run(): I/O error: " + e.toString());
		} catch (Exception e) {
			System.err.println("ThreadedRecorder.run(): " + e.toString());
		} finally {
			// note to main thread
			thread = null;
			finished = true;
			stopRequested = false;
			paused = false;
			os = null;
			notifyStop();
		}
	}
	
	public void tearDown() throws IOException {
		source.tearDown();
	}
	
	public boolean isRecording() {
		return !finished;
	}
	
	public boolean isPaused() {
		return paused;
	}
	
	/**
	 * Register a StateListener with the ThreadedRecorder to get notified if the
	 * state changed.
	 * @param client
	 */
	public void addStateListener(StateListener client) {
		dependents.add(client);
	}
	
	/**
	 * De-register a StateListener to allow the garbage collection to work properly
	 * @param client
	 */
	public void removeStateListener(StateListener client) {
		for (int i = 0; i < dependents.size(); ++i)
			if (dependents.get(i) == client) {
				dependents.remove(i);
				break;
			}
	}
	
	private void notifyStart() {
		for (StateListener s : dependents)
			s.recorderStarted(this);
	}
	
	private void notifyPause() {
		for (StateListener s : dependents)
			s.recorderPaused(this);
	}
	
	private void notifyStop() {
		for (StateListener s : dependents)
			s.recorderStopped(this);
	}
	
	@SuppressLint("DefaultLocale")
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("usage: bin.ThreadedRecorder <mixer-name>");
			System.exit(1);
		}
		
		ThreadedRecorder tr = new ThreadedRecorder(new AudioCapture(args[0], 16, 16000, 0));
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		
		String h = "Commands:\n" +
				"  's <filename>' : start recording\n" +
				"  'p' : pause recording\n" +
				"  'e' : finish recording\n" +
				"  'h' : display this help";
		
		System.out.println(h);

		String line;
		while (true) {
			line = br.readLine();
			
			if (line == null)
				break;
			else if (line.toUpperCase().startsWith("E")) {
				System.out.println("Recording finished...");
				tr.stop();
			} else if (line.toUpperCase().startsWith("P")) {
				System.out.println("Recording (un)paused...");
				tr.pause();
			} else if (line.toUpperCase().startsWith("S ")) {
				System.out.println(" " + line.substring(2));
				tr.start(line.substring(2));
			} else if (line.toUpperCase().startsWith("H"))
				System.out.println(h);
		}
	}
}
