package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.imglib2.img.Img;

import org.junit.jupiter.api.Test;

class ZarrOpenActionsTest
{

	@Test
	void testOpenValidImagePath() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image" );
		ZarrOpenActions actions = new ZarrOpenActions( path, null );
		AtomicInteger counter = new AtomicInteger( 0 );
		Consumer< Img< ? > > imgOpeningCounter = img -> counter.incrementAndGet();
		actions.openImage( imgOpeningCounter );
		assertEquals( 1, counter.get() );
	}

	@Test
	void testOpenInvalidImagePath() throws URISyntaxException
	{
		String[] invalidPaths = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0/image/0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image/c"
		};
		for ( String invalidPath : invalidPaths )
		{
			Path path = ZarrTestUtils.resourcePath( invalidPath );
			ZarrOpenActions actions = new ZarrOpenActions( path, null );
			AtomicInteger counter = new AtomicInteger( 0 );
			Consumer< Img< ? > > imgOpeningCounter = img -> counter.incrementAndGet();
			actions.openImage( imgOpeningCounter );
			assertEquals( 0, counter.get() );
		}
	}
}
