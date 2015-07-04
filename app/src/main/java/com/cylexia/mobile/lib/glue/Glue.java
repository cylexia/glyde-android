/**
 *
 */
package com.cylexia.mobile.lib.glue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

//import com.cylexia.mobile.rivet.io.FileManager;

/**
 * Glue RunTime for Java/Android
 * (c)2012-14 by Cylexia
 *
 * version is in VERSION constant below
 *
 * @author sparx104
 *
 */
public class Glue {
    /*********************************************************************/
    public final static String VERSION = "1.6.150703";
	/*********************************************************************/

	public final static int PLUGIN_DONE = 1;
	public final static int PLUGIN_NOT_MINE = -1;
	public final static int PLUGIN_INLINE_REDIRECT = -2;      // call setRedirectLabel() to set the label
	public final static int PLUGIN_DONE_STOP_OK = -3;
	public final static int PLUGIN_DONE_EXIT_ALL = -254;
	/*********************************************************************/

	private final ArrayList<Plugin> plugins;
	private Map<String, Executable> executables;
    private OutputStream stdout;
    private boolean kill;
    private Context currentContext;
    private File platformPath;
    private Map<String, Integer> markerLineCache;
	private List<String> scriptLines;			// TODO: as lines are parsed the raw tokens should be stored per line for variable replacement later
	private Map<String, String> loaded_vars;
	private String last_error;
	private String extra_error_info;
	private String redirect_label;

    private Glue() {
        this.plugins = new ArrayList<Glue.Plugin>();
        this.currentContext = null;
        this.platformPath = null;
        this.markerLineCache = null;			// TODO: precompile label positions
        attachPlugin( new GlueEval() );
        attachPlugin( new GluePlatform() );
    }

	public String getLastError() {
		return last_error;
	}

	public void attachPlugin( Glue.Plugin p ) {
        Iterator<Glue.Plugin> pli = plugins.iterator();
        while( pli.hasNext() ) {
            if( pli.next() == p ) {
                return;
            }
        }
        plugins.add( p );
        p.glueAttach( this );
    }

	/**
	 * Add an Executable for platform.exec to run
	 * @param name the name of this executable (this is passed to the class so you can use the same class
	 *             for multiple tasks)
	 * @param executable the class
	 */
	public void addExecutable( String name, Executable executable ) {
		if( executables == null ) {
			this.executables = new HashMap<String, Executable>();
		}
		executables.put( name, executable );
	}

	/**
	 * Delete a previously registered executable
	 * @param name the name to remove
	 */
	public void deleteExecutable( String name ) {
		if( executables != null ) {
			executables.remove( name );
		}
	}

	/**
	 * Plugins should set this then return -2 to stop executing and resume from this label.  It will
	 * be cleared once used
	 * @param redirect_label the label to resume from
	 */
	public void setRedirectLabel(String redirect_label) {
		this.redirect_label = redirect_label;
	}

	/**
     * @param stdout the stdout to set
     */
    public void setStdOut( OutputStream stdout ) {
        this.stdout = stdout;
    }

    /**
	 * This should be called in onCreate() on any Activity which uses Glue to allow various
	 * "platform." commands to work
     * @param context the current context
     */
    public void setCurrentContext( Context context ) {
        this.currentContext = context;
    }

	/**
	 * This should be called in onDestroy() on any Activity which uses Glue to ensure its
	 * reference isn't kept around.  If the given context isn't the current context nothing will
	 * be done
	 * @param context the context calling this
	 */
	public void clearCurrentContext( Context context ) {
		if( currentContext == context ) {
			this.currentContext = null;
		}
	}

	/**
	 *
	 * @return the currently assigned context or null
	 */
	public Context getCurrentContext() {
		return currentContext;
	}

	/**
     * @param rootPath the path for the "platform" file IO
     */
    public void setPlatformPath(File rootPath) {
        this.platformPath = rootPath;
    }

    /**
     * @return the path for "platform" file IO
     */
    public File getPlatformPath() {
        return platformPath;
    }

    /**
     * Kill the script (only applies to multi-threaded systems)
     */
    public void kill() {
        this.kill = true;
    }

    public Glue echo( String s ) {
        if( stdout != null ) {
            try {
                stdout.write( s.getBytes() );
            } catch( IOException ex ) {
                // ignore
            }
        }
        return this;
    }

//	public boolean compile( String script ) {
//		Map<String, Integer> markers = new HashMap<String, Integer>();
//		ArrayList<CompiledNode> stream = new ArrayList<Glue.CompiledNode>();
//		String[] lines = script.split( "\n" );
//		int i, l = lines.length;
//		for( i = 0; i < l; i++ ) {
//			
//		}
//		
//	}

    /**
     * Echo some info
     */
    public void echoInfo() {
        echo( "Glue v" ).echo( VERSION ).echo( " [android]\n(c)2013-14 by Cylexia\n\n" );
    }

	public void load( String script, Map<String, String> vars ) {
		this.loaded_vars = vars;
		String[] lines = script.split( "\n" );		// TODO: replace with basic style parser which doesn't need the lines
		String line;
		int i, l = lines.length;
		this.scriptLines = new ArrayList<>( l );
		// TODO: load the markerLineCache while loading the array
		for( i = 0; i < l; i++ ) {
			line = lines[i].trim();
			if( !line.startsWith( "#" ) ) {
				scriptLines.add( line );
			}
		}
	}

    public int run() {
		return run( null );
    }

