package sc.fiji.ome.zarr.util;

import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptModule;
import org.scijava.script.ScriptService;
import org.scijava.ui.swing.script.TextEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.plugins.PresetScriptPlugin;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;

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
	 */
	public static String getTooltipText( final Context ctx ) {
		if ( ctx == null ) return DEFAULT_SCRIPT_TITLE;

		PrefService prefService = ctx.getService( PrefService.class );
		if ( prefService == null ) return DEFAULT_SCRIPT_TITLE;

		final String scriptTitle = prefService.get( PresetScriptPlugin.class, "scriptTitle", DEFAULT_SCRIPT_TITLE );
		final String scriptPath = prefService.get( PresetScriptPlugin.class, "scriptPath", "--none--" );

		return Files.exists( Paths.get( scriptPath ).toAbsolutePath() ) ? scriptTitle : DEFAULT_SCRIPT_TITLE;
	}


	/**
	 * Executes either a preset script and passes 'inputPath' arg to it provided
	 * the preset script is a valid file; otherwise it opens a script editor
	 * on a default example script provided in {@link ScriptUtils#getTemplate()}.
	 */
	public static void executePresetScript( final Context ctx, final String inputPath )
	{
		ScriptService scriptService = ctx.getService( ScriptService.class );
		PrefService prefService = ctx.getService( PrefService.class );
		if ( scriptService == null || prefService == null ) {
			logger.error( "Failed obtaining Script and/or Pref services. Is Fiji properly initiated?" );
			return;
		}

		//retrieve the path to the preset script
		final String scriptPath = prefService.get( PresetScriptPlugin.class, "scriptPath", "--none--" );

		if ( Files.exists( Paths.get( scriptPath ).toAbsolutePath() ) )
		{
			//the filepath is viable, let's run the script
			try
			{
				ScriptModule module = scriptService.getScript(new File(scriptPath)).createModule();
				module.setInput( "path", inputPath );
				logger.info( "Executing script: {}", scriptPath );
				logger.info( "on String parameter: {}", inputPath );
				module.run();
				logger.info( "External script finished now." );
			}
			catch ( Exception e )
			{
				logger.warn( " Something went wrong executing the script: {}. Message: {}", scriptPath, e.getMessage() );
			}
		}
		else
		{
			//this opens an _always new_ window with the template script,
			//...at least the user is more likely to notice that this "help" came up
			final TextEditor editor = new TextEditor(ctx);
			editor.createNewDocument("open_ome_zarr_my_way.py", getTemplate());
			editor.setVisible(true);
		}
	}

	private static String getTemplate()
	{
		return "# RESAVE THIS SCRIPT AND OPEN IN THE MENU\n" +
				"# Fiji -> Plugins -> OME-Zarr -> Preset DragAndDrop User Script\n" +
				"# to have this available among the drag-and-drop choices.\n" +
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
