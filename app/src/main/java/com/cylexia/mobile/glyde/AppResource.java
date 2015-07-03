package com.cylexia.mobile.glyde;

import android.graphics.Bitmap;
import android.util.Log;

import com.cylexia.mobile.lib.glue.FileManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Access to a ".app" file's contents and what they mean to the system
 */
class AppResource {
	private List<String> scriptfiles;
	private String title;
	private String icon_path;
	private FileManager fmgr;
	private Map<String, String> variables;
	//private String change_root;

	public AppResource(FileManager fmgr, String srcfile) throws IOException {
		super();
		this.fmgr = fmgr;
		String src = FileManager.readInputStream( fmgr.getInputStream( srcfile ) );
		this.scriptfiles = new ArrayList<String>();
		int s = 0, e, b;
		String line, k, v;
		while( (e = src.indexOf( ';', s )) > -1 ) {
			line = src.substring( s, e ).trim();
			s = (e + 1);

			if( (b = line.indexOf( '=' )) > -1 ) {
				k = line.substring( 0, b ).toLowerCase();
				v = line.substring( (b + 1) );
				if( k.equals( "script" ) ) {
					scriptfiles.add( v );
				} else if( k.equals( "icon" ) ) {
					this.icon_path = v;
				} else if( k.equals( "title" ) ) {
					this.title = v;
				} else if( k.equals( "chroot" ) ) {
					//this.change_root = (Configuration.ROOT_PATH + File.pathSeparator + v);
					//fmgr.setRootPath( change_root );
				} else if( k.equals( "var" ) ) {
					if( (b = v.indexOf( '=' )) > -1 ) {
						if( variables == null ) {
							this.variables = new HashMap<String, String>();
						}
						variables.put( v.substring( 0, b ), v.substring( (b + 1) ) );
					}
				}
			}
		}
	}

	public String getScript() throws IOException {
		StringBuilder script = new StringBuilder();
		for( String src : scriptfiles ) {
			script.append( fmgr.readFromFile( src ) ).append( "\n\n" );
		}
		return script.toString();
	}

	public String getTitle() {
		return title;
	}

	public Bitmap getIcon() {
		if( icon_path.isEmpty() ) {
			return null;
		}
		try {
			return fmgr.getBitmap( icon_path );
		} catch( IOException ex ) {
			Log.e( "getIcon()", ex.toString() );
			return null;
		}
	}

	public String getIconName() {
		return icon_path;
	}

	/**
	 * Perform any final configuration changes to the environment before launching the application
	 */
	public void configureForLaunch() {
		//if( change_root != null ) {
		//	fmgr.setRootPath( change_root );
		//}
	}

	public Map<String, String> getVariables() {
		return variables;
	}
}
