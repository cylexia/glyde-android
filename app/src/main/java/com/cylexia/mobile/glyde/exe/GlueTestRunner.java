package com.cylexia.mobile.glyde.exe;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by sparx104 on 03/07/2015.
 */
public class GlueTestRunner implements Glue.Executable {

	@Override
	public int exec( Glue glue, Context context, String name, String args, String label ) {
		FileManager fm = FileManager.getInstance( context );
		StringOutputStream stdout = new StringOutputStream();
		try {
			String script = fm.readFromFile( "#tests/" + args + ".test" );
			Map<String, String> vars = new HashMap<String, String>();
			Glue g = Glue.init( context );
			g.setStdOut( stdout );
			g.load( script, vars );
			g.run();
		} catch( IOException ex ) {
			Log.e( "GTR", ex.toString() );
		}
		String res = stdout.toString();
		Log.i( "GTR", res );
		glue.setRedirectLabel( label );
		return Glue.PLUGIN_INLINE_REDIRECT;
	}

	private static class StringOutputStream extends OutputStream {
		private StringBuilder data;

		public StringOutputStream() {
			super();
			this.data = new StringBuilder();
		}

		@Override
		public void write(int oneByte) throws IOException {
			data.append( (char)oneByte );
		}

		@Override
		public String toString() {
			return data.toString();
		}
	}
}
