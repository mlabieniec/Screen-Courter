package com.reelfx.model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.reelfx.Applet;
import com.reelfx.controller.LinuxController;
import com.reelfx.controller.WindowsController;
import com.reelfx.model.util.CountingMultipartEntity;
import com.reelfx.model.util.ProcessWrapper;
import com.reelfx.model.util.StreamGobbler;

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
public class PostProcessor extends ProcessWrapper implements ActionListener {
	
	// FILE LOCATIONS AND FLAGS
	private static String ext = ".mov"; //Applet.IS_MAC ? ".mov" : ".mp4";
	public static File DEFAULT_OUTPUT_FILE = new File(Applet.BASE_FOLDER.getAbsolutePath()+File.separator+"review"+ext);
	private File outputFile = null;
	private URI postUrl = null;
	private boolean postRecording = false, postData = false;
	private Map<String,Object> metadata;
	
	// ENCODING SETTINGS
	public final static int OFFSET_VIDEO = 0;
	public final static int OFFSET_AUDIO = 1;
	public final static int MERGE_AUDIO_VIDEO = 2;
	public final static int ENCODE_TO_X264 = 3;
	private Map<Integer, String> encodingOpts = new HashMap<Integer, String>();
	
	// STATES
	public final static int ENCODING_STARTED = 0;
	public final static int ENCODING_PROGRESS = 1;
	public final static int ENCODING_FAILED = 2;	
	public final static int ENCODING_COMPLETE = 3;
	public final static int POST_STARTED = 4;
	public final static int POST_PROGRESS = 5;
	public final static int POST_FAILED = 6;
	public final static int POST_COMPLETE = 7;
	
	private static Logger logger = Logger.getLogger(PostProcessor.class);
	protected Process ffmpegProcess;
	protected StreamGobbler errorGobbler, inputGobbler;
	
	public void encodingOptions(Map<Integer,String> opts) {
		encodingOpts = opts;
	}
	
	public synchronized void saveToComputer(File file) {
		if(!file.getName().endsWith(ext) && !file.getName().endsWith(".avi"))
			file = new File(file.getAbsoluteFile()+ext); // extension will probably change for Windows
		outputFile = file;
		postRecording = false;
		postData = false;
		super.start();
	}
	
	// implemented and works, but ended up not using
	public synchronized void postDataToInsight(String url) {
		outputFile = null;
		setPostURI(url);
		postRecording = false;
		postData = true;
		super.start();
	}
	
	public synchronized void postRecordingToInsight(String url) {
		outputFile = DEFAULT_OUTPUT_FILE;
		setPostURI(url);
		postRecording = true;
		postData = false;
		super.start();
	}

	@Override
	public synchronized void start() {
		logger.fatal("Don't call this directly!");
	}

