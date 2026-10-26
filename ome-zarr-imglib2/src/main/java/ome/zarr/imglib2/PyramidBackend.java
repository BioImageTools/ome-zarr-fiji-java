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
package ome.zarr.imglib2;

import java.net.URI;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Plug-point for reading an OME-Zarr image as a resolution pyramid.
 * <p>
 * An implementation encapsulates everything specific to one reader library
 * (N5, zarr-java, ...): discovering metadata, selecting resolution levels,
 * opening cached cell images, and assembling axis information. Callers invoke
 * {@link #read(URI)} and consume the returned {@link PyramidContents}, which is
 * always a pyramid of one or more resolution levels regardless of whether the
 * location pointed at a whole multiscale image or a single level.
 * <p>
 * The pixel type is a property of the data being read, not of the backend, so
 * it is a type parameter of {@link #read(URI)} rather than of the backend
 * itself: a single (untyped) backend instance can read images of any pixel
 * type, and callers never have to choose {@code T} before the data is read.
 */
public interface PyramidBackend
{
	/**
	 * Read the OME-Zarr image at {@code inputUri} and return all of its state as
	 * an immutable {@link PyramidContents}.
	 * <p>
	 * The location may point at either kind of OME-Zarr node:
	 * <ul>
	 *   <li>a <b>multiscales group</b> &rarr; a pyramid with one level per
	 *       resolution described by the group's multiscales metadata;</li>
	 *   <li>a <b>single array</b> (one resolution level, e.g. a dropped
	 *       {@code .../0} folder) &rarr; a one-level pyramid. Because an array
	 *       node alone rarely carries enough information to be interpreted, the
	 *       backend recovers axis calibration and OMERO metadata from the
	 *       <em>parent</em> multiscales group when that group can be read and
	 *       lists this array (this is what allows a single OME-Zarr v0.4 / Zarr v2
	 *       level, which has no metadata of its own, to be read correctly). If the
	 *       parent cannot supply them, the backend falls back to the array's own
	 *       {@code dimension_names} (Zarr v3 only), reading the level uncalibrated
	 *       (unit scale, no units, no OMERO). The parent is only consulted once,
	 *       lazily, and only for the single-array case, so the extra cost on
	 *       remote stores is at most one small metadata request.</li>
	 * </ul>
	 *
	 * @param <T> pixel type of the image being read
	 * @param inputUri location of the OME-Zarr node to read — a {@code file:} URI
	 *   for local datasets, or an {@code http(s):} / {@code s3:} URI for remote
	 *   datasets (which remote schemes are supported depends on the concrete
	 *   backend's underlying store)
	 * @return the image as an immutable pyramid of one or more resolution levels
	 * @throws ome.zarr.imglib2.exceptions.NotAMultiscaleImageException if the
	 *   location is neither a readable multiscales group nor a readable array
	 * @throws ome.zarr.imglib2.exceptions.MultiImageDatasetException if the
	 *   location is a container of multiple images (e.g. a
	 *   {@code bioformats2raw.layout} group) rather than a single image
	 * @throws ome.zarr.imglib2.exceptions.SingleArrayAxesUnknownException if the
	 *   location is a single array whose axes cannot be determined — it declares
	 *   no {@code dimension_names} and no parent multiscales metadata listing it
	 *   could be read (e.g. a lone Zarr v2 level)
	 */
	< T extends NativeType< T > & RealType< T > > PyramidContents< T > read( URI inputUri );

	/**
	 * Name of the library behind this backend.
	 */
	default String getName()
	{
		return getClass().getSimpleName();
	}
}
