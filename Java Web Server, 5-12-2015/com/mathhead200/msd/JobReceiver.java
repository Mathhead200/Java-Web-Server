package com.mathhead200.msd;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class JobReceiver
{
	
	private static class JobScheduler
	{	
		private int threadsInUse = 0;
		private ExecutorService threadPool = Executors.newCachedThreadPool();
		private Set<String> jobIDs = new HashSet<>();
		
		public JobScheduler() {
			for( File file : Job.DIR.toFile().listFiles() )
				if( file.isFile() )
					jobIDs.add( file.getName() );
		}
		
		private class JobMonitor implements Runnable {
			public final Job job;
			
			public JobMonitor(Job job) {
				this.job = job;
			}
			
			public void run() {
				// process is starting
				synchronized(JobScheduler.this) {
					// threads are in use
					threadsInUse += Integer.valueOf( job.get("threads") );
				}
				
				// spawn a process, and monitor it until completion
				ProcessBuilder builder = new ProcessBuilder( job.get("prgm") );
				try {
					
					job.put("status", "running");
					job.put("exit_code", "");
					job.save();
					System.out.println("Starting job " + job.getID());
					
					Process process = builder.start();
					process.waitFor();
					
					System.out.println("Finished job " + job.getID());
					job.put("status", "terminated");
					job.put("exit_code", "" + process.exitValue());
					
				} catch(IOException e) {
					job.put("status", "error");
					job.put("exception", e.toString());
					e.printStackTrace(); // TODO: ...
				} catch(InterruptedException e) {
					job.put("status", "error");
					job.put("exception", e.toString());
					e.printStackTrace(); // TODO: ...
				}
				
				// process done
				synchronized(JobScheduler.this) {
					// threads are now freed up
					threadsInUse -= Integer.valueOf( job.get("threads") );
				}
				
				try {
					job.save();
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		public void submit(Job job) {
			String idHead = job.get("server_name") + "," + job.get("name") + ",";
			int n = 1;
			String id;
			synchronized(this) {
				while( jobIDs.contains(id = idHead + n) )
					n++;
				jobIDs.add(id);
			}
			job.setID(id);
			threadPool.execute( new JobMonitor(job) );
		}
		
		public void shutdown() {
			threadPool.shutdown();
		}
		
		public int getThreadsInUse() {
			synchronized(this) {
				return threadsInUse;
			}
		}
	}
	
	// args[0] - port to listen on, default is port 13194
	// args[1] - total number of threads to work with
	// args[2] - number of threads reserved as express threads (for single threaded jobs)
	public static void main(String[] args) {
		
		// set up command line arguments
		final int port = args.length > 0 ? Integer.parseInt(args[0]) : 13194;
		
		ExecutorService threadPool = Executors.newCachedThreadPool(); // create thread pool to handle each connection and job asynchronously
		JobScheduler scheduler = new JobScheduler();
		
		try( ServerSocket server = new ServerSocket(port) ) {
			
			// to listener for server commands, from the TUI
			threadPool.execute(new Thread(() -> {
				try( Scanner scanner = new Scanner(System.in) ) {

					while( !Thread.interrupted() ) {
						String line = scanner.nextLine().toUpperCase();

						// interpret command
						if( line.equals("QUIT") ) {
							try {
								server.close();
							} catch(IOException e){
								e.printStackTrace();
								System.exit(0);
							}
							break;
						} // else if, TODO: more commands
						else {
							System.out.println("Ignoring unrecognized command: " + line);
						}
					}
					
				}
			}));
			
			// start listening for connections
			System.out.println("-- Listening on port " + port);
			while( !server.isClosed() ) {
				Socket socket = server.accept();
				threadPool.execute(() -> {
					try( BufferedReader in = new BufferedReader( new InputStreamReader(socket.getInputStream()) );
					     PrintWriter out = new PrintWriter( socket.getOutputStream(), true );
					) {
						
						String cmd = in.readLine().toUpperCase();
						if( cmd.equals("THREADS") ) {
							System.out.println( "-- THREADS: " + socket.getInetAddress() );
							int threadsInUse;
							out.println( threadsInUse = scheduler.getThreadsInUse() );
							System.out.println("   threadsInUse = " + threadsInUse);
						} else if( cmd.equals("SUBMIT") ) {
							System.out.println( "-- SUBMIT: "+ socket.getInetAddress() );
							Job job = Job.read(in);
							scheduler.submit(job);
							out.println( job.getID() );
							System.out.println( "   jobID = " + job.getID() );
							System.out.println( "   job   = " + job );
						} else {
							System.out.println( "-- Unrecognized command '" + cmd + "': " + socket.getInetAddress() );
						}
						
					} catch(IOException e) {
						e.printStackTrace();
					} finally {
						try {
							socket.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
			
		} catch(SocketException e) {
		} catch(IOException e) {
			
		} finally {
			
			try {
				System.in.close();
			} catch (IOException e) {
				throw new Error(e);
			}
			threadPool.shutdown();
			scheduler.shutdown();
			
		}
	}

}
