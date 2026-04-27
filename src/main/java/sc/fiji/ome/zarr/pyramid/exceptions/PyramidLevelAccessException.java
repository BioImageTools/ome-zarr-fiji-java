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
package sc.fiji.ome.zarr.pyramid.exceptions;

/**
 * Thrown when a pyramid backend cannot read a specific resolution level of
 * an OME-Zarr dataset — e.g. the underlying zarr library rejected the
 * request with an {@code IOException} or {@code ZarrException}, the chunks
 * are missing or corrupted, or a remote read failed. The multiscale
 * metadata itself parsed fine; otherwise
 * {@link NotAMultiscaleImageException} would have been thrown earlier.
 */
public class PyramidLevelAccessException extends RuntimeException
{

	public PyramidLevelAccessException( final String path, final int levelIndex, final Throwable cause )
	{
		super( "Cannot open resolution level " + levelIndex + " of: " + path, cause );
	}
}