    public int run( String startlbl ) {
		Map<String, String> vars = loaded_vars;
		this.kill = false;
        String label = "", ts = "";
        Stack<Integer> gss = new Stack<Integer>();
        Stack<String> gsp = new Stack<String>();
        int runline;
        int eb;
        if( (startlbl != null) && !startlbl.isEmpty() ) {
            label = startlbl;//.toLowerCase();
            if( !label.startsWith( ":" ) ) {
                label = (":" + label);
            }
        }
        int i = 0, l = scriptLines.size();
        if( (markerLineCache != null) && (label != null) ) {
            if( markerLineCache.containsKey( label ) ) {
                i = markerLineCache.get( label ).intValue();
            } else {
                _error( ("Missing marker: " + label) );
                return 0;
            }
        }
        for( ; i < l; i++ ) {
            if( this.kill ) {
                return 0;		// script was killed, works in multi-threaded environments
            }
            String line = scriptLines.get( i );
            if( (line.length() > 0) && (label.length() == 0) && (line.charAt( 0 ) != ':') ) {
                runline = 1;
                while( (int)line.charAt( 0 ) == 40 ) {     // (
                    eb = line.indexOf( ')' );
                    //ts = line.substring( 1, eb );
                    //if( _parseVarInt( vars, ts ) == 0 ) {
					if( !Dict.boolValueOf( vars, line.substring( 1, eb ) ) ) {		// if empty or 0[....] then is false
                        runline = 0;
                        break;
                    }
                    line = line.substring( (eb + 1) ).trim();
                }
                if( runline == 1 ) {
                    Map<String, String> w = Glue._parse( line, vars );
                    if( w == null ) {
                        _error( "Parser failed: " + line );
                        return 0;
                    }
                    String c = w.get( "_" );
                    if( c.equals( "value" ) || c.equals( "@" ) || c.equals( "put" ) ) {
						Dict.setInto( vars, w, Dict.valueOf( w, c ) );
					} else if( c.equals( "get@" ) ) {
						Dict.setInto( vars, w, Dict.valueOf( vars, Dict.valueOf( w, c ) ) );

					} else if( c.equals( "setpart" ) ) {
						Dict.setInto( vars, w, Glue._setPart( Dict.valueOf( w, "in" ), Dict.valueOf( w, c ), Dict.valueOf( w, "to" ) ) );
					//} else if( c.equals( "setparts" ) ) {
					//	Dict.setInto( vars, w, Glue._setParts( Dict.valueOf( w, "in" ), Dict.rootValue( w ), w ) );
					} else if( c.equals( "getpart" ) ) {
						Dict.setInto( vars, w, Glue._getPart( Dict.valueOf( w, "from" ), Dict.rootValue( w ) ) );
					} else if( c.equals( "getparts" ) || c.equals( "extractparts" ) ) {
						Dict.setInto( vars, w, Glue._getParts( Dict.valueOf( w, "from" ), Dict.rootValue( w ) ) );

					} else if( c.equals( "echo" ) || c.equals( "print" ) ) {
						echo( Dict.valueOf( w, c ) );
						ts = "&";
						while( Dict.containsKey( w, ts ) ) {
							echo( Dict.valueOf( w, ts ) );
							ts = (ts + "&");
						}
					} else if( c.equals( "stop" ) ) {
						return 1;
					} else if( c.equals( "gosub" ) || c.equals( "goto" ) ) {
						if( c.equals( "gosub" ) ) {
							gsp.push( Dict.valueOf( vars, "_params" ) );
							Dict.set( vars, "_params", Glue.mapToParts( w ) );
							gss.push( i );        // fall through
						}
						label = Dict.valueOf( w, c );
						i = -1;        // TODO: this doesn't use the markerCache - we need to update that
					} else if( c.equals( "return" ) ) {
						if( !gss.isEmpty() ) {
							i = gss.pop();
						} else {
							_error( "RETURN without GOSUB" );
						}
					} else if( c.equals( "while" ) ) {
						if( Dict.boolValueOf( w, c ) ) {
							label = Dict.valueOf( w, "goto" );
							i = -1;
						}
					} else if( c.equals( "until" ) ) {
						if( !Dict.boolValueOf( w, c )  ) {
							label = Dict.valueOf( w, "goto" );
							i = -1;
						}
					} else if( c.equals( "+++" ) ) {
						// NOTE: interactive mode is not available

					} else if( c.equals( "increase" ) || c.equals( "incr" ) ) {
						Dict.setInto( vars, w, numberToString( (Dict.numValueOf( w, c ) + Dict.numValueOf( w, "by" )) ) );
					} else if( c.equals( "decrease" ) || c.equals( "decr" ) ) {
						Dict.setInto( vars, w, numberToString( (Dict.numValueOf( w, c ) - Dict.numValueOf( w, "by" )) ) );
					} else if( c.equals( "divide" ) ) {
						Dict.setInto( vars, w, numberToString( (Dict.numValueOf( w, c ) / Dict.numValueOf( w, "by" )) ) );
					} else if( c.equals( "multiply" ) ) {
						Dict.setInto( vars, w, numberToString( (Dict.numValueOf( w, c ) * Dict.numValueOf( w, "by" )) ) );
					} else if( c.equals( "moddiv" ) ) {
						Dict.setInto( vars, w, numberToString( (Dict.numValueOf( w, c ) % Dict.numValueOf( w, "by" )) ) );
					} else if( c.equals( "join" ) ) {
						StringBuilder k = new StringBuilder( "&" );
						StringBuilder v = new StringBuilder( Dict.valueOf( w, c ) );
						while( Dict.containsKey( w, k.toString() ) ) {
							v.append( Dict.valueOf( w, k.toString() ) );
							k.append( "&" );
						}
						Dict.setInto( vars, w, v.toString() );

					} else if( c.equals( "cutleftof" ) || c.equals( "croprightoffof" ) ) {
						Dict.setInto( vars, w, Dict.valueOf( w, c ).substring( 0, Dict.intValueOf( w, "at", 0 ) ) );
					} else if( c.equals( "cutrightof" ) || c.equals( "cropleftoffof" ) ) {
						Dict.setInto( vars, w, Dict.valueOf( w, c ).substring( Dict.intValueOf( w, "at", 0 ) ) );
					} else if( c.equals( "findindexof" ) ) {
						int ps;
						if( Dict.boolValueOf( w, "ignorecase" ) ) {
							ps = Dict.valueOf( w, "in" ).toLowerCase().indexOf( Dict.valueOf( w, c ).toLowerCase() );
						} else {
							ps = Dict.valueOf( w, "in" ).indexOf( Dict.valueOf( w, c ) );
						}
						Dict.setInto( vars, w, String.valueOf( ps ) );
					} else if( c.equals( "getlengthof" ) ) {
						Dict.setInto( vars, w, String.valueOf( Dict.valueOf( w,c ).length() ) );

					} else if( c.equals(  "" ) ) {
						//
					} else {
                            boolean handled = false;
                            if( line.charAt( 0 ) != ':' ) {
                                for( int pi = 0, pl = plugins.size(); pi < pl; pi++ ) {
                                    int r = plugins.get( pi ).glueCommand( this, w, vars );
                                    if( r == PLUGIN_DONE ) {			// handled
                                        handled = true;
                                        break;
                                    } else if( r == PLUGIN_NOT_MINE ) {    // not this plugin's
										//
									} else if( r == PLUGIN_INLINE_REDIRECT ) {
										// redirect to the label in setRedirectLabel()
										label = this.redirect_label;
										if( label != null ) {
											this.redirect_label = null;
											if( !label.startsWith( ":" ) ) {
												label = (":" + label);
											}
											// reset the script
											gss = new Stack<Integer>();
											gsp = new Stack<String>();
											i = -1;        // TODO: lookup the label
											handled = true;
											break;
										} else {
											_error( "Redirect requested with no label" );
											return 0;
										}
									} else if( r < -128 ) {
										// values below -128 are returned immediately to the caller, this allows event processing
										// typical/reserved values in use are:
										//  -254    stop interpreter loop NOW (eg. system exit)
										return r;
                                    } else {				// r = 0, error
                                        _error( ("Command failed: " + c) );
                                        return 0;
                                    }
                                }
                            }
                            if( !handled ) {
                                _error( ("Invalid command: " + c) );
                                return 0;
                            }
                    }
                }
            } else if( line.equals( label ) ) {
                label = "";
            }
        }
        if( label.equals( "" ) ) {
            // not waiting for a label, just ran out of code
            return 1;
        } else {
            _error( ("Missing marker: " + label) );
            return 0;
        }
    }

