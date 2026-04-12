package sc.fiji.ome.zarr.plugins.macros;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Get ImagePlus" )
public class ImagePlusFromZarr implements Command
{
	@Parameter
	LogService logService;

	@Parameter
	Dataset d;

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
		imagePlus = getImagePlus( d, resolutionLevel, channel, timepoint );

		if ( imagePlus == null )
		{
			logService.error( "The current dataset " + d.getName() + " is not OME-Zarr." );
			return;
		}
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
