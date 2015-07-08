package com.cylexia.mobile.glyde.glue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;

import com.cylexia.mobile.glyde.ViewActivity;
import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.cylexia.mobile.glyde.VecText;

/**
 * Created by sparx104 on 27/04/2015.
 */
public class ExtGlyde implements Glue.Plugin {
	public static final int GLUE_STOP_ACTION = -200;
	public static final String KEYDEF_USEID = "useid";
	public static final String KEYDEF_LABEL = "label";

	private final FileManager filemanager;

	private Bitmap bitstore;
	private Canvas plane;
	private Map<String, ImageMap> resources;
	private Map<String, Map<String, String>> styles;
	private Map<String, Button> buttons;
	private Map<String, Map<String, String>> keys;

	private List<String> button_sequence;
	private String action, action_params;
	private String resume_label;

	private String last_action_id;
	private String last_error_msg;

	private String window_title;
	private int window_width;
	private int window_height;

	private Timer timer;
	private String timer_label;
	private int timer_interval;

	private Paint background;
	private ViewActivity activity;

	private int _offset_x;
	private int _offset_y;

	public ExtGlyde( ViewActivity activity, FileManager fm) {
		super();
		this.activity = activity;
		this.filemanager = fm;
		this.timer_interval = -1;
	}

	public void setSize( int width, int height ) {
		this.window_width = width;
		this.window_height = height;
		this.bitstore = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
		this.plane = new Canvas( bitstore );
		if( background != null ) {
			plane.drawRect( 0, 0, plane.getWidth(), plane.getHeight(), background );
		}
	}

	public String getWindowTitle() {
		return window_title;
	}

	public int getWindowWidth() {
		return window_width;
	}

	public int getWindowHeight() {
		return window_height;
	}

	/**
	 * If set then the script requests that the given action be performed then resumed from
	 * getResumeLabel().  This is cleared once read
	 * @return
	 */
	public String getAction() {
		String o = action;
		this.action = null;
		return o;
	}

	public String getActionParams() {
		return action_params;
	}

	/**
	 * Certain actions call back to the runtime host and need to be resumed, resume from this label
	 * This is cleared once read
	 * @return
	 */
	public String getResumeLabel() {
		String o = resume_label;
		this.resume_label = null;
		return o;
	}

	/**
	 * Called when this is attached to a {@link com.cylexia.mobile.lib.glue.Glue} instance
	 *
	 * @param g the instance being attached to
	 */
	@Override
	public void glueAttach(Glue g) {
		//
	}

	/**
	 * The bitmap the drawing operations use
	 * @return
	 */
	public Bitmap getBitmap() {
		return bitstore;
	}

	/**
	 * Called to execute a glue command
	 *
	 * @param w    the command line.  The command is in "_"
	 * @param vars the current Glue variables map
	 * @return 1 if the command was successful, 0 if it failed or -1 if it didn't belong to this
	 * plugin
	 */
	@Override
	public int glueCommand( Glue glue, Map<String, String> w, Map<String, String> vars ) {
		String c = w.get( "_" );
		if( c.startsWith( "f." ) ) {
			String wc = w.get( c );
			String cmd = c.substring( 2 );
			if( cmd.equals( "setwidth" ) || cmd.equals( "setviewwidth" ) ) {
				return setupView( w );
			} else if( cmd.equals( "settitle" ) ) {
				this.window_title = wc;
				activity.setTitle( wc );
				return 1;
			} else if( cmd.equals( "setoffsetx" ) ) {
				this._offset_x = intValueOf( w, c );
				this._offset_y = intValueOf( w, "y", intValueOf( w, "aty" ) );

			} else if( cmd.equals( "doaction" ) ) {
				return doAction( glue, wc, w );

			} else if( cmd.equals( "clear" ) || cmd.equals( "clearview" ) ) {
				clearUI();
			} else if( cmd.equals( "shade" ) || cmd.equals( "shadeview" ) ) {
				shadeUI();

			} else if( cmd.equals( "loadresource" ) ) {
				return loadResource( glue, wc, valueOf( w, "as" ) );

			} else if( cmd.equals( "removeresource" ) ) {
				if( resources != null ) {
					resources.remove( wc );
				}

			} else if( cmd.equals( "definestyle" ) ) {
				defineStyle( wc, w );

			} else if( cmd.equals( "getlastactionid" ) ) {
				vars.put( valueOf( w, "into" ), last_action_id );

			} else if( cmd.equals( "onkeypressed" ) ) {
				addKeyPressedHandler( wc, w );

			} else if( cmd.equals( "starttimerwithinterval" ) ) {
				startTimer( glue, intValueOf( w, c ), valueOf( w, "ontickgoto" ) );
			} else if( cmd.equals( "stoptimer" ) ) {
				if( this.timer != null ) {
					timer.cancel();
					this.timer = null;
				}

			} else if( cmd.equals( "drawas" ) ) {
				drawAs( wc, w );

			} else if( cmd.equals( "writeas" ) ) {
				// TODO: colour
				return writeAs( wc, w );

			} else if( cmd.equals( "markas" ) || cmd.equals( "addbutton" ) ) {
				return markAs( wc, w );

			} else if( cmd.equals( "paintrectas" ) ) {
				return paintRectAs( wc, w, false );

			} else if( cmd.equals( "paintfilledrectas" ) ) {
				return paintRectAs( wc, w, true );

			} else if( cmd.equals( "exit" ) ) {
				activity.finish();

			} else {
				return -1;
			}
			return 1;
		}
		return 0;
	}

