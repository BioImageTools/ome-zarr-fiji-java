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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether a remote http(s) URL points at the root of an OME-Zarr
 * dataset by issuing HEAD requests to the well-known Zarr metadata files.
 */
public class ZarrOverHttpUtils
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int CONNECT_TIMEOUT_MS = 5_000;

	private static final int READ_TIMEOUT_MS = 5_000;

	/**
	 * Files that, if any of them exists at the URL, identify it as a Zarr
	 * group or array root. Ordered to put the v3 marker first because newer
	 * datasets are increasingly v3.
	 */
	private static final String[] METADATA_FILES = { "zarr.json", ".zgroup", ".zarray" };

	private ZarrOverHttpUtils()
	{
		// prevent instantiation
	}

	/**
	 * Determines whether the given URL points at the root of a Zarr dataset.
	 * <p>
	 * Issues HEAD requests for {@code zarr.json} (Zarr v3), {@code .zgroup}, and
	 * {@code .zarray} (Zarr v2) under the URL and returns {@code true} on the
	 * first successful response. Any non-2xx response, network failure, or
	 * timeout is treated as "not present".
	 *
	 * @param baseUri the URL to probe; must use the {@code http} or
	 *   {@code https} scheme. Trailing slashes are normalized.
	 * @return {@code true} if at least one Zarr metadata file is reachable,
	 *   {@code false} otherwise (including for null or non-http URIs).
	 */
	public static boolean isZarrUrl( final URI baseUri )
	{
		if ( baseUri == null )
			return false;
		final String scheme = baseUri.getScheme();
		if ( !"http".equalsIgnoreCase( scheme ) && !"https".equalsIgnoreCase( scheme ) )
			return false;

		final URI base = ensureTrailingSlash( baseUri );
		for ( final String name : METADATA_FILES )
		{
			if ( existsByHead( base.resolve( name ) ) )
				return true;
		}
		return false;
	}

	private static URI ensureTrailingSlash( final URI uri )
	{
		final String s = uri.toString();
		return s.endsWith( "/" ) ? uri : URI.create( s + "/" );
	}

	private static boolean existsByHead( final URI uri )
	{
		HttpURLConnection conn = null;
		try
		{
			conn = ( HttpURLConnection ) uri.toURL().openConnection();
			conn.setRequestMethod( "HEAD" );
			conn.setConnectTimeout( CONNECT_TIMEOUT_MS );
			conn.setReadTimeout( READ_TIMEOUT_MS );
			conn.setInstanceFollowRedirects( true );
			final int code = conn.getResponseCode();
			return code >= 200 && code < 300;
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