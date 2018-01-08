package com.mathhead200.web_server;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public final class HttpSettings
{
	/** The port this server listens for HTTP connection on. */
	public final int port;
	
	/** Whether or not to allow a persistent connection between HTTP requests.
 		If false, no connection will persist.
 		If true, only HTTP/1.1 or "Connection: Keep-Alive" requests will persist. */
	public final boolean allowPersistentConnections;
	
	/** Should CGI (or ServerProcess) scripts inherent the server's environmental variables. */
	public final boolean inheritServerEnv;
	
	/** A list of possible index file names, in order of preference. */
	public final List<String> indexFiles;
	
	/** A list files to be interpreted and run as CGI scripts. */
	public final List<String> cgiFiles;
	
	/** A collection of paths that lead to Java CGI "scripts" (ServerProcess).
    	Note that actual files at these paths will not resolve! */
	public final Map<String, ServerProcess> javaCGI;
	
	/** An association of default MIME types by file extension.
    	Only used if Java can't acquire the MIME type (via the OS) for a requested file. */
	public final Map<String, String> mimeTypes;

	
	public HttpSettings(
			int port,
			boolean allowPersistentConnections,
			boolean inheritServerEnv,
			List<String> indexFiles,
			List<String> cgiFiles,
			Map<String, ServerProcess> javaCGI,
			Map<String, String> mimeTypes
	) {
		this.port = port;
		this.allowPersistentConnections = allowPersistentConnections;
		this.inheritServerEnv = inheritServerEnv;
		this.indexFiles = Collections.unmodifiableList(indexFiles);
		this.cgiFiles = Collections.unmodifiableList(cgiFiles);
		this.javaCGI = Collections.unmodifiableMap(javaCGI);
		this.mimeTypes = Collections.unmodifiableMap(mimeTypes);
	}
}
