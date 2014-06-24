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
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 
 * raw audio playback, i.e. without the detour of converting from double-samples (AudioSource)
 * to signed shorts; for time-critical applications. 
 * 
 * use e.g. enableStressTest(0.95) to stress-test this component by actively waiting 95% of the time.  
 *
 * @author hoenig
 *
 */
public class RawPlayer implements Runnable, LineListener{
	
	public interface PlayEventListener {
		// actual playback starts
		public void playbackStarted(RawPlayer instance);
		// actual playback stops (was actively stopped or ais is at its and, and has been playback stops now)
		public void playbackStopped(RawPlayer instance);
		/* some error occurred, e.g. when trying
		 * line = (SourceDataLine) AudioSystem.getMixer(mixer).getLine(info);
		 * */
		public void playbackFailed(RawPlayer instance, Exception e);
	}	

	private Set<PlayEventListener> dependents = new HashSet<PlayEventListener>();
	
	Thread thread;
	
	boolean firsttime = true;
	
	boolean stopped = false;
	
	Semaphore stopMutex = new Semaphore(1);
	
	AudioInputStream ais;
	
	SourceDataLine line = null;
	
	double desiredBufSize;
	
	private Mixer.Info mixer;
	
	boolean stressTestEnabled = false;	
	double activeSleepRatio;
	
	private int factor_buffer_smaller = 16;

	private Thread shutdownHook = null;

	private Exception exception = null;
	
	public RawPlayer(AudioInputStream ais){
		this(ais, null, 0.0);		
	}

	public RawPlayer(AudioInputStream ais, String mixerName){
		this(ais, mixerName, 0.0);
	}
	
	/**
	 * set up player. no lines are occupied until start() is called.
	 * @param ais
	 * @param mixerName
	 * @param desiredBufSize in seconds, determines latency when stop()ing: currently,
	 * stopping cannot begin before desiredBufSize, and afterwards only with a granularity of
	 *  desiredBufSize / factor_buffer_smaller.
	 */
	public RawPlayer(AudioInputStream ais, String mixerName, double desiredBufSize){
		this.ais = ais;
		thread = new Thread(this);
		thread.setName("RawPlayer");
		this.desiredBufSize = desiredBufSize;
			 
		setMixer(null);
		System.err.println("searching for mixerName " + mixerName);
		try {
			setMixer(MixerUtil.getMixerInfoFromName(mixerName, false));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (getMixer() == null)
			System.err.println("could not find mixer " + mixerName);			

		System.out.println(String.format("mixer: Description = %s, Name = %s, Vendor = %s, Version = %s",
				getMixer().getDescription(),
				getMixer().getName(),
				getMixer().getVendor(),
				getMixer().getVersion()));
	
	}	

	/**
	 * disregisters this as a lineListener and .
	 * @param shutdownInProgress
	 * 
	 * outdated, as player removes shutdownhook when ready with playing, which should suffice.
	 */
//	private void dispose(){
//		
//		if (line != null)
//			line.removeLineListener(this);
//		line = null;
//		
//		if (dependents != null)
//			dependents.clear();		
//		dependents = null;
//		
////		thread = null;
////		ais = null;
////		
////		setMixer(null);		
//		
//	}
	
	public void addStateListener(PlayEventListener client) {
		dependents.add(client);
	}
	
	/* outdated; just do a stopPlaying and set object to null
	public void removeStateListener(PlayEventListener client) {
		dependents.remove(client);
	}
	*/
	
	private void notifyStart() {
		System.err.println("RawPlayer: notifyStart for " + dependents.size());
		for (PlayEventListener s : dependents){
			System.err.println("notify " + s);
			s.playbackStarted(this);
		}
	}
	
	private void notifyStop() {
		System.err.println("RawPlayer: notifyStop for " + dependents.size());
		for (PlayEventListener s : dependents){
			System.err.println("notify " + s);
			s.playbackStopped(this);
		}
	}
	
	private void notifyFailure(Exception e){
		System.err.println("RawPlayer: notifyFailure for " + dependents.size());
		for (PlayEventListener s : dependents){		
			s.playbackFailed(this, e);
		}		
	}
	
	/**
	 * stress-test this component: sleep (actively) *after* reading data from ais.
	 * sleeping time is relative to (the duration of) amount of data  activeSleepRatio: the ratio of time spent (actively) sleeping
	 * relative to the audio samples played back. 
	 * Use e.g. 0.95 for a hard stress test.
	 * 
	 * @param activeSleepRatio
	 */
	public void enableStressTest(double activeSleepRatio){
		stressTestEnabled = true;
		this.activeSleepRatio = activeSleepRatio;
	}
	
	public void start(){
		if (shutdownHook != null){
			System.err.println("RawPlayer.start() error: shutdownHook != null!");
		}
		Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread(){
			public void run() {
				// free audio device
				if (line != null){
					System.err.println("RawPlayer: Inside Shutdown Hook: closing line...");				
					line.close();
					System.err.println("RawPlayer: Inside Shutdown Hook: line closed.");
				}
				else{
					System.err.println("RawPlayer: Inside Shutdown Hook: line = null -> nothing to do.");
				}
			}			
		});		
		thread.start();
	}
	