	public void activityStart() {

	}

	public void activityStop() {
		// should we suspend the timer?
	}

	public void activityDestroy() {
		if( timer != null ) {
			timer.cancel();
		}
	}

	public String tryKeyAction( int keycode, KeyEvent event ) {
		if( keys == null ) {
			return null;
		}
		String keyname;
		switch( keycode ) {
			case KeyEvent.KEYCODE_DPAD_UP:
				keyname = "direction_up";
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				keyname = "direction_left";
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				keyname = "direction_down";
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				keyname = "direction_right";
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_BUTTON_A:
				keyname = "direction_fire";
				break;
			default:
				keyname = String.valueOf( keycode );
				break;
		}
		if( keys.containsKey( keyname ) ) {
			Map<String, String> kdata = keys.get( keyname );
			this.last_action_id = kdata.get( KEYDEF_USEID );
			return kdata.get( KEYDEF_LABEL );
		}
		return null;
	}

	public String getLabelForButtonAt( int x, int y ) {
		if( buttons != null ) {
			for( String id : button_sequence ) {
				Button btn = buttons.get( id );
				if( btn.rect.contains( x, y ) ) {
					this.last_action_id = id;
					return btn.label;
				}
			}
		}
		return null;
	}

	/**
	 * Get the id of the button at the given index
	 * @param index the index of the button
	 * @return the id or null if the index is out of bounds
	 */
	public String getButtonIdAtIndex( int index ) {
		if( button_sequence != null ) {
			if( (index >= 0) && (index < button_sequence.size()) ) {
				return button_sequence.get( index );
			}
		}
		return null;
	}

	/**
	 * Get the rect of the given indexed button
	 * @param index the button index
	 * @return the rect or null if index is out of bounds
	 */
	public Rect getButtonRectAtIndex( int index ) {
		if( button_sequence != null ) {
			String id = getButtonIdAtIndex( index );
			if( id != null ) {
				return buttons.get( id ).rect;
			}
		}
		return null;
	}

	/**
	 * Return the label for the given indexed button.  Also sets the lastActionId value
	 * @param index the index
	 * @return the label or null if index is out of bounds
	 */
	public String getButtonLabelAtIndex( int index ) {
		if( button_sequence != null ) {
			if( (index >= 0) && (index < button_sequence.size()) ) {
				String id = button_sequence.get( index );
				if( id != null ) {
					this.last_action_id = id;
					return buttons.get( id ).label;
				}
			}
		}
		return null;
	}

	public int getButtonSize() {
		if( button_sequence != null ) {
			return button_sequence.size();
		} else {
			return 0;
		}
	}

	public String getLastErrorMessage() {
		try {
			return last_error_msg;
		} finally {
			this.last_error_msg = null;
		}
	}

