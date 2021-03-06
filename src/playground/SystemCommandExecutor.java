package playground;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * This class is part of our example that demonstrates how to 
 * exec (execute) a system process (command) from a Java application.
 * 
 * Documentation for this class is available at this URL:
 * 
 * http://www.devdaily.com/java/java-exec-processbuilder-process-1
 * 
 * Copyright 2010 alvin j. alexander, devdaily.com.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.

 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Please see the following page for the LGPL license:
 * http://www.gnu.org/licenses/lgpl.txt
 * 
 */

public class SystemCommandExecutor
{
  private List<String> commandInformation;
  private String adminPassword;
  
  /**
   * Use this constructor if you don't need to run the command with sudo and a password.
   * 
   * @param commandInformation The command you want to run.
   */
  public SystemCommandExecutor(final List<String> commandInformation)
  {
    // TODO is this the right exception to throw?
    if (commandInformation==null) throw new IllegalStateException("The commandInformation is required.");
    this.commandInformation = commandInformation;
    this.adminPassword = null;
  }

  /**
   * Use this constructor when you want to run the given command with sudo and a supplied
   * password.
   * 
   * @param commandInformation The command you want to run.
   * @param adminPassword The admin or root password for the current system.
   */
  public SystemCommandExecutor(final List<String> commandInformation, final String adminPassword)
  {
    // TODO is this the right exception to throw?
    if (commandInformation==null || adminPassword==null) throw new IllegalStateException("The commandInformation and password are both required.");
    this.commandInformation = commandInformation;
    this.adminPassword = adminPassword;
  }
  
  public int execCommand()
  throws IOException, InterruptedException
  {
    int exitValue = -99;

    try
    {
      ProcessBuilder pb = new ProcessBuilder(commandInformation);
      Process process = pb.start();

      // we're going to write the password to the sudo command, so we need an output writer
      OutputStream stdOutput = process.getOutputStream();
      
      // i'm doing these on a separate line here in case i need to set them to null
      // to get the threads to stop
      InputStream inputStream = process.getInputStream();
      InputStream errorStream = process.getErrorStream();

      // TODO the inputStreamHandler may only be needed for sudo commands
      ThreadedStreamHandler inputStreamHandler = new ThreadedStreamHandler(inputStream, stdOutput, adminPassword);
      ThreadedStreamHandler errorStreamHandler = new ThreadedStreamHandler(errorStream);

      // TODO the inputStreamHandler has a nasty side-effect of hanging if the given password is wrong; fix it
      inputStreamHandler.start();
      errorStreamHandler.start();

      // TODO a better way to do this?
      System.out.println("waiting for process ...");
      exitValue = process.waitFor();
 
      // process is done, stop the threads
      System.out.println("interrupting threads");
      inputStreamHandler.interrupt();
      errorStreamHandler.interrupt();

      // i need to learn about this, but code doesn't work without it
      inputStreamHandler.join();
      errorStreamHandler.join();

      // get the results from the input and error gobblers
      System.out.println("--- Start InputGobbler Output");
      System.out.println(inputStreamHandler.getOutputBuffer());
      System.out.println("--- Stop InputGobbler Output");
      
      System.out.println("");
      System.out.println("--- Start ErrorGobbler Output");
      System.out.println(errorStreamHandler.getOutputBuffer());
      System.out.println("--- Stop ErrorGobbler Output");
    }
    catch (IOException e)
    {
      // TODO deal with this here, or just throw it?
      throw e;
    }
    catch (InterruptedException e)
    {
      // generated by process.waitFor() call
      // TODO deal with this here, or just throw it?
      throw e;
    }
    finally
    {
      return exitValue;
    }
  }
}


