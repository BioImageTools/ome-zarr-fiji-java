package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sc.fiji.ome.zarr.util.ZarrTestUtils.IMAGE_NAME;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.img.Img;
import net.imglib2.util.Cast;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.scijava.Context;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.prefs.PrefService;
import org.scijava.ui.swing.script.TextEditor;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import bdv.viewer.ViewerFrame;
import bdv.util.BdvStackSource;
import ij.ImagePlus;
import sc.fiji.ome.zarr.plugins.DragAndDropUserScriptSettings;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.settings.ZarrDragAndDropOpenSettings;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;

class ZarrOpenActionsTest
{
	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyc/3d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyc/3d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyt/3d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyt/3d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyct/4d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyct/4d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzc/4d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzc/4d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzt/4d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzt/4d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}

	static Stream< String > omeZarrSingleImages()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr/0",
				"sc/fiji/ome/zarr/util/3d_testing/xyc/3d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/3d_testing/xyc/3d_dataset_v5.ome.zarr/0",
				"sc/fiji/ome/zarr/util/3d_testing/xyt/3d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/3d_testing/xyt/3d_dataset_v5.ome.zarr/0",
				"sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v5.ome.zarr/0",
				"sc/fiji/ome/zarr/util/4d_testing/xyct/4d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/4d_testing/xyct/4d_dataset_v5.ome.zarr/0",
				"sc/fiji/ome/zarr/util/4d_testing/xyzc/4d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/4d_testing/xyzc/4d_dataset_v5.ome.zarr/0",
				"sc/fiji/ome/zarr/util/4d_testing/xyzt/4d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/4d_testing/xyzt/4d_dataset_v5.ome.zarr/0",
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
			Function< Img< ? >, Object > singleScaleOpeningCounter = img -> singleScaleCounter.incrementAndGet();
			actions.openImage( multiScaleOpeningCounter, singleScaleOpeningCounter, "" );
			assertEquals( 1, multiScaleCounter.get() );
			assertEquals( 0, singleScaleCounter.get() );
		}
	}

	@Test
	void testOpenValidSingleScaleImagePath() throws URISyntaxException
	{
		String[] validPaths = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr/0"
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
				Function< Img< ? >, Object > singleScaleOpeningCounter = img -> singleScaleCounter.incrementAndGet();
				actions.openImage( multiScaleOpeningCounter, singleScaleOpeningCounter, "" );
				assertEquals( 0, multiScaleCounter.get() );
				assertEquals( 1, singleScaleCounter.get() );
			}
		}
	}

	@Test
	void testOpenInvalidImagePaths() throws URISyntaxException
	{
		String[] invalidPaths = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr/0/c/0"
		};
		try (Context context = new Context())
		{
			for ( String invalidPath : invalidPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				ZarrOpenActions actions = new ZarrOpenActions( path, context, null, System.out::println );
				Function< PyramidalDataset< ? >, Object > multiScaleNoOp = pyramidalDataset -> null;
				Function< Img< ? >, Object > singleScaleNoOp = img -> null;
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
			ZarrDragAndDropOpenSettings settings = new ZarrDragAndDropOpenSettings( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION, 10 );
			ZarrOpenActions actions = new ZarrOpenActions( path, context, settings, System.out::println );
			Function< PyramidalDataset< ? >, Object > multiScaleNoOp = pyramidalDataset -> null;
			Function< Img< ? >, Object > singleScaleNoOp = img -> null;
			assertDoesNotThrow( () -> actions.openImage( multiScaleNoOp, singleScaleNoOp, "" ) );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenMultiScaleDatasetInImageJ( String resource ) throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context ); // no settings object means that the highest resolution is loaded by default
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
				assertArrayEquals( new long[] { 64, 64 }, dimensions );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyc" ) )
				{
					assertArrayEquals( new long[] { 64, 64, 3 }, dimensions );
				}
				if ( resource.contains( "xyt" ) )
				{
					assertArrayEquals( new long[] { 64, 64, 4 }, dimensions );
				}
				if ( resource.contains( "xyz" ) )
				{
					assertArrayEquals( new long[] { 64, 64, 16 }, dimensions );
				}
			}
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) )
				{
					assertArrayEquals( new long[] { 64, 64, 3, 4 }, dimensions );
				}
				if ( resource.contains( "xyzc" ) )
				{
					assertArrayEquals( new long[] { 64, 64, 16, 3 }, dimensions );
				}
				if ( resource.contains( "xyzt" ) )
				{
					assertArrayEquals( new long[] { 64, 64, 16, 4 }, dimensions ); // highest resolution
				}
			}
			if ( resource.contains( "5d_testing" ) )
			{
				assertArrayEquals( new long[] { 64, 64, 16, 3, 4 }, dimensions ); // highest resolution
			}
			assertEquals( IMAGE_NAME, dataset.getName() );
			DisplayService displayService = context.getService( DisplayService.class );
			assertNotNull( displayService );
			SwingUtilities.invokeAndWait( () -> {} ); // wait until all Swing events are processed
			Display< ? > activeDisplay = displayService.getActiveDisplay();
			assertNotNull( activeDisplay );
			activeDisplay.close(); // Close the active display / image
			assertTrue( displayService.getDisplays().isEmpty() );
			assertEquals( 0, datasetService.getDatasets().size() ); // The dataset is dereferenced now
		}
	}

	@Disabled( "This test is currently failing, since full support for opening single scale images is not yet implemented." )
	@ParameterizedTest
	@MethodSource( "omeZarrSingleImages" )
	void testOpenSingleScaleImageInImageJ( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context );
			ImagePlus imagePlus = Cast.unchecked( actions.openIJWithImage() );
			int channels = imagePlus.getNChannels();
			int frames = imagePlus.getNFrames();
			int slices = imagePlus.getNSlices();
			int[] dimensions = imagePlus.getDimensions();
			if ( resource.contains( "2d_testing" ) )
			{
				assertArrayEquals( new int[] { 64, 64, 1, 1, 1 }, dimensions );
				assertEquals( 1, channels );
				assertEquals( 1, frames );
				assertEquals( 1, slices );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyc" ) )
				{
					assertArrayEquals( new int[] { 64, 64, 3, 1, 1 }, dimensions );
					assertEquals( 3, channels );
					assertEquals( 1, frames );
					assertEquals( 1, slices );
				}
				if ( resource.contains( "xyt" ) )
				{
					assertArrayEquals( new int[] { 64, 64, 4, 1, 1 }, dimensions );
					assertEquals( 1, channels );
					assertEquals( 4, frames );
					assertEquals( 1, slices );
				}
				if ( resource.contains( "xyz" ) )
				{
					assertArrayEquals( new int[] { 64, 64, 16, 1, 1 }, dimensions );
					assertEquals( 1, channels );
					assertEquals( 1, frames );
					assertEquals( 16, slices );
				}
			}
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) )
				{
					assertArrayEquals( new int[] { 64, 64, 3, 4, 1 }, dimensions );
					assertEquals( 3, channels );
					assertEquals( 4, frames );
					assertEquals( 1, slices );
				}
				if ( resource.contains( "xyzc" ) )
				{
					assertArrayEquals( new int[] { 64, 64, 16, 3, 1 }, dimensions );
					assertEquals( 3, channels );
					assertEquals( 1, frames );
					assertEquals( 16, slices );
				}
				if ( resource.contains( "xyzt" ) )
				{
					assertArrayEquals( new int[] { 64, 64, 16, 4, 1 }, dimensions );
					assertEquals( 1, channels );
					assertEquals( 4, frames );
					assertEquals( 16, slices );
				}
			}
			if ( resource.contains( "5d_testing" ) )
			{
				assertArrayEquals( new int[] { 64, 64, 16, 3, 4 }, dimensions );
				assertEquals( 3, channels );
				assertEquals( 4, frames );
				assertEquals( 16, slices );
			}

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 0, datasets.size() ); // A single scale image is opened as image not as dataset
			imagePlus.close();
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
			BdvHandle bdvHandle = Cast.unchecked( actions.openBDVWithImage() );

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 1, datasets.size() ); // The dataset service knows the dataset now
			if ( resource.contains( "5d_testing" ) )
			{
				assertEquals( 1, bdvHandle.getViewerPanel().state().getCurrentTimepoint() );
			}
			bdvHandle.close();
			// wait until all Swing events are processed
			SwingUtilities.invokeAndWait( () -> {} );
			datasets = datasetService.getDatasets();
			assertEquals( 0, datasets.size() ); // The dataset service has correctly removed the dataset from the cache
		}
	}

	@Disabled( "This test is currently failing, since full support for opening single scale images is not yet implemented." )
	@ParameterizedTest
	@MethodSource( "omeZarrSingleImages" )
	void testOpenSingleScaleImageInBDV( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path, context );
			BdvStackSource< ? > bdvStackSource = Cast.unchecked( actions.openBDVWithImage() );
			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 0, datasets.size() ); // A single scale image is opened in BDV as an image, not as a dataset
			assertNotNull( bdvStackSource );
			ConverterSetup converterSetup0 = bdvStackSource.getConverterSetups().get( 0 );
			assertEquals( 0, converterSetup0.getDisplayRangeMin() ); // omero metadata is not supported for a single scale image
			assertEquals( 255, converterSetup0.getDisplayRangeMax() );
			assertEquals( "(r=255,g=255,b=255,a=255)", converterSetup0.getColor().toString() );
			assertEquals( 0, bdvStackSource.getBdvHandle().getViewerPanel().state().getCurrentTimepoint() );
			if ( resource.contains( "2d_testing" ) )
			{
				assertEquals( 1, bdvStackSource.getConverterSetups().size() ); // 1 channel
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyc" ) )
				{
					assertEquals( 3, bdvStackSource.getConverterSetups().size() );
				}
				if ( resource.contains( "xyt" ) )
				{
					assertEquals( 1, bdvStackSource.getConverterSetups().size() ); // 1 channel
				}
				if ( resource.contains( "xyz" ) )
				{
					assertEquals( 1, bdvStackSource.getConverterSetups().size() ); // 1 channel
				}
			}
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) )
				{
					assertEquals( 3, bdvStackSource.getConverterSetups().size() );
				}
				if ( resource.contains( "xyzc" ) )
				{
					assertEquals( 3, bdvStackSource.getConverterSetups().size() );
				}
				if ( resource.contains( "xyzt" ) )
				{
					assertEquals( 1, bdvStackSource.getConverterSetups().size() ); // 1 channel
				}
			}
			if ( resource.contains( "5d_testing" ) )
			{
				assertEquals( 3, bdvStackSource.getConverterSetups().size() );
			}
			bdvStackSource.close();
		}
	}

	@Test
	void testRunScriptWithNoScriptSpecified() throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		try (MockedStatic< JOptionPane > mocked = Mockito.mockStatic( JOptionPane.class ))
		{
			mocked.when( () -> JOptionPane.showConfirmDialog(
					Mockito.any(),
					Mockito.any(),
					Mockito.any(),
					Mockito.anyInt() ) )
					.thenReturn( JOptionPane.NO_OPTION );

			try (Context context = new Context())
			{
				PrefService prefService = context.getService( PrefService.class );
				prefService.put( DragAndDropUserScriptSettings.class, "scriptPath", "--none--" );
				String resource = "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr";
				Path path = ZarrTestUtils.resourcePath( resource );
				ZarrOpenActions actions = new ZarrOpenActions( path, context, null, System.out::println );
				actions.runScript();

				// wait until all Swing events are processed
				SwingUtilities.invokeAndWait( () -> {} );

				boolean found = false;
				String text = null;

				for ( Window window : Window.getWindows() )
				{
					if ( window instanceof TextEditor )
					{
						TextEditor editor = ( TextEditor ) window;
						found = true;
						text = editor.getTextArea().getText();
						break;
					}
				}
				assertTrue( found, "TextEditor window should be open" );
				assertEquals( ScriptUtils.getTemplate(), text );
			}
		}
	}

	@Test
	void testRunScriptWithScriptSpecified() throws URISyntaxException, IOException
	{

		try (Context context = new Context())
		{
			PrefService prefService = context.getService( PrefService.class );
			Path temp = Files.createTempFile( "myScriptFile", ".py" );
			String template = ScriptUtils.getTemplate(); // template script that opens the image in the BigDataViewer
			String[] lines = template.split( "\\R" );
			Files.write( temp, Arrays.asList( lines ) );

			File tempFile = temp.toFile();
			tempFile.deleteOnExit();
			prefService.put( DragAndDropUserScriptSettings.class, "scriptPath", tempFile.getAbsolutePath() );
			String resource = "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr";
			Path path = ZarrTestUtils.resourcePath( resource );
			ZarrOpenActions actions = new ZarrOpenActions( path, context, null, System.out::println );
			actions.runScript();

			boolean foundTextEditor = false;
			boolean foundBigDataViewer = false;
			for ( Window window : Window.getWindows() )
			{
				if ( window instanceof TextEditor )
				{
					foundTextEditor = true;
					break;
				}
			}
			for ( Window window : Window.getWindows() )
			{
				if ( window instanceof ViewerFrame )
				{
					foundBigDataViewer = true;
					break;
				}
			}
			assertFalse( foundTextEditor, "TextEditor window should not be open" );
			assertTrue( foundBigDataViewer, "BigDataViewer window should be open" );
		}
	}
}
