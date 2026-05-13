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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sun.net.httpserver.HttpServer;

class ZarrUtilsTest
{
	@Test
	void testIsZarrFolder_validZarrFolders() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr/0"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			assertTrue( ZarrUtils.isZarr( path.toUri() ) );
		}
	}

	@Test
	void testIsZarrFolder_invalidZarrFolders() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0/0/0"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			assertFalse( ZarrUtils.isZarr( path.toUri() ) );
		}
	}

	@Test
	void detectsLocalZarrFolderViaFileUri() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr" );
		assertTrue( ZarrUtils.isZarr( path.toUri() ) );
	}

	@Test
	void rejectsNonZarrLocalFolder() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing" );
		assertFalse( ZarrUtils.isZarr( path.toUri() ) );
	}

	@Test
	void rejectsNullUri()
	{
		assertFalse( ZarrUtils.isZarr( null ) );
	}

	@Test
	void rejectsUnsupportedScheme()
	{
		assertFalse( ZarrUtils.isZarr( URI.create( "ftp://example.com/foo" ) ) );
	}

	@Test
	void rejectsMalformedFileUri()
	{
		// jar: URIs throw FileSystemNotFoundException from Paths.get(URI); the
		// dispatcher should catch that rather than propagate.
		assertFalse( ZarrUtils.isZarr( URI.create( "jar:file:/tmp/foo.jar!/bar" ) ) );
	}

	// --- isZarr(URI) — http: URI tests ---

	private HttpServer server;

	private final Set< String > existingPaths = Collections.synchronizedSet( new HashSet<>() );

	private final Set< String > htmlPaths = Collections.synchronizedSet( new HashSet<>() );

	static Stream< String > omeZarrPaths()
	{
		return Stream.of(
				"/dataset/zarr.json",
				"/dataset/.zgroup",
				"/dataset/.zarray",
				"/dataset/.zattrs"
		);
	}

	@BeforeEach
	void startServer() throws IOException
	{
		existingPaths.clear();
		htmlPaths.clear();
		server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		server.createContext( "/", exchange -> {
			final String path = exchange.getRequestURI().getPath();
			final int code = existingPaths.contains( path ) ? 200 : 404;
			if ( htmlPaths.contains( path ) )
				exchange.getResponseHeaders().set( "Content-Type", "text/html; charset=utf-8" );
			exchange.sendResponseHeaders( code, -1 );
			exchange.close();
		} );
		server.start();
	}

	@AfterEach
	void stopServer()
	{
		if ( server != null )
			server.stop( 0 );
	}

	private URI base()
	{
		return URI.create( "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/dataset" );
	}

	private void exists( final String... paths )
	{
		existingPaths.addAll( Arrays.asList( paths ) );
	}

	@SuppressWarnings( "all" )
	private void existsAsHtml( final String... paths )
	{
		existingPaths.addAll( Arrays.asList( paths ) );
		htmlPaths.addAll( Arrays.asList( paths ) );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrPaths" )
	void detectsZarrRoot( final String path )
	{
		exists( path );
		assertTrue( ZarrUtils.isZarr( base() ) );
	}

	@Test
	void rejectsLocationWithoutMetadataFiles()
	{
		// no exists() call: server returns 404 for everything
		assertFalse( ZarrUtils.isZarr( base() ) );
	}

	@Test
	void rejectsHtmlContentTypeResponse()
	{
		// A server that returns HTTP 200 with text/html for Zarr metadata paths
		// (e.g. a single-page application routing all requests to index.html)
		// must not be identified as a Zarr dataset.
		existsAsHtml( "/dataset/zarr.json", "/dataset/.zgroup", "/dataset/.zarray" );
		assertFalse( ZarrUtils.isZarr( base() ) );
	}

	@Test
	void normalizesTrailingSlashOnBaseUrl()
	{
		exists( "/dataset/zarr.json" );
		final URI withSlash = URI.create( base() + "/" );
		assertTrue( ZarrUtils.isZarr( withSlash ) );
	}
}
