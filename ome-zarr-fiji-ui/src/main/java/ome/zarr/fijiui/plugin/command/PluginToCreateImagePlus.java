package ome.zarr.fijiui.plugin.command;

import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;
import ome.zarr.fiji.Pyramidal;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Extract ImagePlus" )
public class PluginToCreateImagePlus extends DynamicCommand
{
	@Parameter
	Pyramidal p;

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
		final Img< ? extends NumericType > img = p.getPyramidContents().asImg( resolutionLevel );
		final String name = p.getPyramidContents().name + " (R=" + resolutionLevel + ",C=" + channel + ",T=" + timepoint + ")";
		//TODO make sure the channel and time are 4th and 5th dimension, respectively
		imagePlus = ImageJFunctions.wrap( Views.hyperSlice(
				Views.hyperSlice( img, 3, channel ), 4, timepoint ), name );
	}
}
