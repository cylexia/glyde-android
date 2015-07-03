package com.cylexia.mobile.glyde;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cylexia.mobile.glyde.R;


public class LaunchActivity extends AppCompatActivity {
	private LinearLayout scriptList;

	public LaunchActivity() {
		super();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_launch );

		FileManager.getInstance( this ).setRootPath( Configuration.ROOT_PATH );
		this.scriptList = (LinearLayout)findViewById( R.id.scriptList );
		loadItems();
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate( R.menu.menu_launch, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if( id == R.id.action_settings ) {
			return true;
		}

		return super.onOptionsItemSelected( item );
	}

	private void loadItems() {
		FileManager fm = FileManager.getInstance( this );
		List<String> files = new ArrayList<String>();
		for( int cycle = 0; cycle < 3; cycle++ ) {
			switch( cycle ) {
				case 0:
					files.addAll( loadLocalFileList( fm ) );
					// TODO: launch default activity if one is specified and then get rid of all this
					break;
				case 1:
					for( File f : fm.listFiles( ".app" ) ) {
						files.add( f.getName() );
					}
					break;
				case 2:
					for( File f : fm.listFiles( ".ape" ) ) {
						files.add( (f.getName() + "|main.app") );
					}
					break;
			}
		}
		Collections.sort( files );
		for( String f : files ) {
			try {
				AppResource r = new AppResource( fm, f );
				ListItem item = new ListItem( this, r );
				scriptList.addView( item );
				//fm.setRootPath( Configuration.ROOT_PATH );		// AppResource may change this during load
			} catch( IOException ex ) {
				Log.e( "loadItems()", ("[File: " + f + "] " + ex.toString()) );
			}
		}
	}

	private List<String> loadLocalFileList( FileManager fm ) {
		ArrayList<String> items = new ArrayList<String>();
		String intlist;
		try {
			intlist = fm.readFromFile( "#intapps.lst" );
		} catch( IOException ex ) {
			return items;
		}

		String key, val;
		int s = 0, e, b;
		while( (e = intlist.indexOf( ';', s )) > -1 ) {
			val = intlist.substring( s, e ).trim();
			if( !val.startsWith( "#" ) ) {
				if( (b = val.indexOf( "=" )) > -1 ) {
					key = val.substring( 0, b ).toLowerCase();
					val = val.substring( (b + 1) );
					items.add( ("#" + val + ".app") );
				}
			}
			s = (e + 1);
		}
		return items;
	}

	/**
	 * A View to provide the "button" for each script.  By stacking them it makes a list
	 */
	private class ListItem extends View implements View.OnClickListener {
		private AppResource resource;
		private Bitmap icon;
		private String label;
		private Paint focus_paint;
		private boolean scaled;

		public ListItem( Context context, AppResource r ) {
			super( context );
			this.resource = r;
			this.icon = resource.getIcon();
			this.label = resource.getTitle();
			this.focus_paint = new Paint();
			focus_paint.setColor( Color.parseColor( "#00aaff" ) );
			focus_paint.setStyle( Paint.Style.FILL_AND_STROKE );
			this.scaled = false;

			setFocusable( true );
			setOnClickListener( this );
		}

		@Override
		protected void onDraw( Canvas canvas ) {
			super.onDraw( canvas );
			if( hasFocus() ) {
				canvas.drawRect( 0, 0, getWidth(), getHeight(), focus_paint );
			}
			VecText v = VecText.getInstance();
			int x = 5, y = 5;
			if( icon != null ) {
				if( !scaled ) {
					// we will scale the bitmap here if needed
					int scaleto = (canvas.getWidth() / 7);
					if( scaleto > 96 ) {
						scaleto = 96;
					}
					this.icon = Bitmap.createScaledBitmap( icon, scaleto, scaleto, true );
					this.scaled = true;
				}
				canvas.drawBitmap( icon, 5, 5, null );
				x += (icon.getWidth() + 5);
				y = (5 + ((icon.getHeight() - v.getGlyphHeight( 4, 2 )) / 2));
			}
			if( canvas.getWidth() > 500 ) {        // 480 is usual
				v.drawString( canvas, label, Color.BLACK, x, y, 4, 2, 2 );
			} else {
				v.drawString( canvas, label, Color.BLACK, x, y, 2, 1, 2 );
			}
		}

		@Override
		protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
			VecText v = VecText.getInstance();
			int w = (v.getGlyphWidth( 4, 2 ) * label.length());
			int h = v.getGlyphHeight( 4, 2 );
			if( icon != null ) {
				w += (icon.getWidth() + 5);
				h = icon.getHeight();
			}
			//setMeasuredDimension( (w + 10), (h + 10) );
			int parentWidth = MeasureSpec.getSize( widthMeasureSpec );
			setMeasuredDimension( parentWidth, (h + 10) );
		}

		@Override
		public void onClick( View v ) {
			if( v == this ) {
				try {
					resource.configureForLaunch();			// setup root path and such
					Intent launch = new Intent( getContext(), ViewActivity.class );
					launch.putExtra( ViewActivity.STORE_SCRIPT, resource.getScript() );
					launch.putExtra( ViewActivity.STORE_ICON_NAME, resource.getIconName() );
					Map<String, String> vars = resource.getVariables();
					if( vars != null ) {
						launch.putExtra( ViewActivity.STORE_VARIABLES, Glue.mapToParts( vars ) );
					}
					getContext().startActivity( launch );
				} catch( IOException ex ) {
					Toast.makeText( getContext(), "Unable to access required script files", Toast.LENGTH_SHORT ).show();
				}
			}
		}
	}

	/**
	 * Access to a ".app" file's contents and what they mean to the system
	 */
	private static class AppResource {
		private List<String> scriptfiles;
		private String title;
		private String icon_path;
		private FileManager fmgr;
		private Map<String, String> variables;
		//private String change_root;

		public AppResource( FileManager fmgr, String srcfile ) throws IOException {
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
}
