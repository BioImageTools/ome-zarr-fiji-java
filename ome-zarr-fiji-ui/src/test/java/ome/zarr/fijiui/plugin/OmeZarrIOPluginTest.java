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
package ome.zarr.fijiui.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.scijava.Context;
import org.scijava.io.location.BytesLocation;
import org.scijava.io.location.FileLocation;
import org.scijava.io.location.Location;
import org.scijava.io.location.URLLocation;

import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.ZarrTestUtils;

class OmeZarrIOPluginTest
{
	@Test
	void openDelegatesToZarrOpenActions() throws URISyntaxException, IOException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/" );
			final FileLocation fileLocation = new FileLocation( path.toUri() );

			final OmeZarrIOPlugin plugin = new OmeZarrIOPlugin();
			plugin.setContext( context );

			try (MockedStatic< ZarrOpenActions > mocked = Mockito.mockStatic( ZarrOpenActions.class ))
			{
				plugin.open( fileLocation );
				mocked.verify( () -> ZarrOpenActions.openWithSettings( path.toUri(), context ), times( 1 ) );
			}
		}
	}

	@Test
	void supportsOpenAcceptsLocalZarrDirectory() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/" );
		assertTrue( supportsOpen( new FileLocation( path.toUri() ) ) );
	}

	@Test
	void supportsOpenRejectsNonZarrDirectory() throws URISyntaxException
	{
		// The testdata folder itself is a plain directory, not a Zarr root.
		final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/" );
		assertFalse( supportsOpen( new FileLocation( path.toUri() ) ) );
	}

	/**
	 * A {@code BytesLocation} cannot be expressed as a URI at all; the plugin
	 * must decline it rather than fail.
	 */
	@Test
	void supportsOpenRejectsLocationWithoutUri()
	{
		assertFalse( supportsOpen( new BytesLocation( 8 ) ) );
	}

	/**
	 * The remote case, which is what {@code fiji://open/url?p=...} resolves to:
	 * {@code fiji-links} hands us a {@code URLLocation} / {@code HTTPLocation}
	 * rather than a {@link FileLocation}.
	 */
	@Test
	void supportsOpenAcceptsRemoteZarrUrl() throws URISyntaxException, IOException
	{
		final Path datasetRoot = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr" );
		final HttpServer httpServer = serve( datasetRoot );
		try
		{
			final URL url = new URL( "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/" );
			assertTrue( supportsOpen( new URLLocation( url ) ) );
		}
		finally
		{
			httpServer.stop( 0 );
		}
	}

	@Test
	void openDelegatesForRemoteLocation() throws IOException
	{
		try (Context context = new Context())
		{
			final URI uri = URI.create( "http://127.0.0.1:1/2d_dataset_v5.ome.zarr/" );
			final URLLocation location = new URLLocation( uri.toURL() );

			final OmeZarrIOPlugin plugin = new OmeZarrIOPlugin();
			plugin.setContext( context );

			try (MockedStatic< ZarrOpenActions > mocked = Mockito.mockStatic( ZarrOpenActions.class ))
			{
				plugin.open( location );
				mocked.verify( () -> ZarrOpenActions.openWithSettings( uri, context ), times( 1 ) );
			}
		}
	}

	private static boolean supportsOpen( final Location location )
	{
		try (Context context = new Context())
		{
			final OmeZarrIOPlugin plugin = new OmeZarrIOPlugin();
			plugin.setContext( context );
			return plugin.supportsOpen( location );
		}
	}

	/** Serves {@code datasetRoot} over HTTP, answering HEAD and GET. */
	private static HttpServer serve( final Path datasetRoot ) throws IOException
	{
		final HttpServer httpServer = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		httpServer.createContext( "/", exchange -> {
			final String relativePath = exchange.getRequestURI().getPath().substring( 1 );
			final Path filePath = datasetRoot.resolve( relativePath );
			final boolean isHead = "HEAD".equals( exchange.getRequestMethod() );
			if ( !relativePath.isEmpty() && Files.exists( filePath ) && !Files.isDirectory( filePath ) )
			{
				final byte[] content = Files.readAllBytes( filePath );
				exchange.sendResponseHeaders( 200, isHead ? -1 : content.length );
				if ( !isHead )
					exchange.getResponseBody().write( content );
			}
			else
			{
				exchange.sendResponseHeaders( 404, -1 );
			}
			exchange.close();
		} );
		httpServer.start();
		return httpServer;
	}
}
