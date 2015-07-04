package com.cylexia.mobile.glyde.exe;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;
import com.cylexia.mobile.lib.glue.LogOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by sparx104 on 03/07/2015.
 */
public class GlueTestRunner implements Glue.Executable {
	public final static int GTR_EXE = 0;
	public final static int PLATFORM_TEST_EXE = 1;

	private final int app_mode;

	public GlueTestRunner(int app) {
		super();
		this.app_mode = app;
	}

	@Override
	public int exec(Glue glue, Context context, String name, String args, String label) {
		switch( app_mode ) {
			case GTR_EXE:
				return execGtrExe( glue, context, name, args, label );

			case PLATFORM_TEST_EXE:
				return execPlatformTestExe( glue, context, name, args, label );
		}
		return 0;
	}

	public int execGtrExe(Glue glue, Context context, String name, String args, String label) {
		FileManager fm = FileManager.getInstance( context );
		try {
			String script = fm.readFromFile( "#tests/" + args + ".test" );
			Map<String, String> vars = new HashMap<String, String>();
			Glue g = Glue.init( context );
			g.setStdOut( LogOutputStream.getInstance( "stdout" ) );
			g.addExecutable( "platform-test.exe", new GlueTestRunner( GlueTestRunner.PLATFORM_TEST_EXE ) );
			g.load( script, vars );
			g.run();
		} catch( IOException ex ) {
			Log.e( "GTR", ex.toString() );
		}
		String res = LogOutputStream.getInstance( "stdout" ).toString();
		Log.i( "GTR", res );
		glue.setRedirectLabel( label );
		return Glue.PLUGIN_INLINE_REDIRECT;
	}

	public int execPlatformTestExe(Glue glue, Context context, String name, String args, String label) {
		int i = args.length();
		StringBuilder rev = new StringBuilder( i );
		while( --i >= 0 ) {
			rev.append( args.charAt( i ) );
		}
		try {
			FileManager.getInstance( context ).writeToFile( "exec.txt", rev.toString() );
			glue.setRedirectLabel( label );
			return Glue.PLUGIN_INLINE_REDIRECT;
		} catch( IOException ex ) {
			return 0;
		}
	}
}