	private void addKeyPressedHandler( String key, Map<String, String> data ) {
		if( keys == null ) {
			keys = new HashMap<String, Map<String, String>>();
		}
		if( key.equals( "##" ) ) {
			key = "#";
		} else if( (key.length() > 0) && key.startsWith( "#" ) ) {
			try {
				key = String.valueOf( (char)Integer.parseInt( key.substring( 1 ) ) );
			} catch( NumberFormatException ex ) {
				return;
			}
		}
		Map<String, String> k = new HashMap<String, String>();
		k.put( "id", valueOf( data, KEYDEF_USEID ) );
		k.put( "label", valueOf( data, KEYDEF_LABEL ) );
		keys.put( key, k );
	}

	/**
	 * Add a definition to the (lazily created) styles map.  Note, the complete string is stored
	 * so beware that keys like "_" and "f.setstyle" are added too
	 * @param name the name of the style
	 * @param data the complete arguments string
	 */
	private void defineStyle(String name, Map<String, String> data) {
		if( styles == null ) {
			this.styles = new HashMap<String, Map<String, String>>();
		}
		applyStyle( data );
		styles.put( name, data );
	}

	private int setupView( Map<String, String> w ) {
		if( w.containsKey( "backgroundcolour" ) ) {
			this.background = new Paint();
			background.setColor( parseColour( w.get( "backgroundcolour" ) ) );
			background.setStyle( Paint.Style.FILL_AND_STROKE );
			plane.drawRect( 0, 0, plane.getWidth(), plane.getHeight(), background );
		} else {
			this.background = null;
		}
		setSize( intValueOf( w, valueOf( w, "_" ) ), intValueOf( w, "height" ) );
		return 1;
	}

	private void clearUI() {
		this.button_sequence = null;
		this.buttons = null;
		this.keys = null;
		setSize( window_width, window_height );
	}

	private void shadeUI() {
		this.button_sequence = null;
		this.buttons = null;
		this.keys = null;

		Paint p = new Paint();
		p.setColor( Color.BLACK );
		p.setStrokeWidth( 1 );

		int i, e, s;
		if( window_width > window_height ) {
			e = (window_width * 2);
			s = window_height;
		} else {
			e = (window_height * 2);
			s = window_width;
		}
		for( i = 0; i <= e; i += 10 ) {
			plane.drawLine( i, 0, (i - s), s, p );
		}
	}

	private int doAction( Glue g, String action, Map<String, String> w ) {
		this.action = action;
		this.action_params = valueOf( w, "args", valueOf( w, "withargs" ) );
		String done_label = valueOf( w, "ondonegoto" );
		// internal actions we can handle
		if( action.equals( "chrome.hide" ) ) {
			if( activity.getActionBar() != null ) {
				activity.getActionBar().hide();
			}
		} else if( action.equals( "chrome.show" ) ) {
			if( activity.getActionBar() != null ) {
				activity.getActionBar().show();
			}
		} else {
			// pass the action up to the activity to see if it can do it
			StringBuilder b = new StringBuilder();
			b.append( done_label ).append( "\t" );
			b.append( valueOf( w, "onerrorgoto", done_label ) ).append( "\t" );
			b.append( valueOf( w, "onunsupportedgoto", done_label ) );
			this.resume_label = b.toString();
			return ExtGlyde.GLUE_STOP_ACTION;        // expects labels to be DONE|ERROR|UNSUPPORTED
		}
		g.setRedirectLabel( done_label );
		return Glue.PLUGIN_INLINE_REDIRECT;
	}

