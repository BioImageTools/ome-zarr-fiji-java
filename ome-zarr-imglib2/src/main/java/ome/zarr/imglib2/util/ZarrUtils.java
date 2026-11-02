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
package ome.zarr.imglib2.util;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.imglib2.exceptions.NotZarrException;
import ome.zarr.imglib2.exceptions.ZarrAccessException;

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

	/** Extension of a zipped OME-Zarr archive: one ZIP file holding the whole dataset. */
	public static final String OZX_EXTENSION = ".ozx";

	private static final String NO_METADATA_FOUND_AT =
			"No Zarr metadata (zarr.json, .zgroup, .zarray or .zattrs) found at: ";

	/**
	 * Prevents instantiation. {@code protected} only so that the deprecated
	 * {@link ome.zarr.imglib2.ZarrUtils} can extend this class; make it
	 * {@code private} again when that one is removed.
	 */
	protected ZarrUtils()
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
	 * @throws ZarrAccessException if the folder may not be searched
	 */
	private static boolean isZarrFolder( final Path folder )
	{
		if ( Files.isDirectory( folder ) && !Files.isExecutable( folder ) )
			throw new ZarrAccessException( folder.toUri(), -1, "Could not access " + folder + ": permission denied.", null );
		for ( final String name : METADATA_FILES )
			if ( Files.exists( folder.resolve( name ) ) )
				return true;
		return false;
	}

	/**
	 * Determines whether the URI points at the root of a Zarr dataset, i.e.,
	 * whether {@link #checkZarr(URI)} succeeds. Use that to learn why not.
	 *
	 * @param uri location to probe; may be {@code null}
	 * @return {@code true} if the URI points at the root of a Zarr dataset
	 */
	public static boolean isZarr( final URI uri )
	{
		try
		{
			checkZarr( uri );
			return true;
		}
		catch ( ZarrAccessException | NotZarrException e )
		{
			return false;
		}
	}

	/**
	 * Checks that the URI points at the root of a Zarr dataset.
	 * <p>
	 * Supported schemes:
	 * <ul>
	 *   <li>{@code file:} or no scheme – checks for well-known Zarr metadata
	 *       files on the local filesystem</li>
	 *   <li>{@code http:} / {@code https:} – sends HTTP HEAD requests for
	 *       well-known Zarr metadata files</li>
	 * </ul>
	 * A zipped archive ({@link #OZX_EXTENSION}) is judged by the archive file
	 * alone – present as a regular file, or reachable over HTTP – since looking
	 * inside it would mean reading the whole ZIP index.
	 * Other schemes (e.g. {@code s3:}) always throw {@link ZarrAccessException}.<br>
	 * They are not probed because doing so cheaply is not possible: it would require
	 * creating an (authenticated), scheme-specific client (such as an S3 client)
	 * purely to look for metadata files, only to discard it and
	 * build another client for the actual read later.<br>
	 * Callers that accept such schemes should therefore bypass this method and
	 * attempt to read the dataset directly, letting the backend
	 * report a clear error if the location turns out not to be OME-Zarr.
	 * Both exceptions carry a user-facing message that names the location.
	 *
	 * @param uri location to probe; may be {@code null}
	 * @throws NotZarrException if the location could be looked into, but holds no
	 *         Zarr metadata
	 * @throws ZarrAccessException if the location could not be looked into, so
	 *         whether it is Zarr is unknown
	 */
	public static void checkZarr( final URI uri )
	{
		if ( uri == null )
			throw new NotZarrException( null, "No location given." );
		if ( isOzxArchive( uri ) )
		{
			if ( !isOzxArchiveAccessible( uri ) )
				throw new NotZarrException( uri, "No archive file found at: " + displayLocation( uri ) );
			return;
		}
		final String scheme = uri.getScheme();
		if ( scheme == null || "file".equalsIgnoreCase( scheme ) )
		{
			final Path folder;
			try
			{
				folder = Paths.get( uri );
			}
			catch ( RuntimeException e )
			{
				throw new ZarrAccessException( uri, -1, "Could not access " + uri + ": not a valid local path.", e );
			}
			if ( !isZarrFolder( folder ) )
				throw new NotZarrException( uri, NO_METADATA_FOUND_AT + folder );
			return;
		}
		if ( "http".equalsIgnoreCase( scheme ) || "https".equalsIgnoreCase( scheme ) )
		{
			if ( !isZarrUrl( uri ) )
				throw new NotZarrException( uri, NO_METADATA_FOUND_AT + uri );
			return;
		}
		throw new ZarrAccessException( uri, -1,
				"Could not access " + uri + ": '" + scheme + "' locations cannot be checked for Zarr metadata.", null );
	}

	/**
	 * Tries the metadata files in turn and returns {@code true} as soon as one is
	 * found. A refused file (e.g. HTTP 403) does not stop the search, because
	 * S3-style stores answer 403 instead of 404 for a missing key: a Zarr v2
	 * dataset there refuses {@code zarr.json}, but serves {@code .zgroup}.
	 * <p>
	 * If no file is found and at least one was refused, the refusal is thrown
	 * instead of returning {@code false}: whether the location is Zarr is then
	 * unknown.
	 */
	private static boolean isZarrUrl( final URI baseUri )
	{
		final URI base = ensureTrailingSlash( baseUri );
		ZarrAccessException refusal = null;
		for ( final String name : METADATA_FILES )
		{
			try
			{
				if ( isHttpAccessible( base.resolve( name ), baseUri ) )
					return true;
			}
			catch ( ZarrAccessException e )
			{
				refusal = e;
			}
		}
		if ( refusal != null )
			throw refusal;
		return false;
	}

	/** {@code uri} as shown to the user: a local path for {@code file:}, else the URI. */
	private static String displayLocation( final URI uri )
	{
		if ( "file".equalsIgnoreCase( uri.getScheme() ) )
		{
			try
			{
				return Paths.get( uri ).toString();
			}
			catch ( RuntimeException e )
			{
				// not a valid local path: show the URI as it is
			}
		}
		return uri.toString();
	}

	private static boolean isOzxArchiveAccessible( final URI archiveUri )
	{
		final String scheme = archiveUri.getScheme();
		if ( scheme == null || "file".equalsIgnoreCase( scheme ) )
		{
			final Path archive;
			try
			{
				archive = Paths.get( archiveUri );
			}
			catch ( RuntimeException e )
			{
				return false;
			}
			if ( Files.isRegularFile( archive ) && !Files.isReadable( archive ) )
				throw new ZarrAccessException( archiveUri, -1, "Could not access " + archive + ": permission denied.", null );
			return Files.isRegularFile( archive );
		}
		if ( "http".equalsIgnoreCase( scheme ) || "https".equalsIgnoreCase( scheme ) )
			return isHttpAccessible( archiveUri, archiveUri );
		return false;
	}

	/**
	 * Whether {@code uri} names a zipped OME-Zarr archive ({@link #OZX_EXTENSION}),
	 * i.e. whether its last path segment carries that extension
	 * (case-insensitively; a trailing slash makes no difference).
	 * <p>
	 * Pure URI arithmetic, so it costs no I/O and behaves the same for every
	 * scheme.
	 *
	 * @param uri location to inspect; may be {@code null}
	 * @return {@code true} if {@code uri} names a {@code .ozx} archive
	 */
	public static boolean isOzxArchive( final URI uri )
	{
		if ( uri == null )
			return false;
		final String path = uri.getPath();
		if ( path == null || path.isEmpty() )
			return false;
		return lastSegment( uri ).toLowerCase( Locale.ROOT ).endsWith( OZX_EXTENSION );
	}

	private static URI ensureTrailingSlash( final URI uri )
	{
		final String s = uri.toString();
		return s.endsWith( "/" ) ? uri : URI.create( s + "/" );
	}

	/**
	 * {@code uri} with a single trailing slash removed, undoing the one
	 * {@link #parentUri} always adds. Needed when a parent location is joined
	 * with a file name again, which would otherwise yield {@code //}.
	 * <table border="1">
	 *   <caption>Representative examples:</caption>
	 *   <tr><th>{@code uri}</th><th>result</th></tr>
	 *   <tr><td>{@code file:/data/img.ome.zarr/}</td><td>{@code file:/data/img.ome.zarr}</td></tr>
	 *   <tr><td>{@code file:/data/img.ozx}</td><td>{@code file:/data/img.ozx} (unchanged)</td></tr>
	 *   <tr><td>{@code https://example.com//}</td><td>{@code https://example.com/}</td></tr>
	 * </table>
	 *
	 * @param uri location to strip; may be {@code null}
	 * @return {@code uri} with any single trailing slash removed
	 */
	public static URI stripTrailingSlash( final URI uri )
	{
		if ( uri == null )
			return null;
		final String s = uri.toString();
		return s.endsWith( "/" ) ? URI.create( s.substring( 0, s.length() - 1 ) ) : uri;
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
		return uri.resolve( rawPath.endsWith( "/" ) ? ".." : "." );
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
		return Arrays.stream( path.split( "/" ) )
				.filter( segment -> !segment.isEmpty() && !".".equals( segment ) )
				.collect( Collectors.toList() );
	}

	private static boolean isHttpAccessible( final URI uri, final URI location )
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
			if ( code == HttpURLConnection.HTTP_NOT_FOUND )
				return false;
			if ( code < 200 || code >= 300 )
				throw new ZarrAccessException( location, code,
						"Could not access " + location + ": " + httpRefusal( code, conn.getResponseMessage() ), null );
			final String contentType = conn.getHeaderField( "Content-Type" );
			return contentType == null || !contentType.toLowerCase().startsWith( "text/html" );
		}
		catch ( IOException e )
		{
			logger.debug( "HEAD request failed for {}: {}", uri, e.getMessage() );
			throw new ZarrAccessException( location, -1,
					"Could not access " + location + ": the server could not be reached (" + e + ").", e );
		}
		finally
		{
			if ( conn != null )
				conn.disconnect();
		}
	}

	private static String httpRefusal( final int code, final String responseMessage )
	{
		if ( code == HttpURLConnection.HTTP_UNAUTHORIZED )
			return "the server requires authentication (HTTP 401).";
		if ( code == HttpURLConnection.HTTP_FORBIDDEN )
			return "the server denied access (HTTP 403 Forbidden).";
		return "the server answered HTTP " + code + ( responseMessage == null ? "" : " " + responseMessage ) + ".";
	}
}
