package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static sc.fiji.ome.zarr.util.ZarrTestUtils.IMAGE_NAME;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.img.Img;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

class ZarrOpenActionsTest
{
	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenValidMultiScaleImagePath( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
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
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0/image",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example/scale0/image"
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
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0/image/0",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example/scale0",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example/scale0/image/c"
		};
		try (Context context = new Context())
		{
			for ( String invalidPath : invalidPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				ZarrOpenActions actions = new ZarrOpenActions( path, context, System.out::println );
				Consumer< PyramidalDataset< ? > > multiScaleNoOp = dataset -> {};
				Consumer< Img< ? > > singleScaleNoOp = img -> {};
				assertDoesNotThrow( () -> actions.openImage( multiScaleNoOp, singleScaleNoOp, "" ) );
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenDatasetImageJ( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context );
			actions.openIJWithImage();

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 1, datasets.size() ); // The dataset service knows the dataset now
			Dataset dataset = datasets.get( 0 );
			assertEquals( IMAGE_NAME, dataset.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenDatasetBDV( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context );
			actions.openBDVWithImage();

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 1, datasets.size() ); // The dataset service knows the dataset now
			// TODO: try to get a BdvHandle here, close it and test if dataset.size() is 0 after closing
			// TODO: add test for single scale image closing
		}
	}
}
