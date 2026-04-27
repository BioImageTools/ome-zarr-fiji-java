package sc.fiji.ome.zarr.pyramid;

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
