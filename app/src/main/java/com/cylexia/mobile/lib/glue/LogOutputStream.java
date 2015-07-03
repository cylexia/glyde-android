package com.cylexia.mobile.lib.glue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by sparx104 on 28/04/2015.
 */
public class LogOutputStream extends ByteArrayOutputStream {
	private static Map<String, LogOutputStream> instances;

	private LogOutputStream() {
		super();
	}

	@Override
	public String toString() {
		return new String( toByteArray() );
	}

	public static LogOutputStream getInstance( String id ) {
		if( LogOutputStream.instances == null ) {
			LogOutputStream.instances = new HashMap<>();
		}
		if( !LogOutputStream.instances.containsKey( id ) ) {
			LogOutputStream.instances.put( id, new LogOutputStream() );
		}
		return LogOutputStream.instances.get( id );
	}
}
