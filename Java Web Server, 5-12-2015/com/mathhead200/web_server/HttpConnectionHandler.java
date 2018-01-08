package com.mathhead200.web_server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


/**
 * Instantiated to handle individual HTTP connections.
 * 
 * @author Christopher D'Angelo
 */
public class HttpConnectionHandler implements Runnable
{
	/** The socket this instance communicates on. */
	private final Socket socket;
	
	/** Where this instance sends it's output. For logging and debugging. */
	private PipedOutputStream logPipe = new PipedOutputStream();
	
	/** Where this instance sends it's error output. For logging and debugging. */
	private PipedOutputStream errPipe = new PipedOutputStream();
	
	/** Where various behavior-modifying settings are stored. */
	private HttpSettings settings;
	
	
	/**
	 * Creates an instance to handle a connection with the given socket.
	 * 
	 * @param socket - A socket to communicate with.
	 * @param settings - Various settings that modify how this connection should operate
	 */
	public HttpConnectionHandler(Socket socket, HttpSettings settings) {
		this.socket = socket;
		this.settings = settings;
	}
	
	
	/** @see #logPipe */
	public PipedOutputStream getLogPipe() {
		return logPipe;
	}
	
	/** @see #errPipe */
	public PipedOutputStream getErrPipe() {
		return errPipe;
	}
	
	
	/**
	 * Converts the date to RFC 1123 date format, used in HTTP response headers.
	 * 
	 * @param date - A date object.
	 * @return a string in RFC 1123 date format.
	 */
	public static String toHttpDate(Date date) {
		DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		df.setTimeZone( TimeZone.getTimeZone("GMT") );
		return df.format(date);
	}
	
	/** @see #toHttpDate(Date) */
	public static String toHttpDate(long timestamp) {
		return toHttpDate( new Date(timestamp) );
	}
	
	/**
	 * Gets the current server time in RFC 1123 date format, used in HTTP response headers.
	 * 
	 * @return a string in RFC 1123 date format.
	 * @see #toHttpDate(Date)
	 */
	public static String getHttpDtae() {
		return toHttpDate( Calendar.getInstance().getTime() );
	}
	
	/**
	 * Reads a stream of bytes until the two byte sequence "\r\n" (0x0D 0x0A).
	 * Then converts the bytes up to that point into a UTF-8 encoded string.
	 * 
	 * @param input - An input stream to read from.
	 * @return A UTF-8 string, not including the end-of-line sequence \r\n.
	 * @throws IOException - If a read error occurs.
	 */
	public static String nextCRLF(InputStream input) throws IOException {
		StringBuilder builder = new StringBuilder();
		int prev = input.read();
		if( prev < 0 )
			return null;
		int curr = input.read();
		if( curr < 0 )
			return Character.toString((char) prev);
		while( !(prev == '\r' && curr == '\n') ) {
			builder.append((char) prev);
			prev = curr;
			curr = input.read();
			if( curr < 0 )
				break;
		}
		return builder.toString();
	}
	
