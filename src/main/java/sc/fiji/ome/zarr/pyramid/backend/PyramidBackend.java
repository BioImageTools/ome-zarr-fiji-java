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
package sc.fiji.ome.zarr.pyramid.backend;

import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageDataImpl;

/**
 * Plug-point for reading an OME-Zarr multi-resolution image.
 * <p>
 * An implementation encapsulates everything specific to one reader library
 * (N5, zarr-java, ...): discovering multiscale metadata, selecting a resolution
 * level, opening cached cell images, and assembling axis information. The
 * backend-agnostic {@link Pyramidal5DImageDataImpl}
 * calls {@link #load()} once and derives its state from the returned
 * {@link PyramidContents}.
 *
 * @param <T> pixel type
 * @param <V> volatile pixel type
 */
public interface PyramidBackend<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
{
	/**
	 * Open the multi-resolution image and return all state needed by the
	 * concrete pyramidal image data class. Called exactly once per image.
	 */
	PyramidContents< T, V > load();
}
