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
package sc.fiji.ome.zarr.util;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for detecting Zarr datasets.
 * Handles {@link Path} checks and scheme-agnostic {@link URI} probing
 * ({@code file:} and {@code http(s):}).
 */
public class ZarrUtils
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

	private static final int READ_TIMEOUT_MILLIS = 5_000;

	/**
	 * Well-known Zarr metadata file names. The presence of any one of these at a
	 * location is sufficient to identify it as a Zarr dataset root.
	 * Ordered v3-first so newer datasets are recognised on the first probe.
	 */
	static final String[] METADATA_FILES = { "zarr.json", ".zgroup", ".zarray", ".zattrs" };

	private ZarrUtils()
	{
		// prevent instantiation
	}

	/**
	 * Determines whether the given path appears to be the root of a Zarr dataset.
	 * <p>
	 * The method checks for the presence of well-known Zarr (and consequently OME-Zarr) metadata files:
	 * <ul>
	 *   <li>{@code .zgroup}, {@code .zattrs} or {@code .zarray} for Zarr v2</li>
	 *   <li>{@code zarr.json} for Zarr v3</li>
	 * </ul>
	 * The existence of any one of these files is considered sufficient to
	 * identify the folder as a Zarr dataset folder.
	 *
	 * @param folder the path to the directory to check
	 * @return {@code true} if the folder contains Zarr metadata files indicating
	 *         a Zarr v2 or v3 dataset, {@code false} otherwise
	 */
	private static boolean isZarrFolder( final Path folder )
	{
		for ( final String name : METADATA_FILES )
			if ( Files.exists( folder.resolve( name ) ) )
				return true;
		return false;
	}

	/**
	 * @param uri location to probe
	 * @return {@code true} if the URI points at the root of a Zarr dataset.
	 *   Returns {@code false} for unknown schemes, null, or any error during
	 *   probing.
	 */
	public static boolean isZarr( final URI uri )
	{
		if ( uri == null )
			return false;
		final String scheme = uri.getScheme();
		if ( scheme == null || "file".equalsIgnoreCase( scheme ) )
		{
			try
			{
				return isZarrFolder( Paths.get( uri ) );
			}
			catch ( RuntimeException e )
			{
				return false;
			}
		}
		if ( "http".equalsIgnoreCase( scheme ) || "https".equalsIgnoreCase( scheme ) )
			return isZarrUrl( uri );
		return false;
	}

	private static boolean isZarrUrl( final URI baseUri )
	{
		final URI base = ensureTrailingSlash( baseUri );
		for ( final String name : METADATA_FILES )
		{
			if ( isHttpAccessible( base.resolve( name ) ) )
				return true;
		}
		return false;
	}

	private static URI ensureTrailingSlash( final URI uri )
	{
		final String s = uri.toString();
		return s.endsWith( "/" ) ? uri : URI.create( s + "/" );
	}

	private static boolean isHttpAccessible( final URI uri )
	{
		HttpURLConnection conn = null;
		try
		{
			conn = ( HttpURLConnection ) uri.toURL().openConnection();
			conn.setRequestMethod( "HEAD" );
			conn.setConnectTimeout( CONNECT_TIMEOUT_MILLIS );
			conn.setReadTimeout( READ_TIMEOUT_MILLIS );
			conn.setInstanceFollowRedirects( true );
			final int code = conn.getResponseCode();
			if ( code < 200 || code >= 300 )
				return false;
			final String contentType = conn.getHeaderField( "Content-Type" );
			return contentType == null || !contentType.toLowerCase().startsWith( "text/html" );
		}
		catch ( IOException e )
		{
			logger.debug( "HEAD request failed for {}: {}", uri, e.getMessage() );
			return false;
		}
		finally
		{
			if ( conn != null )
				conn.disconnect();
		}
	}
}
