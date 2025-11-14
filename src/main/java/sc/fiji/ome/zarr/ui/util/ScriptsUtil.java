package sc.fiji.ome.zarr.ui.util;

import net.imagej.legacy.LegacyService;
import org.scijava.Context;
import org.scijava.module.Module;
import org.scijava.module.ModuleService;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.ui.PluginPresetDndScript;

import java.io.File;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ScriptsUtil {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private ScriptsUtil() {
        //prevent instantiation
    }

	private static final String BTN_LINE1_DEFAULT = "Set Own";
	private static final String BTN_LINE2_DEFAULT = "Script";

	/**
	 * @return A two-item array for the upper and bottom row of the "run script" DnD button.
	 */
	public static String[] getButtonLabels(Context ctx) {
        if (ctx == null) {
            return new String[] {BTN_LINE1_DEFAULT, BTN_LINE2_DEFAULT};
        }
		PrefService prefService = ctx.getService(PrefService.class);
		if (prefService == null) {
			//can't retrieve user labels, go with the defaults then...
			return new String[] {BTN_LINE1_DEFAULT, BTN_LINE2_DEFAULT};
		}

		final String line1 = prefService.get(PluginPresetDndScript.class, "line1", BTN_LINE1_DEFAULT);
		final String line2 = prefService.get(PluginPresetDndScript.class, "line2", BTN_LINE2_DEFAULT);
		return new String[] {line1, line2};
	}


	/**
	 * Executes either a preset script and passes 'inputPath' arg to it provided
	 * the preset script is a valid file; otherwise it opens a script editor
	 * on a default example script provided in {@link ScriptsUtil#templateScriptItself()}.
	 */
	public static void executePresetScript(Context ctx, String inputPath) {
		ScriptService scriptService = ctx.getService(ScriptService.class);
		ModuleService moduleService = ctx.getService(ModuleService.class);
		PrefService prefService = ctx.getService(PrefService.class);
		if (scriptService == null || moduleService == null || prefService == null) return;

		//retrieve the path to the preset script
		final String scriptPath = prefService.get(PluginPresetDndScript.class, "scriptPath", "--none--");

		if (Files.exists( Paths.get(scriptPath).toAbsolutePath() )) {
			//the filepath is viable, let's run the script
			try {
				final Module module = moduleService.createModule( scriptService.getScript( new File(scriptPath) ) );
				module.setInput("path", inputPath);

                logger.info("Executing script: {}", scriptPath);
                logger.info("on String parameter: {}", inputPath);
				module.run();
                logger.info("External script finished now.");
			} catch (Exception e) {
                logger.warn(" Something went wrong executing the script: {}. Message: {}", scriptPath, e.getMessage());
			}
		} else {
			//the filepath is not functional, let's show a template script
			ScriptInfo s = new ScriptInfo(ctx, "open_ome_zarr_my_way.py", new StringReader(templateScriptItself()));
			ctx.getService(LegacyService.class).openScriptInTextEditor(s);
		}
	}


	private static String templateScriptItself() {
		return
			"# RESAVE THIS SCRIPT AND POINT Fiji -> Plugins -> Preset DragAndDrop User Script ON IT\n"+
			"\n"+
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
