/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package sc.fiji.ome.zarr.util;

import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptModule;
import org.scijava.script.ScriptService;
import org.scijava.ui.swing.script.TextEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;

import sc.fiji.ome.zarr.plugins.DragAndDropUserScriptSettings;

public class ScriptUtils
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private ScriptUtils()
	{
		//prevent instantiation
	}

	private static final String DEFAULT_SCRIPT_TITLE = "Script not defined";

	/**
	 * Retrieves preset script and its title, and returns the title if the script
	 * actually exits (is reachable).
	 * Otherwise, it returns {@link ScriptUtils#DEFAULT_SCRIPT_TITLE}.
	 * <br>
	 * @param ctx scijava context
	 * @return the script title
	 */
	public static String getTooltipText( final Context ctx )
	{
		if ( ctx == null )
			return DEFAULT_SCRIPT_TITLE;

		PrefService prefService = ctx.getService( PrefService.class );
		if ( prefService == null )
			return DEFAULT_SCRIPT_TITLE;

		final String scriptTitle = prefService.get( DragAndDropUserScriptSettings.class, "scriptTitle", DEFAULT_SCRIPT_TITLE );
		final String scriptPath = prefService.get( DragAndDropUserScriptSettings.class, "scriptPath", "--none--" );

		return Files.exists( Paths.get( scriptPath ).toAbsolutePath() ) ? scriptTitle : DEFAULT_SCRIPT_TITLE;
	}

	/**
	 * Executes either a preset script and passes 'inputPath' arg to it provided
	 * the preset script is a valid file; otherwise it opens a script editor
	 * on a default example script provided in {@link ScriptUtils#getTemplate()}.
	 * <br>
	 * @param ctx scijava context
	 * @param inputPath path to the input image
	 */
	public static void executePresetScript( final Context ctx, final String inputPath, final Consumer< String > errorHandler )
	{
		ScriptService scriptService = ctx.getService( ScriptService.class );
		PrefService prefService = ctx.getService( PrefService.class );
		if ( scriptService == null || prefService == null )
		{
			logger.error( "Failed obtaining Script and/or Pref services. Is Fiji properly initiated?" );
			errorHandler.accept(
					"Service for running scripts and/or service for reading preferences are not available. Is Fiji properly initiated?" );
			return;
		}

		//retrieve the path to the preset script
		final String scriptPath = prefService.get( DragAndDropUserScriptSettings.class, "scriptPath", "--none--" );

		if ( Files.exists( Paths.get( scriptPath ).toAbsolutePath() ) )
		{
			logger.debug( "Script path is valid: {}. Attempting to run the script.", scriptPath );
			//the filepath is viable, let's run the script
			try
			{
				ScriptModule module = scriptService.getScript( new File( scriptPath ) ).createModule();
				module.setContext( ctx );
				module.setInput( "path", inputPath );
				logger.info( "Executing script: {}", scriptPath );
				logger.info( "on String parameter: {}", inputPath );
				module.run();
				logger.info( "External script finished now." );
			}
			catch ( Exception e )
			{
				logger.warn(
						" Something went wrong executing the script: {} on this dataset: {}. Message: {}", scriptPath, inputPath,
						e.getMessage()
				);
				errorHandler.accept( "Script could not be processed on OME-Zarr dataset. " + "\n\r\n" + "Script path: " + scriptPath + "\n"
						+ "Dataset path: " + inputPath + "\n\n" + "Error message: " + e.getMessage() );

			}
		}
		else
		{
			logger.info( "Script path is not valid: {}. Opening script editor with a script template instead.", scriptPath );
			errorHandler.accept( "Script path is not valid: " + scriptPath
					+ ".\n\nPlease provide a valid path via Plugins Plugins > OME-Zarr > Drag & Drop User Script Settings.\n\nWill open now script editor with a script template." );
			//this opens an _always new_ window with the template script,
			//...at least the user is more likely to notice that this "help" came up
			final TextEditor editor = new TextEditor( ctx );
			editor.createNewDocument( "open_ome_zarr_my_way.py", getTemplate() );
			editor.setVisible( true );
		}
	}

	static String getTemplate()
	{
		return "# RESAVE THIS SCRIPT AND OPEN IN THE MENU\n" +
				"# Fiji > Plugins > OME-Zarr > Drag & Drop User Script Settings\n" +
				"# to have this available among the OME-Zarr drag & drop choices.\n" +
				"# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n" +
				"\n" +
				"#@ String path\n" +
				"\n" +
				"from org.janelia.saalfeldlab.n5.bdv import N5Viewer\n" +
				"N5Viewer.show(path)\n" +
				"\n" +
				"print(\"Started BDV on a path: \"+path)\n" +
				"\n" +
				"# from https://forum.image.sc/t/ome-zarr-viewer-supporting-private-s3-buckets/106235/15\n" +
				"# credit: John Bogovic\n";
	}
}
