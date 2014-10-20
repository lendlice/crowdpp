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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;


public class ThreadedPlayer implements Runnable {

	/**
	 * A StateListener can be used to get notified by the ThreadedPlayer
	 * to react on its state changes without actively polling the player's 
	 * state.
	 * 
	 * @author sikoried
	 */
	public interface StateListener {
		public void playerStarted(ThreadedPlayer instance);
		public void playerStopped(ThreadedPlayer instance);
		public void playerPaused(ThreadedPlayer instance);
	}
	
	public interface ProgressListener {
		public void resetCount();
		public void bytesPlayed(int b);
	}
	
	private List<StateListener> dependents = new LinkedList<StateListener>();
	private List<ProgressListener> progress = new LinkedList<ProgressListener>();
	
	private Thread thread = null;
	
	private AudioPlay player;

	/** is the player paused? */
	private volatile boolean paused = false;

	/** is the playback finished? indicator to the player thread */
	private volatile boolean finished = true;
	
	/**
	 * Initialize a ThreadedPlayer.
	 */
	public ThreadedPlayer() {
	
	}

	/**
	 * Setup the ThreadedPlayer to play the given audio file.
	 * @param mixer null for default mixer (not advised)
	 * @param format
	 * @param file
	 * @param desuredBufDur 0 for default, milliseconds otherwise
	 * @throws IOException
	 * @throws UnsupportedAudioFileException 
	 */
	public void setup(String mixer, RawAudioFormat format, File file, double desiredBufDur) 
		throws LineUnavailableException, IOException, UnsupportedAudioFileException {
		setup(mixer, new AudioFileReader(file.getCanonicalPath(), format, true), desiredBufDur);
	}
	
	public void setup(String mixer, RawAudioFormat format, InputStream inputStream, double desiredBufDur) 
		throws LineUnavailableException, IOException {
		setup(mixer, new AudioFileReader(inputStream, format, true), desiredBufDur);
	}
	
	/**
	 * Setup the ThreadedPlayer to play the given audio data.
	 * @param mixer null for default mixer (not advised)
	 * @param format
	 * @param data
	 * @param desiredBufDur 0 for default, milliseconds otherwise
	 * @throws IOException
	 */
	public void setup(String mixer, RawAudioFormat format, byte [] data, double desiredBufDur) 
		throws LineUnavailableException, IOException {
		setup(mixer, new AudioFileReader(data, format), desiredBufDur);
	}
	
	/**
	 * Setup the ThreadedPlayer to play from the given AudioSource.
     * @param mixer
     * @param source
	 * @param desiredBufDur 0 for default, milliseconds otherwise
	 */
	public void setup(String mixer, AudioSource source, double desiredBufDur) 
		throws LineUnavailableException, IOException {
		
		try {
			if (isPlaying())
				stop();
		} catch (InterruptedException e) {
			throw new IOException(e.toString());
		}
			
		player = new AudioPlay(mixer, source, desiredBufDur);
		paused = false;
	}
	
	/** 
	 * pause/resume the playback
	 *
	 */
	public void pause() {
		if (paused) {
			thread = new Thread(this);
			thread.start();
			paused = false;
		} else {
			paused = true;
		}
	}

	/**
	 * Stop the playback and tear down the source. This method blocks until the 
	 * playback thread died. You might be looking for pause()!
	 */
	public void stop() throws InterruptedException, IOException {
		// make sure that the player is marked for tear down
		finished = true;
		
		// trigger stop in the thread
		paused = true;
		
		if (thread != null)
			thread.join();
		
		thread = null;
	}
	
	/**
	 * Start the playback.
	 * @throws IOException
	 */
	public void start() throws IOException {
		if (thread != null)
			throw new IOException("Thread is still playing!");

		thread = new Thread(this);
		finished = false;
		thread.start();
	}

	public void run() {
		try {
			notifyStart();
			
			int b;
			while (!paused) {
				if ((b = player.write()) <= 0) {
					finished = true;
					break;
				}
				notifyBytesWritten(b);
			}
			
			// free the device if everything was played
			if (finished) {
				player.tearDown();
				
				thread = null;
				paused = false;
				notifyStop();
			} else
				notifyPause();
		} catch (IOException e) {
			System.err.println("ThreadedPlayer.run(): I/O error: " + e.toString());
			e.printStackTrace();
		} 
//		catch (Exception e) {
//			System.err.println("ThreadedPlayer.run(): " + e.toString());
//			e.printStackTrace();
//		}		
		catch (LineUnavailableException e) {
			System.err.println("LineUnavailableException: " + e.toString());
			e.printStackTrace();
		}
	}
	
	public void tearDown() throws IOException {
		if (player == null){
			System.err.println("player is null - no need to tearDown");
		}
		else{
			System.err.println("player.tearDown()...");
			player.tearDown();
		}
	}
	
	public boolean isPlaying() {
		return thread != null && !finished;
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
	
	
	public void addProgressListener(ProgressListener client) {
		progress.add(client);
	}
	
	public void removeProgressListener(ProgressListener client) {
		for (int i = 0; i < progress.size(); ++i)
			if (progress.get(i) == client) {
				progress.remove(i);
				break;
			}
	}
	
	private void notifyBytesWritten(int b) {
		for (ProgressListener p : progress)
			p.bytesPlayed(b);
	}
	
	private void notifyStart() {
		for (StateListener s : dependents)
			s.playerStarted(this);
	}
	
	private void notifyPause() {
		for (StateListener s : dependents)
			s.playerPaused(this);
	}

	private void notifyStop() {
		for (StateListener s : dependents)
			s.playerStopped(this);
		for (ProgressListener p : progress)
			p.resetCount();
	}
	
	public static void main(String [] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: app.ThreadedPlayer mixer-name <file1 ...>");
			System.exit(1);
		}
		
		ThreadedPlayer play = new ThreadedPlayer();
		
		for (int i = 1; i < args.length; ++i) {
			String file = args[i];
			System.err.println("Hit [ENTER] to start playing " + file);
			System.in.read();
			
			play.setup(args[0], new AudioFileReader(file, RawAudioFormat.getRawAudioFormat("ssg/16"), true), 0.);
			play.start();
			
			while (play.isPlaying())
				Thread.yield();
		}
	}
}