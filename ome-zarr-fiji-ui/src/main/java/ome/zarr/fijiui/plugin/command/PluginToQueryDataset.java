package ome.zarr.fijiui.plugin.command;

import ome.zarr.fiji.Pyramidal;
import ome.zarr.imglib2.PyramidContents;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Query Pyramidal Dataset" )
public class PluginToQueryDataset implements Command
{
	@Parameter
	Pyramidal pd;

	static final String qNumDimensions = "Number of dimensions";

	static final String qChannels = "Number of channels";

	static final String qTimepoints = "Number of time points";

	static final String qResLevels = "Number of resolution levels";

	@Parameter( choices = { qNumDimensions, qChannels, qTimepoints, qResLevels } )
	String query = qResLevels;

	@Parameter( type = ItemIO.OUTPUT )
	long queriedQuantity = -1;

	@Override
	public void run()
	{
		final PyramidContents< ? > p = pd.getPyramidContents();
		switch ( query )
		{
		case qNumDimensions:
			queriedQuantity = p.numDimensions();
			break;
		case qChannels:
			queriedQuantity = p.numChannels();
			break;
		case qTimepoints:
			queriedQuantity = p.numTimepoints();
			break;
		case qResLevels:
		default:
			queriedQuantity = p.numResolutionLevels();
		}
	}
}
