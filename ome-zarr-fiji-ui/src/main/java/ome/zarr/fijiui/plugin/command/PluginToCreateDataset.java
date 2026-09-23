package ome.zarr.fijiui.plugin.command;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.util.PyramidalUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Extract Dataset" )
public class PluginToCreateDataset implements Command
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
	DatasetService ds;

	@Parameter( type = ItemIO.OUTPUT )
	Dataset dataset;

	@Override
	public void run()
	{
		dataset = ds.create( PyramidalUtils.wrapResLevelAt( pyramidal.getPyramidContents(), resolutionLevel, channel, timepoint ) );
	}
}
