/*-
 * #%L
 * OME-Zarr reader based on imglib2
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

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.exceptions.AlreadyOccupiedException;
import ome.zarr.imglib2.exceptions.StoreAccessException;

import java.io.IOException;

/**
 * Progressive-writing API for OME-Zarr multiscales data.
 * <p>
 * Methods must be called in order:
 * <ol>
 *   <li>{@link #initEmptyContainer()} — write the top-level OME-Zarr scaffold (metadata only).</li>
 *   <li>{@link #initEmptyMultiscales(String, PyramidContents, OmeZarrWritingOptions)} — write the
 *       multiscales group skeleton for one image (metadata only). May be called more than once
 *       for multiple multiscales groups within the same container.</li>
 *   <li>{@link #writeRegion(RandomAccessibleInterval, int)} — write pixel data for one region
 *       at one resolution level. Typically called repeatedly until all regions and levels
 *       are covered.</li>
 * </ol>
 * <p>
 * Each completed step leaves the store in a valid, partially-filled OME-Zarr state.
 * Steps are not atomic and do not lock the store. Recovering or reconfiguring a
 * {@code PyramidSaver} from a partially written store is not supported.
 * <p>
 * All methods write immediately — there is no intermediate buffer and no {@code flush()}.
 * Pixel memory is allocated and owned entirely by the caller.
 * <p>
 * Implementing objects are constructed with a URI parameter (where applicable): one
 * instance per target store, analogous to the existing backend readers
 * ({@code ZarrJavaPyramidBackend}, {@code N5PyramidBackend}).
 * <p>
 * {@code PyramidSaver} provides no downsampling. The caller is responsible for producing
 * lower-resolution levels, using e.g. {@code PyramidSaverUtils} utility methods built on
 * imglib2's {@code Views.resample()}.
 *
 * @param <T> pixel type, must match the type of {@link PyramidContents} passed to
 *            {@link #initEmptyMultiscales} and the {@link RandomAccessibleInterval}s
 *            passed to {@link #writeRegion}
 */
public interface PyramidSaver< T extends NativeType< T > & RealType< T > >
{
	/**
	 * Writes the top-level OME-Zarr scaffold: group structure and top-level metadata,
	 * no pixel data. Must be called before {@link #initEmptyMultiscales}.
	 * <p>
	 * The target URI is fixed at construction time; no URI is accepted here.
	 * <p>
	 * TODO: should this method be part of {@code PyramidSaver} at all? It concerns
	 * only the container level, not the multiscales pyramid itself. Kept here for now
	 * to keep the full write sequence in one interface.
	 *
	 * @throws AlreadyOccupiedException if the target location already contains OME-Zarr data
	 * @throws StoreAccessException if the accessing or writing to the store experienced any issue
	 */
	void initEmptyContainer();

	/*
	 * Recovering or reconfiguring a PyramidSaver from a partially written OME-Zarr
	 * is not supported; the following interface methods are therefore commented out.
	 *
	 * void initFromExistingContainer( URI );
	 * void initFromExistingMultiscales( String path );
	 */

	/**
	 * Writes a skeleton OME-Zarr {@code multiscales} group at {@code path}: all
	 * metadata (axes, coordinate transforms, chunk layout, compression) but no pixel
	 * data. {@link #writeRegion} requires this to have been called for the same
	 * {@code path} first; calling {@link #writeRegion} without it will fail.
	 * <p>
	 * Shape, pyramid layout, and writing options are fixed after this call —
	 * it is not possible for some chunks to be written with compression A and
	 * others with B.
	 *
	 * @param path relative path within the container, e.g. {@code "/"} for a
	 *             single-image store or an HCS well path such as {@code "A/1/0"}
	 * @param pc   defines the shape and calibration of the pyramid; pixel arrays
	 *             need not be populated
	 * @param opts chunk layout, compression, sharding, and optional annotations;
	 *             use {@link OmeZarrWritingOptions#defaultOptionsFor(PyramidContents)}
	 *             for auto-computed defaults
	 * @throws AlreadyOccupiedException if {@code path} already exists in the container
	 * @throws StoreAccessException if the accessing or writing to the store experienced any issue
	 */
	void initEmptyMultiscales( String path, PyramidContents< T > pc, OmeZarrWritingOptions opts );

	/**
	 * Convenience overload using {@link OmeZarrWritingOptions#defaultOptionsFor(PyramidContents)}.
	 *
	 * @see #initEmptyMultiscales(String, PyramidContents, OmeZarrWritingOptions)
	 */
	default void initEmptyMultiscales( final String path, final PyramidContents< T > pc )
	{
		initEmptyMultiscales( path, pc, OmeZarrWritingOptions.defaultOptionsFor( pc ) );
	}

	/**
	 * Writes the pixels of {@code region} into the store at the given resolution
	 * {@code level}. The region's {@link net.imglib2.Interval} min/max coordinates
	 * determine where within the full array the data lands; a zero-based interval
	 * (min&nbsp;==&nbsp;0 in every dimension) is valid only for a single write that
	 * covers the entire array at that level.
	 * <p>
	 * {@link #initEmptyMultiscales} must have been called for the target path before
	 * any {@code writeRegion} call.
	 *
	 * @param region pixel data to write, with correct min/max coordinates;
	 *               all dimensions (including time and channel) are encoded in the interval
	 * @param level  resolution level index (0 = highest resolution)
	 * @throws IllegalStateException if {@link #initEmptyMultiscales} has not been called already
	 * @throws StoreAccessException if the accessing or writing to the store experienced any issue
	 */
	void writeRegion( RandomAccessibleInterval< T > region, int level );
}
