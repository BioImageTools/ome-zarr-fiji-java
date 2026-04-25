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
package sc.fiji.ome.zarr.settings;

import java.util.NoSuchElementException;

/**
 * Selects the library used to read OME-Zarr datasets.
 */
public enum ZarrReaderBackend
{
	/**
	 * Read via the N5 universe library (supports Zarr v2 and v3 through n5-zarr).
	 */
	N5( "N5 universe" ),

	/**
	 * Read via the zarr-java library (supports Zarr v2 and v3).
	 */
	ZARR_JAVA( "zarr-java" );

	private final String description;

	ZarrReaderBackend( final String description )
	{
		this.description = description;
	}

	public static ZarrReaderBackend getByName( final String name )
	{
		for ( final ZarrReaderBackend option : values() )
			if ( option.name().equals( name ) )
				return option;
		throw new NoSuchElementException( name );
	}

	public static ZarrReaderBackend getByDescription( final String description )
	{
		for ( final ZarrReaderBackend option : values() )
			if ( option.description.equals( description ) )
				return option;
		return null;
	}

	public String getDescription()
	{
		return description;
	}
}
