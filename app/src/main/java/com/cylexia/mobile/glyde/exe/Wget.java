package com.cylexia.mobile.glyde.exe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.cylexia.mobile.lib.glue.FileManager;
import com.cylexia.mobile.lib.glue.Glue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.cylexia.mobile.glyde.ViewActivity;

/**
 * Created by sparx104 on 30/04/2015.
 */
public class Wget implements Glue.Executable {
	private final static int EVENT_COMPLETE = 1;

	private ViewActivity notify_activity;
	private String on_done_label;
	private Handler callback;

	public Wget( ViewActivity runloop_activity ) {
		super();
		this.notify_activity = runloop_activity;
	}

	@Override
	public int exec( Glue glue, Context c, String name, String args, String label ) {
		this.on_done_label = label;
		this.callback = new Handler( Looper.getMainLooper() );

		int b = args.indexOf( "-o" );
		if( b > -1 ) {
			try {
				(new Thread( new Downloader( new URL( args.substring( 0, b ).trim() ), args.substring( (b + 2) ).trim() ), "Download" )).start();
			} catch( MalformedURLException ex ) {
				//
			}
		}
		//HttpURLConnection conn = HttpURL.
		return -200;
	}

	private class Downloader implements Runnable {
		private URL url;
		private String downloadfile;
		public Downloader( URL u, String target ) {
			super();
			this.url = u;
			this.downloadfile = target;
		}

		@Override
		public void run() {
			//Log.i( "wget", "Url: " + url.toString() );
			//callback.post( new RunloopNotifier() );
			//if( 1 == 1 ) return;
			InputStream input = null;
			OutputStream output = null;
			HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection) url.openConnection();
				connection.connect();

				// download the file
				input = connection.getInputStream();
				output = new FileOutputStream( FileManager.getInstance( notify_activity ).getFile( downloadfile ) );

				byte data[] = new byte[4096];
				long total = 0;
				int count;
				while ((count = input.read(data)) != -1) {
					total += count;
					output.write(data, 0, count);
				}
			} catch( Exception ex ) {
				Log.e( "wget", ex.toString() );
			} finally {
				try {
					if (output != null)
						output.close();
					if (input != null)
						input.close();
				} catch( IOException ex ) {
					//
				}

				if( connection != null ) {
					connection.disconnect();
				}
			}
			callback.post( new RunloopNotifier() );
		}
	}

	private class RunloopNotifier implements Runnable {
		@Override
		public void run() {
			notify_activity.runScriptAndSync( on_done_label );
		}
	}

	private class MsgHandler extends Handler {
		public MsgHandler() {
			super( Looper.getMainLooper() );
		}

		@Override
		public void handleMessage( Message msg ) {
			if( msg.what == EVENT_COMPLETE ) {
				notify_activity.runScriptAndSync( on_done_label );

			}
			super.handleMessage( msg );
		}
	}


/*
	private class DownloadTask extends AsyncTask<String, Integer, String> {

		private Context context;

		public DownloadTask(Context context) {
			this.context = context;
		}

		@Override
		protected String doInBackground(String... sUrl) {
			InputStream input = null;
			OutputStream output = null;
			HttpURLConnection connection = null;
			try {
				URL url = new URL(sUrl[0]);
				connection = (HttpURLConnection) url.openConnection();
				connection.connect();

				// expect HTTP 200 OK, so we don't mistakenly save error report
				// instead of the file
				if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
					return "Server returned HTTP " + connection.getResponseCode()
							+ " " + connection.getResponseMessage();
				}

				// this will be useful to display download percentage
				// might be -1: server did not report the length
				int fileLength = connection.getContentLength();

				// download the file
				input = connection.getInputStream();
				output = new FileOutputStream("/sdcard/file_name.extension");

				byte data[] = new byte[4096];
				long total = 0;
				int count;
				while ((count = input.read(data)) != -1) {
					// allow canceling with back button
					if (isCancelled()) {
						input.close();
						return null;
					}
					total += count;
					// publishing the progress....
					if (fileLength > 0) // only if total length is known
						publishProgress((int) (total * 100 / fileLength));
					output.write(data, 0, count);
				}
			} catch (Exception e) {
				return e.toString();
			} finally {
				try {
					if (output != null)
						output.close();
					if (input != null)
						input.close();
				} catch (IOException ignored) {
				}

				if (connection != null)
					connection.disconnect();
			}
			return null;
		}
		*/
}
