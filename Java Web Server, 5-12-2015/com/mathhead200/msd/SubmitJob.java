package com.mathhead200.msd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Scanner;

import com.mathhead200.web_server.ServerProcess;


public class SubmitJob extends ServerProcess {
	
	public int runProcess(InputStream in, PrintStream out, PrintStream err, Map<String, String> env) {
		
		// setup Job
		Job job = new Job();
		String query;
		if( env.get("REQUEST_METHOD").equalsIgnoreCase("POST") ) {
			Scanner scanner = new Scanner(in);
			query = scanner.nextLine();
			scanner.close();
		} else
			query = env.get("QUERY_STRING");
		for( String str : query.split("&") ) {
			String[] pair = str.split("=", 2);
			job.put( pair[0].toLowerCase(), pair[1] );
		}
		
		
		// figure out which server (JobReciever) to submit the job to
		int bestThreadsInUse = Integer.MAX_VALUE;
		String bestHost = null;
		int bestPort = 0;
		String bestName = null;
		
		try( BufferedReader reader = new BufferedReader(new FileReader("msd-process-handlers")) ) {
			
			for( String line; (line = reader.readLine()) != null; ) {
				if( line.length() == 0 || line.charAt(0) == '#' )
					continue;
				String[] arr = line.split(":", 2);
				if( arr.length < 2 ) {
					err.println("Illegal line: " + line);
					continue;
				}
				String[] brr = arr[1].split("\\s+");
				if( brr.length < 2 ) {
					err.println("Illegal line: " + line);
					continue;
				}
				String host = arr[0];
				int port = 0;
				try {
					port = Integer.parseInt(brr[0]);
				} catch(NumberFormatException e) {
					err.println("Illegal line: " + e);
					continue;
				}
				String name = brr[1];
				if( name.indexOf(',') >= 0 ) {
					err.println("Illegal name: " + name);
					continue;
				}
				
				try( Socket socket = new Socket() ) {
					socket.connect(new InetSocketAddress(host, port) , 5000);
					
					try( PrintWriter writer = new PrintWriter( socket.getOutputStream(), true );
				         Scanner scanner = new Scanner( socket.getInputStream() );
					) {
						
						socket.setSoTimeout(5000);
						writer.println("THREADS");
						int threadsInUse = scanner.nextInt();
						if( threadsInUse < bestThreadsInUse ) {
							bestThreadsInUse = threadsInUse;
							bestHost = host;
							bestPort = port;
							bestName = name;
						}
						
					}
				} catch(UnknownHostException e) {
					err.println("Counld not find host " + name + " at " + host + " on port " + port);
				} catch(SocketTimeoutException e) {
					err.println("No response from host " + name + " at " + host + " on port " + port);
				} catch(IOException e) {
					e.printStackTrace(err);
				}
			}
			
			
		} catch (IOException e) {
			e.printStackTrace(err);
			return 1;
		}
		
		
		// submit job request the server found/selected above
		if( bestHost == null ) {
			err.println("No JobReciever available");
			System.out.println("2");
			return 2;
		}
		out.print("Content-Type: text/plain\r\n\r\n"); // finish HTTP header
		String jobID = null;
		
		try( Socket socket = new Socket(bestHost, bestPort);
		     PrintWriter writer = new PrintWriter( socket.getOutputStream(), true );
		     Scanner scanner = new Scanner( socket.getInputStream() );
		) {
			
			writer.println("SUBMIT");
			job.put("server_name", bestName);
			job.write(writer);
			writer.flush();
			
			jobID = scanner.nextLine();
			
		} catch (IOException e) {
			e.printStackTrace(err);
			return 3;
		}
		
		
		// display jobID to user so that they can look up the job's status later
		out.println(jobID);
		
		
		// done
		return 0;
		
	}

}
