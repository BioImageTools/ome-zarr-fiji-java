package sc.fiji.ome.zarr.plugins.macros;

import net.imagej.Dataset;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Current OME-Zarr Image in ImageJ" )
public class ChannelsFromZarr implements Command
{
	@Parameter
	LogService logService;

	@Parameter
	Dataset d;

	@Parameter( type = ItemIO.OUTPUT )
	int channels;

	@Override
	public void run()
	{
		if ( !( d instanceof PyramidalDataset ) )
		{
			logService.error( "The current dataset " + d.getName() + " is not OME-Zarr." );
			channels = -1;
			return;
		}

		channels = ( ( PyramidalDataset< ? > ) d ).numChannels();
	}
}
