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
package ome.zarr.imglib2;

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
 * Utility methods for detecting Zarr datasets on the local filesystem and over
 * HTTP. See {@link #isZarr(URI)} for the schemes that can be probed and why
 * others (e.g. {@code s3:}) cannot.
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
	 * Determines whether the URI points at the root of a Zarr dataset.
	 * <p>
	 * Supported schemes:
	 * <ul>
	 *   <li>{@code file:} or no scheme – checks for well-known Zarr metadata
	 *       files on the local filesystem</li>
	 *   <li>{@code http:} / {@code https:} – sends HTTP HEAD requests for
	 *       well-known Zarr metadata files</li>
	 * </ul>
	 * Other schemes (e.g. {@code s3:}) always return {@code false}.<br>
	 * They are not probed because doing so cheaply is not possible: it would require
	 * creating an (authenticated), scheme-specific client (such as an S3 client)
	 * purely to look for metadata files, only to discard it and
	 * build another client for the actual open later.<br>
	 * Callers that accept such schemes should therefore bypass this method and
	 * attempt to open the dataset directly, letting the open method
	 * report a clear error if the location turns out not to be OME-Zarr.
	 *
	 * @param uri location to probe; may be {@code null}
	 * @return {@code true} if the URI points at the root of a Zarr dataset,
	 *         {@code false} for unsupported schemes, {@code null}, or any error
	 *         during probing
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

	/**
	 * Parent location of {@code uri}, i.e. {@code uri} with its last path segment
	 * removed and a trailing slash kept, or {@code null} if {@code uri} has no
	 * parent segment.
	 * <p>
	 * This is deliberately scheme-agnostic string manipulation rather than store
	 * access, so it behaves identically for {@code file:}, {@code http(s):} and
	 * {@code s3:} locations and — importantly for remote stores — costs no I/O.
	 * It is used to walk from a dropped resolution-level folder up to the
	 * multiscales group that holds the axis and OMERO metadata.
	 *
	 * @param uri location to take the parent of
	 * @return the parent URI (with trailing slash), or {@code null}
	 */
	public static URI parentUri( final URI uri )
	{
		if ( uri == null )
			return null;
		final String stripped = stripTrailingSlashes( uri.toString() );
		final int slash = stripped.lastIndexOf( '/' );
		if ( slash < 0 )
			return null;
		// Keep the trailing slash so the result is treated as a folder location.
		return URI.create( stripped.substring( 0, slash + 1 ) );
	}

	/**
	 * Last path segment of {@code uri} (its folder or file name), ignoring any
	 * trailing slash, or an empty string when there is none.
	 *
	 * @param uri location whose last segment is wanted
	 * @return the last path segment, or {@code ""}
	 */
	public static String lastSegment( final URI uri )
	{
		if ( uri == null )
			return "";
		final String stripped = stripTrailingSlashes( uri.toString() );
		final int slash = stripped.lastIndexOf( '/' );
		return slash < 0 ? stripped : stripped.substring( slash + 1 );
	}

	/**
	 * Whether {@code childUri} is the location described by a multiscales
	 * {@code datasets[].path} entry. Matches either when the dataset path equals
	 * the child's last segment (the common single-segment case, e.g. {@code "0"})
	 * or when the child location ends with {@code "/" + datasetPath} (a
	 * multi-segment relative dataset path).
	 *
	 * @param childUri location of the dropped array node
	 * @param datasetPath a {@code datasets[].path} value from parent multiscales
	 *   metadata
	 * @return {@code true} if {@code datasetPath} identifies {@code childUri}
	 */
	public static boolean isChildPath( final URI childUri, final String datasetPath )
	{
		if ( childUri == null || datasetPath == null || datasetPath.isEmpty() )
			return false;
		if ( datasetPath.equals( lastSegment( childUri ) ) )
			return true;
		final String stripped = stripTrailingSlashes( childUri.toString() );
		return stripped.endsWith( "/" + stripTrailingSlashes( datasetPath ) );
	}

	private static String stripTrailingSlashes( final String s )
	{
		int end = s.length();
		while ( end > 0 && s.charAt( end - 1 ) == '/' )
			end--;
		return s.substring( 0, end );
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
