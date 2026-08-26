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
package ome.zarr.imglib2.exceptions;

/**
 * Thrown when an OME-Zarr array node is read on its own, but its axis
 * semantics cannot be determined.
 * <p>
 * A single resolution level (a bare Zarr array) only carries the information
 * needed to interpret its dimensions when either it declares them itself
 * (a Zarr v3 array's {@code dimension_names}) or a parent multiscales group
 * that lists this array can be read to supply them. A lone Zarr v2
 * ({@code .zarray}) array has neither: it exposes only a shape and data type,
 * so a shape such as {@code [4, 64, 64]} is indistinguishable between x/y/t,
 * x/y/z and x/y/c.
 */
public class SingleArrayAxesUnknownException extends RuntimeException
{

	public SingleArrayAxesUnknownException( final String path )
	{
		super( message( path ) );
	}

	public SingleArrayAxesUnknownException( final String path, final Throwable cause )
	{
		super( message( path ), cause );
	}

	private static String message( final String path )
	{
		return "Cannot determine the axes of the OME-Zarr array at " + path
				+ ": it declares no axis names of its own (Zarr v2 has no dimension_names) and no parent "
				+ "multiscales metadata could be read to supply them. Open the parent OME-Zarr group instead.";
	}
}
