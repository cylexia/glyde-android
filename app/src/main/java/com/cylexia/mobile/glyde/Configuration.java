package com.cylexia.mobile.glyde;

import com.cylexia.mobile.glyde.exe.GlueTestRunner;
import com.cylexia.mobile.glyde.exe.HashCode;
import com.cylexia.mobile.glyde.exe.UI;
import com.cylexia.mobile.glyde.exe.Wget;
import com.cylexia.mobile.lib.glue.Glue;

/**
 * Created by sparx104 on 30/04/2015.
 *
 * If building a custom app clone the code then modify this to customise the system.  Also modify
 * the manifest as required and files in "assets" folder.
 */
public class Configuration {
	// -v- CUSTOMISE -v-

	// The path to read & write files to in the storage
	public final static String ROOT_PATH = "Glyde";

	// The standalone app file if using without LaunchActivity
	public final static String STANDALONE_APP_FILE = "#default.app";

	// The executable modules (for platform.exec) to be used
	public static void attachExec( Glue runtime, ViewActivity activity, GlydeView view ) {
		runtime.addExecutable( "ui", new UI( activity ) );
		runtime.addExecutable( "wget", new Wget( activity ) );
		runtime.addExecutable( "hashcode", new HashCode() );
		runtime.addExecutable( "gluetestrunner", new GlueTestRunner( GlueTestRunner.GTR_EXE ) );
	}

	// -^- CUSTOMISE -^-//
}