	private int createEntityAs( String id, Map<String, String> args ) {
		/* param        used by     does
		    value       text        the text value
		    size        text
		    thickness   text
		    textcolour  text        text colour
		    linecolour  rect        the border colour
		    fillcolour  filledrect  the fill colour
		    align       text        text alignment
		*/

		int rx = intValueOf( args, "x", intValueOf( args, "atx" ) );
		int ry = intValueOf( args, "y", intValueOf( args, "aty" ) );
		int rw = intValueOf( args, "width" );
		int rh = intValueOf( args, "height" );
 		Rect rect = new Rect( rx, ry, (rx + rw), (ry + rh) );

		// if we're given an ID or RESOURCE value, the width and height values will be replaced
		//  with those of the resource
		ImageMap resource = null;
		String resid, imgid = null;
		if( args.containsKey( "id" ) || args.containsKey( "resource" ) ) {
			String rid = valueOf( args, "id", valueOf( args, "resource" ) );
			if( resources == null ) {
				return 0;
			}
			int b = rid.indexOf( '.' );
			if( b > -1 ) {
				resid = rid.substring( 0, b );
				imgid = rid.substring( (b + 1) );
				if( resources.containsKey( resid ) ) {
					resource = resources.get( resid );    // imgmap: ExtGlyde.ImageMap
					Rect resrect = resource.getRectWithId( imgid );
					rect = new Rect(
							rect.left,
							rect.top,
							(rect.left + resrect.width()),
							(rect.top + resrect.height())
					);
				} else {
					error( ("No such resource: " + resid) );
					return 0;
				}
			} else {
				error( ("Invalid resource: " + rid) );
				return 0;
			}
		}

		// filled rect first, then empty rect, resource and text
		int d, x, y;
		if( args.containsKey( "fillcolour" ) ) {
			drawRect( rect, parseColour( valueOf( args, "fillcolour" ) ), true );
		}
		if( args.containsKey( "linecolour" ) ) {
			drawRect( rect, parseColour( valueOf( args, "linecolour" ) ), false );
		}

		if( resource != null ) {
			if( !resource.drawToCanvas( imgid, plane, rect.left, rect.top ) ) {
				error( "[Glyde] Unable to draw resource" );
				return 0;
			}
		}

		String text = valueOf( args, "value", "" );
		if( text.length() > 0 ) {
			x = (rect.left + _offset_x);
			y = (rect.top + _offset_y);
			rw = rect.width();
			rh = rect.height();
			int size = intValueOf( args, "size", 2 );
			int thickness = intValueOf( args, "thickness", 1 );
			VecText v = VecText.getInstance();
			int tw = (v.getGlyphWidth( size, thickness ) * text.length());
			int th = v.getGlyphHeight( size, thickness );
			int tx, ty;
			if( rw > 0 ) {
				String align = valueOf( args, "align", "2" );
				if( align.equals( "2" ) || align.equals( "centre" ) ) {
					tx = (x + ((rw - tw) / 2));
				} else if( align.equals( "1" ) || align.equals( "right" ) ) {
					tx = (x + (rw - tw));
				} else {
					tx = x;
				}
			} else {
				rw = tw;
				tx = x;
			}
			if( rh > 0 ) {
				ty = (y + ((rh - th) / 2));
			} else {
				rh = th;
				ty = y;
			}
			int clr = parseColour( valueOf( args, "textcolour", "#000" ) );
			v.drawString( plane, text, clr, tx, ty, size, thickness, (thickness + 1) );
			// if w/h were 0 then replace with the text w/h
			rect = new Rect( x, y, (x + rw), (y + rh) );
		}
		return buttonise( id, rect.left, rect.top, rect.width(), rect.height(), args );
	}

	private void drawRect( Rect r, int clr, boolean filled ) {
		Paint p = new Paint();
		p.setColor( clr );
		p.setStrokeWidth( 1 );
		if( filled ) {
			p.setStyle( Paint.Style.FILL_AND_STROKE );
		} else {
			p.setStyle( Paint.Style.STROKE );
		}
		plane.drawRect(
				(r.left + _offset_x),
				(r.top + _offset_y),
				(r.right + _offset_x),
				(r.bottom + _offset_y),
				p
			);
	}

	private void error( String msg ) {
		this.last_error_msg = msg;
	}

	// TODO: this should use a rect and alignment options along with colour support
	private int writeAs( String id, Map<String, String> args ) {
		applyStyle( args );
		if( !args.containsKey( "textcolour" ) ) {
			args.put( "textcolour", valueOf( args, "colour" ) );
		}
		return createEntityAs( id, args );
	}

	private int drawAs( String id, Map<String, String> args ) {
		applyStyle( args );
		return createEntityAs( id, args );
	}

