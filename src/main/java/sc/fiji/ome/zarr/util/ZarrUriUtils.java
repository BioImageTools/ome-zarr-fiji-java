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
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheme-agnostic utility for asking "does this URI point at a Zarr dataset?".
 * Handles {@code file:} URIs via filesystem checks and {@code http(s):} URIs
 * via HEAD requests.
 */
public class ZarrUriUtils
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

	private static final int READ_TIMEOUT_MILLIS = 5_000;

	private ZarrUriUtils()
	{
		// prevent instantiation
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
				return ZarrOnFileSystemUtils.isZarrFolder( Paths.get( uri ) );
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
		for ( final String name : ZarrOnFileSystemUtils.METADATA_FILES )
		{
			if ( isAccessible( base.resolve( name ) ) )
				return true;
		}
		return false;
	}

	private static URI ensureTrailingSlash( final URI uri )
	{
		final String s = uri.toString();
		return s.endsWith( "/" ) ? uri : URI.create( s + "/" );
	}

	private static boolean isAccessible( final URI uri )
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