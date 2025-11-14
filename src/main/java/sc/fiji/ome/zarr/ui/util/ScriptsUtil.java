package sc.fiji.ome.zarr.ui.util;

import net.imagej.legacy.LegacyService;
import org.scijava.Context;
import org.scijava.module.Module;
import org.scijava.module.ModuleService;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptService;
import sc.fiji.ome.zarr.ui.PluginPresetDndScript;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ScriptsUtil {

	public static final String BTN_LINE1_DEFAULT = "Set Own";
	public static final String BTN_LINE2_DEFAULT = "Script";

	/**
	 * @return A two-item array for the upper and bottom row of the "run script" DnD button.
	 */
	public static String[] getButtonLabels(Context ctx) {
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
				//
				System.out.println("==> Executing external script: "+scriptPath);
				System.out.println("==> on String parameter: "+inputPath);
				module.run();
				System.out.println("==> External script finished now.");
			} catch (Exception e) {
				//TODO: turn into warning log
				System.out.println("SOME WENT WRONG EXECUTING THE SCRIPT: "+scriptPath);
				System.out.println(e.getMessage());
			}
		} else {
			//the filepath is not functional, let's show a template script
			ScriptInfo s = new ScriptInfo(ctx, "open_ome_zarr_my_way.py", new StringReader(templateScriptItself()));
			ctx.getService(LegacyService.class).openScriptInTextEditor(s);
		}
	}


	public static String templateScriptItself() {
		return
			"# RESAVE THIS SEEDS SCRIPT AND POINT THE BDV_WITH_SEEDS DIALOG ON IT\n"+
			"\n"+
			"#@ ImagePlus imp\n"+
			"#@ float contrast_min\n"+
			"#@ float contrast_max\n"+
			"\n"+
			"# It is important that seeds (any non-zero pixels) are stored directly into the input 'imp' image!\n"+
			"\n"+
			"# The 'contrast_min' and 'contrast_max' report the current BDV display (contrast) setting\n"+
			"# used with the displayed input image (from which the 'imp' is cropped out). You may want\n"+
			"# (but need not) to consider this for the seeds extraction...\n"+
			"\n"+
			"from ij import IJ\n"+
			"\n"+
			"# It is possible to report from this script, it will appear in Fiji console.\n"+
			"print(\"contrast:\",contrast_min,contrast_max)\n"+
			"\n"+
			"# Example threshold function that thresholds _inplace_\n"+
			"def threshold(thres_val):\n"+
			"    pxs = imp.getProcessor().getPixels()\n"+
			"    for i in range(len(pxs)):\n"+
			"        pxs[i] = 1 if pxs[i] > thres_val else 0\n"+
			"\n"+
			"# Example of using a standard Fiji plugin \n"+
			"IJ.run(imp, \"Maximum...\", \"radius=1\")\n"+
			"threshold(0.65)\n"+
			"\n"+
			"# Don't use the updateAndRepaintWindow() in conjunction with BDV+SAMJ,\n"+
			"# but it is useful when running (debugging) this script directly from Fiji\n"+
			"# (e.g. on some of the debug crop-out that came from BDV+SAMJ).\n"+
			"#\n"+
			"# imp.updateAndRepaintWindow()";
	}
}
