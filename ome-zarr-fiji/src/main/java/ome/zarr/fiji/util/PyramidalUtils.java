package ome.zarr.fiji.util;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.write.PyramidContentsUtils;
import ij.ImagePlus;
import org.scijava.convert.ConvertService;

public class PyramidalUtils
{
	private PyramidalUtils()
	{}

	//TODO or the weaker?: public static ImgPlus< ? > wrapResLevel(
	//TODO or just remove this method (it's redundant and types-problematic)
	public static < T extends NativeType< T > & RealType< T > > ImgPlus< T > wrapResLevel(
			final Pyramidal pyramidal,
			final int resolutionLevel )
	{
		return ( ImgPlus< T > ) wrapResLevel( pyramidal.getPyramidContents(), resolutionLevel );
	}

	public static < T extends NativeType< T > & RealType< T > > ImgPlus< T > wrapResLevel(
			final PyramidContents< T > pyramidContents,
			final int resolutionLevel )
	{
		// NB: asImg() checks validity of the requested resolutionLevel
		final ImgPlus< T > imgPlus = ImgPlus.wrap( pyramidContents.asImg( resolutionLevel ) );
		imgPlus.setName( pyramidContents.name + " (R=" + resolutionLevel + ")" );
		return imgPlus;
	}

	public static < T extends NativeType< T > & RealType< T > > ImgPlus< T > wrapResLevelAt(
			final Pyramidal pyramidal,
			final int resolutionLevel,
			final int channel,
			final int timePoint )
	{
		return ( ImgPlus< T > ) wrapResLevelAt( pyramidal.getPyramidContents(), resolutionLevel, channel, timePoint );
	}

	public static < T extends NativeType< T > & RealType< T > > ImgPlus< T > wrapResLevelAt(
			final PyramidContents< T > pyramidContents,
			final int resolutionLevel,
			final int channel,
			final int timePoint )
	{
		// NB: xyzReducedView() checks validity of the requested parameters
		final ImgPlus< T > imgPlus =
				ImgPlus.wrapRAI( PyramidContentsUtils.xyzReducedView( pyramidContents, resolutionLevel, channel, timePoint ) );
		imgPlus.setName( pyramidContents.name + " (R=" + resolutionLevel + ",C=" + channel + ",T=" + timePoint + ")" );
		return imgPlus;
	}

	public static Dataset asDatasetForResLevelAt(
			final Pyramidal pyramidal,
			final int resolutionLevel,
			final int channel,
			final int timePoint )
	{
		DatasetService ds = pyramidal.getContext().getService( DatasetService.class );
		assert ds != null: "The available Context is missing the DatasetService.";

		return ds.create( wrapResLevelAt( pyramidal.getPyramidContents(), resolutionLevel, channel, timePoint ) );
	}

	public static ImagePlus asImagePlusForResLevelAt(
			final Pyramidal pyramidal,
			final int resolutionLevel,
			final int channel,
			final int timePoint )
	{
		ConvertService cs = pyramidal.getContext().getService( ConvertService.class );
		assert cs != null: "The available Context is missing the ConvertService.";
		return cs.convert(
				wrapResLevelAt( pyramidal.getPyramidContents(), resolutionLevel, channel, timePoint ),
				ImagePlus.class );
		// BTW: The conversion created a (not-displayed) Dataset along the way,
		//      which can be reached via the DatasetService -- not sure this is entirely true!
	}
}
