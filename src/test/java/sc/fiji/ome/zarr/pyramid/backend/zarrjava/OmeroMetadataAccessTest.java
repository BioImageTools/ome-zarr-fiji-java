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
package sc.fiji.ome.zarr.pyramid.backend.zarrjava;

import dev.zarr.zarrjava.ZarrException;
import dev.zarr.zarrjava.experimental.ome.MultiscaleImage;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroChannel;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroMetadata;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroRdefs;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroWindow;
import dev.zarr.zarrjava.store.FilesystemStore;
import dev.zarr.zarrjava.store.StoreHandle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OmeroMetadataAccessTest
{
	static Stream< String > omeZarr5dExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}

	@ParameterizedTest
	@MethodSource( "omeZarr5dExamples" )
	void testCanAccessOmeroMetadataWithExperimentalOme( final String resource ) throws URISyntaxException, IOException, ZarrException
	{
		final Path path = ZarrTestUtils.resourcePath( resource );
		final FilesystemStore store = new FilesystemStore( path.toString() );
		final StoreHandle handle = store.resolve();
		final MultiscaleImage multiscaleImage = MultiscaleImage.open( handle );

		final OmeroMetadata omero = multiscaleImage.getOmeroMetadata();

		assertNotNull( omero );
		assertEquals( Integer.valueOf( 1 ), omero.id );
		assertEquals( "0.4", omero.version );

		final OmeroRdefs rdefs = omero.rdefs;
		assertNotNull( rdefs );
		assertEquals( Integer.valueOf( 1 ), rdefs.defaultT );
		assertEquals( Integer.valueOf( 71 ), rdefs.defaultZ );
		assertEquals( "color", rdefs.model );

		final List< OmeroChannel > channels = omero.channels;
		assertNotNull( channels );
		assertEquals( 3, channels.size() );

		final OmeroChannel channel0 = channels.get( 0 );
		assertEquals( "lynEGFP", channel0.label );
		assertEquals( "00FF00", channel0.color );
		assertWindow( channel0.window, 0.0, 255.0, 3.0, 246.0 );

		final OmeroChannel channel1 = channels.get( 1 );
		assertEquals( "NLStdTomato", channel1.label );
		assertEquals( "FF0000", channel1.color );
		assertWindow( channel1.window, 0.0, 255.0, 6.0, 133.0 );
	}

	private void assertWindow( final OmeroWindow window, final double min, final double max, final double start, final double end )
	{
		assertNotNull( window );
		assertEquals( min, window.min );
		assertEquals( max, window.max );
		assertEquals( start, window.start );
		assertEquals( end, window.end );
	}
}
