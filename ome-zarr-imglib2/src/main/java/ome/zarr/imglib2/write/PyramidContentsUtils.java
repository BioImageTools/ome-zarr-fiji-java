package ome.zarr.imglib2.write;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.optional.CacheOptions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;

import java.util.Arrays;

public class PyramidContentsUtils
{
	public static boolean isSpatialAxis( final AxisCalibration axis )
	{
		return ( axis.name.equals( AxisCalibration.X ) )
				|| ( axis.name.equals( AxisCalibration.Y ) )
				|| ( axis.name.equals( AxisCalibration.Z ) );
	}

	/**
	 * Constructs a scaffold/skeleton {@link PyramidContents} to define everything but the
	 * actual (pixel) data. The returned objects are pre-fated for the {@link PyramidSaver}s.
	 * <p>
	 * The returned objects offer pixel data whenever the underlying {@link CachedCellImg} pixel
	 * arrays are touched. On the other hand, as long as the pixels are not touched, the returned
	 * objects are true scaffolds with no allocated pixels (since the pixels arrays are only
	 * allocated on-demand). It is, however, not practical to use the returned objects for any
	 * serious work because only a small number of pixel arrays is allowed to exist in the main
	 * memory, and eviction leads to data loss (which, again, would deliberately and unchangeably
	 * happen very early).
	 * <p>
	 * This is a convenience short-cut for the {@link #create(String, NativeType, long[], long, long, AxisCalibration[][], double[][], AffineTransform3D, Omero)}
	 * that assumes the same downscaling factors (per pyramidal level) for all spatial axes.
	 *
	 * @param name                       Name for the created {@link PyramidContents#name}.
	 * @param pixelType                  Pixel type for the constructed {@link PyramidContents#cachedCellImgs}
	 * @param baseLevelXYZdims           Co-defines the pixel array {@link PyramidContents#cachedCellImgs} geometry/shape.
	 * @param channels                   Co-defines the pixel array geometry/shape.
	 * @param timePoints                 Co-defines the pixel array geometry/shape.
	 * @param baseLevelAxes              Defines the axes, the pixel array's "axes"/dimensions will be in the same order as this array.
	 * @param spatialIsotropicDownScales List of down-scaling factors for all spatial axes.
	 * @return Scaffold/skeleton content with no sensible pixels.
	 */
	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > create(
			String name,
			T pixelType,
			long[] baseLevelXYZdims,
			long channels,
			long timePoints,
			AxisCalibration[] baseLevelAxes,
			double[] spatialIsotropicDownScales )
	{
		int spatialDimsCnt = 0;
		for ( AxisCalibration axis : baseLevelAxes )
			spatialDimsCnt += isSpatialAxis( axis ) ? 1 : 0;
		assert spatialDimsCnt > 0: "No spatial axis discovered in baseLevelAxes definition.";

		double[][] spatialDownScales = new double[ spatialIsotropicDownScales.length ][ spatialDimsCnt ];
		for ( int l = 0; l < spatialIsotropicDownScales.length; l++ )
			Arrays.fill( spatialDownScales[ l ], spatialIsotropicDownScales[ l ] );

		return create( name, pixelType, baseLevelXYZdims, channels, timePoints, baseLevelAxes, spatialDownScales );
	}

	/**
	 * See {@link #create(String, NativeType, long[], long, long, AxisCalibration[], double[])}.
	 * <p>
	 * This is a convenience short-cut for the {@link #create(String, NativeType, long[], long, long, AxisCalibration[][], double[][], AffineTransform3D, Omero)}
	 * that defines the downscaling factors (per pyramidal level) per spatial axes.
	 */
	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > create(
			String name,
			T pixelType,
			long[] baseLevelXYZdims,
			long channels,
			long timePoints,
			AxisCalibration[] baseLevelAxes,
			double[][] spatialDownScales ) //x,y[,z] order of axis is assumed
	{
		AxisCalibration[][] axes = new AxisCalibration[ spatialDownScales.length + 1 ][ baseLevelAxes.length ];
		//
		//copy the base level as is
		for ( int i = 0; i < baseLevelAxes.length; ++i )
		{
			final AxisCalibration a = baseLevelAxes[ i ];
			axes[ 0 ][ i ] = new AxisCalibration( a.name, a.unit, a.scale );
		}
		//create the downscaled levels
		for ( int l = 1; l < axes.length; l++ )
		{
			for ( int i = 0; i < baseLevelAxes.length; ++i )
			{
				final AxisCalibration a = baseLevelAxes[ i ];
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
					//re-use the base level definition of the (non-spatial) axis (as here happens no down-scaling)
					axes[ l ][ i ] = axes[ 0 ][ i ];
				}
			}
		}

