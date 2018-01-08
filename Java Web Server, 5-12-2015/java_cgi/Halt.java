package java_cgi;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

import com.mathhead200.web_server.ServerProcess;


public class Halt extends ServerProcess {

	public int runProcess(InputStream in, PrintStream out, PrintStream err, Map<String, String> env) throws Exception {
		
		while(true)
			Thread.sleep(Long.MAX_VALUE);
		
	}

}
