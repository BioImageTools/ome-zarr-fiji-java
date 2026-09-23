package ome.zarr.imglib2.write;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;

public class PyramidSaverTest
{

	public static void main( String[] args )
	{
		AxisCalibration[] axes = new AxisCalibration[] {
				new AxisCalibration( AxisCalibration.T, "seconds", 1 ),
				new AxisCalibration( AxisCalibration.Y, "microns", 1.6 ),
				new AxisCalibration( AxisCalibration.X, "microns", 1.2 ),
		};

		PyramidContents< UnsignedShortType > p = PyramidContentsUtils.create(
				"empty pyramidal",
				new UnsignedShortType(),
				new long[] { 512, 1024 },
				0,
				5,
				axes,
				new double[][] {
						{ 2.0, 4.1 },
						{ 10.0, 1.0 },
						{ 2.2, 4.3 }
				}
		);

		System.out.println( p );

		CachedCellImg< UnsignedShortType, ? > ccimg = ( CachedCellImg< UnsignedShortType, ? > ) p.asLargestImg();
		System.out.print( "grid sizes: " );
		for ( int d : ccimg.getCellGrid().getCellDimensions() )
			System.out.print( d + " px, " );
		System.out.println();

		RandomAccessibleInterval< UnsignedShortType > newImg = PyramidContentsUtils.xyzReducedView( p, 0, 3, 3 );
		System.out.println( newImg );
	}
}
