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
package ome.zarr.fiji.open;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import ome.zarr.fiji.read.ZarrReader;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;

class ZarrOpenRequestTest
{
	private static final URI URI_UNDER_TEST = URI.create( "file:/tmp/does-not-need-to-exist.ome.zarr" );

	@Test
	void aRequestCarriesTheSettingsItWasBuiltWith()
	{
		try (Context context = new Context())
		{
			final PyramidBackend backend = ZarrTestBackend.INSTANCE;
			final ZarrOpenRequest request =
					new ZarrOpenRequest( URI_UNDER_TEST, context, backend, 500, message -> {} );

			assertEquals( URI_UNDER_TEST, request.uri() );
			assertSame( context, request.context() );
			assertSame( backend, request.backend() );
			assertEquals( 500, request.preferredMaxWidth() );
			assertNotNull( request.errorHandler() );
			assertEquals( "ZarrOpenRequest{uri=" + URI_UNDER_TEST + ", backend=test, preferredMaxWidth=500}",
					request.toString() );
		}
	}

	@Test
	void theConvenienceConstructorAsksForTheHighestResolution()
	{
		try (Context context = new Context())
		{
			final ZarrOpenRequest request = new ZarrOpenRequest( URI_UNDER_TEST, context, null );

			assertNull( request.preferredMaxWidth(), "No preferred width means the highest resolution" );
			assertNotNull( request.errorHandler(), "Failures are reported through IJ::error by default" );
			assertEquals( "ZarrOpenRequest{uri=" + URI_UNDER_TEST + ", backend=null, preferredMaxWidth=null}",
					request.toString() );
		}
	}

	@Test
	void theReaderIsBuiltOnceAndShared()
	{
		try (Context context = new Context())
		{
			final ZarrOpenRequest request = new ZarrOpenRequest( URI_UNDER_TEST, context, ZarrTestBackend.INSTANCE );

			final ZarrReader reader = request.reader();
			assertNotNull( reader );
			assertSame( reader, request.reader(), "Two openers sharing a request must share its reader" );
		}
	}

	/** A backend that is never read from — only its name has to be there. */
	private enum ZarrTestBackend implements PyramidBackend
	{
		INSTANCE;

		@Override
		public String getName()
		{
			return "test";
		}

		@Override
		public < T extends NativeType< T > & RealType< T > > PyramidContents< T > read( final URI uri )
		{
			throw new UnsupportedOperationException( "This backend is not meant to read" );
		}
	}
}
