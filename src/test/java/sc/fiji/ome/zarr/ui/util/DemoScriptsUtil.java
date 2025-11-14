package sc.fiji.ome.zarr.ui.util;

import net.imagej.ImageJ;

public class DemoScriptsUtil {
	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();

		System.out.println("Button labels as two lines: ");
		for (String s : ScriptsUtil.getButtonLabels(ij.context())) {
			System.out.println("LINE: "+s);
		}

		final String inputPath = "/home/ulman/Documents/talks/CEITEC/2025_11_ZarrSymposium_Zurich/data/MitoEM_fixedRes.zarr/MitoEM_fixedRes";
		System.out.println("\nLet's run the script... on String param: "+inputPath);
		ScriptsUtil.executePresetScript(ij.context(), inputPath);
		System.out.println("Done.");
	}
}
