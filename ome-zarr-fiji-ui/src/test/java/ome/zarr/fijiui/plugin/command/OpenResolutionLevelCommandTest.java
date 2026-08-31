/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.fijiui.plugin.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.imagej.DatasetService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import org.scijava.module.MutableModuleItem;

import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingUtilities;

import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.ZarrTestUtils;

class OpenResolutionLevelCommandTest
{

	// 2 resolution levels: level 0 = 64x64x16, level 1 = 32x32x8
	private static final String PYRAMID_RESOURCE = "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr";

	/** Null dataset cancels the command before the dialog is shown. */
	@Test
	void initializeCancelsWhenNoDatasetIsOpen()
	{
		try (Context context = new Context())
		{
			final OpenResolutionLevelCommand cmd = createCommand( context );
			cmd.initialize();
			assertTrue( cmd.isCanceled() );
		}
	}

	/** A 2-level pyramid produces exactly the choices ["Resolution 0", "Resolution 1"]. */
	@Test
	void initializeCreatesOneChoicePerResolutionLevel() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final OpenResolutionLevelCommand cmd = createCommand( context );
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openBDVWithImage();
			cmd.initialize();
			assertFalse( cmd.isCanceled() );
			final MutableModuleItem< ? > item = assertInstanceOf( MutableModuleItem.class, cmd.getInfo().getInput( "resolutionLevel" ) );
			assertEquals( Arrays.asList( "Resolution 0", "Resolution 1" ), item.getChoices() );
		}
	}

	/** Selecting level 1 opens a new dataset at 32 px width (the half-resolution level). */
	@Test
	void runOpensDatasetAtRequestedResolutionLevel() throws URISyntaxException, ExecutionException, InterruptedException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openBDVWithImage();

			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "resolutionLevel", "Resolution 1" );
			runCommand( context, inputs );

			final DatasetService datasetService = context.getService( DatasetService.class );
			final PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 1, datasetService.getDatasets().size() );
			assertEquals( 2, pyramidalService.getPyramidals().size() );
			assertEquals( 32, datasetService.getDatasets().get( 0 ).getImgPlus().dimension( 0 ) );
		}
	}

	/** The newly opened dataset shares the same {@link PyramidalDataset#getPyramidContents()} instance as the source. */
	@Test
	void runReusesSamePyramidDataAcrossResolutionLevels() throws URISyntaxException, ExecutionException, InterruptedException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openIJWithImage();

			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "resolutionLevel", "Resolution 1" );
			runCommand( context, inputs );

			final DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 2, datasetService.getDatasets().size() );
			final PyramidalDataset sourceDataset = assertInstanceOf( PyramidalDataset.class, datasetService.getDatasets().get( 0 ) );
			final PyramidalDataset levelDataset = assertInstanceOf( PyramidalDataset.class, datasetService.getDatasets().get( 1 ) );
			assertSame( sourceDataset.getPyramidContents(), levelDataset.getPyramidContents() );
		}
	}

	/**
	 * Opening in BDV and then running the command produces a level dataset sharing the same {@link ome.zarr.imglib2.PyramidContents}.
	 */
	@Test
	void runAfterBdvOpenSharesSamePyramidData() throws URISyntaxException, ExecutionException, InterruptedException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openBDVWithImage();
			final PyramidalService pyramidalService = context.getService( PyramidalService.class );
			final Pyramidal bdvPyramidal = pyramidalService.getPyramidals().get( 0 );

			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "resolutionLevel", "Resolution 1" );
			runCommand( context, inputs );

			assertEquals( 2, pyramidalService.getPyramidals().size() );
			final Pyramidal level1 = pyramidalService.getPyramidals().get( 1 );
			assertEquals( 1, context.getService( DatasetService.class ).getDatasets().size() );
			assertEquals( 2, pyramidalService.getPyramidals().size() );
			assertSame( bdvPyramidal.getPyramidContents(), level1.getPyramidContents() );
		}
	}

	/**
	 * Opening in IJ2 at the highest resolution and then running the command produces
	 * two datasets sharing the same {@link ome.zarr.imglib2.PyramidContents}.
	 */
	@Test
	void runAfterIj2OpenSharesSamePyramidData() throws URISyntaxException, ExecutionException, InterruptedException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openIJWithImage();

			final DatasetService datasetService = context.getService( DatasetService.class );
			final PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 1, datasetService.getDatasets().size() );
			assertEquals( 1, pyramidalService.getPyramidals().size() );
			final Pyramidal ij2Dataset = pyramidalService.getPyramidals().get( 0 );

			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "resolutionLevel", "Resolution 1" );
			runCommand( context, inputs );

			assertEquals( 2, datasetService.getDatasets().size() );
			assertEquals( 2, pyramidalService.getPyramidals().size() );
			final Pyramidal levelDataset = pyramidalService.getPyramidals().get( 1 );
			assertSame( ij2Dataset.getPyramidContents(), levelDataset.getPyramidContents() );
		}
	}

	/**
	 * Regression: when an IJ dataset is open and the user switches to BDV, running the command
	 * must use the BDV dataset (i.e. the most recently focused one), not the IJ dataset that
	 * SciJava's preprocessor injects as the "active" dataset.
	 */
	@Test
	void testOpenResolutionLevelOfBdvDatasetWhenIjDatasetAlsoOpen() throws URISyntaxException, ExecutionException, InterruptedException
	{
		final Path path5d = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr" );
		final Path path2d = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr" );
		try (Context context = new Context())
		{
			new ZarrOpenActions( path5d.toUri(), context ).openIJWithImage();
			new ZarrOpenActions( path2d.toUri(), context ).openBDVWithImage();

			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "resolutionLevel", "Resolution 1" );
			runCommand( context, inputs );

			final DatasetService datasetService = context.getService( DatasetService.class );
			final PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 2, datasetService.getDatasets().size() );
			assertEquals( 3, pyramidalService.getPyramidals().size() );
			// resolution 1 of the BDV (2D) dataset must be opened, not the IJ (5D) one
			assertArrayEquals( new long[] { 32, 32 }, datasetService.getDatasets().get( 1 ).dimensionsAsLongArray() );
		}
	}

	@AfterEach
	void tearDown()
	{
		closeWindows();
	}

	// --- helpers ---

	private static OpenResolutionLevelCommand createCommand( final Context context )
	{
		final OpenResolutionLevelCommand cmd = new OpenResolutionLevelCommand();
		cmd.setContext( context );
		return cmd;
	}

	private static void runCommand( final Context context, final Map< String, Object > inputs )
			throws ExecutionException, InterruptedException
	{
		CommandInfo info = context.getService( CommandService.class ).getCommand( OpenResolutionLevelCommand.class );
		context.getService( ModuleService.class ).run( info, true, inputs ).get();
	}

	private static void closeWindows()
	{
		try
		{
			SwingUtilities.invokeAndWait( () -> {
				for ( Window window : Window.getWindows() )
					window.dispose();
			} );
		}
		catch ( InterruptedException | InvocationTargetException e )
		{
			Thread.currentThread().interrupt();
		}
	}
}
