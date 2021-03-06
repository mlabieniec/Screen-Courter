/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.reelfx.model;


import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.reelfx.Applet;
import com.reelfx.model.util.ProcessWrapper;
import com.reelfx.model.util.StreamGobbler;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

import javax.sound.sampled.Mixer;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

/**
 * 
 * @author Daniel Dixon (http://www.danieldixon.com)
 * 
 * 	Copyright (C) 2010  ReelFX Creative Studios (http://www.reelfx.com)
 *
 *	This program is free software: you can redistribute it and/or modify
 * 	it under the terms of the GNU General Public License as published by
 * 	the Free Software Foundation, either version 3 of the License, or
 * 	(at your option) any later version.
 * 	
 * 	This program is distributed in the hope that it will be useful,
 * 	but WITHOUT ANY WARRANTY; without even the implied warranty of
 * 	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * 	GNU General Public License for more details.
 * 	
 * 	You should have received a copy of the GNU General Public License
 * 	along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
public class ScreenRecorder extends ProcessWrapper implements ActionListener {
	
	private static String EXT = Applet.IS_WINDOWS ? ".avi" : ".mov";
	private static Logger logger = Logger.getLogger(ScreenRecorder.class);
	
	// FILE LOCATIONS
	public static File OUTPUT_FILE = new File(Applet.BASE_FOLDER.getAbsolutePath()+File.separator+"screen_capture"+EXT);
	protected static File MAC_EXEC = new File(Applet.BIN_FOLDER.getAbsoluteFile()+File.separator+"mac-screen-recorder");
	protected static File FFMPEG_EXEC = new File(Applet.BIN_FOLDER.getAbsoluteFile()+File.separator+"ffmpeg"+(Applet.IS_WINDOWS ? ".exe" : ""));
	
	// STATES
	public final static int RECORDING_STARTED = 100;
	public final static int RECORDING_COMPLETE = 101;
	
    private Process recordingProcess;
    private StreamGobbler errorGobbler, inputGobbler;
    private Mixer audioSource = null;
    private int audioIndex = 0;
    private ScreenRecorder self = this;
    
    /**
     * If this guy is to handle audio as well, give it the Java Mixer object to read from.
     * 
     * @param mixer
     */
	public synchronized void start(Mixer mixer,int index) {
		audioSource = mixer;
		audioIndex = index;
		super.start();
	}

	public void run() {
    	try {
    		if(Applet.IS_MAC) {
	        	List<String> macArgs = new ArrayList<String>();
	            macArgs.add(MAC_EXEC.getAbsolutePath());
	            macArgs.add(OUTPUT_FILE.getAbsolutePath());

	            ProcessBuilder pb = new ProcessBuilder(macArgs);
	            recordingProcess = pb.start();
	            fireProcessUpdate(RECORDING_STARTED);
	            
	            errorGobbler = new StreamGobbler(recordingProcess.getErrorStream(), false, "mac E");
	            inputGobbler = new StreamGobbler(recordingProcess.getInputStream(), false, "mac O");
	            
	            logger.info("Starting listener threads...");
	            errorGobbler.start();
	            inputGobbler.start();
	            
	            recordingProcess.waitFor();
	            fireProcessUpdate(RECORDING_COMPLETE);
    		} 
    		
    		else if(Applet.IS_LINUX) {
    			// can have problem with file permissions when methods are invoked via Javascript even if applet is signed, 
    			// thus some code needs to wrapped in a privledged block
    			AccessController.doPrivileged(new PrivilegedAction<Object>() {

					@Override
					public Object run() {
						
						try {
							int width = Applet.CAPTURE_VIEWPORT.width;
							if(width % 2 != 0) width--;
							int height = Applet.CAPTURE_VIEWPORT.height;
							if(height % 2 != 0) height--;
							
			    			List<String> ffmpegArgs = new ArrayList<String>();
			    			//ffmpegArgs.add("/usr/bin/ffmpeg");
			    	    	ffmpegArgs.add(Applet.BIN_FOLDER.getAbsoluteFile()+File.separator+"ffmpeg");
			    	    	// screen capture settings
			    	    	ffmpegArgs.addAll(parseParameters("-y -f x11grab -s "+width+"x"+height+" -r 20 -i :0.0+"+Applet.CAPTURE_VIEWPORT.x+","+Applet.CAPTURE_VIEWPORT.y));
			    	    	// microphone settings (good resource: http://www.oreilly.de/catalog/multilinux/excerpt/ch14-05.htm)
			    	    	/* 04/29/2010 - ffmpeg gets much better framerate when not recording microphone (let Java do this)
			    	    	 * if(audioSource != null) { 
			    	    		String driver = audioIndex > 0 ? "/dev/dsp"+audioIndex : "/dev/dsp";
			    	    		ffmpegArgs.addAll(parseParameters("-f oss -ac 1 -ar "+AudioRecorder.FREQ+" -i "+driver));
			    	    	}*/
			    	    	// output file settings
			    	    	ffmpegArgs.addAll(parseParameters("-vcodec mpeg4 -r 20 -b 5000k")); // -s "+Math.round(width*SCALE)+"x"+Math.round(height*SCALE))
			    	    	ffmpegArgs.add(OUTPUT_FILE.getAbsolutePath());
			    	    	
			    	    	logger.info("Executing this command: "+prettyCommand(ffmpegArgs));
			    	    	
			    	        ProcessBuilder pb = new ProcessBuilder(ffmpegArgs);
			    	        recordingProcess = pb.start();
			    	        // fireProcessUpdate(RECORDING_STARTED); // moved to action listener method
			    	        
			    	        errorGobbler = new StreamGobbler(recordingProcess.getErrorStream(), false, "ffmpeg E");
				            inputGobbler = new StreamGobbler(recordingProcess.getInputStream(), false, "ffmpeg O");
				            
				            logger.info("Starting listener threads...");
				            errorGobbler.start();
				            errorGobbler.addActionListener("Stream mapping:", self);
				            inputGobbler.start();
				            
				            recordingProcess.waitFor();
				            
				            fireProcessUpdate(RECORDING_COMPLETE);
	            
						}
			            catch (Exception e) {
							logger.error("Error running Linux screen recorder!",e);
						}
						return null;
					}
				});
    		}
    		
    		else if(Applet.IS_WINDOWS) {
    			// can have problem with file permissions when methods are invoked via Javascript even if applet is signed, 
    			// thus some code needs to wrapped in a privileged block
    			AccessController.doPrivileged(new PrivilegedAction<Object>() {

					@Override
					public Object run() {
						
						try {
							List<String> ffmpegArgs = new ArrayList<String>();
				            ffmpegArgs.add(FFMPEG_EXEC.getAbsolutePath());
				            // for full screen, use simply "cursor:desktop"
				            int width = Applet.CAPTURE_VIEWPORT.width % 2 == 0 ? Applet.CAPTURE_VIEWPORT.width : Applet.CAPTURE_VIEWPORT.width - 1;
				            int height = Applet.CAPTURE_VIEWPORT.height % 2 == 0 ? Applet.CAPTURE_VIEWPORT.height : Applet.CAPTURE_VIEWPORT.height - 1;
				            String viewport = ":offset="+Applet.CAPTURE_VIEWPORT.x+","+Applet.CAPTURE_VIEWPORT.y;
				            viewport += ":size="+width+","+height;
				            ffmpegArgs.addAll(parseParameters("-y -f gdigrab -r 20 -i cursor:desktop"+viewport+" -vcodec mpeg4 -b 5000k "+OUTPUT_FILE));
				            
				        	logger.info("Executing this command: "+prettyCommand(ffmpegArgs));
				            ProcessBuilder pb = new ProcessBuilder(ffmpegArgs);
							recordingProcess = pb.start();
				            //fireProcessUpdate(RECORDING_STARTED); // moved to action listener method
				            
							// ffmpeg doesn't get the microphone on Windows, but this allows it to record a better frame rate anyway
							
				            errorGobbler = new StreamGobbler(recordingProcess.getErrorStream(), false, "ffmpeg E");
				            inputGobbler = new StreamGobbler(recordingProcess.getInputStream(), false, "ffmpeg O");
				            
				            logger.info("Starting listener threads...");
				            errorGobbler.start();
				            errorGobbler.addActionListener("Stream mapping:", self);
				            inputGobbler.start();
				            			         
							recordingProcess.waitFor();

				            fireProcessUpdate(RECORDING_COMPLETE);
			            
						}
			            catch (Exception e) {
							logger.error("Error while running Windows screen recorder!",e);
						}
						return null;
					}
				});
	            
    		}
           
      } catch (Exception ie) {
    	  logger.error("Exception while running ScreenRecorder!",ie);
      }
	}
	
	public void startRecording() {      
		if(Applet.IS_MAC) {
	    	PrintWriter pw = new PrintWriter(recordingProcess.getOutputStream());
	    	pw.println("start");
	    	pw.flush();
		}
		// nothing for linux or windows
	}
	
	public void stopRecording() {  
		if(Applet.IS_LINUX || Applet.IS_WINDOWS) {
	    	PrintWriter pw = new PrintWriter(recordingProcess.getOutputStream());
	    	pw.print("q");
	    	pw.flush();
		} else if(Applet.IS_MAC) {
	    	PrintWriter pw = new PrintWriter(recordingProcess.getOutputStream());
	    	pw.println("stop");
	    	pw.flush();
		}
		logger.info("Screen recording should be stopped.");
	}
	
	public void closeDown() {
		logger.info("Closing down ScreenRecorder...");
		if(Applet.IS_MAC && recordingProcess != null) {
	    	PrintWriter pw = new PrintWriter(recordingProcess.getOutputStream());
	    	pw.println("quit");
	    	pw.flush();
		} else if((Applet.IS_LINUX || Applet.IS_WINDOWS) && recordingProcess != null) {
	    	PrintWriter pw = new PrintWriter(recordingProcess.getOutputStream());
	    	pw.print("q");
	    	pw.flush();
		}
		// nothing for linux or windows
	}
	
	@Override
    protected void finalize() throws Throwable {
		logger.info("Finalizing ScreenRecorder...");
    	super.finalize();
    	closeDown();
    	if(recordingProcess != null)
    		recordingProcess.destroy();
    }
    
    public static void deleteOutput() {
    	AccessController.doPrivileged(new PrivilegedAction<Object>() {

			@Override
			public Object run() {
				try {
					if(OUTPUT_FILE.exists() && !OUTPUT_FILE.delete())
						throw new Exception("Can't delete the old video file!");
				} catch (Exception e) {
					logger.error(e.getMessage(),e);
				}
				return null;
			}
		});
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		fireProcessUpdate(RECORDING_STARTED);
	}
}
