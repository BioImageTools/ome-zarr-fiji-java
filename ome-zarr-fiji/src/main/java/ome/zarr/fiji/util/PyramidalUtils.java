package ome.zarr.fiji.util;

import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.write.PyramidContentsUtils;

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
}