		AffineTransform3D baseLevelTransform = new AffineTransform3D(); //identity matrix
		return create( name, pixelType, baseLevelXYZdims, channels, timePoints, axes, spatialDownScales, baseLevelTransform, null );
	}

	/**
	 * See {@link #create(String, NativeType, long[], long, long, AxisCalibration[], double[])}.
	 * <p>
	 * This is the fully-configurable workhorse that defines everything for the {@link PyramidContents}
	 * except for (sensible) pixels. An initial transformation is assumed that gets copied, one for each
	 * pyramidal level, and adjusted accordingly to 'spatialDownScalesPerLevel'.
	 */
	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > create(
			String name,
			T pixelType,
			long[] xyzDimsAtBaseLevel,
			long channels,
			long timePoints,
			AxisCalibration[][] axesPerLevel,
			double[][] spatialDownScalesPerLevel, //x,y[,z] order of axis is assumed
			AffineTransform3D transformAtBaseLevel,
			Omero omero ) // can be null
	{
		assert axesPerLevel.length >= 1: "There has to be at least one definition of axes.";

		assert xyzDimsAtBaseLevel.length >= 2: "Only (spatial) 2D or 3D images are supported, timePoints and channel are not counted here.";
		assert channels >= 0: "Number of channels cannot be negative.";
		assert timePoints >= 0: "Number of time points cannot be negative.";

		assert axesPerLevel.length == spatialDownScalesPerLevel.length + 1
				: "Number of spatialDownScales must be one less than number of axes calibrations.";

		AffineTransform3D[] transforms = new AffineTransform3D[ axesPerLevel.length ];
		//
		//copy the base level as is
		transforms[ 0 ] = transformAtBaseLevel.copy();
		//
		//create the downscaled levels
		for ( int l = 0; l < spatialDownScalesPerLevel.length; l++ )
		{
			AffineTransform3D levelT = new AffineTransform3D();
			levelT.scale( spatialDownScalesPerLevel[ l ][ 0 ], spatialDownScalesPerLevel[ l ][ 1 ],
					spatialDownScalesPerLevel[ l ].length > 2 ? spatialDownScalesPerLevel[ l ][ 2 ] : 1.0 );
			//TODO: apply translate!!
			transforms[ l + 1 ] = transformAtBaseLevel.copy().concatenate( levelT );
			//TODO: check how transforms are prepared for BDV
		}

		final PyramidContents.Builder< T > builder = PyramidContents.builder();
		builder.name( name )
				.type( pixelType )
				.axesPerLevel( axesPerLevel )
				.transforms( transforms )
				.omero( omero );

		//create only the base-level image
		int dims = xyzDimsAtBaseLevel.length;
		dims += channels >= 1 ? 1 : 0;
		dims += timePoints >= 1 ? 1 : 0;
		assert dims == axesPerLevel[ 0 ].length
				: "The number of defined axes must correspond to spatial dimensions, and presence of channels and/or time points.";

		final long[] dimsAtBaseLevel = new long[ dims ];
		dims = 0;
		for ( AxisCalibration axis : axesPerLevel[ 0 ] )
		{
			if ( axis.name.equals( AxisCalibration.X ) )
			{
				dimsAtBaseLevel[ dims++ ] = xyzDimsAtBaseLevel[ 0 ];
			}
			else if ( axis.name.equals( AxisCalibration.Y ) )
			{
				dimsAtBaseLevel[ dims++ ] = xyzDimsAtBaseLevel[ 1 ];
			}
			else if ( axis.name.equals( AxisCalibration.Z ) )
			{
				dimsAtBaseLevel[ dims++ ] = xyzDimsAtBaseLevel[ 2 ];
			}
			else if ( axis.name.equals( AxisCalibration.C ) )
			{
				dimsAtBaseLevel[ dims++ ] = channels;
			}
			else if ( axis.name.equals( AxisCalibration.T ) )
			{
				dimsAtBaseLevel[ dims++ ] = timePoints;
			}
			else
			{
				assert false: "Detected unknow axis specification.";
			}
		}

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

	/**
	 * Return axes/dimensions permutation that would re-order the {@link PyramidContents#cachedCellImgs}
	 * to start with axis X, then Y, then possibly Z if present, then C (channel)
	 * if present, and then T (time) if present.
	 * <p>
	 * The method requires that XY axes are always present -- the images are at least 2D.
	 *
	 * @param pc Input with {@link PyramidContents#axesPerLevel} from which the axes names are discovered.
	 * @return Permutation as array: Source dimension 't' should be moved to 'permutation[t]'.
	 */
	public static < T extends NativeType< T > & RealType< T > > int[] axesPermutationForXYZCT(
			final PyramidContents< T > pc )
	{
		//check axes presence and possible positions within the pixel array/tensor
		final int axisIndexX = pc.axisIndex( AxisCalibration.X );
		final int axisIndexY = pc.axisIndex( AxisCalibration.Y );
		final int axisIndexZ = pc.axisIndex( AxisCalibration.Z );
		final int axisIndexChannel = pc.axisIndex( AxisCalibration.C );
		final int axisIndexTime = pc.axisIndex( AxisCalibration.T );

		// arbitrary decision to request 2D as minimum, but on the other hand,
		// it warrants that there are always at least two dimensions...
		assert axisIndexX != -1: "The x-axis must be present.";
		assert axisIndexY != -1: "The y-axis must be present.";

		final int[] axesPermutation = new int[ pc.asImg().numDimensions() ];
		axesPermutation[ axisIndexX ] = 0;
		axesPermutation[ axisIndexY ] = 1;
		int nextFreeDim = 2;
		if ( axisIndexZ > -1 )
			axesPermutation[ axisIndexZ ] = nextFreeDim++;
		if ( axisIndexChannel > -1 )
			axesPermutation[ axisIndexChannel ] = nextFreeDim++;
		if ( axisIndexTime > -1 )
			axesPermutation[ axisIndexTime ] = nextFreeDim++;

		assert nextFreeDim == axesPermutation.length: "Encountered unrecognized axis.";
		return axesPermutation;
	}

	/**
	 * Source dimension index 't' should land at 'permutation[t]'. Or, said differently,
	 * 'permutation[x]' is the target position for the axis currently at source position 'x'.
	 */
	public static < T extends NativeType< T > & RealType< T > > RandomAccessibleInterval< T > permutatedAxesView(
			RandomAccessibleInterval< T > img,
			int[] permutation )
	{
		assert permutation.length == img.numDimensions(): "Permutation length is different from image's number of dimensions.";

		RandomAccessibleInterval< T > out = img;
		int[] p = permutation.clone();
		for ( int i = 0; i < p.length; i++ )
		{
			// skip when the dimension happens to be where it is expected to be
			if ( p[ i ] == i )
				continue;
			// seek the axis that belongs at slot i
			int x = i;
			while ( p[ x ] != i )
				x++;
			out = Views.permute( out, i, x );
			// update the permutation to keep it synchronized with the current order of dimensions
			int tmp = p[ i ];
			p[ i ] = p[ x ];
			p[ x ] = tmp;
		}
		return out;
	}

	public static < T extends NativeType< T > & RealType< T > > RandomAccessibleInterval< T > xyzReducedView(
			PyramidContents< T > pc,
			int resolutionLevel,
			int channel,
			int timepoint )
	{
		assert resolutionLevel >= 0: "Resolution level cannot be negative.";
		assert resolutionLevel < pc.numResolutionLevels(): "Resolution level cannot exceed available levels.";
		assert channel >= 0: "Channel position cannot be negative.";
		assert timepoint >= 0: "Time point cannot be negative.";

		// re-order first
		final int[] permutation = axesPermutationForXYZCT( pc );
		RandomAccessibleInterval< T > view = permutatedAxesView( pc.asImg( resolutionLevel ), permutation );

		// extract the channel and timepoint
		final int axisIndexChannel = pc.axisIndex( AxisCalibration.C );
		if ( axisIndexChannel > -1 )
		{
			view = Views.hyperSlice( view, permutation[ axisIndexChannel ], channel );
		}

		final int axisIndexTime = pc.axisIndex( AxisCalibration.T );
		if ( axisIndexTime > -1 )
		{
			view = Views.hyperSlice( view, permutation[ axisIndexTime ], timepoint );
		}
		return view;
	}
}
