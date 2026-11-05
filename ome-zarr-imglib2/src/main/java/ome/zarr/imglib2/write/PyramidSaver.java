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
import ome.zarr.imglib2.exceptions.NotAMultiscaleImageException;
import ome.zarr.imglib2.exceptions.StoreAccessException;

/**
 * Progressive-writing API for OME-Zarr multiscales data.
 * <p>
 * Writing pixels always targets one multiscales group, the one the saver is
 * <em>bound</em> to. There are two ways to bind it, and both end in the same state:
 * <ul>
 *   <li>{@link #createMultiscales(String, PyramidContents, OmeZarrWritingOptions)} — create
 *       a new, empty multiscales group (metadata only, creating the store if needed);</li>
 *   <li>{@link #openMultiscales(String, NativeType)} — bind to a multiscales group that
 *       already exists, e.g. one created by an earlier session or another process.</li>
 * </ul>
 * Then {@link #writeRegion(RandomAccessibleInterval, int)} writes pixel data for one region
 * at one resolution level, typically repeatedly until all regions and levels are covered.
 * This is the envisioned/intended way of using the saver.
 * <p>
 * This interface, however, is not prohibiting the implementing classes to be re-used
 * on other (different or even the same) group of the same OME-Zarr -- just bind the
 * saver again to the other group; there is no group parameter on {@link #writeRegion}.
 * This lets several multiscales groups (e.g., HCS wells) to share the same saver.
 * <p>
 * There is deliberately no separate "create the container/store" step: the store is
 * created on demand by {@link #createMultiscales} -- during the binding.
 * <p>
 * Each completed step leaves the store in a valid, partially-filled OME-Zarr state.
 * Steps are not atomic and do not lock the store. Shape, pyramid layout and writing
 * options are those stored in the group; {@link #openMultiscales} cannot change them.
 * <p>
 * All methods write immediately — there is no intermediate buffer and no {@code flush()}.
 * Pixel memory is allocated and owned entirely by the caller.
 * <p>
 * Implementing objects are constructed with the store URI (where applicable): one
 * instance per target store, analogous to the existing backend readers
 * ({@code ZarrJavaPyramidBackend}, {@code N5PyramidBackend}).
 * <p>
 * {@code PyramidSaver} provides no downsampling. The caller is responsible for producing
 * lower-resolution levels, using e.g. {@code PyramidSaverUtils} utility methods built on
 * imglib2's {@code Views.resample()}.
 *
 * @param <T> pixel type, must match the type of the bound multiscales group and of the
 *            {@link RandomAccessibleInterval}s passed to {@link #writeRegion}
 */
public interface PyramidSaver< T extends NativeType< T > & RealType< T > >
{
	/**
	 * Creates a skeleton OME-Zarr {@code multiscales} group at {@code groupPath} and binds
	 * this saver to it: all metadata (axes, coordinate transforms, chunk layout,
	 * compression) but no pixel data. The store is created first if it does not
	 * exist yet.
	 * <p>
	 * Shape, pyramid layout, and writing options are fixed after this call —
	 * it is not possible for some chunks to be written with compression A and
	 * others with B.
	 *
	 * @param groupPath relative path within the store, e.g. {@code "/"} for a
	 *             single-image store or an HCS well path such as {@code "A/1/0"}
	 * @param pc   defines the shape and calibration of the pyramid; pixel arrays
	 *             need not be populated; if they are, their content is not considered
	 * @param opts chunk layout, compression, sharding, and optional annotations;
	 *             use {@link OmeZarrWritingOptions#defaultOptionsFor(PyramidContents)}
	 *             for auto-computed defaults
	 * @throws AlreadyOccupiedException if {@code groupPath} already holds OME-Zarr content
	 * @throws StoreAccessException if the accessing or writing to the store experienced any issue
	 */
	void createMultiscales( final String groupPath, final PyramidContents< T > pc, final OmeZarrWritingOptions opts );

	/**
	 * Convenience overload using {@link OmeZarrWritingOptions#defaultOptionsFor(PyramidContents)}.
	 *
	 * @see #createMultiscales(String, PyramidContents, OmeZarrWritingOptions)
	 */
	default void createMultiscales( final String groupPath, final PyramidContents< T > pc )
	{
		createMultiscales( groupPath, pc, OmeZarrWritingOptions.defaultOptionsFor( pc ) );
	}

	/**
	 * Binds this saver to the existing OME-Zarr {@code multiscales} group at
	 * {@code groupPath}; resolution levels and their chunk layout are read from the store.
	 * Nothing is written, the group and the store itself is not changed at this moment.
	 * <p>
	 * Only a multiscales group is accepted. A bare (single-scale) array is not, even
	 * though the readers open one as a one-level pyramid: it has no level list to
	 * write into.
	 *
	 * @param groupPath relative path of the multiscales group within the store
	 * @param type pixel type the caller will write; checked against the type stored
	 *             in the group, since {@code T} alone cannot be checked at runtime
	 * @throws NotAMultiscaleImageException if {@code groupPath} is not a multiscales group
	 * @throws IllegalArgumentException if {@code type} differs from the stored pixel type
	 * @throws StoreAccessException if the accessing of the store experienced any issue
	 */
	void openMultiscales( final String groupPath, final T type );

	/**
	 * Writes the pixels of {@code region} into the bound multiscales group at the
	 * given resolution {@code level}. The region's {@link net.imglib2.Interval} min/max
	 * coordinates determine where within the full array the data lands; a zero-based
	 * interval (min&nbsp;==&nbsp;0 in every dimension) is valid only for a single write
	 * that covers the entire array at that level.
	 *
	 * @param region pixel data to write, with correct min/max coordinates;
	 *               all dimensions (including time and channel) are encoded in the interval
	 * @param level  resolution level index (0 = highest resolution)
	 * @throws IllegalStateException if neither {@link #createMultiscales} nor
	 *         {@link #openMultiscales} has been called
	 * @throws StoreAccessException if the accessing or writing to the store experienced any issue
	 */
	void writeRegion( final RandomAccessibleInterval< T > region, final int level );
}
