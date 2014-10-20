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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * raw audio capture, i.e. without the detour of converting to double-samples (AudioSource)
 * and back to signed shorts; for time-critical applications. 
 * 
 * use e.g. enableStressTest(0.95) to stress-test this component by actively waiting 95% of the time.  
 * @author hoenig
 *
 */
public class RawCapturer implements Runnable, LineListener{
	
	public interface CaptureEventListener {
		// actual recording starts
		public void captureStarted(RawCapturer instance);
		// actual recording stops (was actively stopped)
		public void captureStopped(RawCapturer instance);
		/* some error occurred, e.g. when trying
		 * line = (TargetDataLine) AudioSystem.getMixer(mixer).getLine(info);
		 * */
		public void captureFailed(RawCapturer instance, Exception e);
	}	

	private Set<CaptureEventListener> dependents = new HashSet<CaptureEventListener>();
	
	Thread thread = null;
	
	boolean firsttime = true;
	
	boolean stopped = false;
	
	Semaphore stopMutex = new Semaphore(1);
	
	OutputStream os;
	AudioFormat format;
	
	TargetDataLine line = null;
	
	double desiredBufSize;
	
	private Mixer.Info mixer;
	
	boolean stressTestEnabled = false;	
	double activeSleepRatio;

	private int factor_buffer_smaller = 16;

	private Thread shutdownHook = null;
	
	Exception exception = null;

	private boolean stopRequested = false;

	private boolean stopRequestedFulfilled;

	private double myBufDuration;
	
	public RawCapturer(BufferedOutputStream os, AudioFormat format){
		this(os, format, null, 0.0);		
	}

	public RawCapturer(BufferedOutputStream os, AudioFormat format, String mixerName){
		this(os, format, mixerName, 0.0);
	}
	
