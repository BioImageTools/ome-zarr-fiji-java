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
package sc.fiji.ome.zarr.plugins;

//import static org.junit.jupiter.api.Assertions.assertArrayEquals;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertInstanceOf;
//import static org.junit.jupiter.api.Assertions.assertSame;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import net.imagej.Dataset;
//import net.imagej.DatasetService;
//import net.imglib2.util.Cast;
//
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.scijava.Context;
//import org.scijava.display.Display;
//import org.scijava.display.DisplayService;
//import org.scijava.module.MutableModuleItem;
//
//import java.awt.Window;
//import java.net.URISyntaxException;
//import java.nio.file.Path;
//import java.util.Arrays;
//import java.util.List;
//
//import javax.swing.SwingUtilities;
//
//import sc.fiji.ome.zarr.open.ZarrOpenActions;
//import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData;
//import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
//import sc.fiji.ome.zarr.util.BdvFocusService;
//import sc.fiji.ome.zarr.util.ZarrTestUtils;

class OpenResolutionLevelCommandTest
{
//
//	// 2 resolution levels: level 0 = 64x64x16, level 1 = 32x32x8
//	private static final String PYRAMID_RESOURCE = "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr";
//
//	/** Null dataset cancels the command before the dialog is shown. */
//	@Test
//	void initializeCancelsWhenNoDatasetIsOpen()
//	{
//		try (Context context = new Context())
//		{
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			cmd.dataset = null;
//			cmd.initialize();
//			assertTrue( cmd.isCanceled() );
//		}
//	}
//
//	/** A non-pyramidal dataset type cancels the command before the dialog is shown. */
//	@Test
//	void initializeCancelsWhenDatasetIsNotAPyramidalDataset()
//	{
//		try (Context context = new Context())
//		{
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			cmd.dataset = Mockito.mock( Dataset.class );
//			cmd.initialize();
//			assertTrue( cmd.isCanceled() );
//		}
//	}
//
//	/** A 2-level pyramid produces exactly the choices ["Resolution 0", "Resolution 1"]. */
//	@Test
//	void initializeCreatesOneChoicePerResolutionLevel() throws URISyntaxException
//	{
//		try (Context context = new Context())
//		{
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			cmd.dataset = openPyramid( context );
//			cmd.initialize();
//			assertFalse( cmd.isCanceled() );
//			final MutableModuleItem< ? > item = Cast.unchecked( cmd.getInfo().getInput( "resolutionLevel" ) );
//			assertEquals( Arrays.asList( "Resolution 0", "Resolution 1" ), item.getChoices() );
//		}
//	}
//
//	/** Selecting level 1 opens a new dataset at 32 px width (the half-resolution level). */
//	@Test
//	void runOpensDatasetAtRequestedResolutionLevel() throws URISyntaxException
//	{
//		try (Context context = new Context())
//		{
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			cmd.dataset = openPyramid( context );
//			cmd.setInput( "resolutionLevel", "Resolution 1" );
//			cmd.run();
//			final DatasetService datasetService = context.getService( DatasetService.class );
//			assertEquals( 1, datasetService.getDatasets().size() );
//			assertEquals( 32, datasetService.getDatasets().get( 0 ).getImgPlus().dimension( 0 ) );
//			closeDisplays( context );
//		}
//	}
//
//	/** The newly opened dataset shares the same {@link PyramidalDataset#getPyramidal5DImageData()} instance as the source. */
//	@Test
//	void runReusesSamePyramidDataAcrossResolutionLevels() throws URISyntaxException
//	{
//		try (Context context = new Context())
//		{
//			final PyramidalDataset< ? > pyramid = openPyramid( context );
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			cmd.dataset = pyramid;
//			cmd.setInput( "resolutionLevel", "Resolution 1" );
//			cmd.run();
//			final DatasetService datasetService = context.getService( DatasetService.class );
//			assertInstanceOf( PyramidalDataset.class, datasetService.getDatasets().get( 0 ) );
//			final PyramidalDataset< ? > levelDataset = Cast.unchecked( datasetService.getDatasets().get( 0 ) );
//			assertSame( pyramid.getPyramidal5DImageData(), levelDataset.getPyramidal5DImageData() );
//			closeDisplays( context );
//		}
//	}
//
//	/**
//	 * Opening in BDV (simulated via {@link BdvFocusService#notifyBdvWindowFocused}) and then
//	 * running the command produces a level dataset sharing the same {@link sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData}.
//	 */
//	@Test
//	void runAfterBdvOpenSharesSamePyramidData() throws URISyntaxException
//	{
//		try (Context context = new Context())
//		{
//			final PyramidalDataset< ? > bdvDataset = openPyramid( context );
//			final BdvFocusService bdvHandleService = context.getService( BdvFocusService.class );
//			bdvHandleService.notifyBdvWindowFocused( bdvDataset );
//
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			// dataset intentionally not set – simulates invocation from a BDV context
//			cmd.initialize();
//			assertFalse( cmd.isCanceled() );
//			cmd.setInput( "resolutionLevel", "Resolution 1" );
//			cmd.run();
//
//			final DatasetService datasetService = context.getService( DatasetService.class );
//			final List< Dataset > datasets = datasetService.getDatasets();
//			assertEquals( 1, datasets.size() );
//			final PyramidalDataset< ? > levelDataset = Cast.unchecked( datasets.get( 0 ) );
//			assertSame( bdvDataset.getPyramidal5DImageData(), levelDataset.getPyramidal5DImageData() );
//			closeDisplays( context );
//		}
//	}
//
//	/**
//	 * Opening in IJ2 at the highest resolution and then running the command produces
//	 * two datasets sharing the same {@link sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData}.
//	 */
//	@Test
//	void runAfterIj2OpenSharesSamePyramidData() throws URISyntaxException
//	{
//		try (Context context = new Context())
//		{
//			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
//			new ZarrOpenActions( path.toUri(), context ).openIJWithImage();
//
//			final DatasetService datasetService = context.getService( DatasetService.class );
//			assertEquals( 1, datasetService.getDatasets().size() );
//			final PyramidalDataset< ? > ij2Dataset = Cast.unchecked( datasetService.getDatasets().get( 0 ) );
//
//			final OpenResolutionLevelCommand cmd = createCommand( context );
//			cmd.dataset = ij2Dataset;
//			cmd.initialize();
//			assertFalse( cmd.isCanceled() );
//			cmd.setInput( "resolutionLevel", "Resolution 1" );
//			cmd.run();
//
//			assertEquals( 2, datasetService.getDatasets().size() );
//			final PyramidalDataset< ? > levelDataset = Cast.unchecked( datasetService.getDatasets().get( 1 ) );
//			assertSame( ij2Dataset.getPyramidal5DImageData(), levelDataset.getPyramidal5DImageData() );
//			closeDisplays( context );
//		}
//	}
//
//	/**
//	 * Regression: when an IJ dataset is open and the user switches to BDV, running the command
//	 * must use the BDV dataset (the most recently focused one), not the IJ dataset that
//	 * SciJava's preprocessor injects as the "active" dataset.
//	 */
//	@Test
//	void testOpenResolutionLevelOfBdvDatasetWhenIjDatasetAlsoOpen() throws URISyntaxException
//	{
//		final Path path1 = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr" );
//		final Path path2 = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr" );
//		try (Context context = new Context())
//		{
//			try
//			{
//				new ZarrOpenActions( path1.toUri(), context ).openIJWithImage();
//				final DatasetService datasetService = context.getService( DatasetService.class );
//				final PyramidalDataset< ? > ijDataset = Cast.unchecked( datasetService.getDatasets().get( 0 ) );
//
//				new ZarrOpenActions( path2.toUri(), context ).openBDVWithImage();
//
//				final OpenResolutionLevelCommand cmd = createCommand( context );
//				// simulate SciJava's preprocessor injecting the active IJ dataset
//				cmd.dataset = ijDataset;
//				cmd.initialize();
//				assertFalse( cmd.isCanceled() );
//				cmd.setInput( "resolutionLevel", "Resolution 1" );
//				cmd.run();
//
//				// resolution 1 of the BDV (2D) dataset must be opened, not the IJ (5D) one
//				assertEquals( 3, datasetService.getDatasets().size() );
//				assertArrayEquals( new long[] { 32, 32 }, datasetService.getDatasets().get( 2 ).dimensionsAsLongArray() );
//			}
//			finally
//			{
//				closeDisplays( context );
//				closeWindows();
//			}
//		}
//	}
//
//	/**
//	 * Regression for the focus lifecycle: after opening in IJ, then opening in BDV, then switching
//	 * focus back to the IJ window, running the command must operate on the IJ dataset again — the
//	 * earlier BDV focus must not stay "sticky" and shadow the IJ dataset the user returned to.
//	 */
//	@Test
//	@SuppressWarnings( "all" )
//	void testOpenResolutionLevelOfIjDatasetWhenFocusReturnsFromBdv() throws URISyntaxException, InterruptedException
//	{
//		final Path path1 = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr" );
//		final Path path2 = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr" );
//		try (Context context = new Context())
//		{
//			try
//			{
//				new ZarrOpenActions( path1.toUri(), context ).openIJWithImage();
//				Thread.sleep( 100 ); // give the IJ window time to open and be registered by the BdvFocusService
//				final DatasetService datasetService = context.getService( DatasetService.class );
//				final PyramidalDataset< ? > ijDataset = Cast.unchecked( datasetService.getDatasets().get( 0 ) );
//
//				new ZarrOpenActions( path2.toUri(), context ).openBDVWithImage();
//				Thread.sleep( 100 );
//
//				// user clicks the IJ window again: the KeyboardFocusManager listener observes the
//				// ImageWindow gaining focus, which we simulate here
//				context.getService( BdvFocusService.class ).notifyImageJWindowFocused();
//
//				final OpenResolutionLevelCommand cmd = createCommand( context );
//				// SciJava's preprocessor injects the now-active IJ dataset
//				cmd.dataset = ijDataset;
//				cmd.initialize();
//				assertFalse( cmd.isCanceled() );
//				cmd.setInput( "resolutionLevel", "Resolution 1" );
//				cmd.run();
//
//				// resolution 1 of the IJ (5D) dataset must be opened, not the BDV (2D) one
//				assertEquals( 3, datasetService.getDatasets().size() );
//				final PyramidalDataset< ? > levelDataset = Cast.unchecked( datasetService.getDatasets().get( 2 ) );
//				assertSame( ijDataset.getPyramidal5DImageData(), levelDataset.getPyramidal5DImageData() );
//			}
//			finally
//			{
//				closeDisplays( context );
//				closeWindows();
//			}
//		}
//	}
//
//	// --- helpers ---
//
//	private static OpenResolutionLevelCommand createCommand( final Context context )
//	{
//		final OpenResolutionLevelCommand cmd = new OpenResolutionLevelCommand();
//		cmd.setContext( context );
//		return cmd;
//	}
//
//	private static PyramidalDataset< ? > openPyramid( final Context context ) throws URISyntaxException
//	{
//		final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
//		return Pyramidal5DImageData.openWithN5( context, path.toUri(), null ).asPyramidalDataset();
//	}
//
//	private static void closeDisplays( final Context context )
//	{
//		final DisplayService displayService = context.getService( DisplayService.class );
//		if ( displayService != null )
//			displayService.getDisplays().forEach( Display::close );
//	}
//
//	private static void closeWindows()
//	{
//		SwingUtilities.invokeLater( () -> {
//			for ( Window window : Window.getWindows() )
//				window.dispose();
//		} );
//	}
}