	private int markAs( String id, Map<String, String> args ) {
		applyStyle( args );
		return buttonise(
				id,
				(intValueOf( args, "x", intValueOf( args, "atx" ) ) + _offset_x),
				(intValueOf( args, "y", intValueOf( args, "aty" ) ) + _offset_y),
				intValueOf( args, "width" ),
				intValueOf( args, "height" ),
				args
		);
	}

	private int paintRectAs( String id, Map<String, String> args, boolean filled ) {
		applyStyle( args );
		if( !args.containsKey( "linecolour" ) ) {
			args.put( "linecolour", valueOf( args, "colour", "#000" ) );
		}
		if( filled ) {
			if( !args.containsKey( "fillcolour" ) ) {
				args.put( "fillcolour", valueOf( args, "colour", "#000" ) );
			}
		}
		return createEntityAs( id, args );
	}

	private int buttonise( String id, int x, int y, int w, int h, Map<String, String> args ) {
		if( args.containsKey( "border" ) ) {
			Paint p = new Paint();
			p.setStrokeWidth( 1 );
			// TODO: illegal colours (#xxx) throw an exception.  this needs to be sorted for all parseColour instances
			p.setColor( parseColour( valueOf( args, "border" ) ) );
			p.setStyle( Paint.Style.STROKE );
			plane.drawRect( x, y, (x + w), (y + h), p );
		}
		if( args.containsKey( "onclickgoto" ) ) {
			if( buttons == null ) {
				this.buttons = new HashMap<>();
				this.button_sequence = new ArrayList<String>();
			}
			if( !buttons.containsKey( id ) ) {
				buttons.put( id, new Button( x, y, w, h, valueOf( args, "onclickgoto" ) ) );
				button_sequence.add( id );
				return 1;
			}
			return 0;
		} else {
			return 1;
		}
	}

	private void applyStyle(Map<String, String> a) {
		if( styles == null ) {
			return;
		}
		if( a.containsKey( "style" ) ) {
			Map<String, String> style = styles.get( valueOf( a, "style" ) );
			for( Map.Entry<String, String> kv : style.entrySet() ) {
				String k = kv.getKey();
				if( !a.containsKey( k ) ) {
					a.put( k, kv.getValue() );
				}
			}
		}
	}

	private void startTimer( Glue g, int interval, String label ) {
		if( timer != null ) {
			timer.cancel();
		}
		this.timer = new Timer();

		timer.schedule( new IntervalTask( g, label ), (interval * 100), (interval * 100) );
	}

	private int loadResource( Glue g, String src, String id ) {
		if( resources == null ) {
			this.resources = new HashMap<String, ImageMap>();
		}
		if( !resources.containsKey( id ) ) {
			try {
				g.setExtraErrorInfo( ("Unable to read from file [src=" + src + "; root=" + filemanager.getRoot().toString() + "]") );
				String data = filemanager.readFromFile( src );
				g.setExtraErrorInfo( ("Unable to load image map [data=" + ((data != null) ? data.substring( 0, 20 ) : "(null)") + "]") );
				resources.put( id, new ImageMap( filemanager, data ) );
				g.setExtraErrorInfo( null );
			} catch( IOException ex ) {
				Log.e( "loadResource", ex.toString() );
				return 0;
			}
		}
		return 1;
	}

	private int intValueOf( Map<String, String> w, String k ) {
		return intValueOf( w, k, 0 );
	}

	private int intValueOf( Map<String, String> w, String k, int def ) {
		if( w.containsKey( k ) ) {
			try {
				return Integer.parseInt( w.get( k ) );
			} catch( NumberFormatException ex ) {
				return 0;
			}
		} else {
			return def;
		}
	}

	private String valueOf( Map<String, String> w, String k ) {
		return valueOf( w, k, "" );
	}

	private String valueOf( Map<String, String> w, String k, String def ) {
		if( w.containsKey( k ) ) {
			return w.get( k );
		} else {
			return def;
		}
	}

