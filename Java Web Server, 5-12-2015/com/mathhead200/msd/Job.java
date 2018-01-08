package com.mathhead200.msd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;


public class Job extends HashMap<String, String>
{
	private static final long serialVersionUID = 3902586398127097663L;

	public static final Path DIR = new File("./jobs/").getAbsoluteFile().toPath().normalize();
	
	private String id = null;

	
	public String getID() {
		return id;
	}

	public void setID(String id) {
		this.id = id;
	}
	
	public File getFile() {
		return DIR.resolve( new File("./" + id).toPath().normalize() ).toFile();
	}

	public static Job read(BufferedReader reader) throws IOException {
		Job job = new Job();
		
		for( String line; (line = reader.readLine()) != null && line.length() != 0; ) {
			String[] arr = line.split("=", 2);
			if( arr.length != 2 )
				throw new IOException("malformed Job file");
			job.put(arr[0], arr[1]);
		}
		
		return job;
	}
	
	public void write(PrintWriter writer) throws IOException {
		for( Entry<String, String> entree : entrySet() ) {
			writer.print( entree.getKey() );
			writer.print("=");
			writer.println( entree.getValue() );
		}
		writer.println();
	}
	
	public void save() throws IOException {
		try( PrintWriter writer = new PrintWriter(new FileWriter(getFile())) ) {
			write(writer);
		}
	}
	
	public void load() throws IOException {
		try( BufferedReader reader = new BufferedReader(new FileReader(getFile())) ) {
			Job job = read(reader);
			putAll(job);
		}
	}
	
	public boolean isSingleThreaded() {
		return !containsKey("threads") || get("threads").equals("1");
	}
}
