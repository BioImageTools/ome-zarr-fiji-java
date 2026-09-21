package ome.zarr.fijiui.plugin.command;

import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.write.PyramidContentsUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Extract ImagePlus" )
public class PluginToCreateImagePlus extends DynamicCommand
{
	@Parameter
	Pyramidal pyramidal;

	@Parameter
	int resolutionLevel = 0;

	@Parameter
	int channel = 0;

	@Parameter
	int timepoint = 0;

	@Parameter( type = ItemIO.OUTPUT )
	ImagePlus imagePlus;

	@Override
	public void run()
	{
		final PyramidContents< ? > pc = pyramidal.getPyramidContents();
		final String name = pc.name + " (R=" + resolutionLevel + ",C=" + channel + ",T=" + timepoint + ")";

		imagePlus = ImageJFunctions.wrap( PyramidContentsUtils.xyzReducedView(
				pc, resolutionLevel, channel, timepoint ), name );
	}
}
