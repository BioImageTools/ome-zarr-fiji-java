/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ome.zarr.fiji.bdv;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Invalidate;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import org.scijava.AbstractContextual;
import org.scijava.Context;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.util.volatiles.VolatileView;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;

public class PyramidalBdv< T extends NativeType< T > & RealType< T > > extends AbstractContextual implements Pyramidal
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final PyramidContents< T > contents;

	private final List< SourceAndConverter< T > > sources;

	private final RandomAccessibleInterval< ? >[] volatileImgs;

	public PyramidalBdv( final Context context, final PyramidContents< T > contents )
	{
		this.contents = contents;
		volatileImgs = createVolatileImgs( contents );
		sources = initSourceAndConverters( contents, Cast.unchecked( volatileImgs ) );
		setContext( context );
	}

	@Override
	public PyramidContents< T > getPyramidContents()
	{
		return contents;
	}

	/**
	 * @return a list of BigDataViewer sources, representing a 5D (XYZCT) multi-resolution image, one source for each channel of the dataset.
	 * 	 The sources provide nested volatile versions. The sources are
	 * 	 multi-resolution, reflecting the resolution pyramid of the OME-Zarr.
	 */
	public List< SourceAndConverter< T > > asSources()
	{
		return sources;
	}

	public String getName()
	{
		return contents.name();
	}

	/**
	 * Drops every chunk loaded for these sources, volatile and not, so a viewer
	 * reads the storage again on its next repaint.
	 * <p>
	 * The non-volatile images are those of {@link #getPyramidContents()},
	 * so this also drops what e.g. an ImageJ window on these contents have loaded.
	 */
	@Override
	public void invalidateCachedPixels()
	{
		boolean unreachable = false;
		for ( final RandomAccessibleInterval< ? > volatileImg : volatileImgs )
		{
			final Invalidate< ? > cache = volatileCacheOf( volatileImg );
			if ( cache != null )
				cache.invalidateAll();
			else
				unreachable = true;
		}
		if ( unreachable )
			logger.info( "Cannot reach the BigDataViewer cache of {}, so a reload may not show in BigDataViewer.", getName() );
	}

	@SuppressWarnings( "unchecked" )
	private static < T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > >
			List< SourceAndConverter< T > > initSourceAndConverters( final PyramidContents< T > contents,
					final RandomAccessibleInterval< V >[] volatileImgs )
	{
		final int nLevels = contents.numResolutionLevels();
		final int numChannels = contents.numChannels();
		final T type = contents.type();
		final AffineTransform3D[] mipmapTransforms = new AffineTransform3D[ nLevels ];
		Arrays.setAll( mipmapTransforms, contents::transform );
		final VoxelDimensions voxelDimensions = voxelDimensions( contents );

		final V volatileType = volatileImgs[ 0 ].getType();

		final RandomAccessibleInterval< T >[][] levelToChannels = new RandomAccessibleInterval[ nLevels ][];
		Arrays.setAll( levelToChannels, level -> splitInputStackIntoSourceStacks( contents, contents.asImg( level ) ) );

		final RandomAccessibleInterval< V >[][] levelToVolatileChannels = new RandomAccessibleInterval[ nLevels ][];
		Arrays.setAll( levelToVolatileChannels, level -> splitInputStackIntoSourceStacks( contents, volatileImgs[ level ] ) );

		final String[] channelLabels = contents.channelLabels();
		final List< SourceAndConverter< T > > sources = new ArrayList<>( numChannels );
		for ( int channelNumber = 0; channelNumber < numChannels; channelNumber++ )
		{
			final int channel = channelNumber;
			final String channelLabel = channelLabels[ channelNumber ];
			final RandomAccessibleInterval< T >[] mipmapImgs = new RandomAccessibleInterval[ nLevels ];
			Arrays.setAll( mipmapImgs, level -> levelToChannels[ level ][ channel ] );
			final RandomAccessibleInterval< V >[] mipmapVolatileImgs = new RandomAccessibleInterval[ nLevels ];
			Arrays.setAll( mipmapVolatileImgs, level -> levelToVolatileChannels[ level ][ channel ] );
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile = new RandomAccessibleIntervalMipmapSource4D<>(
					mipmapVolatileImgs, volatileType, mipmapTransforms, voxelDimensions, channelLabel, true );
			final RandomAccessibleIntervalMipmapSource4D< T > source4D = new RandomAccessibleIntervalMipmapSource4D<>(
					mipmapImgs, type, mipmapTransforms, voxelDimensions, channelLabel, true );
			final SourceAndConverter< T > sourceAndConverter = createSourceAndConverter( source4D, source4DVolatile );
			sources.add( sourceAndConverter );
			BigDataViewer.createConverterSetup( sourceAndConverter, channelNumber );
		}
		return sources;
	}

	/**
	 * Derives the BigDataViewer {@link VoxelDimensions} (x, y, z spacing and the
	 * spatial unit) from the full-resolution axis calibrations. Missing spatial
	 * axes default to a spacing of 1, since BigDataViewer always expects a 3D
	 * voxel size.
	 */
	private static VoxelDimensions voxelDimensions( final PyramidContents< ? > contents )
	{
		double xScale = 1.0;
		double yScale = 1.0;
		double zScale = 1.0;
		String unit = "";
		for ( final AxisCalibration axis : contents.axes( 0 ) )
		{
			switch ( axis.name )
			{
			case AxisCalibration.X:
				xScale = axis.scale;
				unit = axis.unit;
				break;
			case AxisCalibration.Y:
				yScale = axis.scale;
				break;
			case AxisCalibration.Z:
				zScale = axis.scale;
				break;
			default:
				break; // channel / time axes carry no voxel size
			}
		}
		return new FinalVoxelDimensions( unit, xScale, yScale, zScale );
	}

	/**
	 * Wraps each resolution level's {@link CachedCellImg} as a volatile view.
	 */
	@SuppressWarnings( "unchecked" )
	private static < T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > >
			RandomAccessibleInterval< V >[] createVolatileImgs( final PyramidContents< T > contents )
	{
		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		final RandomAccessibleInterval< V >[] volatileImgs = new RandomAccessibleInterval[ contents.numResolutionLevels() ];
		for ( int level = 0; level < contents.numResolutionLevels(); level++ )
			volatileImgs[ level ] = VolatileViews.wrapAsVolatile( contents.asImg( level ), sharedQueue );
		return volatileImgs;
	}

	/**
	 * The cache behind a volatile view that {@link VolatileViews#wrapAsVolatile}
	 * made of a {@link CachedCellImg}.
	 *
	 * @return the cache, or {@code null}, if the volatile image does not wrap a {@link VolatileCachedCellImg}
	 */
	private static Invalidate< ? > volatileCacheOf( final RandomAccessibleInterval< ? > volatileImg )
	{
		if ( volatileImg instanceof VolatileView )
		{
			final Object img = ( ( VolatileView< ?, ? > ) volatileImg ).getVolatileViewData().getImg();
			if ( img instanceof VolatileCachedCellImg )
				return ( ( VolatileCachedCellImg< ?, ? > ) img ).getCache();
		}
		return null;
	}

	private static < T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > >
			SourceAndConverter< T > createSourceAndConverter( final RandomAccessibleIntervalMipmapSource4D< T > source4D,
					final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile )
	{
		final Converter< V, ARGBType > converterVolatile = BigDataViewer.createConverterToARGB( source4DVolatile.getType() );
		final Converter< T, ARGBType > converter = BigDataViewer.createConverterToARGB( source4D.getType() );
		final SourceAndConverter< V > sourceAndConverterVolatile =
				BigDataViewer.wrapWithTransformedSource( new SourceAndConverter<>( source4DVolatile, converterVolatile ) );
		return new SourceAndConverter<>( source4D, converter, sourceAndConverterVolatile );
	}

	/**
	 * Splits a single multichannel image stack into one {@link RandomAccessibleInterval} per channel,
	 * and ensures every result has XYZ and T dimensions (adding singleton axes where absent).
	 */
	@SuppressWarnings( "unchecked" )
	private static < T > RandomAccessibleInterval< T >[] splitInputStackIntoSourceStacks( final PyramidContents< ? > contents,
			final RandomAccessibleInterval< T > img )
	{
		final int numChannels = contents.numChannels();
		final boolean cAxisPresent = contents.hasAxis( AxisCalibration.C );
		final boolean zAxisPresent = contents.hasAxis( AxisCalibration.Z );
		final boolean timeAxisPresent = contents.hasAxis( AxisCalibration.T );

		final RandomAccessibleInterval< T >[] sourceStacks = new RandomAccessibleInterval[ numChannels ];

		// If there is a channel dimension, slice img along channel dimension.
		if ( cAxisPresent )
			Arrays.setAll( sourceStacks, channel -> Views.hyperSlice( img, contents.axisIndex( AxisCalibration.C ), channel ) );
		else
			sourceStacks[ 0 ] = img;

		// If there is no Z dimension, augment the sourceStacks by a Z dimension.
		if ( !zAxisPresent )
			Arrays.setAll( sourceStacks, channel -> Views.addDimension( sourceStacks[ channel ], 0, 0 ) );

		// If there is no T dimension, augment the sourceStacks by a T dimension.
		if ( !timeAxisPresent )
			Arrays.setAll( sourceStacks, channel -> Views.addDimension( sourceStacks[ channel ], 0, 0 ) );

		// If at this point the dim order is XYTZ (because there was only a T axis, and we appended a Z axis after that), permute to XYZT
		if ( !zAxisPresent && timeAxisPresent )
			Arrays.setAll( sourceStacks, channel -> Views.permute( sourceStacks[ channel ], 2, 3 ) );

		return sourceStacks;
	}

	// -- Reference counting, similar to what AbstractData does --

	@SuppressWarnings( "unused" )
	@Parameter( required = false )
	private ObjectService objectService;

	private int refs = 0;

	public void incrementReferences()
	{
		logger.debug( "incrementReferences" );
		refs++;
		if ( refs == 1 )
			register();
	}

	public void decrementReferences()
	{
		logger.debug( "decrementReferences" );
		if ( refs == 0 )
		{
			throw new IllegalStateException( "decrementing reference count when it is already 0" );
		}
		refs--;
		if ( refs == 0 )
			unregister();
	}

	/**
	 * Client code can monitor how many BDVs are showing this PyramidalBdv.
	 * @return Internal references count.
	 */
	public int getReferencesCount()
	{
		return refs;
	}

	/**
	 * Called the first time the reference count is incremented.
	 */
	private void register()
	{
		if ( objectService != null )
			objectService.addObject( this );
	}

	/**
	 * Called when the reference count is decremented to zero.
	 */
	private void unregister()
	{
		if ( objectService != null )
			objectService.removeObject( this );
	}
}
