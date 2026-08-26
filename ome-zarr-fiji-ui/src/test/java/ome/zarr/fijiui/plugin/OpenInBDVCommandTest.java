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
package ome.zarr.fijiui.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingUtilities;

import net.imagej.Dataset;
import net.imagej.DatasetService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.command.CommandService;

import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.ZarrTestUtils;

class OpenInBDVCommandTest
{
	@AfterEach
	void closeAllWindows()
	{
		for ( final Window window : Window.getWindows() )
			window.dispose();
	}

	private static void flushEventQueue() throws InterruptedException
	{
		try
		{
			SwingUtilities.invokeAndWait( () -> {} );
		}
		catch ( InvocationTargetException e )
		{
			throw new IllegalStateException( e );
		}
	}

	// 2 resolution levels: level 0 = 64x64x16, level 1 = 32x32x8
	private static final String PYRAMID_RESOURCE = "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr";

	/**
	 * Opening an IJ2 dataset in BDV via the {@link OpenInBDVCommand} should produce a second, distinct
	 * {@link ome.zarr.fiji.Pyramidal} that shares the same {@link ome.zarr.imglib2.PyramidContents}.
	 */
	@Test
	void runAfterIj2OpenCreatesSecondBdvDataset() throws URISyntaxException, ExecutionException, InterruptedException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openIJWithImage();

			final PyramidalService pyramidalService = context.getService( PyramidalService.class );
			final DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 1, datasetService.getDatasets().size() );
			assertEquals( 1, pyramidalService.getPyramidals().size() );

			final Pyramidal ij2Dataset = pyramidalService.getPyramidals().get( 0 );
			// The IJ dataset becomes the active pyramidal only via the asynchronous AWT
			// "activeWindow" event; flush the event queue so it is resolved before the
			// command's preprocessor injects the active dataset.
			flushEventQueue();
			context.getService( CommandService.class ).run( OpenInBDVCommand.class, true ).get();

			final List< Dataset > datasets = datasetService.getDatasets();
			final List< Pyramidal > pyramidals = pyramidalService.getPyramidals();
			assertEquals( 1, datasets.size() );
			assertEquals( 2, pyramidals.size() );
			final Pyramidal bdvDataset = pyramidals.get( 1 );
			assertNotSame( ij2Dataset, bdvDataset );
			assertSame( ij2Dataset.getPyramidContents(), bdvDataset.getPyramidContents() );
		}
	}

	/**
	 * When a dataset is already open in BDV and the command is invoked again (with no active
	 * {@link net.imagej.display.ImageDisplay}), it should fall back to the focused BDV dataset via
	 * {@link PyramidalService} and produce a second, distinct {@link Pyramidal} sharing
	 * the same {@link ome.zarr.imglib2.PyramidContents}.
	 */
	@Test
	@Disabled( "Occasionally fails in the MacOS CI" )
	void runAfterBdvOpenCreatesSecondBdvDataset() throws URISyntaxException, ExecutionException, InterruptedException
	{
		try (Context context = new Context())
		{
			// Simulate the first BDV open.
			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
			new ZarrOpenActions( path.toUri(), context ).openBDVWithImage();

			final DatasetService datasetService = context.getService( DatasetService.class );
			final PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 0, datasetService.getDatasets().size() );
			assertEquals( 1, pyramidalService.getPyramidals().size() );

			context.getService( CommandService.class ).run( OpenInBDVCommand.class, true ).get();

			final List< Dataset > datasets = datasetService.getDatasets();
			final List< Pyramidal > pyramidals = pyramidalService.getPyramidals();
			assertEquals( 0, datasets.size() );
			assertEquals( 2, pyramidals.size() );
			final Pyramidal firstBdvPyramidal = pyramidals.get( 0 );
			final Pyramidal secondBdvPyramidal = pyramidals.get( 1 );
			assertNotSame( firstBdvPyramidal, secondBdvPyramidal );
			assertSame( firstBdvPyramidal.getPyramidContents(), secondBdvPyramidal.getPyramidContents() );
		}
	}
}
