package sc.fiji.ome.zarr.plugins;

import bdv.viewer.SourceAndConverter;
import edu.mines.jtk.dsp.Real1Test;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Current OME-Zarr Image in ImageJ" )
public class OpenInImageJCommand implements Command
{
	@Parameter
	LogService logService;

	@Parameter
	DatasetService datasetService;

	//there's gotta be a list of available datasets because as soon as a dataset is displayed
	//with the standard ImageJ window, the dialog is no longer asking and, instead, it is filling
	//the opened and active dataset for the 'd' right-away
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

	public static < T extends NativeType< T > & RealType< T > > ImagePlus getImagePlus(
			Dataset d, int resolutionLevel,
			int channel, int timepoint )
	{
		if ( !( d instanceof PyramidalDataset ) )
			return null;

		PyramidalDataset< T > pd = ( PyramidalDataset< T > ) d;
		SourceAndConverter< T > sac = pd.asSources().get( channel );
		RandomAccessibleInterval< T > rai = sac.getSpimSource().getSource( timepoint, resolutionLevel );
		return ImageJFunctions.wrap( rai, sac.getSpimSource().getName() );
	}
}
