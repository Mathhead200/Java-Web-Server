package com.mathhead200.web_server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;


/**
 * Represents a native-CGI script.
 * Allows you to write CGI-type processes in Java into the server itself.
 * (Also might lead to better performance.)
 * 
 * @author Christopher D'Angelo
 */
public abstract class ServerProcess
{
	public static final class Results
	{
		/** The exit status of this "script" */
		public final int exitStatus;

		/** The output from this "script" */
		public final InputStream out;

		/** The error output from this "script" */
		public final InputStream err;

		private Results(int exitStatus, InputStream out, InputStream err) {
			this.exitStatus = exitStatus;
			this.out = out;
			this.err = err;
		}
	}

	public abstract int runProcess(InputStream in, PrintStream out, PrintStream err,
			Map<String, String> env) throws Exception;

	/**
	 * Invokes {@link #runProcess}, transforming the output stream
	 * that {@link #runProcess} uses into a readable InputStream. <br>
	 * i.e. Converts the bytes coming from <code>in</code> into
	 * different bytes via the abstract method {@link #runProcess}.
	 * 
	 * @param in - an input stream containing the input
	 * @param env - standard environmental variables for a CGI script
	 * @return A {@link Results} object containing the results.
	 */
	public final Results start(InputStream in, Map<String, String> env) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		ByteArrayOutputStream err = new ByteArrayOutputStream(1024);
		int exitStatus = 0;
		try {
			exitStatus = runProcess( in, new PrintStream(out), new PrintStream(err), env );
		} catch(Exception e) {
			e.printStackTrace( new PrintStream(err) );
			exitStatus = Math.abs(e.getClass().getName().hashCode()) % 256; //default error code for unhandled exceptions in Java?
		}
		InputStream rOut = new ByteArrayInputStream( out.toByteArray() );
		InputStream rErr = new ByteArrayInputStream( err.toByteArray() );
		return new Results(exitStatus, rOut, rErr);
	}
}
