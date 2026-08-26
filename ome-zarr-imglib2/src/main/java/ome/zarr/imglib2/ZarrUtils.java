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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for Zarr datasets.
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
	 * build another client for the actual read later.<br>
	 * Callers that accept such schemes should therefore bypass this method and
	 * attempt to read the dataset directly, letting the backend
	 * report a clear error if the location turns out not to be OME-Zarr.
	 *
	 * @param uri location to probe; may be {@code null}
	 * @return {@code true} if the URI points at the root of a Zarr dataset;
	 *         {@code false} for an unsupported scheme, a {@code null} URI, or any
	 *         error during probing
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
	 * This is URI resolution rather than store access, so it behaves identically
	 * for {@code file:}, {@code http(s):} and {@code s3:} locations and costs no I/O.
	 * It is used to walk from a dropped resolution-level folder up to the multiscales group
	 * that holds the axis and OMERO metadata.
	 * <p>
	 * Only the path is walked up, since that is the only part of a URI with
	 * traversable segments: e.g. {@code https://example.com} and
	 * {@code https://example.com/} have no parent, and neither has an opaque URI
	 * such as {@code mailto:someone@example.com}. Any query or fragment is
	 * dropped from the parent before walking up.
	 * <table border="1">
	 *   <caption>Representative examples; {@code ZarrUtilsTest} covers the full set</caption>
	 *   <tr><th>{@code uri}</th><th>result</th><th>note</th></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/0}</td>
	 *       <td>{@code file:/data/img.ome.zarr/}</td>
	 *       <td>a dropped resolution level; a trailing slash on the input
	 *           makes no difference</td></tr>
	 *   <tr><td>{@code file:///data/my%20img.ome.zarr/0}</td>
	 *       <td>{@code file:/data/my%20img.ome.zarr/}</td>
	 *       <td>percent-encoded segments stay encoded</td></tr>
	 *   <tr><td>{@code s3://bucket/img.ome.zarr/0}</td>
	 *       <td>{@code s3://bucket/img.ome.zarr/}</td>
	 *       <td>the same walk on any scheme, without contacting the store</td></tr>
	 *   <tr><td>{@code https://example.com/0}</td>
	 *       <td>{@code https://example.com/}</td>
	 *       <td>a single segment has the store root as its parent</td></tr>
	 *   <tr><td>{@code https://example.com/a/b?x=1}</td>
	 *       <td>{@code https://example.com/a/}</td>
	 *       <td>query dropped, as is a fragment</td></tr>
	 *   <tr><td>{@code https://example.com/}</td><td>{@code null}</td>
	 *       <td>the host is not a path segment, so there is nothing to remove</td></tr>
	 *   <tr><td>{@code mailto:someone@example.com}</td><td>{@code null}</td>
	 *       <td>opaque URI, no path at all</td></tr>
	 *   <tr><td>{@code null}</td><td>{@code null}</td>
	 *       <td>never throws on a missing location</td></tr>
	 * </table>
	 *
	 * @param uri location to take the parent of
	 * @return the parent URI (with trailing slash), or {@code null}
	 */
	public static URI parentUri( final URI uri )
	{
		if ( uri == null )
			return null;
		final String rawPath = uri.getRawPath();
		if ( rawPath == null ) // opaque URI: no path to walk up
			return null;
		final int numSegments = pathSegments( uri ).size();
		if ( numSegments == 0 || ( numSegments == 1 && !rawPath.startsWith( "/" ) ) )
			return null; // no path segment to remove, hence no parent
		// Let URI resolution remove the last path segment: it keeps the trailing
		// slash (so the result is treated as a folder location), collapses
		// doubled slashes, and leaves scheme and authority alone. A location
		// already ending in a slash needs ".." to lose its last segment.
		return rawPath.endsWith( "/" ) ? uri.resolve( ".." ) : uri.resolve( "." );
	}

	/**
	 * Last path segment of {@code uri} (its folder or file name), ignoring any
	 * trailing slash. Used as the display name of a single array node, so the
	 * segment is decoded: {@code file:///data/my%20img.zarr} yields
	 * {@code "my img.zarr"}.
	 * <p>
	 * A location without path segments is a store root and is named after its
	 * host or bucket instead ({@code s3://bucket} yields {@code "bucket"}); only
	 * a location with neither, such as an opaque URI, yields {@code ""}.
	 * <table border="1">
	 *   <caption>Representative examples; {@code ZarrUtilsTest} covers the full set</caption>
	 *   <tr><th>{@code uri}</th><th>result</th><th>note</th></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/0}</td><td>{@code "0"}</td>
	 *       <td>the name of a dropped resolution level; the scheme plays no
	 *           role</td></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/./0/}</td><td>{@code "0"}</td>
	 *       <td>trailing, doubled and {@code "."} segments are resolved
	 *           away</td></tr>
	 *   <tr><td>{@code file:///data/my%20img.ome.zarr}</td>
	 *       <td>{@code "my img.ome.zarr"}</td>
	 *       <td>decoded, since this is a display name</td></tr>
	 *   <tr><td>{@code https://example.com/img.zarr/0?x=1}</td><td>{@code "0"}</td>
	 *       <td>neither a query nor a fragment is part of the name</td></tr>
	 *   <tr><td>{@code s3://bucket}</td><td>{@code "bucket"}</td>
	 *       <td>no path segment, so the authority names the store root</td></tr>
	 *   <tr><td>{@code file:///}</td><td>{@code ""}</td>
	 *       <td>neither a segment nor an authority to fall back on</td></tr>
	 *   <tr><td>{@code null}</td><td>{@code ""}</td>
	 *       <td>never throws on a missing location</td></tr>
	 * </table>
	 *
	 * @param uri location whose last segment is wanted
	 * @return the last path segment, the host or bucket, or {@code ""}
	 */
	public static String lastSegment( final URI uri )
	{
		if ( uri == null )
			return "";
		final List< String > segments = pathSegments( uri );
		if ( !segments.isEmpty() )
			return segments.get( segments.size() - 1 );
		return uri.getAuthority() != null ? uri.getAuthority() : "";
	}

	/**
	 * Whether {@code childUri} is the location described by a multiscales
	 * {@code datasets[].path} entry, i.e. whether the child's trailing path
	 * segments are exactly those of {@code datasetPath}. This covers the common
	 * single-segment case ({@code "0"}) as well as a multi-segment relative
	 * dataset path ({@code "sub/0"}).
	 * <p>
	 * Whole segments are compared, so {@code "0"} does not match a child ending
	 * in {@code "x0"}, and the comparison is unaffected by trailing slashes,
	 * doubled slashes, percent-encoding in the child location, or a query or
	 * fragment.
	 * <table border="1">
	 *   <caption>Representative examples; {@code ZarrUtilsTest} covers the full set</caption>
	 *   <tr><th>{@code childUri}</th><th>{@code datasetPath}</th>
	 *       <th>result</th><th>note</th></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/0}</td><td>{@code "0"}</td>
	 *       <td>{@code true}</td><td>the common single-segment case</td></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/sub/0}</td><td>{@code "sub/0"}</td>
	 *       <td>{@code true}</td><td>a multi-segment dataset path</td></tr>
	 *   <tr><td>{@code file:///data/my%20img.zarr/level%200}</td>
	 *       <td>{@code "level 0"}</td><td>{@code true}</td>
	 *       <td>the location is encoded, the metadata value is plain text</td></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/0}</td><td>{@code "/0"}</td>
	 *       <td>{@code true}</td>
	 *       <td>a dataset path is relative per the spec, but a stray leading
	 *           slash still identifies the same segment</td></tr>
	 *   <tr><td>{@code file:///data/img.ome.zarr/x0}</td><td>{@code "0"}</td>
	 *       <td>{@code false}</td>
	 *       <td>whole segments only: {@code "0"} is not the tail of
	 *           {@code "x0"}</td></tr>
	 *   <tr><td>{@code file:///data/0}</td><td>{@code "img.ome.zarr/0"}</td>
	 *       <td>{@code false}</td>
	 *       <td>a dataset path longer than the child's path cannot identify
	 *           it</td></tr>
	 *   <tr><td>{@code https://example.com}</td><td>{@code "example.com"}</td>
	 *       <td>{@code false}</td><td>the host is not a path segment</td></tr>
	 *   <tr><td>{@code null} or any</td><td>any or {@code null}</td>
	 *       <td>{@code false}</td><td>never throws on missing arguments</td></tr>
	 * </table>
	 *
	 * @param childUri location of the dropped array node
	 * @param datasetPath a {@code datasets[].path} value from parent multiscales
	 *   metadata
	 * @return {@code true} if {@code datasetPath} identifies {@code childUri}
	 */
	public static boolean isChildPath( final URI childUri, final String datasetPath )
	{
		if ( childUri == null || datasetPath == null )
			return false;
		final List< String > dataset = segments( datasetPath );
		final List< String > child = pathSegments( childUri );
		if ( dataset.isEmpty() || dataset.size() > child.size() )
			return false;
		return child.subList( child.size() - dataset.size(), child.size() ).equals( dataset );
	}

	/**
	 * Decoded path segments of {@code uri}, e.g. {@code [img.ome.zarr, 0]} for
	 * {@code file:///data/img.ome.zarr/0/}, or an empty list when the URI has no
	 * path segments (a store root, or an opaque URI).
	 */
	private static List< String > pathSegments( final URI uri )
	{
		return segments( uri.normalize().getPath() );
	}

	/**
	 * Non-empty segments of the slash-separated {@code path}, ignoring empty and
	 * {@code "."} segments, or an empty list when there are none.
	 */
	private static List< String > segments( final String path )
	{
		if ( path == null || path.isEmpty() )
			return Collections.emptyList();
		final List< String > segments = new ArrayList<>();
		for ( final String segment : path.split( "/" ) )
		{
			if ( !segment.isEmpty() && !".".equals( segment ) )
				segments.add( segment );
		}
		return segments;
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
