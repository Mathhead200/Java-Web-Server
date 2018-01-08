package java_cgi;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.mathhead200.web_server.ServerProcess;


public class AddNums extends ServerProcess {

	public int runProcess(InputStream in, PrintStream out, PrintStream err, Map<String, String> env) throws Exception {
		
		out.print("Content-Type: text/plain\r\n\r\n");
		Map<String, String> form = new HashMap<String, String>(4);
		String query;
		if( env.get("REQUEST_METHOD").equalsIgnoreCase("POST") ) {
			Scanner scanner = new Scanner(in);
			query = scanner.nextLine();
			scanner.close();
		} else
			query = env.get("QUERY_STRING");
		for( String str : query.split("&") ) {
			String[] pair = str.split("=", 2);
			form.put( pair[0], pair[1] );
		}
		double a = Double.parseDouble( form.get("a") );
		double b = Double.parseDouble( form.get("b") );
		out.printf( "Dear %s, the sum of %f and %f is %f.",
				form.get("name"), a, b, a + b );
		return 0;
		
	}

}
