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
package ome.zarr.imglib2.write;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import ome.zarr.imglib2.metadata.AxisCalibration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.exceptions.AlreadyOccupiedException;

/**
 * In-memory implementation of {@link PyramidSaver} that collects pixel data into an
 * own {@link PyramidContents}. The {@link PyramidContents} is exposed with public API
 * so that it can be either written directly, or (and especially) read and displayed
 * as if it was coming from {@link PyramidBackend#read(URI)}.
 * <p>
 * Being a {@link PyramidSaver}, it can be used interchangeably with a caller's write
 * pipeline. The exactly same pipeline can thus write to either persistent storage or
 * RAM. Caller "only" needs to be very careful in the later case. As a consequence,
 * for example, the {@link #initEmptyContainer()} is a no-op as there is no real
 * container to initialize. Indeed, this class is not producing any real OME-Zarr.
 * <p>
 * {@link #initEmptyMultiscales} allocates one {@link net.imglib2.cache.img.DiskCachedCellImg}
 * per resolution level. This implementation provides that evicted chunks are swapped
 * to a temp directory and restored on re-access, so written data is never lost — correct
 * behavior for a write target. This is a difference compared to {@link PyramidContents}
 * that come from {@link PyramidBackend#read(URI)}, which looses any changes made to evicted
 * chunks. Here, the underlying disk-caching machinery can be configured by passing a custom
 * {@link DiskCachedCellImgOptions} at construction, includes also the configuration of the
 * temporary files used for chunks mem-disk swapping.
 * <p>
 * Note again, the returned {@link PyramidContents} can be wrapped into a
 * {@code PyramidalDataset} or {@code PyramidalBdv} for display in ImageJ or BDV.
 * Direct pixel writes to its arrays are also possible, bypassing {@link #writeRegion}.
 *
 * @param <T> pixel type
 */
public class InMemoryPyramidSaver< T extends NativeType< T > & RealType< T > > implements PyramidSaver< T >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final DiskCachedCellImgOptions diskOpts;

	/*
	 * Populated by initEmptyMultiscales(); null before that call.
	 * writeRegion() requires it to be non-null.
	 */
	private PyramidContents< T > data;

	public InMemoryPyramidSaver()
	{
		this( DiskCachedCellImgOptions.options() );
	}

	public InMemoryPyramidSaver( final DiskCachedCellImgOptions diskOpts )
	{
		this.diskOpts = diskOpts;
	}

	/**
	 * No-op: there is no container concept for an in-memory store, it is not
	 * building any real OME-Zarr anywhere. The method only logs an informational
	 * message.
	 */
	@Override
	public void initEmptyContainer()
	{
		logger.info( "InMemoryPyramidSaver.initEmptyContainer() is a no-op for in-memory storage." );
	}

	/**
	 * Allocates one {@link net.imglib2.cache.img.DiskCachedCellImg} per resolution
	 * level, copying shape and calibration from {@code pyramidContents}, and cell/chunk
	 * sizes from {@code opts}. The {@code pyramidContents.asImg(level)} must return valid
	 * objects for each resolution level. From these "image objects", the image shape/geometry
	 * is taken but their pixel arrays are not touched/read, so the "image objects" need not
	 * have their pixel arrays available and populated (consider using {@link PyramidContentsUtils#create()}
	 * methods).
	 * <p>
	 * On return from this method, the underlying *new* {@link PyramidContents} is immediately
	 * accessible via {@link #getPyramidContents()}. Indeed, this method isn't keeping the
	 * reference to the provided {@code pyramidContents}, it builds a new one for itself.
	 *
	 * @param path This is ignored, and can even be null.
	 * @param pyramidContents defines the shape and calibration of the pyramid; pixel
	 *                        arrays are not read and need not be populated but they
	 *                        *must* exist for every resolution level (as the data shape
	 *                        is taken from them)
	 * @param opts only the chunk layout is considered, consider
	 *             using {@link OmeZarrWritingOptions#defaultOptionsFor(PyramidContents)}
	 *             for auto-computed defaults
	 * @throws AlreadyOccupiedException if this method is called more than once on the same
	 * instance, which is because this Saver is not capable of saving several multiscales
	 * in one OME-Zarr, which is because it is not building any OME-Zarr explicitly
	 */
	@Override
	public void initEmptyMultiscales(
			final String path,
			final PyramidContents< T > pyramidContents,
			final OmeZarrWritingOptions opts ) throws IOException
	{
		if ( data != null )
			throw new AlreadyOccupiedException( "This saver cannot store more than one multiscales (PyramidContents)." );

		final int[][] chunkShapes = opts.getChunkShapePerLevel();
		final int nLevels = pyramidContents.numResolutionLevels();

		final DiskCachedCellImg< T, ? >[] imgs = new DiskCachedCellImg[ nLevels ];
		for ( int level = 0; level < nLevels; level++ )
		{
			// NB: DiskCachedCellImgOptions is immutable and uses copy-on-write with every setter,
			//     so levelOpts is used either shared or as a dedicated modified copy
			final DiskCachedCellImgOptions levelOpts = ( chunkShapes != null && chunkShapes[ level ] != null )
					? diskOpts.cellDimensions( chunkShapes[ level ] )
					: diskOpts;

			imgs[ level ] = new DiskCachedCellImgFactory<>( pyramidContents.type, levelOpts )
					.create( pyramidContents.asImg( level ).dimensionsAsLongArray() );
		}

		data = PyramidContents.< T >builder()
				.name( pyramidContents.name )
				.type( pyramidContents.type )
				.transforms( pyramidContents.transforms )
				.cachedCellImgs( Cast.unchecked( imgs ) )
				.axesPerLevel( pyramidContents.axesPerLevel )
				.omero( pyramidContents.omero )
				.build();
	}

	/**
	 * Copies pixels from {@code region} into the backing store at the given
	 * resolution level, using the region's own min/max coordinates to locate the
	 * destination inside the level array.
	 *
	 * @throws IllegalStateException if {@link #initEmptyMultiscales} has not been called
	 */
	@Override
	public void writeRegion( final RandomAccessibleInterval< T > region, final int level )
			throws IOException
	{
		if ( data == null )
			throw new IllegalStateException( "initEmptyMultiscales() must be called before writeRegion()." );

		final RandomAccessibleInterval< T > dest = Views.interval( data.asImg( level ), region );
		LoopBuilder.setImages( region, dest ).forEachPixel( ( src, tgt ) -> tgt.set( src ) );
	}

	/**
	 * Returns the {@link PyramidContents} backed by the in-memory
	 * {@link net.imglib2.cache.img.DiskCachedCellImg}s, or {@code null} if
	 * {@link #initEmptyMultiscales} has not been called yet.
	 */
	public PyramidContents< T > getPyramidContents()
	{
		return data;
	}
}