	public void run() {
		try {
			String ffmpeg = "ffmpeg" + (Applet.IS_WINDOWS ? ".exe" : "");
			
			// ----- quickly merge audio and video after recording -----------------------
			if(outputFile != null && encodingOpts.containsKey(MERGE_AUDIO_VIDEO) && !Applet.IS_MAC) {
				fireProcessUpdate(ENCODING_STARTED);
				// get information about the media file:
				//Map<String,Object> metadata = parseMediaFile(ScreenRecorder.OUTPUT_FILE.getAbsolutePath());
				//printMetadata(metadata);
				
				List<String> ffmpegArgs = new ArrayList<String>();
		    	ffmpegArgs.add(Applet.BIN_FOLDER.getAbsoluteFile()+File.separator+ffmpeg);
		    	ffmpegArgs.add("-y"); // overwrite any existing file
		    	// audio and video files
		    	if(AudioRecorder.OUTPUT_FILE.exists()) { // if opted for microphone
		    		// delay the audio if needed ( http://howto-pages.org/ffmpeg/#delay )
		    		if(encodingOpts.containsKey(OFFSET_AUDIO))
		    			ffmpegArgs.addAll(parseParameters("-itsoffset 00:00:0"+encodingOpts.get(OFFSET_AUDIO))); // assume offset is less than 10 seconds
		    		ffmpegArgs.addAll(parseParameters("-i "+AudioRecorder.OUTPUT_FILE.getAbsolutePath()));
		    		// delay the video if needed ( http://howto-pages.org/ffmpeg/#delay )
		    		if(encodingOpts.containsKey(OFFSET_VIDEO))
		    			ffmpegArgs.addAll(parseParameters("-itsoffset 00:00:0"+encodingOpts.get(OFFSET_VIDEO)));
		    	}
		    	ffmpegArgs.addAll(parseParameters("-i "+ScreenRecorder.OUTPUT_FILE));
		    	// export settings
			    ffmpegArgs.addAll(getFfmpegCopyParams());
		    	// resize screen
		    	//ffmpegArgs.addAll(parseParameters("-s 1024x"+Math.round(1024.0/(double)Applet.SCREEN.width*(double)Applet.SCREEN.height)));
		    	
		    	ffmpegArgs.add(outputFile.getAbsolutePath());
		    	logger.info("Executing this command: "+prettyCommand(ffmpegArgs));
		        ProcessBuilder pb = new ProcessBuilder(ffmpegArgs);
		        ffmpegProcess = pb.start();
		
		        errorGobbler = new StreamGobbler(ffmpegProcess.getErrorStream(), false, "ffmpeg E");
		        inputGobbler = new StreamGobbler(ffmpegProcess.getInputStream(), false, "ffmpeg O");
		        
		        logger.info("Starting listener threads...");
		        errorGobbler.start();
		        inputGobbler.start();  
		        
		        ffmpegProcess.waitFor();
		        logger.info("Done encoding...");
		        fireProcessUpdate(ENCODING_COMPLETE);
			} // end merging audio/video
			
			// ----- encode file to X264 -----------------------
			else if(outputFile != null && encodingOpts.containsKey(ENCODE_TO_X264) && !Applet.IS_MAC && !DEFAULT_OUTPUT_FILE.exists()) {
				fireProcessUpdate(ENCODING_STARTED);
				File inputFile = Applet.IS_LINUX ? LinuxController.MERGED_OUTPUT_FILE : WindowsController.MERGED_OUTPUT_FILE;
				
				// get information about the media file:
				//metadata = parseMediaFile(inputFile.getAbsolutePath());
				//printMetadata(metadata);
				
				List<String> ffmpegArgs = new ArrayList<String>();
		    	ffmpegArgs.add(Applet.BIN_FOLDER.getAbsoluteFile()+File.separator+ffmpeg);
		    	ffmpegArgs.addAll(parseParameters("-y -i "+inputFile.getAbsolutePath()));
		    	ffmpegArgs.addAll(getFfmpegX264FastFirstPastBaselineParams());
		    	
		    	ffmpegArgs.add(DEFAULT_OUTPUT_FILE.getAbsolutePath());
		    	logger.info("Executing this command: "+prettyCommand(ffmpegArgs));
		        ProcessBuilder pb = new ProcessBuilder(ffmpegArgs);
		        ffmpegProcess = pb.start();
		
		        errorGobbler = new StreamGobbler(ffmpegProcess.getErrorStream(), false, "ffmpeg E");
		        inputGobbler = new StreamGobbler(ffmpegProcess.getInputStream(), false, "ffmpeg O");
		        
		        logger.info("Starting listener threads...");
		        //errorGobbler.addActionListener("frame", this);
		        errorGobbler.start();
		        inputGobbler.start();  
		        
		        ffmpegProcess.waitFor();
		        logger.info("Done encoding...");
		        fireProcessUpdate(ENCODING_COMPLETE);
			}
			// do we need to copy the X264 encoded file somewhere?
			if(outputFile != null && encodingOpts.containsKey(ENCODE_TO_X264) && !Applet.IS_MAC && !outputFile.getAbsolutePath().equals(DEFAULT_OUTPUT_FILE.getAbsolutePath())) {
				FileUtils.copyFile(DEFAULT_OUTPUT_FILE, outputFile);
				fireProcessUpdate(ENCODING_COMPLETE);
			}
			
			// ----- just copy the file if it's a Mac -----------------------
			if(outputFile != null && Applet.IS_MAC) {
				FileUtils.copyFile(ScreenRecorder.OUTPUT_FILE, outputFile);
				fireProcessUpdate(ENCODING_COMPLETE);
			}
			
			try {	
				
				// ----- post data of screen capture to Insight -----------------------			
		        if(postRecording) {
		        	// base code: http://stackoverflow.com/questions/1067655/how-to-upload-a-file-using-java-httpclient-library-working-with-php-strange-pro
		        	fireProcessUpdate(POST_STARTED);
		        	
		        	HttpClient client = new DefaultHttpClient();
		        	client.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		        	
		        	CountingMultipartEntity entity = new CountingMultipartEntity();
		        	ContentBody body = new FileBody(outputFile,"video/quicktime");
		        	entity.addPart("capture_file",body);
		        	
		        	HttpPost post = new HttpPost(postUrl);
		        	post.setEntity(entity);
		        	
		        	logger.info("Posting file to server... "+post.getRequestLine());
		        	
		        	HttpResponse response = client.execute(post);
		        	HttpEntity responseEntity = response.getEntity();
		        	
		        	logger.info("Response Status Code: "+response.getStatusLine());
		            if (responseEntity != null) {
		            	logger.info(EntityUtils.toString(responseEntity)); // to see the response body
		            }
		            
		            // redirection to show page (meaning everything was correct); NOTE: Insight redirects you to the login form when you're not logged in (or no api_key)
		            //if(response.getStatusLine().getStatusCode() == 302) {
		            	//Header header = response.getFirstHeader("Location");
		            	//logger.info("Redirecting to "+header.getValue());
		            	//Applet.redirectWebPage(header.getValue());
		            	//Applet.APPLET.showDocument(new URL(header.getValue()),"_self");
		            
		            if(response.getStatusLine().getStatusCode() == 200) {
		            	fireProcessUpdate(POST_COMPLETE);
		            } else {
		            	fireProcessUpdate(POST_FAILED);
		            }
		            	
		            if (responseEntity != null) {
		            	responseEntity.consumeContent();
		            }
		        	
		        	client.getConnectionManager().shutdown();
		        }
				// ----- post data of screen capture to Insight -----------------------	        
		        else if(postData) {
		        	fireProcessUpdate(POST_STARTED);
		        	
		        	HttpClient client = new DefaultHttpClient();
			    	client.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			    	
			    	HttpPost post = new HttpPost(postUrl);
			    	
			    	logger.info("Sending data to Insight... "+post.getRequestLine());
			    	
			    	HttpResponse response = client.execute(post);
			    	HttpEntity responseEntity = response.getEntity();
			
			    	logger.info("Response Status Code: "+response.getStatusLine());
			        if (responseEntity != null) {
			        	logger.info(EntityUtils.toString(responseEntity)); // to see the response body
			        }
			        
			        if(response.getStatusLine().getStatusCode() == 200) {	
			        	fireProcessUpdate(POST_COMPLETE);
			        } else {
			        	fireProcessUpdate(POST_FAILED);
			        }
			        	
			        if (responseEntity != null) {
			        	responseEntity.consumeContent();
			        }
			    	
			    	client.getConnectionManager().shutdown();
		        }
		        
		        // TODO monitor the progress of the transcoding?
		        // TODO allow canceling of the transcoding?
		        
		  } catch (Exception e) {
			  logger.error("Error occurred while posting the file.", e);
			  fireProcessUpdate(POST_FAILED);
		  } finally {
			  outputFile = null;   
			  encodingOpts = new HashMap<Integer, String>(); // reset encoding options 
			  metadata = null;
		  }
		}
		catch (Exception e) {
			  logger.error("Error occurred while encoding the file.", e);
			  fireProcessUpdate(ENCODING_FAILED);
		}	
	}
	
