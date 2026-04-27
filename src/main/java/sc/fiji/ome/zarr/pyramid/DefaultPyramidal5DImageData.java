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
package sc.fiji.ome.zarr.pyramid;

import java.util.ArrayList;
import java.util.List;

import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imglib2.EuclideanSpace;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import org.scijava.Context;

import bdv.BigDataViewer;
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.backend.PyramidBackend;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.backend.n5.N5PyramidBackend;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

/**
 * A backend-agnostic, OME-Zarr backed pyramidal 5D image that can be visualized
 * in ImageJ in various ways.
 * <p>
 * 5D refers to: x, y, z, t, channels (or simply the dimension in which all
 * images are stacked). The {@link EuclideanSpace} interface adds only
 * {@link #numDimensions()}.
 * <p>
 * The image data is produced by a {@link PyramidBackend} (e.g.
 * {@link N5PyramidBackend}); this class copies the backend's output into its
 * own state, builds the ImageJ {@link Dataset} and the BigDataViewer
 * {@link SourceAndConverter} list, and exposes everything through
 * {@link Pyramidal5DImageData}.
 *
 * @param <T> pixel type
 * @param <V> volatile pixel type
 */
public class DefaultPyramidal5DImageData<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
		implements EuclideanSpace, Pyramidal5DImageData< T >
{
	private final String name;

	private final int numResolutionLevels;

	private final int numTimepoints;

	private final int numChannels;

	private final int numDimensions;

	private final T type;

	private final V volatileType;

	private final VoxelDimensions voxelDimensions;

	private final AffineTransform3D[] transforms;

	private final Omero omero;

	private final Dataset ijDataset;

	private final List< SourceAndConverter< T > > sourceAndConverters;

	/**
	 * Open an OME-Zarr image with the default N5 backend.
	 */
	public DefaultPyramidal5DImageData( final Context context, final String inputPathAsString )
	{
		this( context, inputPathAsString, null );
	}

	/**
	 * Open an OME-Zarr image with the default N5 backend, downsampling to at
	 * most {@code preferredMaxWidth} pixels along x.
	 *
	 * @param preferredMaxWidth maximum width for the ImageJ dataset; if
	 *   {@code null}, the highest resolution is used
	 * @throws NoMatchingResolutionException if {@code preferredMaxWidth} is
	 *   smaller than the width of the smallest resolution level
	 */
	public DefaultPyramidal5DImageData( final Context context, final String inputPathAsString, final Integer preferredMaxWidth )
	{
		this( context, new N5PyramidBackend<>( inputPathAsString, preferredMaxWidth ) );
	}

	/**
	 * Open an OME-Zarr image using the supplied {@link PyramidBackend}.
	 */
	public DefaultPyramidal5DImageData( final Context context, final PyramidBackend< T, V > backend )
	{
		final PyramidContents< T, V > contents = backend.load();
		this.name = contents.name;
		this.numResolutionLevels = contents.numResolutionLevels;
		this.numChannels = contents.numChannels;
		this.numTimepoints = contents.numTimepoints;
		this.numDimensions = contents.numDimensions;
		this.type = contents.type;
		this.volatileType = contents.volatileType;
		this.voxelDimensions = contents.voxelDimensions;
		this.transforms = contents.transforms;
		this.omero = contents.omero;

		this.ijDataset = new DefaultDataset( context, contents.imgPlus );
		this.ijDataset.setName( name );
		this.ijDataset.setRGBMerged( false );

		this.sourceAndConverters = initSourceAndConverters( contents );
	}

	private List< SourceAndConverter< T > > initSourceAndConverters( final PyramidContents< T, V > contents )
	{
		final List< SourceAndConverter< T > > sources = new ArrayList<>();
		for ( int channelNumber = 0; channelNumber < numChannels; channelNumber++ )
		{
			final RandomAccessibleInterval< V >[] channelsVolatile =
					ensureOrdered4dDimensions(
							extractChannel( contents.volatileImgs, contents.channelAxisIndex, channelNumber ),
							contents.zAxisPresent, contents.timeAxisPresent );
			final RandomAccessibleInterval< T >[] channels =
					ensureOrdered4dDimensions(
							extractChannel( contents.cachedCellImgs, contents.channelAxisIndex, channelNumber ),
							contents.zAxisPresent, contents.timeAxisPresent );

			final String channelLabel = contents.channelLabels[ channelNumber ];
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile =
					new RandomAccessibleIntervalMipmapSource4D<>( channelsVolatile, volatileType, transforms, voxelDimensions, channelLabel,
							true );
			final RandomAccessibleIntervalMipmapSource4D< T > source4D =
					new RandomAccessibleIntervalMipmapSource4D<>( channels, type, transforms, voxelDimensions, channelLabel, true );

			final SourceAndConverter< T > sourceAndConverter = createSourceAndConverter( source4D, source4DVolatile );
			sources.add( sourceAndConverter );
			BigDataViewer.createConverterSetup( sourceAndConverter, channelNumber );
		}
		return sources;
	}

	/**
	 * If the channel dimension is present, hyper-slice it out at
	 * {@code channelNumber}; otherwise return the input arrays unchanged.
	 */
	private < R > RandomAccessibleInterval< R >[] extractChannel( final RandomAccessibleInterval< R >[] sourceImgs,
			final int channelAxisIndex, final int channelNumber )
	{
		final RandomAccessibleInterval< R >[] resultImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			resultImgs[ level ] = channelAxisIndex < 0
					? sourceImgs[ level ]
					: Views.hyperSlice( sourceImgs[ level ], channelAxisIndex, channelNumber );
		}
		return resultImgs;
	}

	/**
	 * Make sure images are 4D xyzt even if z and/or t are absent in the input
	 * tensor. A missing z is inserted before t; a missing t is appended.
	 */
	private < R > RandomAccessibleInterval< R >[] ensureOrdered4dDimensions( final RandomAccessibleInterval< R >[] sourceImgs,
			final boolean zAxisPresent, final boolean timeAxisPresent )
	{
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			RandomAccessibleInterval< R > img = sourceImgs[ level ];
			if ( zAxisPresent )
			{
				if ( !timeAxisPresent ) // xyz → xyzt
					img = Views.addDimension( img, 0, 0 );
				// else xyzt already ordered correctly
			}
			else
			{
				if ( timeAxisPresent ) // xyt → xyzt: insert z before t
				{
					img = Views.addDimension( img, 0, 0 );
					img = Views.permute( img, 2, 3 );
				}
				else // xy → xyzt
				{
					img = Views.addDimension( img, 0, 0 );
					img = Views.addDimension( img, 0, 0 );
				}
			}
			sourceImgs[ level ] = img;
		}
		return sourceImgs;
	}

	private SourceAndConverter< T > createSourceAndConverter( final RandomAccessibleIntervalMipmapSource4D< T > source4D,
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile )
	{
		final Converter< V, ARGBType > converterVolatile = BigDataViewer.createConverterToARGB( volatileType );
		final Converter< T, ARGBType > converter = BigDataViewer.createConverterToARGB( type );
		final SourceAndConverter< V > sourceAndConverterVolatile =
				BigDataViewer.wrapWithTransformedSource( new SourceAndConverter<>( source4DVolatile, converterVolatile ) );
		return new SourceAndConverter<>( source4D, converter, sourceAndConverterVolatile );
	}

	// ---------------------------------------------------------------------
	// Interface implementations
	// ---------------------------------------------------------------------

	@Override
	public PyramidalDataset< T > asPyramidalDataset()
	{
		return new PyramidalDataset<>( this );
	}

	@Override
	public Dataset asDataset()
	{
		return ijDataset;
	}

	@Override
	public List< SourceAndConverter< T > > asSources()
	{
		return sourceAndConverters;
	}

	@Override
	public int numChannels()
	{
		return numChannels;
	}

	@Override
	public VoxelDimensions voxelDimensions()
	{
		return voxelDimensions;
	}

	@Override
	public int numDimensions()
	{
		return numDimensions;
	}

	@Override
	public int numResolutionLevels()
	{
		return numResolutionLevels;
	}

	@Override
	public int numTimepoints()
	{
		return numTimepoints;
	}

	@Override
	public T getType()
	{
		return type;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public Omero getOmeroProperties()
	{
		return omero;
	}
}
