package sc.fiji.ome.zarr.ui.util;

import net.imagej.ImageJ;
import sc.fiji.ome.zarr.util.ScriptUtils;

public class DemoScriptsUtil {
	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final String inputPath = "/home/ulman/Documents/talks/CEITEC/2025_11_ZarrSymposium_Zurich/data/MitoEM_fixedRes.zarr/MitoEM_fixedRes";
		System.out.println("\nLet's run the script... on String param: "+inputPath);
		ScriptUtils.executePresetScript(ij.context(), inputPath);
		System.out.println("Done.");
	}
}
