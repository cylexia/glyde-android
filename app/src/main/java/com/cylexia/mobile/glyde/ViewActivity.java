package com.cylexia.mobile.glyde;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.cylexia.mobile.glyde.glue.ExtFrontEnd;
import com.cylexia.mobile.glyde.glue.HashCode;
import com.cylexia.mobile.glyde.glue.UI;

import com.cylexia.mobile.glyde.R;
import com.cylexia.mobile.glyde.glue.Wget;


public class ViewActivity extends AppCompatActivity {
	public static final String STORE_VARIABLES = "com.cylexia.variables";
	public static final String STORE_SCRIPT = "com.cylexia.script";
	public static final String STORE_ICON_NAME = "com.cylexia.iconname";

	//private FrontEndView mainView;
    private FrontEndView mainView;
	private String script;
	private Map<String, String> variables;
	private Glue runtime;
	private ExtFrontEnd ext_frontend;
	private int change_orientation;

	private boolean wait_for_measure_and_start;

	public ViewActivity() {
		super();
	}

	public ViewActivity( String script ) {
		this();
		this.script = script;
	}

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_view);
        FrameLayout l = (FrameLayout)findViewById( R.id.viewLayout );

		//l.setLayoutParams( new FrameLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );

		// since this activity may be created without the launcher having been created we need to do the same
		// setup we do over there, in here...
		FileManager.getInstance( this ).setRootPath( Configuration.ROOT_PATH );

		if( savedInstanceState != null ) {		// this activity has been here before...
			this.script = savedInstanceState.getString( STORE_SCRIPT );
			this.variables = Glue.partsToMap( savedInstanceState.getString( STORE_VARIABLES ) );
		} else {
			Intent launch = getIntent();
			if( (launch != null) && launch.hasExtra( STORE_SCRIPT ) ) {
				this.script = launch.getStringExtra( STORE_SCRIPT );
				String vars = launch.getStringExtra( STORE_VARIABLES );
				if( (vars != null) && !vars.isEmpty() ) {
					this.variables = Glue.partsToMap( vars );
				} else {
					this.variables = new HashMap<String, String>();
				}
				String icon = launch.getStringExtra( STORE_ICON_NAME );
				if( icon != null ) {
					try {
						Bitmap bitmap = FileManager.getInstance( this ).getBitmap( icon );
						BitmapDrawable bmpd = new BitmapDrawable( getResources(), bitmap );
						if( bitmap != null ) {
							if( getSupportActionBar() != null ) {
								getSupportActionBar().setIcon( bmpd );
							}
						}
					} catch( Exception ex ) {
						Log.e( "ViewInit", ("SetActivityIcon: " + ex.toString()) );
						// just ignore it
					}
				}
			} else {
				// we should never be here (actually, if we ever implement the idea of adding
				// shortcuts to the app list we may launch directly and need to decode that
				// here
				AppResource resource;
				try {
					resource = new AppResource( FileManager.getInstance( this ), Configuration.STANDALONE_APP_FILE );
					resource.configureForLaunch();            // setup root path and such

					this.script = resource.getScript();
					this.variables = resource.getVariables();
					if( variables == null ) {
						this.variables = new HashMap<String, String>();
					}
					Bitmap icon = resource.getIcon();
					if( icon != null ) {
						try {
							BitmapDrawable bmpd = new BitmapDrawable( getResources(), icon );
							if( icon != null ) {
								if( getSupportActionBar() != null ) {
									getSupportActionBar().setIcon( bmpd );
								}
							}
						} catch( Exception ex ) {
							Log.e( "ViewInit", ("SetActivityIcon: " + ex.toString()) );
							// just ignore it
						}
					}
				} catch( IOException ex ) {
					Toast.makeText( this, "Unable to start", Toast.LENGTH_LONG ).show();
					return;
				}
			}
		}

		// TODO: setup the Glue object
		this.runtime = Glue.init( this );
		this.ext_frontend = new ExtFrontEnd( FileManager.getInstance( this ) );
		ext_frontend.setSize( 500, 500 );		// TODO: this will probably need all this to be moved to a view...
		runtime.attachPlugin( ext_frontend );

		// attach executables
		// -v- CUSTOMISE -v-
		//  Add or remove entries here according to what your script(s) need
		runtime.addExecutable( "ui", new UI( this ) );
		runtime.addExecutable( "wget", new Wget( this ) );
		runtime.addExecutable( "hashcode", new HashCode() );
		// -^- CUSTOMISE -^-

		this.mainView = new FrontEndView( this, ext_frontend );
		l.addView( mainView );

		// TODO: use a "launcher" screen to invoke this view so script will never be null as the launcher will provide one

		if( (script != null) && !script.isEmpty() ) {
			runtime.load( script );

			// and run it.  it's up to the script to keep track of being restarted due to
			// orientation changes and such
			runScriptAndSync( "" );
		}
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString( STORE_SCRIPT, script );
		outState.putString( STORE_VARIABLES, Glue.mapToParts( variables ) );
		super.onSaveInstanceState( outState );
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(android.content.res.Configuration newConfig) {
		// TODO: deal with this
		super.onConfigurationChanged( newConfig );
	}

	public void runScriptAndSync( String label ) {
		Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
		if( (display.getRotation() == Surface.ROTATION_0) || (display.getRotation() == Surface.ROTATION_180 ) ) {
			variables.put( "ORIENTATION", "h" );
		} else {
			variables.put( "ORIENTATION", "p" );
		}
		boolean repeat = true;
		while( repeat ) {
			repeat = false;
			int result = runtime.run( variables, label );
			mainView.syncUI();
			String t = ext_frontend.getWindowTitle();
			if( (t != null) && !t.isEmpty() ) {
				setTitle( t );
			} else {
				setTitle( "Glyde" );
			}
			switch( result ) {
				case 1:
					// nothing more to do
					break;
				case 0:
					Toast.makeText( this, ("Glue Error: " + runtime.getLastError()), Toast.LENGTH_SHORT ).show();
					break;

				case ExtFrontEnd.GLUE_STOP_ACTION:
					label = doFrontEndAction();
					if( label != null ) {
						repeat = true;
					}
					break;
			}
		}
		if( change_orientation > 0 ) {
			int o = change_orientation;
			this.change_orientation = 0;
			switch( o ) {
				case 1:
					setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_PORTRAIT );
				case 2:
					setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE );
				case 3:
					setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE );
			}
		}
	}

	private String doFrontEndAction() {
		String action = ext_frontend.getAction();
		if( action == null ) {
			return null;
		}
		action = action.toLowerCase();
		String params = ext_frontend.getActionParams();
		String[] labels = ext_frontend.getResumeLabel().split( "\t" );		// DONE|ERROR|UNSUPPORTED
		if( action.equals( "setorientation" ) ) {
			if( params.equalsIgnoreCase( "portrait" ) ) {
				this.change_orientation = 1;
			} else if( params.equalsIgnoreCase( "landscape" ) ) {
				this.change_orientation = 2;
			} else if( params.equalsIgnoreCase( "reverse_landscape" ) ) {
				this.change_orientation = 3;
			}
		} else if( action.equals( "setscalemode" ) ) {
			params = params.toLowerCase();
			if( params.equals( "none" ) ) {
				mainView.setScaleType( ImageView.ScaleType.MATRIX );
				mainView.setImageMatrix( new Matrix() );
			} else { // if( params.equals( "top" ) ) {
				mainView.setScaleType( ImageView.ScaleType.FIT_START );
			}
		} else if( action.equals( "showtoast" ) ) {
			Toast.makeText( this, params, Toast.LENGTH_SHORT ).show();
		} else if( action.equals( "showlongtoast" ) ) {
			Toast.makeText( this, params, Toast.LENGTH_LONG ).show();
		} else {
			return labels[2];		// unsupported
		}
		return labels[0];
	}
}