	private static int parseColour( String clr ) {
		if( (clr == null) || (clr.length() == 0) ) {
			return Color.BLACK;
		}
		try {
			return Color.parseColor( clr );
		} catch( IllegalArgumentException ex ) {
			if( (clr.length() == 4) && (clr.charAt( 0 ) == '#') ) {
				StringBuilder nclr = new StringBuilder( 7 );
				nclr.append( '#' );
				nclr.append( clr.charAt( 1 ) ).append( clr.charAt( 1 ) );
				nclr.append( clr.charAt( 2 ) ).append( clr.charAt( 2 ) );
				nclr.append( clr.charAt( 3 ) ).append( clr.charAt( 3 ) );
				try {
					return Color.parseColor( nclr.toString() );
				} catch( IllegalArgumentException ex2 ) {
					return Color.BLACK;
				}
			} else {
				return Color.BLACK;
			}
		}
	}

	/**
	 * Stores a button
	 */
	private static class Button {
		public final Rect rect;
		public final String label;

		public Button( int x, int y, int w, int h, String label ) {
			super();
			this.rect = new Rect( x, y, (x + w), (y + h) );
			this.label = label;
		}
	}

	/**
	 * Processes a .map source loading the image named in it or the specified image
	 */
	private static class ImageMap {
		private Bitmap bitmap;
		private final HashMap<String, Rect> rects;

		public ImageMap( FileManager fm, String mapdata ) throws IOException {
			this( fm, mapdata, null );
		}

		public ImageMap( FileManager fm, String mapdata, String bmpsrc ) throws IOException {
			super();
			this.rects = new HashMap<>();
			int e;
			String key, value;
			for( String line : mapdata.split( ";" ) ) {
				line = line.trim();
				e = line.indexOf( "=" );
				if( e > -1 ) {
					key = line.substring( 0, e );
					value = line.substring( (e + 1) );
					if( key.startsWith( "." ) ) {
						if( key.equals( ".img" ) ) {
							if( bmpsrc == null ) {
								bmpsrc = value;
							}
						}
					} else {
						rects.put( key, _decodeRect( value ) );
					}
				}
			}
			_loadBitmap( fm, bmpsrc );
		}

		public Rect getRectWithId( String id ) {
			return rects.get( id );
		}

		public boolean drawToCanvas( String id, Canvas g, int x, int y ) {
			return drawToCanvas( id, g, x, y, null );
		}

		public boolean drawToCanvas( String id, Canvas g, int x, int y, Paint p ) {
			if( bitmap == null ) {
				return false;
			}
			Rect src = getRectWithId( id );
			if( src != null ) {
				Rect dest = new Rect( x, y, (x + src.width()), (y + src.height()) );
				g.drawBitmap( bitmap, src, dest, p );
				return true;
			}
			return false;
		}

		private void _loadBitmap( FileManager fm, String src ) throws IOException {
			if( src.isEmpty() ) {
				return;
			}
			this.bitmap = fm.getBitmap( src );
			if( bitmap == null ) {
				throw new IOException( ("Unable to load map specified bitmap: " + fm.getFile( src ).getAbsolutePath()) );
			}
		}

		private Rect _decodeRect( String e ) {
			if( e.charAt( 1 ) == ':' ) {
				int l = ((int)e.charAt( 0 ) - (int)'0');
				int i = 2, x, y, w, h;
				try {
					x = Integer.parseInt( e.substring( i, (i + l) ) );
					i += l;
					y = Integer.parseInt( e.substring( i, (i + l) ) );
					i += l;
					w = Integer.parseInt( e.substring( i, (i + l) ) );
					i += l;
					h = Integer.parseInt( e.substring( i ) );
					return new Rect( x, y, (x + w), (y + h) );
				} catch( NumberFormatException ex ) {
					return null;
				}
			}
			return null;
		}
	}

	/**
	 * Runs a label on a timer
	 */
	private class IntervalTask extends TimerTask {
		private Glue glue;
		private String label;

		/**
		 * Creates a new {@code TimerTask}.
		 */
		protected IntervalTask( Glue g, String label ) {
			super();
			this.glue = g;
			this.label = label;
		}

		/**
		 * The task to run should be specified in the implementation of the {@code run()} method.
		 */
		@Override
		public void run() {
			// we're on a different thread, Glue needs to run on the UI thread:
			ExtGlyde.this.activity.runOnUiThread( new Runnable() {
				public void run() {
					activity.runScriptAndSync( label );
				}
			} );
		}
	}
}