	/**
	 * Converts a string into a sequence of bytes. Uses UTF-8 encoding.
	 * 
	 * @param message - The string to be converted.
	 * @return A list of UTF-8 encoded bytes (stored as integers for convenience.)
	 */
	public static List<Integer> toUTF8(String message) {
		try {
			ArrayList<Integer> utf8 = new ArrayList<Integer>( message.length() );
			for( byte b : message.getBytes("UTF-8") )
				utf8.add((int) b);
			return utf8;
		} catch(UnsupportedEncodingException e) {
			throw new Error(e); //UTF-8 needs to be a supported encoding.
		}
	}
	
	
	/** Overridden from the {@link Runnable} interface.
	    Where the handling of each HTTP connection takes place.
	    Many invocations of run can be running simultaneously (in different threads.) */
	public void run() {
		
		PrintStream log = new PrintStream(logPipe, true); // wrapping logPipe in a PrintStream
		PrintStream errLog = new PrintStream(errPipe, true); // wrapping errPipe in a PrintStream
		boolean keepAlive; //after each request, set to true iff the connection should be maintained.
		
		log.println("-- Accepting Connection...");
		
		//get the input and output stream for this socket; for communication over the network
		try(	BufferedInputStream input = new BufferedInputStream( socket.getInputStream() );
				PrintStream output = new PrintStream( new BufferedOutputStream(socket.getOutputStream()) );
		) {
			
			//so that if no new bytes (requests) are sent after some time, this connection is closed
			socket.setSoTimeout(15000);
			
			do { //while keepAlive
				
				log.println("-- Getting HTTP Request Header...");
				//parses the first line of an HTTP request for the method, URI, and protocol
				final String method, uri, version;
				{	String request = nextCRLF(input);
					log.println("   HTTP Request: " + request);
					if( request == null )
						break;
					String[] arr = request.split("\\s+", 3);
					method = arr[0];
					uri = arr[1];
					version = arr[2];
				}
				//parses the rest of the HTTP request header
				final Map<String, String> fields = new HashMap<String, String>();
				for( String line; (line = nextCRLF(input)).length() != 0; ) {
					String[] pair = line.split(":", 2);
					fields.put( pair[0].trim().toLowerCase(), pair[1].trim() );
				}
				//reads the HTTP request message body, if any
				byte[] requestMessage;
				if( fields.containsKey("content-length") ) {
					final int N = Integer.parseInt( fields.get("content-length") );
					if( version.equalsIgnoreCase("HTTP/1.1") ) {
						output.print(JavaWebServer.PROTOCOL + " 100 Continue\r\n");
						output.print("Date: " + getHttpDtae() + "\r\n");
						output.print("Server: " + JavaWebServer.NAME + "\r\n");
						output.print("\r\n");
						output.flush();
					}
					ByteArrayOutputStream bufferOut = new ByteArrayOutputStream(N);
					for( int n = 0; n < N; n++ ) {
						int b = input.read();
						if( b < 0 )
							break;
						bufferOut.write(b);
					}
					requestMessage = bufferOut.toByteArray();
				} else
					requestMessage = new byte[0];
				
				//based on the protocol and "Connection" field, should we stay alive?
				if( !settings.allowPersistentConnections )
					keepAlive = false;
				else if( version.equalsIgnoreCase("HTTP/1.1") )
					keepAlive = !fields.containsKey("connection") || !fields.get("connection").equalsIgnoreCase("close");
				else
					keepAlive = fields.containsKey("connection") && fields.get("connection").equalsIgnoreCase("keep-alive");
				
				String status = "200 OK";
				String type = null; //MIME type of response
				String date = null; //last modified date
				List<Integer> message = null; //response's message body
				List<String> cgiHeader = null; //CGI script was run and these are it's header fields
				
				//load requested resource if possible, and generate HTTP response
				LOAD: {
					log.println("-- Validating Request...");
					if( method.equalsIgnoreCase("POST") && !fields.containsKey("content-length") ) {
						errLog.println("The method was POST, but the request did not include a Content-Length field.");
						status = "411 Length Required";
						message = toUTF8( status + "The method was POST, but the request did not include a Content-Length field." );
						break LOAD;
					}
					if( !fields.containsKey("host") ) {
						status = "400 Bad Request";
						type = "text/plain";
						message = toUTF8(status);
						break LOAD;
					}
					if( version.equalsIgnoreCase("HTCPCP/1.0") ) {
						//see HTCPCP, defined in RFC 2324
						errLog.println("Request for coffie could not be filled. (TODO: Future extension?)");
						status = "418 I'm a teapot";
						type = "text/plain";
						message = toUTF8(status + "\nRequest for coffie could not be filled.");
						break LOAD;
					}
					
					log.println("-- Finding Resource...");
					Path fullPath; //true path to the requested resource on the underlying OS
					URI uriObject; //URI object wrapping the URI string, uri, parsed earlier
					final String relPath, query; //relative path resolved against ROOT_DIR, and the query string following '?'
					try {
						uriObject = new URI(uri);
						relPath = uriObject.getPath();
						query = uriObject.getQuery();
						fullPath = JavaWebServer.ROOT_DIR.resolve("./" + relPath).normalize();
					} catch(URISyntaxException e) {
						//URI could not be resolved against ROOT_DIR, send 400 Bad Request
						status = "400 Bad Request";
						type = "text/plain";
						message = toUTF8(status);
						break LOAD;
					}
					//check to make sure requested file would be inside of ROOT_DIR
					//	or a child of a child of ... ROOT_DIR.
					//	Also blocks access to ".hidden" files.
					SECURITY: {
						if( !fullPath.toFile().isHidden() )
							for( Path dir = fullPath; dir != null; dir = dir.getParent() )
								if( dir.equals(JavaWebServer.ROOT_DIR) )
									break SECURITY;
						status = "403 Forbidden";
						type = "text/plain";
						message = toUTF8(status);
						break LOAD;
					}
					
					//define local method: populateEnv
					class Local {
						void populateEnv(Map<String, String> env) {
							for( String key : fields.keySet() ) {
								String envKey = key.toUpperCase().replaceAll("-", "_");
								env.put(envKey, fields.get(key));
							}
							env.put("SERVER_NAME", JavaWebServer.NAME);
							env.put("SERVER_PROTOCOL", JavaWebServer.PROTOCOL);
							env.put("SERVER_PORT", Integer.toString(settings.port));
							env.put("REQUEST_METHOD", method);
							env.put("REMOTE_HOST", socket.getInetAddress().getHostName());
							env.put("REMOTE_ADDR", socket.getInetAddress().getHostAddress());
							env.put("SCRIPT_NAME", relPath);
							if( query != null )
								env.put("QUERY_STRING", query);
						}
					}
					Local local = new Local(); //instance to invoke Local methods on
					
					//check if requested resource is a Java CGI "script"
					if( settings.javaCGI.containsKey(relPath.toString()) ) {
						
						log.println("-- Executing Native Server Process...");
						Map<String, String> env = new HashMap<String, String>();
						if( settings.inheritServerEnv )
							env.putAll( System.getenv() ); //Inherit the system's ENV
						local.populateEnv(env); //populate env with the needed CGI variables
						
						//run the Java CGI process
						ServerProcess.Results results = settings.javaCGI.get(relPath.toString()).start(
								new ByteArrayInputStream(requestMessage), env );
						
						//dump Java CGI process's standard error stream 
						for( int b; (b = results.err.read()) >= 0; )
							errLog.write(b);
						
						//check exit code of Java CGI process
						if( results.exitStatus != 0 ) {
							errLog.println("CGI Process terminated with a non-zero error code: " + results.exitStatus);
							status = "500 Internal Server Error";
							type = "text/plain";
							message = toUTF8( status +"\n(CGI Process terminated with a non-zero error code: "
									+ results.exitStatus + ")" );
							break LOAD;
						}
						
						//parse Java CGI process's standard output, and finalize the HTTP response
						ArrayList<String> head = new ArrayList<String>();
						ArrayList<Integer> data = new ArrayList<Integer>();
						for( String line = nextCRLF(results.out); line != null && line.length() != 0; line = nextCRLF(results.out) )
							head.add(line);
						for( int b; (b = results.out.read()) >= 0; )
							data.add(b);
						cgiHeader = head;
						message = data;
						break LOAD; //stop trying to load a resource, HTTP response is ready
						
					}
					
					//check if the request resource actually exists (i.e. existent file, directory, etc...)
					if( !fullPath.toFile().exists() ) {
						status = "404 Not Found";
						type = "text/plain";
						message = toUTF8(status);
						break LOAD;
					}
					
					//if the requested resource is a directory, search for an index file
					if( fullPath.toFile().isDirectory() )
						for( String indexFile : settings.indexFiles ) {
							Path p = fullPath.resolve(indexFile);
							if( p.toFile().isFile() ) {
								fullPath = p;
								break;
							}
						}
					
					//check the type of resource (now): file, directory, (or other?)
					if( fullPath.toFile().isFile() ) {
						
						log.println("-- Interpreting File Type...");
						String ext; //the file extension
						String fileName = fullPath.getFileName().toString();
						{	int i = fileName.lastIndexOf('.');
							ext = (i <= 0 ? "" : fileName.substring(i + 1));
						}
						
						//check if requested resource is in the list of CGI files
						if( settings.cgiFiles.contains(relPath.toString()) ) {
							
							log.println("-- Executing CGI: " + fullPath);
							//set up a CGI process for native execution
							ProcessBuilder processBuilder = new ProcessBuilder();
							processBuilder.directory( fullPath.getParent().toFile() ); //sets the process's working directory
							//sets up the process's ENV
							Map<String, String> env = processBuilder.environment();
							if( !settings.inheritServerEnv )
								env.clear(); //keeps us from inheriting system's ENV
							local.populateEnv(env); //populate env with the needed CGI variables
							
							//Open the CGI file up and look for a shebang.
							//	If it exists, run the file using the following command.
							//	Otherwise try to run the CGI file as a native executable.
							try( InputStream fileIn = new FileInputStream(fullPath.toFile()) ) {
								if( fileIn.read() == '#' && fileIn.read() == '!' ) {
									log.println("   (Script)");
									try( BufferedReader reader = new BufferedReader(new InputStreamReader(fileIn)) ) {
										processBuilder.command( reader.readLine(), fileName );
									}
								} else {
									log.println("   (Native Executable)");
									processBuilder.command(fileName);
								}
							} catch(IOException e) {
								e.printStackTrace(errLog);
								status = "500 Internal Server Error";
								type = "text/plain";
								message = toUTF8( status + "\n" + e.getMessage() );
								break LOAD;
							}
							
							//try to start process
							Process process = null;
							try {
								process = processBuilder.start();
							} catch(SecurityException e) {
								e.printStackTrace(errLog);
								status = "403 Forbidden";
								type = "text/plain";
								message = toUTF8( status + "\n" + e.getMessage() );
								break LOAD;
							} catch(IOException e) {
								e.printStackTrace(errLog);
								status = "500 Internal Server Error";
								type = "text/plain";
								message = toUTF8( status + "\n" + e.getMessage() );
								break LOAD;
							}
							
							//interface with the CGI process's standard input and output
							try(	BufferedOutputStream processIn = new BufferedOutputStream( process.getOutputStream() );
									BufferedInputStream processOut = new BufferedInputStream( process.getInputStream() );
									BufferedInputStream processErr = new BufferedInputStream( process.getErrorStream() );
							) {
								
								//send request's message body to CGI process's standard input
								processIn.write(requestMessage);
								processIn.close();
								
								//parse CGI process's standard output, and finalize the HTTP response
								//	TODO: potential infinite halt when reading from processOut!
								ArrayList<String> head = new ArrayList<String>();
								ArrayList<Integer> data = new ArrayList<Integer>();
								for( String line = nextCRLF(processOut); line != null && line.length() != 0; line = nextCRLF(processOut) )
									head.add(line);
								for( int b; (b = processOut.read()) >= 0; )
									data.add(b);
								cgiHeader = head;
								message = data;
								
								//dump CGI process's standard error
								//	TODO: potential infinite halt when reading from processErr!
								for( int b; (b = processErr.read()) >= 0; )
									errLog.write(b);
								
								process.waitFor(); //waits for process to finish
								
								//Did the CGI process end, and end with exit code 0?
								if( process.exitValue() != 0 ) {
									errLog.println("CGI Process terminated with a non-zero error code: " + process.exitValue());
									status = "500 Internal Server Error";
									type = "text/plain";
									message = toUTF8( status +"\n(CGI Process terminated with a non-zero error code: "
											+ process.exitValue() + ")" );
									break LOAD;
								}
								
							} catch(IOException e) {
								e.printStackTrace(errLog);
								status = "500 Internal Server Error";
								type = "text/plain";
								message = toUTF8( status +"\n" + e.getMessage() );
								break LOAD;
							} finally {
								process.destroy();
							}
							
						} else { //not CGI
						
							log.println("-- Reading File: " + fullPath);
							//get files MIME type
							type = Files.probeContentType(fullPath);
							if( type == null )
								type = settings.mimeTypes.get(ext);
							log.println("   MIME Type: " + type);
							
							//get files "last modified" date
							date = toHttpDate( fullPath.toFile().lastModified() );
							
							//read the requested file, then finalize the HTTP response
							try( BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(fullPath.toFile())) ) {
								ArrayList<Integer> data = new ArrayList<Integer>();
								for( int b; (b = fileIn.read()) >= 0; )
									data.add(b);
								message = data;
							} catch(IOException e) {
								e.printStackTrace(errLog);
								status = "500 Internal Server Error";
								type = "text/plain";
								message = toUTF8( status + "\n" + e.getMessage() );
							}
						}
						
					} else if( fullPath.toFile().isDirectory() ) {
						//the requested resource was a directory and there was no index file
						//	so generate an index file listing the directory's contents.
						
						log.println("-- Generating Index: " + fullPath);
						type = "text/html";
						StringBuilder builder = new StringBuilder(
								"<!doctype html>\r\n\r\n" +
								"<html>\r\n\r\n" +
								"<head>\r\n" +
								"\t<meta charset='UTF-8' />\r\n" +
								"\t<meta name='generator' content='" + JavaWebServer.NAME + "' />\r\n" +
								"\t<title>Index of " + relPath + "</title>\r\n" +
								"</head>\r\n\r\n\r\n" +
								"<body>\r\n" +
								"\t<h1>Index of " + relPath + "</h1>\r\n" +
								"\t<hr />\r\n" +
								"\t<ul>\r\n"
						);
						builder.append("\t\t<li><a href='" + relPath + "'>.</a></li>\r\n");
						// if( !Files.isSameFile(fullPath, ROOT_DIR) ) //don't display an entry for ".." if this is ROOT_DIR
							builder.append("\t\t<li><a href='" + relPath + "/..'>..</a></li>\r\n");
						for( String child : fullPath.toFile().list() )
							builder.append( String.format( "\t\t<li><a href='%s/%s'>%s</a></li>\r\n", relPath, child, child ) );
						builder.append("\t</ul>\r\n</body>\r\n\r\n</html>");
						
						message = toUTF8( builder.toString() );
						
					} else {
						errLog.println("Request was for not for a file nor a directory?");
						status = "403 Forbidden";
						type = "text/plain";
						message = toUTF8( status + "\nRequest was for not for a file nor a directory?" );
					}
				}
				
				log.println("-- Sending HTTP Response Header...");
				String response = JavaWebServer.PROTOCOL + " " + status;
				log.println("   HTTP Response: " + response);
				
				output.print(response + "\r\n");
				output.print("Date: " + getHttpDtae() + "\r\n");
				output.print("Server: " + JavaWebServer.NAME + "\r\n");
				output.print("Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n");
				// output.print("Cache-Control: no-cache\r\n");
				output.print("Content-Length: " + (message != null ? message.size() : 0) + "\r\n");
				if( type != null )
					output.print("Content-Type: " + type + "\r\n");
				if( date != null )
					output.print("Last-Modified: " + date + "\r\n");
				if( cgiHeader != null ) {
					for( String line : cgiHeader )
						output.print(line + "\r\n");
				}
				output.print("\r\n");
				output.flush();
				
				if( message != null ) {
					log.println("-- Sending Message...");
					for( int b : message )
						output.write(b);
					output.flush();
				}
				
				log.println("--------------------------------------------------------------------------------");
				
			} while(keepAlive);
			
			log.println("-- Closing Connection...");
			
		} catch(SocketTimeoutException e) {
			
			log.println("-- Connection Closed due to Inactivity.");
			
		} catch(Exception e) {
			
			log.println("-- Connection Aborted!");
			e.printStackTrace(errLog);
			
		} finally {
			
			try {
				log.close();
				errLog.close();
				socket.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
			
		}
	}
}
