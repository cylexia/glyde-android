package com.cylexia.mobile.glyde.exe;

import android.content.Context;
import android.content.DialogInterface;

import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.cylexia.mobile.glyde.glue.ExtGlyde;
import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.IOException;
import java.util.Map;

import com.cylexia.mobile.glyde.ViewActivity;

/**
 * Created by sparx104 on 30/04/2015.
 */
public class UI implements Glue.Executable {
	private ViewActivity activity;
	private String output_file;
	private String done_label;

	public UI( ViewActivity v ) {
		super();
		this.activity = v;
	}

	@Override
	public int exec( Glue glue, Context context, String name, String args, String label ) {
		// ui ask -prompt -value -file
		// ui choose -prompt -items -file

		Map<String, String> a = FileManager.parseCommandLine( args );
		String cmd = a.get( "_" );
		if( cmd == null ) {
			return 0;
		}
		this.output_file = a.get( "file" );
		this.done_label = label;
		if( cmd.equals( "ask" ) ) {
			ask( context, a );
		} else if( cmd.equals( "choose" ) ) {
			choose( context, a );
		}
		return ExtGlyde.GLUE_STOP_ACTION;
	}

	private void ask( Context context, Map<String, String> args ) {
		AlertDialog.Builder alert = new AlertDialog.Builder( context );

		alert.setTitle( valueOf( args, "title", null ) );
		alert.setMessage( valueOf( args, "prompt", "" ) );

		EditText input = new EditText( context );
		input.setText( valueOf( args, "value", "" ) );
		alert.setView( input );

		alert.setPositiveButton( valueOf( args, "ok", "OK" ), new DialogButtonListener( input ) );
		alert.setNegativeButton( valueOf( args, "cancel", "Cancel" ), new DialogButtonListener( null ) );

		alert.show();
	}

	private void choose( Context context, Map<String, String> args ) {
		//
	}

	private String valueOf( Map<String, String> w, String k, String d ) {
		if( w.containsKey( k ) ) {
			return w.get( k );
		}
		return d;
	}

	private void saveValue( String value ) {
		try {
			FileManager.getInstance( activity ).writeToFile( output_file, value );
			activity.runScriptAndSync( done_label );
		} catch( IOException ex ) {
			Toast.makeText( activity, ("Unable to write file: " + ex.toString()), Toast.LENGTH_SHORT ).show();
			Log.e( "UI", ("saveValue: " + ex.toString()) );
		}
	}

	private class DialogButtonListener implements DialogInterface.OnClickListener {
		private View view;

		public DialogButtonListener( View v ) {
			super();
			this.view = v;
		}

		@Override
		public void onClick( DialogInterface dialog, int which ) {
			String value;
			if( view == null ) {
				value = "";
			} else if( view instanceof EditText ) {
				value = ((EditText)view).getText().toString();
			} else {
				value = "";		// TODO: chooser
			}
			saveValue( value );
		}
	}
}
