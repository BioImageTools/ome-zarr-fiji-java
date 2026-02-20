package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.imglib2.img.Img;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import sc.fiji.ome.zarr.pyramid.NotASingleScaleImageException;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

class ZarrOpenActionsTest
{

	@Test
	void testOpenValidMultiScaleImagePath() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/ome_zarr_v4_example" );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context );
			AtomicInteger multiScaleCounter = new AtomicInteger( 0 );
			AtomicInteger singleScaleCounter = new AtomicInteger( 0 );
			Consumer< PyramidalDataset< ? > > multiScaleOpeningCounter = dataset -> multiScaleCounter.incrementAndGet();
			Consumer< Img< ? > > imgConsumer = img -> singleScaleCounter.incrementAndGet();
			actions.openImage( multiScaleOpeningCounter, imgConsumer, "" );
			assertEquals( 1, multiScaleCounter.get() );
			assertEquals( 0, singleScaleCounter.get() );
		}
	}

	@Test
	void testOpenValidSingleScaleImagePath() throws URISyntaxException
	{
		String[] validPaths = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0/image"
				// "sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image" // NB: OME v05 not supported yet
		};
		try (Context context = new Context())
		{
			for ( String invalidPath : validPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				ZarrOpenActions actions = new ZarrOpenActions( path, context );
				AtomicInteger multiScaleCounter = new AtomicInteger( 0 );
				AtomicInteger singleScaleCounter = new AtomicInteger( 0 );
				Consumer< PyramidalDataset< ? > > multiScaleOpeningCounter = dataset -> multiScaleCounter.incrementAndGet();
				Consumer< Img< ? > > imgConsumer = img -> singleScaleCounter.incrementAndGet();
				actions.openImage( multiScaleOpeningCounter, imgConsumer, "" );
				assertEquals( 0, multiScaleCounter.get() );
				assertEquals( 1, singleScaleCounter.get() );
			}
		}
	}

	@Test
	void testOpenInvalidImagePaths() throws URISyntaxException
	{
		String[] invalidPaths = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0/image/0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image/c"
		};
		try (Context context = new Context())
		{
			for ( String invalidPath : invalidPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				ZarrOpenActions actions = new ZarrOpenActions( path, context );
				Consumer< PyramidalDataset< ? > > multiScaleNoOp = dataset -> {};
				Consumer< Img< ? > > singleScaleNoOp = img -> {};
				assertThrows( NotASingleScaleImageException.class, () -> actions.openImage( multiScaleNoOp, singleScaleNoOp, "" ) );
			}
		}
	}
}
