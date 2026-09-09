package ome.zarr.fijiui.plugin.command;

import ij.ImagePlus;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.util.PyramidalUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Extract ImagePlus" )
public class PluginToCreateImagePlus implements Command
{
	@Parameter
	Pyramidal pyramidal;

	@Parameter
	int resolutionLevel = 0;

	@Parameter
	int channel = 0;

	@Parameter
	int timepoint = 0;

	@Parameter
	ConvertService convertService;

	@Parameter( type = ItemIO.OUTPUT )
	ImagePlus imagePlus;

	@Override
	public void run()
	{
		imagePlus = convertService.convert(
				PyramidalUtils.wrapResLevelAt( pyramidal.getPyramidContents(), resolutionLevel, channel, timepoint ),
				ImagePlus.class );
	}
}
