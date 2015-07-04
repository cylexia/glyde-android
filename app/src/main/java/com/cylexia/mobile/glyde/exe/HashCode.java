package com.cylexia.mobile.glyde.exe;

import android.content.Context;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.IOException;
import java.util.Map;

/**
 * Created by sparx104 on 30/04/2015.
 */
public class HashCode implements Glue.Executable {

	@Override
	public int exec( Glue glue, Context context, String name, String args, String label ) {
		Map<String, String> a = FileManager.parseCommandLine( args );
		String file = a.get( "o" );
		String val = a.get( "_" );
		if( (file == null) || (val == null) ) {
			return 0;
		}
		try {
			FileManager.getInstance( context ).writeToFile( file, String.valueOf( val.hashCode() ) );
			return 1;
		} catch( IOException ex ) {
			// nothing
		}
		return 0;
	}


}
