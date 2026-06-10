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

import java.net.URI;

import org.scijava.AbstractContextual;
import org.scijava.Context;

import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.Dataset;
import net.imglib2.EuclideanSpace;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import sc.fiji.ome.zarr.pyramid.backend.PyramidBackend;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.backend.n5.N5PyramidBackend;
import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.exceptions.NonExistingResolutionLevelException;
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
public class Pyramidal5DImageDataImpl<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
		extends AbstractContextual
		implements EuclideanSpace, Pyramidal5DImageData< T >
{
	private final PyramidContents<T, V> contents;

	private final int preferredResolutionLevel;

	@Override
	public PyramidContents<T, V> getPyramidContents() {
		return contents;
	}

	@Override
	public int preferredResolutionLevel()
	{
		return preferredResolutionLevel;
	}







	private final String name;

	private final int numResolutionLevels;

	private final int numTimepoints;

	private final int numChannels;

	private final int numDimensions;

	private final T type;

	private final VoxelDimensions voxelDimensions;

	private final Omero omero;

	/**
	 * Open an OME-Zarr image with the default N5 backend.
	 *
	 * @param inputUri location of the OME-Zarr root; either a {@code file:} URI
	 *   for local datasets or an {@code http(s):} URI for remote datasets
	 */
	public Pyramidal5DImageDataImpl( final Context context, final URI inputUri )
	{
		this( context, inputUri, null );
	}

	/**
	 * Open an OME-Zarr image with the default N5 backend, downsampling to at
	 * most {@code preferredMaxWidth} pixels along x.
	 *
	 * @param inputUri location of the OME-Zarr root; either a {@code file:} URI
	 *   for local datasets or an {@code http(s):} URI for remote datasets
	 * @param preferredMaxWidth maximum width for the ImageJ dataset; if
	 *   {@code null}, the highest resolution is used
	 * @throws NoMatchingResolutionException if {@code preferredMaxWidth} is
	 *   smaller than the width of the smallest resolution level
	 */
	public Pyramidal5DImageDataImpl( final Context context, final URI inputUri, final Integer preferredMaxWidth )
	{
		this( context, new N5PyramidBackend<>( inputUri ), preferredMaxWidth );
	}

	/**
	 * Open an OME-Zarr image using the supplied {@link PyramidBackend}, using
	 * the highest available resolution for the ImageJ dataset.
	 */
	public Pyramidal5DImageDataImpl( final Context context, final PyramidBackend< T, V > backend )
	{
		this( context, backend, null );
	}

	/**
	 * Open an OME-Zarr image using the supplied {@link PyramidBackend},
	 * selecting for the ImageJ dataset the coarsest resolution level whose
	 * x-width does not exceed {@code preferredMaxWidth}. If
	 * {@code preferredMaxWidth} is {@code null}, the highest resolution is
	 * used.
	 *
	 * @throws NoMatchingResolutionException if {@code preferredMaxWidth} is
	 *   smaller than the width of the smallest resolution level
	 */
	public Pyramidal5DImageDataImpl( final Context context, final PyramidBackend< T, V > backend, final Integer preferredMaxWidth )
	{
		setContext( context );
		contents = backend.load();
		preferredResolutionLevel = selectResolutionLevel( contents.cachedCellImgs, preferredMaxWidth );

		this.name = contents.name;
		this.numResolutionLevels = contents.numResolutionLevels;
		this.numChannels = contents.numChannels;
		this.numTimepoints = contents.numTimepoints;
		this.numDimensions = contents.numDimensions;
		this.type = contents.type;
		this.voxelDimensions = contents.voxelDimensions;
		this.omero = contents.omero;
	}

	/**
	 * Returns the index of the coarsest resolution level whose x-width (index 0
	 * in imglib2 F-order) is ≤ {@code preferredMaxWidth}, or 0 when
	 * {@code preferredMaxWidth} is {@code null}.
	 */
	private static < T extends NativeType< T > & RealType< T > > int selectResolutionLevel(
			final CachedCellImg< T, ? >[] cachedCellImgs, final Integer preferredMaxWidth )
	{
		if ( preferredMaxWidth == null )
			return 0;
		int smallestWidth = Integer.MAX_VALUE;
		for ( int level = 0; level < cachedCellImgs.length; level++ )
		{
			final int width = ( int ) cachedCellImgs[ level ].dimension( 0 );
			if ( width <= preferredMaxWidth )
				return level;
			smallestWidth = Math.min( smallestWidth, width );
		}
		throw new NoMatchingResolutionException( preferredMaxWidth, smallestWidth );
	}

	// ---------------------------------------------------------------------
	// Interface implementations
	// ---------------------------------------------------------------------

	private void checkResolutionLevel( final int resolutionLevel )
	{
		if ( resolutionLevel < 0 || resolutionLevel >= numResolutionLevels )
			throw new NonExistingResolutionLevelException( resolutionLevel, numResolutionLevels );
	}

	@Override
	public PyramidalDataset asPyramidalDataset()
	{
		return new PyramidalDataset( this );
	}

	public PyramidalDataset asPyramidalDataset( final int resolutionLevel )
	{
		checkResolutionLevel( resolutionLevel );
		return new PyramidalDataset( this, resolutionLevel );
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