	/**
	 * Allow extra error information to be added when something goes wrong
	 * @param extra_error_info the value
	 */
	public void setExtraErrorInfo(String extra_error_info) {
		this.extra_error_info = extra_error_info;
	}

	private static double _toNumber( String s ) {
		try {
			return Double.parseDouble( s );
		} catch( NumberFormatException ex ) {
			return 0;
		}
	}


    private static Map<String, String> _parse( String a, Map<String, String> vars ) {
        HashMap<String, String> r = new HashMap<String, String>();
        // parse a sentence type string (eg key value key "value"...).  Separators are in $b
        if( (a.length() == 0) || (a.charAt( 0 ) == '#') ) {
            // comment or empty
            Dict.set( r, "_", "" );
            Dict.set( r, "", "" );
            return r;
        }
        int i;
        if( (i = a.indexOf( ' ' )) > -1 ) {
            String k, s = "";
            String cc = a.substring( 0, i++ );
            if( a.charAt( i ) == '=' ) {
                Dict.set( r, "into", cc );
                k = "";
                i += 2;
            } else {
                k = cc.toLowerCase();
                Dict.set( r, "_", k );
            }
            int l = a.length(), e;
            //console.log( 'Entering parser loop with: ' + a + ' (len ' + l + ')' );
            for( ; i <= l; i++ ) {
                char c = ((i < l) ? a.charAt( i ) : ' ');
                if( c == '"' ) {
                    if( (e = a.indexOf( '"', ++i )) > -1 ) {
                        Dict.set( r, k, Glue._unescape( a.substring( i, e ) ) );
                        i = e;
                        k = s = "";
                    } else {
                        return null;
                    }
                } else if( (c == ',') || (c == ';') ) {
                    // ignored
                } else if( c == '~' ) {
                    s = "";
                } else if( c == ' ' ) {
                    if( k.length() == 0 ) {
                        k = s.toLowerCase();
                        if( k.equals( "=>" ) ) {
                            k = "into";
                        }
                        if( !r.containsKey( "_" ) ) {
                            Dict.set( r, "_", k );
                        }
                    } else {
                        if( (s.length() == 0) || ("+-0123456789:".indexOf( s.charAt( 0 ) ) > -1) ) {
                            Dict.set( r, k, s );
//						} else if( s.charAt( 0 ) == '[' ) {
//							// decode [a]b into part b from var a
//							int eb = s.indexOf( ']' );
//							if( eb > -1 ) {
//								String pd = Dict.valueOf( vars, s.substring( 1, eb ), "" );
//								Dict.set( r, k, _getPart( pd, s.substring( (eb + 1) ) ) );
//							} else {
//								return null;
//							}
                        } else {
                            Dict.set( r, k, Dict.valueOf( vars, s, "" ) );
                        }
                        k = "";
                    }
                    s = "";
                } else {
                    s += c;
                }
            }
            if( (k.length() != 0) && !r.containsKey( k ) ) {
                Dict.set( r, k, "" );
            }
            return r;
        } else {
            r = Dict.create();
            Dict.set( r, "_", a.toLowerCase() );
            Dict.set( r, a, "" );
            return r;
        }
    }

