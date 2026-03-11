package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sc.fiji.ome.zarr.util.ZarrTestUtils.IMAGE_NAME;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.img.Img;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.display.DisplayService;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.util.BdvHandle;

import javax.swing.SwingUtilities;

import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

class ZarrOpenActionsTest
{

	@Test
	void testOpenValidMultiScaleImagePath() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example" );
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
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0/image"
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

	@Test
	void testOpenMultiScaleDatasetInImageJ() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example" );
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
			DisplayService displayService = context.getService( DisplayService.class );
			assertNotNull( displayService );
			displayService.getActiveDisplay().close(); // Close the active display / image
			assertTrue( displayService.getDisplays().isEmpty() );
			assertEquals( 0, datasetService.getDatasets().size() ); // The dataset is dereferenced now
		}
	}

	@Test
	void testOpenSingleScaleImageInImageJ() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0/image" );
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

	@Test
	void testOpenMultiScaleDatasetBDV() throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example" );
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

	@Test
	void testOpenSingleScaleImageInBDV() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/scale0/image" );
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
