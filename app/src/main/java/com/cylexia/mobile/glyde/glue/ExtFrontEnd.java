package com.cylexia.mobile.glyde.glue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cylexia.mobile.glyde.VecText;

/**
 * Created by sparx104 on 27/04/2015.
 */
public class ExtFrontEnd implements Glue.Plugin {
	public static final int GLUE_STOP_ACTION = -200;

	private final FileManager filemanager;

	private Bitmap bitstore;
	private Canvas plane;
	private Map<String, ImageMap> resources;
	private Map<String, Map<String, String>> styles;
	private Map<String, Button> buttons;
	private List<String> button_sequence;
	private String action, action_params;
	private String resume_label;

	private String last_action_id;

	private String window_title;
	private int window_width;
	private int window_height;

	private Paint background;

	public ExtFrontEnd( FileManager fm ) {
		super();
		this.filemanager = fm;
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
		String cmd = w.get( "_" );
		if( cmd.startsWith( "f." ) ) {
			String wc = w.get( w.get( "_" ) );
			cmd = cmd.substring( 2 );
			if( cmd.equals( "setwidth" ) || cmd.equals( "setviewwidth" ) ) {
				return setupView( w );
			} else if( cmd.equals( "settitle" ) ) {
				this.window_title = wc;
				return 1;

			} else if( cmd.equals( "doaction" ) ) {
				return doAction( wc, w );

			} else if( cmd.equals( "clear" ) ) {
				clearUI();

			} else if( cmd.equals( "loadresource" ) ) {
				return loadResource( glue, wc, valueOf( w, "as" ) );

			} else if( cmd.equals( "removeresource" ) ) {
				if( resources != null ) {
					resources.remove( wc );
				}

			} else if( cmd.equals( "setstyle" ) ) {
				setStyle( wc, w );

			} else if( cmd.equals( "getlastactionid" ) ) {
				vars.put( valueOf( w, "into" ), last_action_id );

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

				// TODO: rects and boxes (eg. filled rect)
			} else {
				return -1;
			}
			return 1;
		}
		return 0;
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

	/**
	 * Add a definition to the (lazily created) styles map.  Note, the complete string is stored
	 * so beware that keys like "_" and "f.setstyle" are added too
	 * @param name the name of the style
	 * @param data the complete arguments string
	 */
	private void setStyle( String name, Map<String, String> data ) {
		if( styles == null ) {
			this.styles = new HashMap<String, Map<String, String>>();
		}
		styles.put( name, data );
	}

	private int setupView( Map<String, String> w ) {
		if( w.containsKey( "backgroundcolour" ) ) {
			this.background = new Paint();
			background.setColor( Color.parseColor( w.get( "backgroundcolour" ) ) );
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
		setSize( window_width, window_height );
	}

	private int doAction( String action, Map<String, String> w ) {
		this.action = action;
		this.action_params = valueOf( w, "args", valueOf( w, "withargs" ) );
		String done_label = valueOf( w, "ondonegoto" );
		StringBuilder b = new StringBuilder();
		b.append( done_label ).append( "\t" );
		b.append( valueOf( w, "onerrorgoto", done_label ) ).append( "\t" );
		b.append( valueOf( w, "onunsupportedgoto", done_label ) );
		this.resume_label = b.toString();
		return ExtFrontEnd.GLUE_STOP_ACTION;		// expects labels to be DONE|ERROR|UNSUPPORTED
	}

	// TODO: this should use a rect and alignment options along with colour support
	private int writeAs( String id, Map<String, String> args ) {
		updateFromStyle( args );
		String text = valueOf( args, "value" );
		int x = intValueOf( args, "x", intValueOf( args, "atx" ) );
		int y = intValueOf( args, "y", intValueOf( args, "aty" ) );
		int size = intValueOf( args, "size", 2 );
		int thickness = intValueOf( args, "thickness", 1 );
		VecText v = VecText.getInstance();
		int rw = intValueOf( args, "width", 0 );
		int rh = intValueOf( args, "height", 0 );
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
		v.drawString( plane, text, Color.BLACK, tx, ty, size, thickness, (thickness + 1) );
		return buttonise( id, x, y, rw, rh, args );
	}

	private int drawAs( String id, Map<String, String> args ) {
		updateFromStyle( args );
		int x = intValueOf( args, "x", intValueOf( args, "atx" ) );
		int y = intValueOf( args, "y", intValueOf( args, "aty" ) );
		String rid = valueOf( args, "id", valueOf( args, "resource" ) );
		if( resources != null ) {
			int b = rid.indexOf( '.' );
			if( b > -1 ) {
				String resid = rid.substring( 0, b );
				String imgid = rid.substring( (b + 1) );
				if( resources.containsKey( resid ) ) {
					ImageMap m = resources.get( resid );
					if( m.drawToCanvas( imgid, plane, x, y ) ) {
						Rect r = m.getRectWithId( imgid );
						return buttonise( id, x, y, r.width(), r.height(), args );
					}
				}
			}
		}
		return 0;
	}

	private int markAs( String id, Map<String, String> args ) {
		updateFromStyle( args );
		return buttonise(
				id,
				intValueOf( args, "x", intValueOf( args, "atx" ) ),
				intValueOf( args, "y", intValueOf( args, "aty" ) ),
				intValueOf( args, "width" ),
				intValueOf( args, "height" ),
				args
			);
	}

	private int paintRectAs( String id, Map<String, String> args, boolean filled ) {
		updateFromStyle( args );
		int x = intValueOf( args, "x", intValueOf( args, "atx" ) );
		int y = intValueOf( args, "y", intValueOf( args, "aty" ) );
		int w = intValueOf( args, "width" );
		int h = intValueOf( args, "height" );
		Paint p = new Paint();
		p.setColor( Color.parseColor( valueOf( args, "colour" ) ) );
		p.setStrokeWidth( 1 );
		if( filled ) {
			p.setStyle( Paint.Style.FILL_AND_STROKE );
		} else {
			p.setStyle( Paint.Style.STROKE );
		}
		plane.drawRect( x, y, (x + w), (y + h), p );
		return buttonise( id, x, y, w, h, args );
	}

	private int buttonise( String id, int x, int y, int w, int h, Map<String, String> args ) {
		if( args.containsKey( "border" ) ) {
			Paint p = new Paint();
			p.setStrokeWidth( 1 );
			// TODO: illegal colours (#xxx) throw an exception.  this needs to be sorted for all parseColour instances
			p.setColor( Color.parseColor( valueOf( args, "border" ) ) );
			p.setStyle( Paint.Style.STROKE );
			plane.drawRect( x, y, (x + w), (y + h), p );
		}
		if( args.containsKey( "onclickgoto" ) ) {
			return addButton( id, x, y, w, h, valueOf( args, "onclickgoto" ) );
		} else {
			return 1;
		}
	}

	private int addButton( String id, int x, int y, int w, int h, String label ) {
		if( buttons == null ) {
			this.buttons = new HashMap<>();
			this.button_sequence = new ArrayList<String>();
		}
		if( !buttons.containsKey( id ) ) {
			buttons.put( id, new Button( x, y, w, h, label ) );
			button_sequence.add( id );
			return 1;
		}
		return 0;
	}

	private void updateFromStyle( Map<String, String> a ) {
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
}