    private static int _parseVarInt( Map<String, String> m, String k ) {
        if( m.containsKey( k ) ) {
            try {
                return Integer.parseInt( m.get( k ) );
            } catch( NumberFormatException ex ) {
                // nothing
            }
        }
        return 0;
    }

    private static String numberToString( double n ) {
        String s = String.valueOf( n );
        if( s.endsWith( ".0" ) ) {
            return s.substring( 0, (s.length() - 2) );
        }
        return s;
    }

    private static String _unescape( String s ) {
        int i = s.indexOf( '\\' );
        if( i == -1 ) {
            return s;
        }
        StringBuilder r = new StringBuilder( s.length() );
        r.append( s.substring( 0, i ) );
        int l = s.length();
        for( ; i < l; i++ ) {
            char c = s.charAt( i );
            if( c == '\\' ) {
                switch( s.charAt( ++i ) ) {
                    case 'n': r.append( '\n' ); break;
                    case 'r': r.append( '\r' ); break;
                    case 't': r.append( '\t' ); break;
                    case 'q': r.append( '"' ); break;
                    case 's': r.append( '\\' ); break;
                    case 'a': r.append( '\'' ); break;
                    case '\\': r.append( '\\' ); break;
                }
            } else {
                r.append( c );
            }
        }
        return r.toString();
    }

    private void _error( String m ) {
		if( extra_error_info != null ) {
			m += ("; " + extra_error_info);
			this.extra_error_info = null;
		}
		this.last_error = m;
		Log.e( "GlueError", ("\n[Glue] " + m) );
		echo( ("\n[Glue] " + m) );
    }

    /**
     * Set a named part in a dictionary
     * @param d the dictionary
     * @param sk the key to set
     * @param sv the value to set it to
     * @return the updated dictionary
     */
    private static String _setPart( String d, String sk, String sv ) {
        return Glue._part( d, sk, sv, 1 );
    }

//    /**
//     * Set several parts in a dictionary at once.  The specified list of keys can be capitalised but will be converted to
//     * lower case for reading from the source map
//     * @param d the dictionary
//     * @param klist the list of keys to set separated with "/"'s
//     * @param src the map containing the data to read from
//     * @return the updated dictionary
//     */
//    private static String _setParts( String d, String klist, Map<String, String> src ) {
//        String k;
//        int s = 0, e;
//        while( (e = klist.indexOf( ',', s )) > -1 ) {
//            k = klist.substring( s, e );
//            d = _setPart( d, k, Dict.valueOf( src, k.toLowerCase(), "" ) );
//            s = (e + 1);
//        }
//        k = klist.substring( s );
//        return _setPart( d, k, Dict.valueOf( src, k.toLowerCase(), "" ) );
//    }

    /**
     * Get a named part from a dictionary
     * @param d the dictionary
     * @param gk the key of the part to get
     * @return the part value or ""
     */
    private static String _getPart( String d, String gk ) {
        return Glue._part( d, gk, null, 0 );
    }

    /**
     * Perform part operations
     * @param d the dictionary to use
     * @param sk the key to read/write
     * @param sv the value to write if upd is <code>true</code>
     * @param upd if <code>true</code> the key will be updated with sv and the updated dictionary returned,
     * 		if <code>false</code> the value with this key will be returned
     * @return the updated dictionary or the value depending on upd
     */
    private static String _part( String d, String sk, String sv, int upd ) {
        int ofs = 0, m = 0, rstart, dlen = d.length();
        String k = "", s = k;
        StringBuilder result = new StringBuilder();
        while( ofs < dlen ) {
            int rl = 0;
            int o = (int)d.charAt( ofs );
            rstart = ofs;
            ofs++;
            while( o >= 97 ) {
                rl = ((rl + (o - 97)) << 4);
                o = (int)d.charAt( ofs );
                ofs++;
            }
            rl += (o - 65);
            o = ofs;
            ofs += rl;
            if( m == 0 ) {
                k = d.substring( o, (o + rl) );
                if( k.equals( sk ) ) {
                    m = 2;   // set: don't write the key and value back, we will add at the end. get: key matches
                } else if( upd == 1 ) {
                    result.append( d.substring( rstart, (rl + o) ) );
                    m = 1;   // set: write the key and value, get: X
                } else {
                    m = 3;	 // read but discard value
                }
            } else {
                if( m == 1 ) {
                    result.append( d.substring( rstart, (rl + o) ) );	// set: update, get: X
                } else if( (m == 2) && (upd == 0) ) {
                    return d.substring( o, (o + rl) );						// set: X, get: key matches, return value
                }
                m = 0;
            }
        }
        if( upd == 1 ) {
            s = sk;			// set: write key and value, get: X
            StringBuilder z = new StringBuilder();
            z.append( result );
            for( int i = 0; i < 2; i++ ) {
                int l = s.length();
                z.append( (char)(65 + (l & 15)) );
                while( l > 15 ) {
                    l = (l >> 4);
                    z.insert( 0, (char)(97 + (l & 15)) );
                }
                z.append( s );
                s = sv;
            }
            result = z;
        }
        return result.toString();
    }

