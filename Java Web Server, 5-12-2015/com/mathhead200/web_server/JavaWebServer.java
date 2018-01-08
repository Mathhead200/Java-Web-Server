package com.mathhead200.web_server;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;


/**
 * Acts as the main class for running MyWebServer.
 * 
 * @author Christopher D'Angelo
 */
public class JavaWebServer
{
	/** The name of this web server. Used in the response header. */
	public static final String NAME = "JavaWebServer, Mathhead200";
	
	/** The protocol used by this server. e.g. "HTTP/1.1" or "HTTP/1.0" */
	public static final String PROTOCOL = "HTTP/1.1";
	
	/** A path to the actual directory that acts as the root directory of this server. */
	public static final Path ROOT_DIR = new File("./public_html").getAbsoluteFile().toPath().normalize();
	
	/** Backup default MIME types used to fill the .mime-types file if it's absent. */
	private static final String[] DEFAULT_MIME_TYPES = {
		"text/css    css",
		"text/csv    csv",
		"text/html   htm  html",
		"text/plain  txt",
		"",
		"image/gif      gif",
		"image/jpeg     jpeg  jpg",
		"image/png      png",
		"image/svg+xml  svg",
		"image/x-icon   ico",
		"",
		"audio/mp4       m4a",
		"audio/mpeg      mp3  mpeg",
		"audio/vnd.wave  wav  wave",
		"",
		"video/mp4    mp4",
		"video/x-flv  flv",
		"",
		"application/ecmascript        es",
		"application/font-woff         woff",
		"application/javascript        js",
		"application/json              json",
		"application/ogg               ogg",
		"application/pdf               pdf",
		"application/rss+xml           rss",
		"application/xhtml+xml         xht  xhtml",
		"application/xml               xml",
		"application/xml-dtd           dtd",
		"application/zip               zip",
		"application/x-7z-compressed   7z",
		"application/x-font-ttf        ttf  dfont",
		"application/x-latex           tex",
		"application/x-rar-compressed  rar",
		"application/x-tar             tar",
	};
	

