package java_cgi;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

import com.mathhead200.web_server.ServerProcess;


public class Dump extends ServerProcess {

	public int runProcess(InputStream in, PrintStream out, PrintStream err, Map<String, String> env) throws Exception {
		
		try {
			out.print("Content-Type: text/plain\r\n\r\n");
			out.print("STDIN: ");
			for( int b; (b = in.read()) >= 0; )
				out.write(b);
			out.print("\r\n\r\n");
			for( String var : env.keySet() )
				out.printf( "%s: %s\r\n", var, env.get(var) );
			return 0;
		} catch(IOException e) {
			e.printStackTrace(err);
			return 1;
		}
		
	}

}