    /**
     * Return as subset of the parts in a dictionary.  Can be used to reorder a dictionary as the result will have the keys
     * in the order they are specified here - setting will always move the most recently set to the end of the dictionary
     * @param d the dictionary to read from
     * @param klist the keys you want, the output will have this order
     * @return the new dictionary
     */
    private static String _getParts( String d, String klist ) {
        String k, nm = "";
        int s = 0, e;
        while( (e = klist.indexOf( ',', s )) > -1 ) {
            k = klist.substring( s, e );
            nm = Glue._setPart( nm, k, _getPart( d, k ) );
            s = (e + 1);
        }
        k = klist.substring( s );
        return Glue._setPart( nm, k, _getPart( d, k ) );
    }

    /**
     * Convert a dictionary of parts into a {@link LinkedHashMap} (so the key order is preserved) for use elsewhere
     * @param d the dictionary to convert
     * @return the {@link Map}
     */
    public static Map<String, String> partsToMap( String d ) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		if( d == null ) {
			return map;
		}
        int ofs = 0, dlen = d.length();
        String k = null;
        while( ofs < dlen ) {
            int rl = 0;
            int o = (int)d.charAt( ofs );
            ofs++;
            while( o >= 97 ) {
                rl = ((rl + (o - 97)) << 4);
                o = (int)d.charAt( ofs );
                ofs++;
            }
            rl += (o - 65);
            o = ofs;
            ofs += rl;
            if( k == null ) {
                k = d.substring( o, (o + rl) );
            } else {
                map.put( k, d.substring( o, (o + rl) ) );
                k = null;
            }
        }
        return map;
    }

    /**
     * Convert a {@link Map} into a parts dictionary
     * @param map the map to convert
     * @return the dictionary string
     */
    public static String mapToParts( Map<String, ?> map ) {
        String k = "", s = k;
        StringBuilder result = new StringBuilder();
        Iterator<String> ikeys = map.keySet().iterator();
        while( ikeys.hasNext() ) {
            s = ikeys.next();
            for( int i = 0; i < 2; i++ ) {
                int l = s.length();
                StringBuilder z = new StringBuilder();
                z.append( (char)(65 + (l & 15)) );
                while( l > 15 ) {
                    l = (l >> 4);
                    z.insert( 0, (char)(97 + (l & 15)) );
                }
                result.append( z );
                result.append( s );
                if( i == 0 ) {
                    s = map.get( s ).toString();
                }
            }
        }
        return result.toString();
    }

    /**
     * Convert an array to a Parts dict.  Uses {@link Object#toString()} to get the values and adds a "count" key containing
     * the number of items
     * @param a the array
     * @return the dict
     */
    public static String arrayToParts( Object[] a ) {
        String k = "", s = k;
        StringBuilder result = new StringBuilder();
        int index;
        for( index = 0; index <= a.length; index++ ) {
            if( index < a.length ) {
                s = String.valueOf( index );
            } else {
                s = "count";
            }
            for( int i = 0; i < 2; i++ ) {
                int l = s.length();
                StringBuilder z = new StringBuilder();
                z.append( (char)(65 + (l & 15)) );
                while( l > 15 ) {
                    l = (l >> 4);
                    z.insert( 0, (char)(97 + (l & 15)) );
                }
                result.append( z );
                result.append( s );
                if( i == 0 ) {
                    if( index < a.length ) {
                        s = a[index].toString();
                    } else {
                        s = String.valueOf( a.length );
                    }
                }
            }
        }
        return result.toString();
    }

    public static Glue init() {
        return new Glue();
    }

	public static Glue init( Context c ) {
		Glue g = Glue.init();
		g.setCurrentContext( c );
		return g;
	}

    public static Glue init( OutputStream stdout ) {
        Glue g = Glue.init();
        g.setStdOut( stdout );
        return g;
    }

    /**
     *
     * @author sparx104
     *
     */
    private static class GlueEval implements Plugin {
        public GlueEval() {
            super();
        }

        /* (non-Javadoc)
         * @see com.cylexia.mobile.glue.Glue.Plugin#glueAttach(com.cylexia.mobile.glue.Glue)
         */
        @Override
        public void glueAttach(Glue g) {
            // nothing
        }

        public int glueCommand( Glue glue, Map<String, String> w, Map<String, String> vars ) {
            // no binary operators are supported in eval - provide a "bitwise" command with a plugin if needed
            String c = Dict.valueOf( w, "_" );
            String r;
            int v;
            if( c.equals( "testif" ) || c.equals( "testifnot" ) ) {
                v = Dict.intValueOf( w, c );
                String vs = Dict.valueOf( w, c );
                boolean rs = false;
                // string and int
                if( w.containsKey( "==" ) ) rs = vs.equals( Dict.valueOf( w, "==" ) );
                else if( w.containsKey( "=" ) ) rs = vs.equals( Dict.valueOf( w, "=" ) );
				else if( w.containsKey( "is" ) ) rs = vs.equals( Dict.valueOf( w, "is" ) );
				else if( w.containsKey( "!=" ) ) rs = !vs.equals( Dict.valueOf( w, "!=" ) );
				else if( w.containsKey( "isnot" ) ) rs = !vs.equals( Dict.valueOf( w, "isnot" ) );
				else if( w.containsKey( "<>" ) ) rs = !vs.equals( Dict.valueOf( w, "<>" ) );

                // int only
				else if( w.containsKey( "<" ) ) rs = (v < Dict.intValueOf( w, "<" ));			// int only
				else if( w.containsKey( ">" ) ) rs = (v > Dict.intValueOf( w, ">" ));
				else if( w.containsKey( "<=" ) ) rs = (v <= Dict.intValueOf( w, "<=" ));
				else if( w.containsKey( ">=" ) ) rs = (v >= Dict.intValueOf( w, ">=" ));
				else if( w.containsKey( "lt" ) ) rs = (v < Dict.intValueOf( w, "lt" ));			// int only
				else if( w.containsKey( "gt" ) ) rs = (v > Dict.intValueOf( w, "gt" ));
				else if( w.containsKey( "lte" ) ) rs = (v <= Dict.intValueOf( w, "lte" ));
				else if( w.containsKey( "gte" ) ) rs = (v >= Dict.intValueOf( w, "gte" ));
				else if( w.containsKey( "and" ) ) {
					rs = (Dict.boolValueOf( w, c ) && Dict.boolValueOf( w, "and" ));
                    //rs = (((v == 0) ? false : true) && ((Dict.intValueOf( w, "and" ) == 0) ? false : true));
                }
				else if( w.containsKey( "or" ) ) {
					rs = (Dict.boolValueOf( w, c ) || Dict.boolValueOf( w, "or" ));
                    //rs = (((v == 0) ? false : true) || ((Dict.intValueOf( w, "and" ) == 0) ? false : true));
                }
				if( c.equals( "testif" ) ) {
                    Dict.setInto( vars, w, (rs ? "1" : "0") );
                } else {
                    Dict.setInto( vars, w, (rs ? "0" : "1") );
                }
                return 1;
            } else if( c.equals( "not" ) ) {
                // if value is anything other than 0 return 1 else return 0
                // default boolean is 0 is false, anything else is true
                Dict.setInto( vars, w, ((Dict.boolValueOf( w, "not" ) == false) ? "1" : "0") );
                return 1;
            }
            return -1;		// not ours
        }
    }

    /**
     * Platform module
     * @author sparx104
     *
     */
    private class GluePlatform implements Glue.Plugin {
        private final static String PREFIX = "platform.";

        public GluePlatform() {
            super();
        }

        /* (non-Javadoc)
         * @see com.cylexia.mobile.glue.Glue.Plugin#glueAttach(com.cylexia.mobile.glue.Glue)
         */
        @Override
        public void glueAttach(Glue g) {
            // nothing
        }

        /* (non-Javadoc)
         * @see com.cylexia.mobile.glue.Glue.Plugin#glueCommand(java.util.Map, java.util.Map)
         */
        @Override
        public int glueCommand( Glue glue, Map<String, String> w, Map<String, String> vars ) {
            String c = w.get( "_" );
            if( c == null ) {
                return -1;		// not happening
            }
            c = c.toLowerCase();
            if( c.startsWith( PREFIX ) ) {
				String wc = Dict.valueOf( w, Dict.valueOf( w, "_"  ) );
                switch( c.hashCode() ) {
                    case -1915350602:		// platform.getid
                        Dict.setInto( vars, w, "android" );
                        break;

                    case -1907156805:		// platform.pause
                        echo( "[PLATFORM] pause is unsupported" );
                        break;

                    case -99347837:			// platform.listfilesin
                        Dict.setInto( vars, w, _listFiles( Dict.rootValue( w ) ) );
                        break;

                    case -1825185449:		// platform.readfromfile
                    case 492561793:			// platform.load
                        return readFromFile( w, vars );

                    case 165285403:			// platform.writetofile
                    case 492757528:			// platform.save
                        return writeToFile( w, vars );

                    case -1584674781:		// platform.currentpath
                        Dict.setInto( vars, w, platformPath.getAbsolutePath() );
                        break;

                    case 1217199890:		// platform.setcurrentpathto
                        echo( "[PLATFORM] setCurrentPathTo is unsupported" );
                        break;

					case -797274927:			// platform.getrandomnumberfrom
						Dict.setInto( vars, w, getRandomNumber( Dict.intValueOf( w, c ), Dict.intValueOf( w, "upto" ) ) );
						break;

                    case 878174765:			// platform.loadconfigfrom
                    case -1218477053:		// platform.loadconfig
                        return _loadConfig( Dict.rootValue( w ), w, vars );

                    case -21695619:			// platform.dateserial
                    case -2072040259:		// platform.getdateserial
                    case 1647623478:		// platform.putdateserial
                        Dict.setInto( vars, w, createDateSerial() );
                        break;

                    case 1097219878:		// platform.setenv
                    case 1362322888:		// platform.setenvironmentvariable
                        EnvVars.getInstance().put( Dict.rootValue( w ), Dict.valueOf( w, "to" ) );
                        break;

                    case 753670066:			// platform.getenv
                    case 1026108761:		// platform.putenv
                    case 49194580:			// platform.getenvironmentvariable
                    case -2026795525:		// platform.putenvironmentvariable
                        Dict.setInto( vars, w, EnvVars.getInstance().get( Dict.rootValue( w ) ) );
                        break;

					case 797274927:			// platform.getrandomnumberfrom upto
						Dict.setInto( vars, w, String.valueOf( (Dict.intValueOf( w, Dict.valueOf( w, c ) ) + ((int)Math.random() * Dict.intValueOf( w, "upto" ))) ) );
						break;

					case 1124570528:		// platform.browseto
						try {
							Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( wc ) );
							// check the intent resolves to an activity then start it
							if( (currentContext != null) && (intent.resolveActivity( currentContext.getPackageManager() ) != null) ) {
								currentContext.startActivity( intent );
							}
						} catch( Exception ex ) {
							// nothing
						}
						return Glue.PLUGIN_DONE;		// it works or it doesn't, we will silently fail if needed

					case 492362028:			// platform.exec
						if( Glue.this.executables != null ) {
							if( Glue.this.executables.containsKey( wc ) ) {
								Executable e = Glue.this.executables.get( wc );
								int rescode = e.exec(
										glue, currentContext, wc,
										Dict.valueOf( w, "args", Dict.valueOf( w, "withargs" ) ),
										Dict.valueOf( w, "ondonegoto" )
									);
								if( rescode < 0 ) {
									return rescode;
								}
								Glue.this.setRedirectLabel( Dict.valueOf( w, "ondonegoto" ) );
							} else {
								Glue.this.setRedirectLabel( Dict.valueOf( w, "onerrorgoto" ) );
							}
						} else {
							Glue.this.setRedirectLabel( Dict.valueOf( w, "onerrorgoto" ) );
						}
						return -2;

                    default:
                        return 0;			// no such command
                }
                return 1;		// we did it
            }
            return -1;
        }

		/**
		 * Get a random number
		 * @param from minimum value
		 * @param upto the upper bound is this -1
		 * @return the number as a string
		 */
		private String getRandomNumber( int from, int upto ) {
			int n = (int)(from + (Math.random() * (upto - from)));
			return String.valueOf( n );
		}

        private String _listFiles( String p ) {
            File path = FileManager.getInstance( null ).getRoot();
            if( (p != null) && (p.length() > 0) ) {
                path = new File( path, p );
            }
            File[] list = path.listFiles();
            if( list != null ) {
                return arrayToParts( list );
            } else {
                return "";
            }
        }

        /**
         * Load an .ini style configuration file
         * @param c The filename
         * @param w the command arguments
         * @param vars the variables map to load into
         * @return "1" on success, "0" on error (and error is logged to console)
         */
        private int _loadConfig( String c, Map<String, String> w, Map<String, String> vars ) {
			Reader f = null;
			if( currentContext != null ) {
				try {
					f = new InputStreamReader( FileManager.getInstance( currentContext ).getInputStream( c ) );
				} catch( IOException ex ) {
					_error( "[GLUE.PLATFORM] Unable to read config from " + c );
					Log.e( "Glue", ("platform.loadConfigFrom: " + ex.toString()) );
					return 0;
				}
				try {
					BufferedReader r = new BufferedReader( f );
					String line, key = null;
					int b;
					String value = "", prefix = "";
					while( (line = r.readLine()) != null ) {
						line = line.trim();
						if( line.startsWith( "[" ) && line.endsWith( "]" ) ) {
							prefix = (line.substring( 1, (line.length() - 1) ) + ".");
						} else if( !line.startsWith( "#" ) ) {
							b = line.indexOf( '=' );
							if( b > -1 ) {
								value = Glue._setPart(
										value,
										(prefix + line.substring( 0, b ).trim()),
										line.substring( (b + 1) ).trim()
									);
							}
						}
					}
					Dict.setInto( vars, w, value );
					return 1;
				} catch( IOException ex ) {
					_error( "[GLUE.PLATFORM] Unable to read from " + c );
					return 0;
				}
			}
			return 0;
        }

        /**
         * Create a date serial string
         * @return date serial as yyyymmddhhiiss
         */
        private String createDateSerial() {
            Calendar c = Calendar.getInstance();
            StringBuilder b = new StringBuilder();
            b.append( c.get( Calendar.YEAR ) );
            int v;
            v = (c.get( Calendar.MONTH ) + 1);
            if( v < 10 ) {
                b.append( "0" );
            }
            b.append( v );
            v = c.get( Calendar.DAY_OF_MONTH );
            if( v < 10 ) {
                b.append( "0" );
            }
            b.append( v );
            v = c.get( Calendar.HOUR_OF_DAY );
            if( v < 10 ) {
                b.append( "0" );
            }
            b.append( v );
            v = c.get( Calendar.MINUTE );
            if( v < 10 ) {
                b.append( "0" );
            }
            b.append( v );
            v = c.get( Calendar.SECOND );
            if( v < 10 ) {
                b.append( "0" );
            }
            b.append( v );
            return b.toString();
        }

        /**
         * Read from a file
         * @param w
         * @param vars
         * @return
         */
        private int readFromFile( Map<String, String> w, Map<String, String> vars ) {
            String c = Dict.rootValue( w );
			int result = 0;
            if( currentContext != null) {
				String content;
				try {
					content = FileManager.getInstance( currentContext ).readFromFile( c );
					result = 1;
				} catch( IOException ex ) {
					_error( "[GLUE.PLATFORM] Unable to read from " + Dict.rootValue( w ) );
					Log.e( "Glue", ("platform.readFromFile: " + ex.toString()) );
					content = "";
				}
                Dict.set( vars, Dict.into( w ), content );
            }
			return result;
        }

        private int writeToFile( Map<String, String> w, Map<String, String> vars ) {
			if( currentContext != null ) {
				try {
					FileManager.getInstance( currentContext ).writeToFile( Dict.rootValue( w ), Dict.valueOf( w, "value" ) );
					Dict.setInto( vars, w, "1" );
					return 1;
				} catch( IOException ex ) {
					_error( "[GLUE.PLATFORM] Unable to write to " + Dict.rootValue( w ) );
					Log.e( "Glue", ("platform.writeToFile: " + ex.toString()) );
				}
			}
			return 0;
		}
    }

    /**
     * Plugin interface.  Implement this to provide plugin support
     *
     * @author sparx104
     *
     */
    public interface Plugin {
        /**
         * Called when this is attached to a {@link Glue} instance
         * @param g the instance being attached to
         */
        public void glueAttach( Glue g );

        /**
         * Called to execute a glue command
         * @param w the command line.  The command is in "_"
         * @param vars the current Glue variables map
         * @return 1 if the command was successful, 0 if it failed or -1 if it didn't belong to this plugin
         */
        public int glueCommand( Glue glue, Map<String, String> w, Map<String, String> vars );
    }


	/**
	 * These are plugged into Glue.addExecutable() to allow "platform.exec" to call them
	 */
	public interface Executable {
		/**
		 * Called by platform.exec to simulate execution
         * @param glue The Glue instance for access to setRedirectLabel and run
		 * @param context The current context Glue is aware of.  Maybe null and may not be the visible context
		 * @param name the name this was executed under
		 * @param args arguments as a string
		 * @param label the label to go to once execution is complete if returning a value < 0
		 * @return ignored if 0 or above, if <0 the plugin will return the value for event handling by the (expected!) runloop
		 */
		public int exec( Glue glue, Context context, String name, String args, String label );
	}