	/**
	 * set up recorder. no lines are occupied until start() is called.
	 * @param os
	 * @param mixerName
	 * @param desiredBufSize in seconds, determines latency when stop()ing: currently, 
	 * stopping can take about as long as desiredBufSize / factor_buffer_smaller.
	 */
	public RawCapturer(BufferedOutputStream os, AudioFormat format, String mixerName, double desiredBufSize){
		this.os = os;
		this.format = format;
		thread = new Thread(this);
		thread.setName("RawCapturer");
		this.desiredBufSize = desiredBufSize;
		
		setMixer(null);

		try {
			setMixer(MixerUtil.getMixerInfoFromName(mixerName, true));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (getMixer() == null)
			System.err.println("Error: could not find mixer " + mixerName);			

		System.err.println(String.format("mixer: Description = %s, Name = %s, Vendor = %s, Version = %s",
				getMixer().getDescription(),
				getMixer().getName(),
				getMixer().getVendor(),
				getMixer().getVersion()));
	}
	
	/* outdated
	public void dispose(boolean shutdownInProgress){
		stopCapturing();
		
		if (line != null)
			line.removeLineListener(this);
		line = null;

		if (dependents != null)
			dependents.clear();
		dependents = null;
		
		thread = null;
		os = null;
		format = null;
		
		setMixer(null);
		
		if (shutdownHook != null && !shutdownInProgress){
			System.err.println("RawCapturer.dispose: removing shutdownHook");
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		}
		shutdownHook = null;

	}
	*/
	
	public void addStateListener(CaptureEventListener client) {
		dependents.add(client);
	}
	/* outdated; just do a stopPlaying and set object to null
	public void removeStateListener(CaptureEventListener client) {
		dependents.remove(client);
	}
	*/
	private void notifyStart() {
		System.err.println("RawCapturer: notifyStart for " + dependents.size());		
		for (CaptureEventListener s : dependents)
			s.captureStarted(this);
	}
	private void notifyStop() {
		System.err.println(new Date() + ": RawCapturer: notifyStop for " + dependents.size());
		for (CaptureEventListener s : dependents)
			s.captureStopped(this);
	}
	private void notifyFailure(Exception e){
		System.err.println("RawCapturer: notifyFailure for " + dependents.size());		
		for (CaptureEventListener s : dependents){		
			s.captureFailed(this, e);
		}		
	}	
	
	/**
	 * stress-test this component: sleep (actively) *after* writing data to os.
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
	
	
	public void startCapture(){
		Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread(){
			public void run() {
				// free audio device
				if (line != null){
					System.err.println("RawCapturer: Inside Shutdown Hook: closing line...");				
					line.close();
					System.err.println("RawCapturer: Inside Shutdown Hook: line closed.");
				}
				else{
					System.err.println("RawCapturer: Inside Shutdown Hook: line = null -> nothing to do.");
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
	
	public void stopCapturing(){
		try {
			stopMutex.acquire();
		} catch (InterruptedException e1) {
			System.err.println("stopCapturing: interrupted");
			return;
		}
		
		if (stopped){
			stopMutex.release();
			return;
		}			
		
		// not setting "stopped = true" here, but rather
		// stop line, and let update set stopped to true.
		
		// try to get rid of the line.stop()-hanging -seems to work now, at least under linux
		stopRequested = true;		
		
		// the stop() sometime hangs quite a while!?!
		double sleepDuration = myBufDuration / 10;
		System.err.println("sleepDuration = " + sleepDuration);
		double slept;
		for (slept = 0; slept < 2 * myBufDuration; slept += sleepDuration){
			try {
				Thread.sleep((long) (1000 * sleepDuration));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
			if (stopRequestedFulfilled)
				break;
		}
		if (stopRequestedFulfilled)
			System.err.println("stop request fulfilled after waiting sec: " + slept);
		else
			System.err.println("warning: stop request not fulfilled after waiting sec: " + slept);
		
		System.err.println(new Date() + ": stopCapturing: line.stop()...");
		line.stop();
		System.err.println(new Date() + ": stopCapturing: ... line.stop() done");
		
		stopRequestedFulfilled = false;
		
		stopMutex.release();
		
		// let's only stop as soon as we are notified by update() 
		//stopped = true;
		try {
			if (thread != null)
				thread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
	}

	@Override
	public void run() {
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		if (!AudioSystem.isLineSupported(info)) {
			System.err.println("Not supported line: " + info);
			stopCapturing();
			return;
        }
		
		System.err.println("opening: " + getMixer());
		
		try {
			/* according to the doc, AudioSystem.getMixer should be able to handle null,
			 * but experiment seems to disprove that.  
			 */
			if (getMixer() == null)
				line = (TargetDataLine) AudioSystem.getLine(info);
			else
				line = (TargetDataLine) AudioSystem.getMixer(getMixer()).getLine(info);

			if (desiredBufSize != 0.0)			
				line.open(format, 
						(int)Math.round(desiredBufSize * format.getFrameRate() * format.getFrameSize()));
			else
				line.open(format);				
			
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
		System.err.println("line Bufsize = " + line.getBufferSize());
		
		System.err.println(String.format("desired bufsize = %f, actual = %f",
				desiredBufSize, line.getBufferSize() / format.getFrameRate() / format.getFrameSize()));				

		/* read+write partial buffer (why? see http://download.oracle.com/javase/tutorial/sound/capturing.html)
			 * also, stress testing has confirmed that scheme (see enableStressTest)
			 * */			
		int bufSize = line.getBufferSize() / factor_buffer_smaller;
		// make sure to read integral number of frames:
		bufSize = bufSize / format.getFrameSize() * format.getFrameSize();
		byte [] buffer = new byte[bufSize];		
		
		myBufDuration = bufSize / format.getFrameRate() / format.getFrameSize();		
				
		line.addLineListener(this);
		line.flush();
		line.start();		
		
		// main capturing loop
		while(true){//!stopped){
			int numBytesRead = 0;
			
			numBytesRead = line.read(buffer, 0, buffer.length);												
			
//			System.err.println(String.format("numBytesRead = %d, isActive = %s, isRunning = %s",
//					numBytesRead,
//					line.isActive(), 
//					line.isRunning()));
			
			if (numBytesRead < 0)
				break;
			else if (numBytesRead == 0 &&
					// equivalent to using stopped would be !line.isRunning()
					stopped){
				break;				
			}
				
			//System.err.println("available = " + line.available());
			try {
				os.write(buffer, 0, numBytesRead);
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
				System.err.println("trying to stop...");
				stopped = true;
				break;
			}
			if (stressTestEnabled){
				long nanoSleep = (long) (activeSleepRatio * numBytesRead / format.getFrameRate() / format.getFrameSize() * 1000000000.0);
				
				// simulate busy system by active waiting
				long startTime = System.nanoTime();
				System.err.println("numBytesRead = " + numBytesRead);
				System.err.println("available = " + line.available());
				System.err.println("nanoSleep = " + nanoSleep);

				while ((System.nanoTime() - startTime) < nanoSleep);
			}
			
			if (stopRequested ){
				stopRequestedFulfilled = true;
				stopRequested = false;			
				break;
			}
		}
		
		
//		if (false){//!stopped){
//			System.err.println("line.drain()");
//			line.drain();
//		}
//		System.err.println("line.stop()");
//		line.stop();		
		
		System.err.println("line.flush()");
		line.flush();

		System.err.println("line.close()");
		line.close();		
		line = null;
		
		if (shutdownHook != null){
			System.err.println("RawPlayer.run: removing shutdownHook...");
		
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		}
		else
			System.err.println("RawPlayer.run: warning: no shutdownHook ?!?");
		
		System.err.println("leaving");				
	}
	
	@Override
	public void update(LineEvent le) {		
		if (!le.getLine().equals(line))
			return;
		
		System.err.println(new Date() + ": RawCapturer.update: " + le);
		
		if (le.getType() == LineEvent.Type.START){
			System.err.println("RawCapturer.update: notifyStart()");
			notifyStart();
		}
		else if (le.getType() == LineEvent.Type.STOP){
			System.err.println("RawCapturer.update: notifyStop()");
			stopped = true;
			notifyStop();						
		}
	}	
	
	public static void main(String [] args){
		Mixer.Info [] availableMixers = AudioSystem.getMixerInfo();
	
		for (Mixer.Info m : availableMixers)
			System.err.println(m.getName());
		
		String mixer = null;
		
		System.err.println("usage: > IN.ssg [mixer]");
		
		if (args.length > 0)
			mixer = args[0];		
		
		
		BufferedOutputStream os = new BufferedOutputStream(System.out);
	
		AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
			
		final RawCapturer capturer = new RawCapturer(os, format, mixer, 0.1);
		
		// "Intel [plughw:0,0]" works much better than "Java Sound Audio Engine". 
		// And the latter from time to time refuses to put out anything
		//capturer.enableStressTest(0.99);

		capturer.startCapture();		
		
		// i.e. forever, unless interrupted (see addShutdownHook of RawCapturer)
		capturer.join();
		System.err.println("joined");
	}

	public void setMixer(Mixer.Info mixer) {
		this.mixer = mixer;
	}

	public Mixer.Info getMixer() {
		return mixer;
	}
}

