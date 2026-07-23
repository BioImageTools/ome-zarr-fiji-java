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
package ome.zarr.fijiui.open;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static ome.zarr.ZarrTestUtils.IMAGE_NAME;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.img.Img;
import net.imglib2.util.Cast;
import net.imglib2.util.Util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.scijava.Context;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.prefs.PrefService;
import org.scijava.ui.swing.script.TextEditor;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;

import javax.swing.JOptionPane;

import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import javax.swing.SwingUtilities;

import bdv.viewer.ViewerFrame;
import bdv.util.BdvStackSource;
import ij.ImagePlus;
import ome.zarr.fijiui.settings.UserScriptSettings;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fijiui.open.options.ZarrOpeningSettings;
import ome.zarr.fijiui.open.options.ZarrOpenBehavior;
import ome.zarr.fijiui.open.options.ZarrReaderBackend;
import ome.zarr.fijiui.dialog.DnDActionChooser;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fijiui.util.ScriptUtils;
import ome.zarr.ZarrTestUtils;

class ZarrOpenActionsTest
{
	static Stream< ZarrReaderBackend > readerBackends()
	{
		return Stream.of( ZarrReaderBackend.N5, ZarrReaderBackend.ZARR_JAVA );
	}

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}

	static Stream< String > omeZarrSingleImages()
	{
		return Stream.of(
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr/0"
		);
	}

	@Test
	void openWithSettingsDispatchesToConfiguredOpenAction() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PrefService prefService = context.getService( PrefService.class );
			final ZarrOpeningSettings settings = new ZarrOpeningSettings();
			final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/" );

			try (MockedConstruction< ZarrOpenActions > actionsConstruction =
					mockConstruction( ZarrOpenActions.class );
					MockedConstruction< DnDActionChooser > chooserConstruction =
							mockConstruction( DnDActionChooser.class ))
			{
				settings.setCurrentChoice( ZarrOpenBehavior.BDV_MULTI_RESOLUTION );
				settings.saveSettingsToPreferences( prefService );
				ZarrOpenActions.openWithSettings( path.toUri(), context );

				settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION );
				settings.saveSettingsToPreferences( prefService );
				ZarrOpenActions.openWithSettings( path.toUri(), context );

				settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION );
				settings.saveSettingsToPreferences( prefService );
				ZarrOpenActions.openWithSettings( path.toUri(), context );

				settings.setCurrentChoice( ZarrOpenBehavior.SHOW_SELECTION_DIALOG );
				settings.saveSettingsToPreferences( prefService );
				ZarrOpenActions.openWithSettings( path.toUri(), context );

				final List< ZarrOpenActions > actionsInstances = actionsConstruction.constructed();
				assertEquals( 4, actionsInstances.size() );
				verify( actionsInstances.get( 0 ), times( 1 ) ).openBDVWithImage();
				verify( actionsInstances.get( 1 ), times( 1 ) ).openIJWithImage();
				verify( actionsInstances.get( 2 ), times( 1 ) ).openIJWithImage();

				final List< DnDActionChooser > chooserInstances = chooserConstruction.constructed();
				assertEquals( 1, chooserInstances.size() );
				verify( chooserInstances.get( 0 ), times( 1 ) ).showDialog();
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "readerBackends" )
	void openWithSettingsOpensV5DatasetFromHttpUri( ZarrReaderBackend backend )
			throws URISyntaxException, IOException, InterruptedException, InvocationTargetException
	{
		Path datasetRoot = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr" );
		HttpServer httpServer = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		httpServer.createContext( "/", exchange -> {
			String relativePath = exchange.getRequestURI().getPath().substring( 1 );
			Path filePath = datasetRoot.resolve( relativePath );
			boolean isHead = "HEAD".equals( exchange.getRequestMethod() );
			if ( !relativePath.isEmpty() && Files.exists( filePath ) && !Files.isDirectory( filePath ) )
			{
				byte[] content = Files.readAllBytes( filePath );
				exchange.sendResponseHeaders( 200, isHead ? -1 : content.length );
				if ( !isHead )
				{
					exchange.getResponseBody().write( content );
				}
			}
			else
			{
				exchange.sendResponseHeaders( 404, -1 );
			}
			exchange.close();
		} );
		httpServer.start();
		try
		{
			URI httpUri = URI.create( "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/" );
			try (Context context = new Context())
			{
				PrefService prefService = context.getService( PrefService.class );
				ZarrOpeningSettings settings = new ZarrOpeningSettings();
				settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION );
				settings.setReaderBackend( backend );
				settings.saveSettingsToPreferences( prefService );

				ZarrOpenActions.openWithSettings( httpUri, context );

				DatasetService datasetService = context.getService( DatasetService.class );
				assertEquals( 1, datasetService.getDatasets().size() );
				assertEquals( IMAGE_NAME + " (R)", datasetService.getDatasets().get( 0 ).getName() );
				SwingUtilities.invokeAndWait( () -> {} );
				DisplayService displayService = context.getService( DisplayService.class );
				assertNotNull( displayService.getActiveDisplay() );
				displayService.getActiveDisplay().close();
			}
		}
		finally
		{
			httpServer.stop( 0 );
		}
	}

	@Test
	void openImporterDialogDoesNotThrow() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr" );
		try (Context context = new Context(); MockedConstruction< N5Importer > ignored = mockConstruction( N5Importer.class ))
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			assertDoesNotThrow( actions::openImporterDialog );
		}
	}

	@Test
	void openViewerDialogDoesNotThrow() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr" );
		try (Context context = new Context();
				MockedConstruction< N5ViewerCreator > ignored = mockConstruction( N5ViewerCreator.class ))
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			assertDoesNotThrow( actions::openViewerDialog );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenValidMultiScaleImagePath( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			AtomicInteger multiScaleCounter = new AtomicInteger( 0 );
			AtomicInteger singleScaleCounter = new AtomicInteger( 0 );
			Function< PyramidalDataset, Object > multiScaleOpeningCounter = dataset -> multiScaleCounter.incrementAndGet();
			Function< Img< ? >, Object > singleScaleOpeningCounter = img -> singleScaleCounter.incrementAndGet();
			actions.openImage( multiScaleOpeningCounter, singleScaleOpeningCounter );
			assertEquals( 1, multiScaleCounter.get() );
			assertEquals( 0, singleScaleCounter.get() );
		}
	}

	@Test
	void testOpenValidSingleScaleImagePath() throws URISyntaxException
	{
		String[] validPaths = {
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr/0"
		};
		try (Context context = new Context())
		{
			for ( String invalidPath : validPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, null, System.out::println );
				AtomicInteger multiScaleCounter = new AtomicInteger( 0 );
				AtomicInteger singleScaleCounter = new AtomicInteger( 0 );
				Function< PyramidalDataset, Object > multiScaleOpeningCounter = dataset -> multiScaleCounter.incrementAndGet();
				Function< Img< ? >, Object > singleScaleOpeningCounter = img -> singleScaleCounter.incrementAndGet();
				actions.openImage( multiScaleOpeningCounter, singleScaleOpeningCounter );
				assertEquals( 0, multiScaleCounter.get() );
				assertEquals( 0, singleScaleCounter.get() ); // currently not supported
			}
		}
	}

	@Test
	void testOpenInvalidImagePaths() throws URISyntaxException
	{
		String[] invalidPaths = {
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/0/0",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr/0/c/0"
		};
		try (Context context = new Context())
		{
			for ( String invalidPath : invalidPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, null, System.out::println );
				Function< PyramidalDataset, Object > multiScaleNoOp = pyramidalDataset -> null;
				Function< Img< ? >, Object > singleScaleNoOp = img -> null;
				assertDoesNotThrow( () -> actions.openImage( multiScaleNoOp, singleScaleNoOp ) );
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "readerBackends" )
	void testOpenBioformats2rawCollectionRootReportsMultiImage( ZarrReaderBackend backend ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/bioformats2raw_testing/bf2raw_dataset_v5.ome.zarr" );
		try (Context context = new Context())
		{
			AtomicReference< String > capturedError = new AtomicReference<>();
			Consumer< String > errorHandler = capturedError::set;
			ZarrOpeningSettings settings = new ZarrOpeningSettings();
			settings.setReaderBackend( backend );
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, settings, errorHandler );
			AtomicInteger multiScaleCounter = new AtomicInteger( 0 );
			AtomicInteger singleScaleCounter = new AtomicInteger( 0 );
			Function< PyramidalDataset, Object > multiScaleOpener = dataset -> multiScaleCounter.incrementAndGet();
			Function< Img< ? >, Object > singleScaleOpener = img -> singleScaleCounter.incrementAndGet();
			assertDoesNotThrow( () -> actions.openImage( multiScaleOpener, singleScaleOpener ) );
			assertEquals( 0, multiScaleCounter.get(), "Multi-image collection must not be opened as a single multiscale image" );
			assertEquals( 0, singleScaleCounter.get() );
			assertNotNull( capturedError.get(), "Error handler should have been called for backend " + backend );
			assertTrue( capturedError.get().contains( "multiple images" ),
					"Expected multi-image message from backend, got: " + capturedError.get() );
		}
	}

	@ParameterizedTest
	@MethodSource( "readerBackends" )
	void testOpenBioformats2rawCollectionChildOpens( ZarrReaderBackend backend ) throws URISyntaxException
	{
		String[] childPaths = {
				"ome/zarr/testdata/bioformats2raw_testing/bf2raw_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/bioformats2raw_testing/bf2raw_dataset_v5.ome.zarr/1"
		};
		try (Context context = new Context())
		{
			for ( String childPath : childPaths )
			{
				Path path = ZarrTestUtils.resourcePath( childPath );
				AtomicReference< String > capturedError = new AtomicReference<>();
				Consumer< String > errorHandler = capturedError::set;
				ZarrOpeningSettings settings = new ZarrOpeningSettings();
				settings.setReaderBackend( backend );
				ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, settings, errorHandler );
				AtomicInteger multiScaleCounter = new AtomicInteger( 0 );
				AtomicInteger singleScaleCounter = new AtomicInteger( 0 );
				Function< PyramidalDataset, Object > multiScaleOpener = dataset -> multiScaleCounter.incrementAndGet();
				Function< Img< ? >, Object > singleScaleOpener = img -> singleScaleCounter.incrementAndGet();
				assertDoesNotThrow( () -> actions.openImage( multiScaleOpener, singleScaleOpener ),
						"Opening child image " + childPath + " should not throw" );
				assertEquals( 1, multiScaleCounter.get(),
						"Child image " + childPath + " should be opened as a multiscale image" );
				assertEquals( 0, singleScaleCounter.get() );
				assertNull( capturedError.get(),
						"Error handler should not have been called for child " + childPath + ", got: " + capturedError.get() );
			}
		}
	}

	@Test
	void testOpenNonMatchingResolution() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr" );
			ZarrOpeningSettings settings = new ZarrOpeningSettings( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION, 10 );
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, settings, System.out::println );
			Function< PyramidalDataset, Object > multiScaleNoOp = pyramidalDataset -> null;
			Function< Img< ? >, Object > singleScaleNoOp = img -> null;
			assertDoesNotThrow( () -> actions.openImage( multiScaleNoOp, singleScaleNoOp ) );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testOpenMultiScaleDatasetInImageJ( String resource ) throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context ); // no settings object means that the highest resolution is loaded by default
			actions.openIJWithImage();

			DatasetService datasetService = context.getService( DatasetService.class );
			assertNotNull( datasetService );
			List< Dataset > datasets = datasetService.getDatasets();
			assertNotNull( datasets );
			assertEquals( 1, datasets.size() ); // The dataset service knows the dataset now
			Dataset dataset = datasets.get( 0 );
			PyramidalDataset pyramidalDataset = Cast.unchecked( dataset );
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
			assertEquals( IMAGE_NAME + " (R)", dataset.getName() );
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
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, null, System.out::println );
			ImagePlus imagePlus = Cast.unchecked( actions.openIJWithImage() );
			assertNotNull( imagePlus );
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
	void testOpenMultiScaleDatasetBDV( String resource ) throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			BdvHandle bdvHandle = Cast.unchecked( actions.openBDVWithImage() );

			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertNotNull( pyramidalService );
			List< Pyramidal > pyramidals = pyramidalService.getPyramidals();
			assertNotNull( pyramidals );
			assertEquals( 1, pyramidals.size() ); // The pyramidal service knows the dataset now

			if ( resource.contains( "5d_testing" ) )
			{
				assertEquals( 1, bdvHandle.getViewerPanel().state().getCurrentTimepoint() );
			}
			bdvHandle.close();
			SwingUtilities.invokeAndWait( () -> {} ); // wait until all Swing events are processed
			pyramidals = pyramidalService.getPyramidals();
			assertEquals( 0, pyramidals.size() ); // The pyramidal service has correctly removed the dataset from the cache
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
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, null, System.out::println );
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
		try (MockedStatic< JOptionPane > mocked = mockStatic( JOptionPane.class ))
		{
			mocked.when( () -> JOptionPane.showConfirmDialog(
					any(),
					any(),
					any(),
					anyInt() ) )
					.thenReturn( JOptionPane.NO_OPTION );

			try (Context context = new Context())
			{
				PrefService prefService = context.getService( PrefService.class );
				prefService.put( UserScriptSettings.class, "scriptPath", "--none--" );
				String resource = "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr";
				Path path = ZarrTestUtils.resourcePath( resource );
				AtomicBoolean scriptFailed = new AtomicBoolean( false );
				Consumer< String > errorHandler = errorMessage -> {
					scriptFailed.set( true );
					System.out.println( errorMessage );
				};
				ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, null, errorHandler );
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
				assertTrue( scriptFailed.get(), "Script should fail" );
				assertEquals( ScriptUtils.getTemplate(), text );
			}
		}
	}

	/**
	 * Scenario: open each resolution level of a multi-resolution dataset in ImageJ,
	 * then open the same dataset in BigDataViewer twice.
	 * <p>
	 * Expected: 2 IJ opens produce 2 datasets; each BDV open produces one additional dataset.
	 * All 4 datasets share the same {@link ome.zarr.imglib2.PyramidContents} instance.
	 * <p>
	 * The 5d v4 test dataset has 2 resolution levels:
	 * level 0 → [x=64, y=64, z=16, c=3, t=4] and level 1 → [x=32, y=32, z=8, c=3, t=4].
	 */
	@Test
	void openEachResolutionLevelInIJThenBDVTwice()
			throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr" );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );

			// Open both resolution levels in ImageJ – each produces a separate dataset window,
			// all backed by the same PyramidContents (shared cachedCellImgs / volatileImgs)
			actions.openIJWithImage( 0 ); // highest resolution
			actions.openIJWithImage( 1 ); // coarser resolution

			DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 2, datasetService.getDatasets().size() );

			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 2, pyramidalService.getPyramidals().size() );

			// Each BDV open creates one additional dataset backed by the full pyramid
			BdvHandle bdvHandle1 = null;
			BdvHandle bdvHandle2 = null;
			try
			{
				bdvHandle1 = Cast.unchecked( actions.openBDVWithImage() );
				assertEquals( 3, pyramidalService.getPyramidals().size() );

				bdvHandle2 = Cast.unchecked( actions.openBDVWithImage() );
				assertEquals( 4, pyramidalService.getPyramidals().size() );

				assertSame( datasetService.getDatasets().get( 0 ), pyramidalService.getPyramidals().get( 0 ) );
				assertSame( datasetService.getDatasets().get( 1 ), pyramidalService.getPyramidals().get( 1 ) );

				PyramidalDataset ijLevel0 = Cast.unchecked( pyramidalService.getPyramidals().get( 0 ) );
				PyramidalDataset ijLevel1 = Cast.unchecked( pyramidalService.getPyramidals().get( 1 ) );

				PyramidalBdv< ? > bdv1 = Cast.unchecked( pyramidalService.getPyramidals().get( 2 ) );

				// All 4 datasets (2 IJ + 2 BDV) must be backed by the exact same PyramidContents object
				PyramidContents< ? > sharedPyramid = ijLevel0.getPyramidContents();
				for ( Dataset dataset : datasetService.getDatasets() )
				{
					PyramidalDataset pyramidalDataset = Cast.unchecked( dataset );
					assertSame( sharedPyramid, pyramidalDataset.getPyramidContents(),
							"Every opened dataset must share the same PyramidContents instance" );
				}

				// IJ datasets
				assertArrayEquals( new long[] { 64, 64, 16, 3, 4 }, ijLevel0.getImgPlus().dimensionsAsLongArray(),
						"IJ dataset at level 0 should have the highest resolution dimensions" );
				assertArrayEquals( new long[] { 32, 32, 8, 3, 4 }, ijLevel1.getImgPlus().dimensionsAsLongArray(),
						"IJ dataset at level 1 should have half the spatial dimensions of level 0" );

				// BDV dataset
				assertEquals( 3, bdv1.asSources().size() ); // 3 channels
				assertEquals( 2, bdv1.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() ); // 2 resolution levels
				assertTrue( bdv1.asSources().get( 1 ).getSpimSource().isPresent( 3 ) ); // 4 timepoints
				assertArrayEquals( new long[] { 64, 64, 16 },
						bdv1.asSources().get( 0 ).getSpimSource().getSource( 0, 0 ).dimensionsAsLongArray() );
				assertEquals( 3, bdv1.asSources().size() ); // 3 channels
				assertEquals( 2, bdv1.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() ); // 2 resolution levels
				assertTrue( bdv1.asSources().get( 0 ).getSpimSource().isPresent( 3 ) ); // 4 timepoints
				assertArrayEquals( new long[] { 32, 32, 8 },
						bdv1.asSources().get( 0 ).getSpimSource().getSource( 0, 1 ).dimensionsAsLongArray() );
			}
			finally
			{
				if ( bdvHandle1 != null )
					bdvHandle1.close();
				if ( bdvHandle2 != null )
					bdvHandle2.close();
				SwingUtilities.invokeAndWait( () -> {} );
			}
		}
	}

	/**
	 * Scenario: open a multi-resolution dataset in BigDataViewer first, then open
	 * a specific resolution level in ImageJ.
	 * <p>
	 * Expected: the BDV open produces 1 {@link Pyramidal}; the subsequent IJ open produces a 2nd {@link Pyramidal} (and {@link Dataset})
	 * backed by the same {@link ome.zarr.imglib2.PyramidContents} object.
	 * The 5d v4 test dataset has 2 resolution levels:
	 * level 0 → [x=64, y=64, z=16, c=3, t=4] and level 1 → [x=32, y=32, z=8, c=3, t=4].
	 */
	@Test
	void openInBDVThenOpenSpecificResolutionInIJ()
			throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr" );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			BdvHandle bdvHandle = null;
			try
			{
				// BDV open covers all resolution levels and registers one dataset
				bdvHandle = Cast.unchecked( actions.openBDVWithImage() );

				PyramidalService pyramidalService = context.getService( PyramidalService.class );
				DatasetService datasetService = context.getService( DatasetService.class );
				assertEquals( 1, pyramidalService.getPyramidals().size() );
				assertEquals( 0, datasetService.getDatasets().size() );

				// Opening a specific resolution level in IJ creates a 2nd dataset window,
				// backed by the same PyramidContents as the BDV dataset
				actions.openIJWithImage( 1 ); // coarser resolution: [x=32, y=32, z=8, c=3, t=4]
				assertEquals( 2, pyramidalService.getPyramidals().size() );
				assertEquals( 1, datasetService.getDatasets().size() );

				PyramidalBdv< ? > bdvDataset = Cast.unchecked( pyramidalService.getPyramidals().get( 0 ) );
				PyramidalDataset ijDataset = Cast.unchecked( pyramidalService.getPyramidals().get( 1 ) );
				assertSame( bdvDataset.getPyramidContents(), ijDataset.getPyramidContents(),
						"BDV and IJ datasets must share the same PyramidContents instance" );
				// BDV dataset
				assertEquals( 3, bdvDataset.asSources().size() ); // 3 channels
				assertEquals( 2, bdvDataset.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() ); // 2 resolution levels
				assertTrue( bdvDataset.asSources().get( 1 ).getSpimSource().isPresent( 3 ) ); // 4 timepoints
				assertArrayEquals( new long[] { 64, 64, 16 },
						bdvDataset.asSources().get( 0 ).getSpimSource().getSource( 0, 0 ).dimensionsAsLongArray() );
				assertArrayEquals( new long[] { 32, 32, 8, 3, 4 }, ijDataset.getImgPlus().dimensionsAsLongArray(),
						"IJ dataset at level 1 should have half the spatial dimensions of level 0" );
			}
			finally
			{
				if ( bdvHandle != null )
					bdvHandle.close();
				SwingUtilities.invokeAndWait( () -> {} );
			}
		}
	}

	/**
	 * Verifies that datasets backed by the same resolution level of the same pyramid
	 * all wrap the exact same {@link net.imglib2.cache.img.CachedCellImg} instance.
	 * <p>
	 * {@code CachedCellImg} loads chunks lazily and holds them in a bounded cache.
	 * Two datasets that wrap the same {@code CachedCellImg} share that cache, so a
	 * chunk loaded for one view is immediately available to the other at no additional
	 * memory cost. Two datasets at <em>different</em> levels correctly use distinct
	 * {@code CachedCellImg} instances.
	 */
	@Test
	void sharedCachedCellImgAcrossDatasetsAtSameResolutionLevel()
			throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr" );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			BdvHandle bdvHandle = null;
			try
			{
				actions.openIJWithImage( 0 ); // first instance of level 0
				actions.openIJWithImage( 0 ); // second instance of level 0
				bdvHandle = Cast.unchecked( actions.openBDVWithImage() );
				actions.openIJWithImage( 1 );

				PyramidalService pyramidalService = context.getService( PyramidalService.class );
				PyramidalDataset ijLevel0First = Cast.unchecked( pyramidalService.getPyramidals().get( 0 ) );
				PyramidalDataset ijLevel0Second = Cast.unchecked( pyramidalService.getPyramidals().get( 1 ) );
				PyramidalBdv< ? > bdv = Cast.unchecked( pyramidalService.getPyramidals().get( 2 ) );
				PyramidalDataset ijLevel1 = Cast.unchecked( pyramidalService.getPyramidals().get( 3 ) );

				Img< ? > cellImgIj0First = ijLevel0First.getImgPlus().getImg();
				Img< ? > cellImgIj0Second = ijLevel0Second.getImgPlus().getImg();
				Img< ? > cellImgBdv0 = bdv.getPyramidContents().asImg( 0 );
				Img< ? > cellImgIj1 = ijLevel1.getImgPlus().getImg();

				assertSame( cellImgIj0First, cellImgIj0Second,
						"Two IJ datasets at the same level must wrap the same CachedCellImg" );
				assertSame( cellImgIj0First, cellImgBdv0,
						"IJ and BDV datasets at the same level must wrap the same CachedCellImg" );
				assertNotSame( cellImgIj0First, cellImgIj1,
						"Datasets at different resolution levels must use different CachedCellImgs" );
			}
			finally
			{
				if ( bdvHandle != null )
					bdvHandle.close();
				SwingUtilities.invokeAndWait( () -> {} );
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
			prefService.put( UserScriptSettings.class, "scriptPath", tempFile.getAbsolutePath() );
			String resource = "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr";
			Path path = ZarrTestUtils.resourcePath( resource );
			AtomicBoolean scriptFailed = new AtomicBoolean( false );
			Consumer< String > errorHandler = errorMessage -> {
				scriptFailed.set( true );
				System.out.println( errorMessage );
			};
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context, null, errorHandler );
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
			assertFalse( scriptFailed.get(), "Script should not have failed" );

			for ( Window window : Window.getWindows() )
				window.dispose();
		}
	}

	@Test
	void testOpenDifferentResolutionLevels() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr" );
		try (Context context = new Context())
		{
			ZarrOpenActions actions = new ZarrOpenActions( path.toUri(), context );
			try
			{
				actions.openIJWithImage( 0 );
				actions.openIJWithImage( 1 );

				DatasetService datasetService = context.getService( DatasetService.class );
				PyramidalDataset ijLevel0 = Cast.unchecked( datasetService.getDatasets().get( 0 ) );
				PyramidalDataset ifLevel1 = Cast.unchecked( datasetService.getDatasets().get( 1 ) );

				assertFalse( Util.imagesEqual( Cast.unchecked( ijLevel0.getImgPlus().getImg() ), ifLevel1.getImgPlus().getImg() ) );
				long[] expectedDims0 = new long[] { 64, 64, 16, 3, 4 };
				long[] expectedDims1 = new long[] { 32, 32, 8, 3, 4 };

				assertArrayEquals( expectedDims0, ijLevel0.getImgPlus().dimensionsAsLongArray(),
						"Dimensions of level 0 should be [x=64, y=64, z=16, c=3, t=4]" );
				assertArrayEquals( expectedDims1, ifLevel1.getImgPlus().dimensionsAsLongArray(),
						"Dimensions of level 1 should be [x=32, y=32, z=8, c=3, t=4]" );
				assertSame( ijLevel0.getPyramidContents(), ifLevel1.getPyramidContents() );
			}
			finally
			{
				for ( Window window : Window.getWindows() )
					window.dispose();
			}
		}
	}
}
