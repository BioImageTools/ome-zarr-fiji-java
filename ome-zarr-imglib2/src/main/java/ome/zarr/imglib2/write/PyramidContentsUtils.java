package ome.zarr.imglib2.write;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.optional.CacheOptions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;
import java.util.Arrays;

public class PyramidContentsUtils
{
	//TODO: the class is silently assuming x,y[,z] to be the first in the AxisCalibration arrays

	public static boolean isSpatialAxis( final AxisCalibration axis )
	{
		return ( axis.name.equals( AxisCalibration.X ) )
				|| ( axis.name.equals( AxisCalibration.Y ) )
				|| ( axis.name.equals( AxisCalibration.Z ) );
	}

	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > create(
			String name,
			T pixelType,
			long[] baseLayerDims,
			AxisCalibration[] baseLayerAxes,
			double[] spatialIsotropicDownScales )
	{
		int spatialDimsCnt = 0;
		for ( AxisCalibration axis : baseLayerAxes )
			spatialDimsCnt += isSpatialAxis( axis ) ? 1 : 0;
		assert spatialDimsCnt > 0: "No spatial axis discovered in baseLayerAxes definition.";

		double[][] spatialDownScales = new double[ spatialIsotropicDownScales.length ][ spatialDimsCnt ];
		for ( int l = 0; l < spatialIsotropicDownScales.length; l++ )
			Arrays.fill( spatialDownScales[ l ], spatialIsotropicDownScales[ l ] );

		return create( name, pixelType, baseLayerDims, baseLayerAxes, spatialDownScales );
	}

	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > create(
			String name,
			T pixelType,
			long[] baseLayerDims,
			AxisCalibration[] baseLayerAxes,
			double[][] spatialDownScales ) //x,y[,z] order of axis is assumed
	{
		AxisCalibration[][] axes = new AxisCalibration[ spatialDownScales.length + 1 ][ baseLayerAxes.length ];
		//
		//copy the base layer as is
		for ( int i = 0; i < baseLayerAxes.length; ++i )
		{
			final AxisCalibration a = baseLayerAxes[ i ];
			axes[ 0 ][ i ] = new AxisCalibration( a.name, a.unit, a.scale );
		}
		//create the downscaled layers
		for ( int l = 1; l < axes.length; l++ )
		{
			for ( int i = 0; i < baseLayerAxes.length; ++i )
			{
				final AxisCalibration a = baseLayerAxes[ i ];
				if ( a.name.equals( AxisCalibration.X ) )
				{
					axes[ l ][ i ] = new AxisCalibration( a.name, a.unit, a.scale * spatialDownScales[ l - 1 ][ 0 ] );
				}
				else if ( a.name.equals( AxisCalibration.Y ) )
				{
					axes[ l ][ i ] = new AxisCalibration( a.name, a.unit, a.scale * spatialDownScales[ l - 1 ][ 1 ] );
				}
				else if ( a.name.equals( AxisCalibration.Z ) )
				{
					axes[ l ][ i ] = new AxisCalibration( a.name, a.unit, a.scale * spatialDownScales[ l - 1 ][ 2 ] );
				}
				else
				{
					//re-use the base layer definition of the (non-spatial) axis (as here happens no down-scaling)
					axes[ l ][ i ] = axes[ 0 ][ i ];
				}
			}
		}

		AffineTransform3D baseLayerTransform = new AffineTransform3D(); //identity matrix
		return create( name, pixelType, baseLayerDims, axes, spatialDownScales, baseLayerTransform, null );
	}

	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > create(
			String name,
			T pixelType,
			long[] dimsAtBaseLevel,
			AxisCalibration[][] axesPerLevel,
			double[][] spatialDownScalesPerLevel, //x,y[,z] order of axis is assumed
			AffineTransform3D transformAtBaseLevel,
			Omero omero ) // can be null
	{
		assert axesPerLevel.length == spatialDownScalesPerLevel.length + 1
				: "Number of spatialDownScales must be one less than number of axes calibrations.";

		AffineTransform3D[] transforms = new AffineTransform3D[ axesPerLevel.length ];
		//
		//copy the base layer as is
		transforms[ 0 ] = transformAtBaseLevel.copy();
		//
		//create the downscaled layers
		for ( int l = 0; l < spatialDownScalesPerLevel.length; l++ )
		{
			AffineTransform3D levelT = new AffineTransform3D();
			levelT.scale( spatialDownScalesPerLevel[ l ][ 0 ], spatialDownScalesPerLevel[ l ][ 1 ],
					spatialDownScalesPerLevel[ l ].length > 2 ? spatialDownScalesPerLevel[ l ][ 2 ] : 1.0 );
			//TODO: apply translate!!
			transforms[ l + 1 ] = transformAtBaseLevel.copy().concatenate( levelT );
		}

		PyramidContents.Builder< T > builder = PyramidContents.builder();
		builder.name( name )
				.type( pixelType )
				.axesPerLevel( axesPerLevel )
				.transforms( transforms )
				.omero( omero );

		//create only the base-level image
		final int[] cellsSizes = new int[ axesPerLevel[ 0 ].length ];
		for ( int i = 0; i < cellsSizes.length; i++ )
			cellsSizes[ i ] = isSpatialAxis( axesPerLevel[ 0 ][ i ] ) ? 100 : 1;

		//final CellLoader<T> cellLoader = cell -> { /* no change to zero-initiated memory */ };
		CachedCellImg< T, ? > baseImg = new ReadOnlyCachedCellImgFactory().create(
				dimsAtBaseLevel,
				pixelType,
				cell -> { /* no change to zero-initiated memory */ },
				ReadOnlyCachedCellImgOptions.options()
						.cellDimensions( cellsSizes )
						.cacheType( CacheOptions.CacheType.BOUNDED )
						.maxCacheSize( 10 ) //keep only up to 10 cells, otherwise use the (empty cell) loader
		);

		CachedCellImg< T, ? >[] imgs = new CachedCellImg[ axesPerLevel.length ];
		for ( int l = 0; l < imgs.length; l++ )
			imgs[ l ] = baseImg;
		builder.cachedCellImgs( imgs );

		return builder.build();
	}
}
