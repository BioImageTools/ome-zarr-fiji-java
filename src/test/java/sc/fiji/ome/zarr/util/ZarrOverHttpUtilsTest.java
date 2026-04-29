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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class ZarrOverHttpUtilsTest
{
	private HttpServer server;

	private final Set< String > existingPaths = Collections.synchronizedSet( new HashSet<>() );

	@BeforeEach
	void startServer() throws IOException
	{
		existingPaths.clear();
		server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		server.createContext( "/", new HttpHandler()
		{
			@Override
			public void handle( final HttpExchange exchange ) throws IOException
			{
				final String path = exchange.getRequestURI().getPath();
				final int code = existingPaths.contains( path ) ? 200 : 404;
				exchange.sendResponseHeaders( code, -1 );
				exchange.close();
			}
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

	@Test
	void detectsZarrV3RootByZarrJson()
	{
		exists( "/dataset/zarr.json" );
		assertTrue( ZarrOverHttpUtils.isZarrUrl( base() ) );
	}

	@Test
	void detectsZarrV2RootByZgroup()
	{
		exists( "/dataset/.zgroup" );
		assertTrue( ZarrOverHttpUtils.isZarrUrl( base() ) );
	}

	@Test
	void detectsZarrV2ArrayByZarray()
	{
		exists( "/dataset/.zarray" );
		assertTrue( ZarrOverHttpUtils.isZarrUrl( base() ) );
	}

	@Test
	void rejectsLocationWithoutMetadataFiles()
	{
		// no exists() call: server returns 404 for everything
		assertFalse( ZarrOverHttpUtils.isZarrUrl( base() ) );
	}

	@Test
	void normalizesTrailingSlashOnBaseUrl()
	{
		exists( "/dataset/zarr.json" );
		final URI withSlash = URI.create( base().toString() + "/" );
		assertTrue( ZarrOverHttpUtils.isZarrUrl( withSlash ) );
	}

	@Test
	void rejectsNullUri()
	{
		assertFalse( ZarrOverHttpUtils.isZarrUrl( null ) );
	}

	@Test
	void rejectsNonHttpScheme()
	{
		assertFalse( ZarrOverHttpUtils.isZarrUrl( URI.create( "file:///tmp/foo" ) ) );
		assertFalse( ZarrOverHttpUtils.isZarrUrl( URI.create( "ftp://example.com/foo" ) ) );
	}
}