/*-
 * #%L
 * OME-Zarr reader based on zarr-java
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
package ome.zarr.zarrjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scijava.Context;

import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.PyramidBackendTestBase;
import ome.zarr.ZarrTestUtils;

class ZarrJavaPyramidBackendTest implements PyramidBackendTestBase
{
	public PyramidContents< ? > read( final String resource, final Context context )
			throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new ZarrJavaPyramidBackend().read( path.toUri() );
	}

	@Test
	void testStaticReadPyramid() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr" );
		PyramidContents< ? > contents = ZarrJavaPyramidBackend.readPyramid( path.toUri() );
		assertNotNull( contents );
		assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
		assertEquals( 5, contents.numDimensions() );
		assertEquals( 2, contents.numResolutionLevels() );
	}

	@Test
	void testReadZippedArchive( @TempDir final Path tempDir ) throws Exception
	{
		Path archive = ZarrTestUtils.zipDataset( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr", tempDir.resolve( "5d.ozx" ) );
		PyramidContents< ? > contents = ZarrJavaPyramidBackend.readPyramid( archive.toUri() );
		assertNotNull( contents );
		assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
		assertEquals( 5, contents.numDimensions() );
		assertEquals( 2, contents.numResolutionLevels() );
	}
	/**
	 * A zipped archive served over HTTP, which reaches the store through
	 * {@code FullRangeStore}.
	 */
	@Test
	void testReadZippedArchiveOverHttp( @TempDir final Path tempDir ) throws Exception
	{
		Path archive = ZarrTestUtils.zipDataset( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr",
				tempDir.resolve( "5d.ozx" ) );
		HttpServer server = startFileServer( archive );
		try
		{
			URI uri = URI.create( "http://localhost:" + server.getAddress().getPort() + "/5d.ozx" );
			PyramidContents< ? > contents = ZarrJavaPyramidBackend.readPyramid( uri );
			assertNotNull( contents );
			assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
			assertEquals( 5, contents.numDimensions() );
			assertEquals( 2, contents.numResolutionLevels() );
		}
		finally
		{
			server.stop( 0 );
		}
	}

	/**
	 * Serves {@code file} at {@code /<file name>} on a free port, honouring a
	 * single {@code Range: bytes=<start>-<end>} header – the ZIP entries are read
	 * at those offsets.
	 */
	private static HttpServer startFileServer( final Path file ) throws IOException
	{
		byte[] content = Files.readAllBytes( file );
		HttpServer server = HttpServer.create( new InetSocketAddress( "localhost", 0 ), 0 );
		server.createContext( "/" + file.getFileName(), exchange -> {
			String range = exchange.getRequestHeaders().getFirst( "Range" );
			int start = 0;
			int end = content.length - 1;
			if ( range != null && range.startsWith( "bytes=" ) )
			{
				String[] bounds = range.substring( "bytes=".length() ).split( "-", -1 );
				start = Integer.parseInt( bounds[ 0 ] );
				if ( bounds.length > 1 && !bounds[ 1 ].isEmpty() )
					end = Math.min( Integer.parseInt( bounds[ 1 ] ), content.length - 1 );
			}
			int length = Math.max( end - start + 1, 0 );
			exchange.getResponseHeaders().add( "Accept-Ranges", "bytes" );
			exchange.sendResponseHeaders( range == null ? 200 : 206, length );
			try ( OutputStream out = exchange.getResponseBody() )
			{
				out.write( content, start, length );
			}
		} );
		server.start();
		return server;
	}

}