	protected void finalize() throws Throwable {
		super.finalize();
		ffmpegProcess.destroy();
	}
	
	/**
	 * s
	 * 
	 * @param url
	 */
	private void setPostURI(String url) {
		try {
			URI given = new URI(url);
			String query = given.getQuery() == null ? "" : given.getQuery();
	    	query = query + (query.isEmpty() ? "" : "&") + (Applet.API_KEY.isEmpty() ? "" : "api_key="+Applet.API_KEY);
	    	postUrl = new URI(given.getScheme(),given.getAuthority(),given.getPath(),query,given.getFragment());
		} catch (Exception e) {
			logger.error("Error occurred while processing the post URL (received: "+url+")", e);
			fireProcessUpdate(POST_FAILED);
		}
	}
	
	/**
	 * Called when a stream gobbler finds a line that relevant to this wrapper.
	 */
	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand().contains("frame")) {
			//double total_frames = (Double)metadata.get(Metadata.TOTAL_FRAMES);
			//logger.info("Found frame! Total frames="+total_frames); // TODO exact the frame number
			fireProcessUpdate(ENCODING_PROGRESS, null);
		}
	}
	
	public static void deleteOutput() {
		AccessController.doPrivileged(new PrivilegedAction<Object>() {

			@Override
			public Object run() {
				try {
					if(DEFAULT_OUTPUT_FILE.exists() && !DEFAULT_OUTPUT_FILE.delete())
						throw new Exception("Can't delete the old audio file!");
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		});
	}
}