	public static void main(String[] args) {
		
		try {
			
			// set up GUI for server logging and status monitoring
			final JFrame frame = new JFrame(NAME);
			final JTabbedPane tabbedPane = new JTabbedPane();
			frame.add(tabbedPane);
			
			/**
			 * Created each time a new log is need. Will open a new logging tab
			 * and display what is written to its {@link InputStream} once {@link #run()}.
			 */
			class Logger {
				private JTabbedPane innerPane = new JTabbedPane();
				private JPanel[] panels;
				private Reader[] readers;
				private StringBuilder[] builders;
				private JTextArea[] textAreas;
			
				Logger(String title, boolean closable, InputStream... inputs) {
					panels = new JPanel[inputs.length];
					readers = new Reader[inputs.length];
					builders = new StringBuilder[inputs.length];
					textAreas = new JTextArea[inputs.length];
					
					Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
					for( int i = 0; i < inputs.length; i++ ) {
						if( inputs[i] == null )
							continue;
						readers[i] = new BufferedReader( new InputStreamReader(inputs[i]) );
						builders[i] = new StringBuilder();
						textAreas[i] = new JTextArea( builders[i].toString(), 25, 80 );
						textAreas[i].setFont(font);
						panels[i] = new JPanel(new FlowLayout());
						panels[i].add( new JScrollPane(textAreas[i]) );
						innerPane.add( "[" + i + "]", panels[i] );
					}
					
					if( closable ) {
						ActionListener listener = (e) -> {
							int i = tabbedPane.indexOfComponent(innerPane);
							if( i >= 0 )
								tabbedPane.removeTabAt(i);
						};
						for( JPanel panel : panels ) {
							JButton button = new JButton("Close Log: " + title);
							button.addActionListener(listener);
							panel.add(button);
						}
					}
					innerPane.setSelectedIndex(0);
					tabbedPane.addTab(title, innerPane);
					tabbedPane.setSelectedIndex( tabbedPane.indexOfComponent(innerPane) );
				}
			
				public Runnable getRunnable(int i) {
					if( i < 0 || i >= readers.length || readers[i] == null )
						return null;
					return () -> {
						try {
							while( !Thread.interrupted() ) {
								int c = readers[i].read();
								if( c < 0 )
									break;
								builders[i].append((char) c);
								textAreas[i].setText( builders[i].toString() );
							}
						} catch(IOException e) {
							e.printStackTrace();
						} finally {
							try {
								readers[i].close();
							} catch(IOException e) {
								e.printStackTrace();
							}
						}
					};
				}
				
				public void setInnerTitle(int i, String title) {
					innerPane.setTitleAt(i, title);
				}
			}
			
			PipedOutputStream serverLogPipe = new PipedOutputStream();
			PrintWriter log = new PrintWriter(serverLogPipe, true); // server log: the object (Writer) the server will actually use to write to its log
			
			Logger serverLogger = new Logger( "Server", false, new PipedInputStream(serverLogPipe) );
			serverLogger.setInnerTitle(0, "Log");
			Thread serverLoggerThread = new Thread( serverLogger.getRunnable(0) );
			
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setSize(610, 585);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
			
			serverLoggerThread.start(); // start the logger thread so it will listen to the Writer "log"
			
			
			// set up default server properties
			Properties properties = new Properties();
			properties.setProperty("http_port", "8080");
			properties.setProperty("allow_persistent_connections", "true");
			properties.setProperty("inherit_server_env", "false");
	
			// load server properties file
			log.print("-- Loading '.properties'...  ");
			log.flush();
			try( Reader reader = new FileReader("./.properties") ) {
				properties.load(reader);
				log.println("Done.");
			} catch(FileNotFoundException e) {
				log.println("Failed.");
				log.print("   Creating '.properties'...  ");
				log.flush();
				try( Writer writer = new FileWriter("./.properties") ) {
					properties.store(writer, null);
					log.println("Done.");
				}
			}
	
			// load index file list: .index
			log.print("-- Loading '.index'...  ");
			log.flush();
			List<String> indexFiles = new ArrayList<>();
			try( BufferedReader reader = new BufferedReader(new FileReader("./.index")) ) {
				for( String line; (line = reader.readLine()) != null; )
					if( line.length() != 0 )
						indexFiles.add(line);
				log.println("Done.");
			} catch(FileNotFoundException e) {
				log.println("Failed.");
				log.print("  Creating '.index'...  ");
				log.flush();
				try( PrintWriter writer = new PrintWriter(new FileWriter("./.index")) ) {
					writer.println("index.html");
					writer.println("index.htm");
					log.println("Done.");
				}
				indexFiles.add("index.html");
				indexFiles.add("index.htm");
			}
			
			// load CGI file list: .cgi-files
			log.print("-- Loading '.cgi-files'...  ");
			log.flush();
			List<String> cgiFiles = new ArrayList<>();
			try( BufferedReader reader = new BufferedReader(new FileReader("./.cgi-files")) ) {
				for( String line; (line = reader.readLine()) != null; )
					if( line.length() != 0 )
						cgiFiles.add(line);
				log.println("Done.");
			} catch(FileNotFoundException e) {
				log.println("Failed.");
				log.print("  Creating '.cgi-files'...  ");
				log.flush();
				if( new File("./.cgi-files").createNewFile() )
					log.println("Done.");
				else
					throw new IOException("could not create new file '.cgi-files'");
			}
			
			// load Java CGI list: '.java-cgi'
			log.print("-- Loading '.java-cgi'...  ");
			log.flush();
			Map<String, ServerProcess> javaCGI = new HashMap<>();
			try( BufferedReader reader = new BufferedReader(new FileReader("./.java-cgi")) ) {
				log.println();
				// read in search paths
				List<File> files = new ArrayList<>();
				for( String line; (line = reader.readLine()) != null && line.length() != 0; )
					files.add( new File(line) );
				
				// setup URLs for ClassLoader
				URL[] searchPath = new URL[files.size()];
				for( int i = 0; i < searchPath.length; i++ )
					searchPath[i] = files.get(i).toURI().toURL();
				
				// read ServerProcess class to path(s) mappings
				try( URLClassLoader loader = new URLClassLoader(searchPath) ) { // try-with-resource so the URLClassLoader is closed
					for( String line; (line = reader.readLine()) != null; ) {
						String[] arr = line.split("\\s+");
						if( arr.length < 2 )
							continue;
						try {
							Class<?> c = loader.loadClass(arr[0]);
							for( int i = 1; i < arr.length; i++ )
								try {
									javaCGI.put( arr[i], (ServerProcess) c.newInstance() );
									log.println("   Successfully Loaded Java CGI '" + c.getName() + "' at '" + arr[i] + "'");
								} catch(IllegalAccessException | InstantiationException | ClassCastException e) {
									log.println("   Warning: " + e);
								}
						} catch(ClassNotFoundException e) {
							log.println("   Warning: " + e);
						}
					}
				}
			} catch(FileNotFoundException e) {
				log.println("   Failed.");
				log.print("   Creating '.java-cgi'...  ");
				log.flush();
				if( new File("./.java-cgi").createNewFile() )
					log.println("Done.");
				else
					throw new IOException("could not create new file '.java-cgi'");
			}
			
			// load mime types: .mime-types
			log.print("-- Loading '.mime-types'...  ");
			log.flush();
			Map<String, String> mimeTypes = new HashMap<>();
			try( BufferedReader reader = new BufferedReader(new FileReader("./.mime-types")) ) {
				for( String line; (line = reader.readLine()) != null; ) {
					String[] arr = line.split("\\s+");
					if( arr.length == 0 )
						continue;
					for( int i = 1; i < arr.length; i++ )
						mimeTypes.put( arr[i], arr[0] );
				}
				log.println("Done.");
			} catch(FileNotFoundException e) {
				log.println("Failed.");
				log.print("   Creating '.mime-types'...  ");
				log.flush();
				try( PrintWriter writer = new PrintWriter(new FileWriter("./.mime-types")) ) {
					for( String line : DEFAULT_MIME_TYPES )
						writer.println(line);
					log.println("Done.");
				}
				for( String line : DEFAULT_MIME_TYPES ) {
					String[] arr = line.split("\\s+");
					if( arr.length == 0 )
						continue;
					for( int i = 1; i < arr.length; i++ )
						mimeTypes.put( arr[i], arr[0] );
				}
			}
			
			// setup HttpSettings object
			HttpSettings httpSettings = new HttpSettings(
					Integer.parseInt( properties.getProperty("http_port") ),
					Boolean.parseBoolean( properties.getProperty("allow_persistent_connections") ),
					Boolean.parseBoolean( properties.getProperty("inherit_server_env") ),
					indexFiles,
					cgiFiles,
					javaCGI,
					mimeTypes
			);
			
			
			// start listening for HTTP connections
			ExecutorService threadPool = Executors.newCachedThreadPool(); // create thread pool to handle each connection asynchronously
			
			try( ServerSocket server = new ServerSocket(httpSettings.port) ) {
				
				Map<String, Integer> prevAddrCounts = new HashMap<>();
				
				while( frame.isDisplayable() ) {
					log.println("-- Accepting connections...");
					Socket connection = server.accept();
					HttpConnectionHandler connectionHandler = new HttpConnectionHandler(connection, httpSettings);
					
					String addr = connection.getInetAddress().getHostAddress();
					String title = addr;
					if( prevAddrCounts.containsKey(addr) ) {
						int n = prevAddrCounts.get(addr) + 1;
						title += " (" + n + ")";
						prevAddrCounts.put(addr, n);
					} else
						prevAddrCounts.put(addr, 1);
					
					Logger connectionLogger = new Logger( title, true,
							new PipedInputStream(connectionHandler.getLogPipe()),
							new PipedInputStream(connectionHandler.getErrPipe()) );
					connectionLogger.setInnerTitle(0, "Output Log");
					connectionLogger.setInnerTitle(1, "Error Log");
					threadPool.execute( connectionLogger.getRunnable(0) );
					threadPool.execute( connectionLogger.getRunnable(1) );
					
					threadPool.execute(connectionHandler);
					log.println( "   Connection Accepted: " + connection.getInetAddress().getHostAddress() );
				}
				
			} catch(Exception e) {
				e.printStackTrace();
				System.exit(1);
			} finally {
				threadPool.shutdownNow();
				serverLoggerThread.interrupt();
			}
			
		} catch(IOException e) {
			e.printStackTrace();
		} catch(NumberFormatException e) {
			e.printStackTrace();
		}
	}
}
