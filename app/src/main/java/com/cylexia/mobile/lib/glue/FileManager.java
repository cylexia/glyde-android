package com.cylexia.mobile.lib.glue;
/**
 *
 */

    import java.io.BufferedInputStream;
	import java.io.BufferedWriter;
    import java.io.ByteArrayOutputStream;
    import java.io.File;
    import java.io.FileInputStream;
    import java.io.FileNotFoundException;
    import java.io.FileWriter;
    import java.io.FilenameFilter;
    import java.io.IOException;
    import java.io.InputStream;
	import java.util.HashMap;
	import java.util.Map;
	import java.util.zip.ZipEntry;
	import java.util.zip.ZipFile;

	import android.content.Context;
    import android.content.res.AssetManager;
	import android.graphics.Bitmap;
	import android.graphics.BitmapFactory;
	import android.os.Environment;

/**
 * Provides file access to Glue and Platform.  Can be used externally as well
 * @author sparx104
 *
 */
public class FileManager implements FilenameFilter {
    private static FileManager instance;
	private File root;
	private String root_name;
	private String filter_ext;

    private Context context;

    /**
     *
     */
    private FileManager() {
        super();
    }

	/**
	 * Set the root path, default is the root folder.  You should use this!
	 * @param p the path to be used as the root for files
	 */
	public void setRootPath( String p ) {
		this.root_name = p;
		//Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOCUMENTS)
		this.root = new File( Environment.getExternalStorageDirectory(), p );
		root.mkdirs();		// no effect if it exists so is safe to just call whatever
	}



	/**
     * Read the contents of a file from "RivetData" if it exists.  If it doesn't exist but a file in the assets folder does then the
     * assets file will be read.  If there is no file or asset an {@link IOException} is thrown
     * @param name
     * @return
     * @throws IOException
     */
    public String readFromFile( String name ) throws IOException, FileNotFoundException {
        InputStream src = getInputStream( name );
		return FileManager.readInputStream( src );
    }

    /**
     * Write content to a data file in the specified root
     * @param name the name of the file
     * @param content the value to write
     * @throws IOException
     */
    public void writeToFile( String name, String content ) throws IOException {
        if( !isExternalStorageWritable() ) {
            throw new IOException( "Cannot access storage - is mounted?" );
        }
        File dest = getFile( name );
        BufferedWriter out = new BufferedWriter( new FileWriter( dest ) );
        out.write( content );
        out.close();
    }

    /**
     * Is external storage available for writing?
     * @return <code>true</code> if it is
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals( state );
	}

    /**
     * List the scripts "RivetScripts/*.r"
     * @return array of the script file objects
     */
    public File[] listFiles() {
        return this.listFiles( null );
    }

	public File[] listFiles( String ext ) {
		if( (ext != null) && !ext.startsWith( "." ) ) {
			ext = ("." + ext);
		}
		this.filter_ext = ext;
		return root.listFiles( this );
	}

    /**
     * Get a {@link File} in "RivetData" folder
     * @param name the name
     * @return the file
     */
    public File getFile( String name ) {
        return new File( root, name );
    }

	public InputStream getInputStream( String name ) throws IOException {
		if( name.startsWith( "#" ) ) {
			return context.getAssets().open( name.substring( 1 ) );
		} else if( name.contains( "|" ) ) {
			int b = name.indexOf( '|' );
			ZipFile zip = new ZipFile( getFile( name.substring( 0, b ) ) );
			ZipEntry e = zip.getEntry( name.substring( (b + 1) ) );
			if( e != null ) {
				return zip.getInputStream( e );
			} else {
				throw new IOException( ("Archive does not contain entry: " + name) );
			}
		} else {
			File f = getFile( name );
			if( f.exists() ) {
				return new BufferedInputStream( new FileInputStream( f ) );
			} else {
				AssetManager am = context.getAssets();
				if( am.list( name ).length > 0 ) {
					return am.open( name );
				} else {
					throw new FileNotFoundException( f.getAbsolutePath() );
				}
			}
		}
	}

	/**
	 * Get a bitmap from the root location or assets if prefixed with "#"
	 * @param name the name of the file or #asset
	 * @return the bitmap or null if unable to decode (but resource exists)
	 * @throws IOException
	 */
	public Bitmap getBitmap( String name ) throws IOException {
		return BitmapFactory.decodeStream( getInputStream( name ) );
	}

	/**
	 * If the standard call for external files didn't work we'll try a few hardcoded locations instead
	 * This is for READING only
	 * @param name the name of the file
	 * @return
	 */
//	public File findFile( String name ) {
//		File f = getFile( name );
//		if( !f.exists() ) {
//			f = new File( new File( "/sdcard/", root_name ), name );
//			if( !f.exists() ) {
//				f = new File( context.getDir( root_name, Context.MODE_PRIVATE ), name );
//				if( !f.exists() ) {
//					return getFile( name );			// we did what we could and nothing worked so return the default
//				}
//			}
//		}
//		return f;
//	}

	/**
	 *
	 * @return the root of the (hopefully) restricted file hierarchy
	 */
	public File getRoot() {
		return root;
	}

    /**
     * Check to see if we can read from external storage
     * @return <code>true</code> if storage can be read from
     */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }


    /* (non-Javadoc)
     * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
     */
    @Override
    public boolean accept(File dir, String filename) {
        return ((filter_ext == null) || filename.toLowerCase().endsWith( filter_ext ));
    }

    /**
     * @return the instance
     */
    public static FileManager getInstance( Context c ) {
        if( instance == null ) {
            FileManager.instance = new FileManager();
			instance.setRootPath( "" );
		}
        instance.context = c;
        return instance;
    }

	/**
	 * Read the contents of an inputstream into a string
	 * @param ins the stream to read from
	 * @return the string
	 * @throws IOException
	 */
	public static String readInputStream( InputStream ins ) throws IOException {
		BufferedInputStream src = new BufferedInputStream( ins );
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		int l;
		byte[] buffer = new byte[255];
		while( (l = src.read( buffer )) > -1 ) {
			data.write( buffer, 0, l );
		}
		src.close();
		return data.toString();
	}

	public static Map<String, String> parseCommandLine( String cmd ) {
		HashMap<String, String> map = new HashMap<String, String>();
		String k = "_", t;
		StringBuilder b = new StringBuilder();
		char[] cs = (cmd + " ").toCharArray();
		int i, l;
		boolean inq = false;
		for( i = 0, l = cs.length; i < l; i++ ) {
			char c = cs[i];
			switch( c ) {
				case '\\':
					i++;
					break;
				case '"':
					inq = !inq;
					break;
				case ' ':
					if( !inq ) {
						t = b.toString().trim();
						if( t.length() > 0 ) {
							if( t.charAt( 0 ) == '-' ) {
								k = t.substring( 1 );
							} else {
								if( map.containsKey( k ) ) {
									map.put( k, (map.get( k ) + " " + t) );
								} else {
									map.put( k, t );
								}
							}
							b.delete( 0, b.length() );
						}
					} else {
						b.append( ' ' );
					}
					break;
				default:
					b.append( c );
					break;
			}
		}
		return map;
	}
}