	public void join(){
		try {
			thread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * stops playback, waits for playing thread (to free audio resources and exit).
	 */
	public void stopPlaying(){		
		try {
			stopMutex.acquire();
		} catch (InterruptedException e1) {
			System.err.println("stopPlaying: interrupted");
			return;
		}
		if (stopped){
			stopMutex.release();
			return;
		}		
		
		stopped = true;
		
		stopMutex.release();
		
		try {
			thread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
	}

	@Override
	public void run() {
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, ais.getFormat());
		if (!AudioSystem.isLineSupported(info)) {
			System.err.println("Not supported line: " + info);
			stopPlaying();
			return;
        }
		 
		// get and open Line
		try {
			/* according to the doc, AudioSystem.getMixer should be able to handle null,
			 * but experiment seems to disprove that (at least for capturing, but we do it here as well)  
			 */
			if (getMixer() == null)
				line = (SourceDataLine) AudioSystem.getLine(info);
			else
				line = (SourceDataLine) AudioSystem.getMixer(getMixer()).getLine(info);

			if (desiredBufSize != 0.0){					
			
				line.open(ais.getFormat(), 
						(int)Math.round(desiredBufSize * ais.getFormat().getFrameRate() * ais.getFormat().getFrameSize()));
			}
			else
				line.open(ais.getFormat());				
		}
		/* no sufficient: 
		 *		catch (LineUnavailableException e) {
		 *
		 * we also get java.lang.IllegalArgumentException: Line unsupported,
		 * which needs not to be catched, but we better do!
		 */
		catch (Exception e) {
			e.printStackTrace();

			exception = e;			
			Runnable runnable = new Runnable(){
				@Override
				public void run() {
					notifyFailure(exception);			
				}					
			};
			new Thread(runnable).start();
						
			stopped = true;
			return;
		}		
		System.err.println("Bufsize = " + line.getBufferSize());

		byte [] buffer = new byte[line.getBufferSize()];
		int partialBufferSize = buffer.length / factor_buffer_smaller;
		
		// integral number of frames:
		partialBufferSize = partialBufferSize / ais.getFormat().getFrameSize() * ais.getFormat().getFrameSize();
		
		line.addLineListener(this);
		line.flush();
		line.start();		
		
		// main playback loop
		while(!stopped){
			int numBytesRead = 0;
			try {
				if (firsttime){ // read+write a whole buffer
					numBytesRead = ais.read(buffer);
					firsttime = false;
				}
				else /* 
					 * read+write partial buffer (why? see http://download.oracle.com/javase/tutorial/sound/capturing.html)
					 * also, stress testing has confirmed that scheme (see enableStressTest)
					 * */				 
					numBytesRead = ais.read(buffer, 0, partialBufferSize);
					
			} catch (IOException e) {				
				e.printStackTrace();
				
				exception = e;			
				Runnable runnable = new Runnable(){
					@Override
					public void run() {
						notifyFailure(exception);			
					}					
				};
				new Thread(runnable).start();

				// end, but do not drain!
				stopped = true;		
				break;
			}			
			
		
			if (stressTestEnabled){
				long nanoSleep = (long) (activeSleepRatio * numBytesRead / ais.getFormat().getFrameRate() / ais.getFormat().getFrameSize() * 1000000000.0);
				
				// simulate busy system by active waiting
				long startTime = System.nanoTime();
				System.err.println("numBytesRead = " + numBytesRead);
				System.err.println("available = " + line.available());
				System.err.println("nanoSleep = " + nanoSleep);

				while ((System.nanoTime() - startTime) < nanoSleep);
			}						
			
			if (numBytesRead < 0)
				break;
			//System.err.println("available = " + line.available());

			line.write(buffer, 0, numBytesRead);	
			
		}
		
		if (!stopped)
			line.drain();
		line.stop();
		if (stopped){ 
			line.flush();			
		}
		line.close();
		line = null;
		
		if (shutdownHook != null){
			System.err.println("RawPlayer.run: removing shutdownHook...");
		
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		}
		else
			System.err.println("RawPlayer.run: warning: no shutdownHook ?!?");
		
		stopped = true;		
				
	}
	
	@Override
	public void update(LineEvent le) {		
		if (!le.getLine().equals(line))
			return;
		
		System.err.println("RawPlayer: update: "+ le);
		
		if (le.getType() == LineEvent.Type.START)
			notifyStart();
		else if (le.getType() == LineEvent.Type.STOP)
			notifyStop();						
	}	
	
	public boolean hasStopped(){
		return stopped;
	}
	
	public static void main(String [] args){
		Mixer.Info [] availableMixers = AudioSystem.getMixerInfo();
	
		for (Mixer.Info m : availableMixers)
			System.err.println(m.getName());
		
		String mixer = null;
		
		System.err.println("usage: < IN.wav [mixer]");
		
		if (args.length > 0)
			mixer = args[0];		
		
		AudioInputStream ais = null;
		
		InputStream is = System.in;
		
		try {
			ais = AudioSystem.getAudioInputStream(is);
		} catch (UnsupportedAudioFileException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		
		System.err.println("format = " + ais.getFormat());
		
		final RawPlayer player = new RawPlayer(ais, mixer, 0.1);
		
		// "Intel [plughw:0,0]" works much better than "Java Sound Audio Engine". 
		// And the latter from time to time refuses to put out anything
		//player.enableStressTest(0.98);
		
		player.start();		
		
		player.join();
		System.err.println("joined");

	}

	public void setMixer(Mixer.Info mixer) {
		this.mixer = mixer;
	}

	public Mixer.Info getMixer() {
		return mixer;
	}
}