//	private static class CompiledNode {
//		public static final int STRING = 0;
//		public static final int VAR = 1;
//		public static final int STOP = -1;
//
//		public int type;			// accessors not used to save bloat and time
//		public String key;
//		public String value;
//		
//		public CompiledNode( int type, String key, String value ) {
//			super();
//			this.type = type;
//			this.key = key;
//			this.value = value;
//		}
//	}

    /**
     *
     * @author sparx104
     *
     */
    private static class Dict {
        private final static String INTO = "into";
        private final static String ROOT = "_";

        static HashMap<String, String> create() {
            return new HashMap<String, String>();
        }

        /**
         * Get the value of "_"
         * @param dict
         * @return
         */
        public static String root( Map<String, String> dict ) {
            return Dict.valueOf( dict, ROOT );
        }

        /**
         * Get the value of the key specified in "_" (eg. w[w['_']])
         * @param dict
         * @return
         */
        public static String rootValue( Map<String, String> dict ) {
            return Dict.valueOf( dict, Dict.root( dict ) );
        }

        public static String into( Map<String, String> dict ) {
            return Dict.valueOf( dict, INTO );
        }

        public static String valueOf( Map<String, String> dict, String key ) {
            return Dict.valueOf( dict, key, "" );
        }

        public static String valueOf( Map<String, String> dict, String key, String def ) {
            if( dict.containsKey( key ) ) {
                return dict.get( key );
            }
            return def;
        }

        public static int intValueOf( Map<String, String> dict, String key ) {
            return intValueOf(dict, key, 0);
        }

        public static int intValueOf( Map<String, String> dict, String key, int def ) {
            if( dict.containsKey( key ) ) {
                try {
                    return Integer.parseInt( dict.get( key ) );
                } catch( NumberFormatException ex ) {
                    return 0;
                }
            }
            return def;
        }

        public static double numValueOf( Map<String, String> dict, String key ) {
            return numValueOf(dict, key, 0);
        }

        public static double numValueOf( Map<String, String> dict, String key, double def ) {
            if( dict.containsKey( key ) ) {
                try {
                    return Double.parseDouble(dict.get(key));
                } catch( NumberFormatException ex ) {
                    return 0;
                }
            }
            return def;
        }

		public static boolean boolValueOf( Map<String, String> dict, String key ) {
			return Dict.boolValueOf( dict, key, false );
		}

		public static boolean boolValueOf( Map<String, String> dict, String key, boolean def ) {
			if( dict.containsKey( key ) ) {
				String ts = dict.get( key );
				// if the value is empty or 0[xx] then it's false, anything else is true
				if( ts.isEmpty() || ((ts.length() > 0) && (ts.charAt( 0 ) == '0')) ) {
					return false;
				} else {
					return true;
				}
			} else {
				return def;
			}
		}

        public static boolean containsKey( Map<String, String> dict, String key ) {
            return dict.containsKey( key );
        }

        public static void set( Map<String, String> dict, String key, String val ) {
            dict.put( key, val );
        }

        public static void setInto( Map<String, String> dict, Map<String, String> w, String val ) {
            dict.put( Dict.valueOf( w, "into" ), val );
        }

        public static void set( Map<String, String> dict, String key, int val ) {
            Dict.set(dict, key, String.valueOf(val));
        }

        public static void setInto( Map<String, String> dict, Map<String, String> w, int val ) {
            Dict.setInto(dict, w, String.valueOf(val));
        }
		/*		-- commented out just because it's unused - it works fine
		public static void copyInto( Map<String, String> dict, Map<String, String> src, String prefix ) {
			Iterator<String> itr = src.keySet().iterator();
			while( itr.hasNext() ) {
				String key = itr.next();
				if( prefix == null ) {
					dict.put( key, src.get( key ) );
				} else {
					dict.put( (prefix + key), src.get( key ) );
				}
			}
		}
		*/
    }

    /**
     * Singleton class providing the environment variables.  The values in here are maintained for all instances of Glue/GluePlatform
     *
     * @author sparx104
     *
     */
    public static class EnvVars extends HashMap<String, String> {
        /**
         *
         */
        private static final long serialVersionUID = -1914478220334873744L;

        private static EnvVars instance;

        private EnvVars() {
            super();
        }

        public String get( Object k, String d ) {
            if( containsKey( k ) ) {
                return get( k );
            } else {
                return d;
            }
        }

        /**
         * @return the instance
         */
        public static EnvVars getInstance() {
            if( instance == null ) {
                EnvVars.instance = new EnvVars();
            }
            return instance;
        }
    }
}

