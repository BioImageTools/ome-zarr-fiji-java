package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sc.fiji.ome.zarr.util.ZarrTestUtils.IMAGE_NAME;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.img.Img;
import net.imglib2.util.Cast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;
import org.scijava.display.DisplayService;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import bdv.util.BdvHandle;

import javax.swing.SwingUtilities;

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

	static Stream< String > omeZarrSingleImages()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0/image",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example/scale0/image",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr/0"
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
			Function< PyramidalDataset< ? >, Object > multiScaleOpeningCounter = dataset -> multiScaleCounter.incrementAndGet();
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
				Function< PyramidalDataset< ? >, Object > multiScaleOpeningCounter = dataset -> multiScaleCounter.incrementAndGet();
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
				ZarrOpenActions actions = new ZarrOpenActions( path, context, null, System.out::println );
				Function< PyramidalDataset< ? >, Object > multiScaleNoOp = pyramidalDataset -> null;
				Consumer< Img< ? > > singleScaleNoOp = img -> {};
				assertDoesNotThrow( () -> actions.openImage( multiScaleNoOp, singleScaleNoOp, "" ) );
			}
		}
	}

	@Test
	void testOpenNonMatchingResolution() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr" );
			ZarrOpenActions actions = new ZarrOpenActions( path, context, 10, System.out::println );
			Function< PyramidalDataset< ? >, Object > multiScaleNoOp = pyramidalDataset -> null;
			Consumer< Img< ? > > singleScaleNoOp = img -> {};
			assertDoesNotThrow( () -> actions.openImage( multiScaleNoOp, singleScaleNoOp, "" ) );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenMultiScaleDatasetInImageJ(String resource) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context ); // preferred width equals null results in the highest resolution
			actions.openIJWithImage();

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 1, datasets.size() ); // The dataset service knows the dataset now
			Dataset dataset = datasets.get( 0 );
			PyramidalDataset< ? > pyramidalDataset = Cast.unchecked( dataset );
			long[] dimensions = pyramidalDataset.getImgPlus().dimensionsAsLongArray();
			if ( resource.contains( "2d_testing" ) )
			{
				assertArrayEquals( new long[] { 1000, 1000 }, dimensions ); // highest resolution
			}
			if ( resource.contains( "5d_testing" ) )
			{
				assertArrayEquals( new long[] { 64, 64, 16, 2, 2 }, dimensions ); // highest resolution
			}
			assertEquals( IMAGE_NAME, dataset.getName() );
			DisplayService displayService = context.getService( DisplayService.class );
			assertNotNull( displayService );
			displayService.getActiveDisplay().close(); // Close the active display / image
			assertTrue( displayService.getDisplays().isEmpty() );
			assertEquals( 0, datasetService.getDatasets().size() ); // The dataset is dereferenced now
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrSingleImages" )
	void testOpenSingleScaleImageInImageJ( String resource ) throws URISyntaxException
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
			assertEquals( 0, datasets.size() ); // A single scale image is opened as image not as dataset
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenMultiScaleDatasetBDV(String resource) throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context );
			BdvHandle bdvHandle = actions.openBDVWithImage();

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 1, datasets.size() ); // The dataset service knows the dataset now
			bdvHandle.close();
			// wait until all Swing events are processed
			SwingUtilities.invokeAndWait( () -> {} );
			datasets = datasetService.getDatasets();
			assertEquals( 0, datasets.size() ); // The dataset service has correctly removed the dataset from the cache
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrSingleImages" )
	void testOpenSingleScaleImageInBDV( String resource ) throws URISyntaxException
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
			assertEquals( 0, datasets.size() ); // A single scale image is opened as image not as dataset
		}
	}
}
