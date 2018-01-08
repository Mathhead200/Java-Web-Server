package java_cgi;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

import com.mathhead200.web_server.ServerProcess;


public class Bad extends ServerProcess {

	public int runProcess(InputStream in, PrintStream out, PrintStream err, Map<String, String> env) throws Exception {

		throw new RuntimeException("No worries. This was just a test!");
		
	}

}
