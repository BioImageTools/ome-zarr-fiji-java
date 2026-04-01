package sc.fiji.ome.zarr.plugins;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Current OME-Zarr Image in ImageJ" )
public class OpenInImageJCommand implements Command
{
	@Parameter
	LogService logService;

	@Parameter
	DatasetService datasetService;

	@Parameter
	Dataset d;

	@Parameter
	UIService uiService;

	@Override
	public void run()
	{
		logService.info( "Datasets available cnt = " + datasetService.getDatasets().size() );
		if ( datasetService.getDatasets().isEmpty() )
		{
			logService.info( "No datasets available." );
			return;
		}

		//Dataset d = datasetService.getDatasets().get( 0 );
		long z = d.getDepth();
		long c = d.getChannels();
		long t = d.getFrames();

		logService.info( "Dataset class is " + d.getClass().getName() );
		logService.info( "Dataset " + d.getName()
				+ ", depth=" + z + ", channels=" + c + ", time points=" + t );

		uiService.show( d );
	}
}
