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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.util.Cast;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.scijava.Context;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.module.MutableModuleItem;

import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class OpenResolutionLevelCommandTest
{
	// 2 resolution levels: level 0 = 64x64x16, level 1 = 32x32x8
	private static final String PYRAMID_RESOURCE = "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr";

	/** Null dataset cancels the command before the dialog is shown. */
	@Test
	void initializeCancelsWhenNoDatasetIsOpen()
	{
		try (Context context = new Context())
		{
			final OpenResolutionLevelCommand cmd = createCommand( context );
			cmd.dataset = null;
			cmd.initialize();
			assertTrue( cmd.isCanceled() );
		}
	}

	/** A non-pyramidal dataset type cancels the command before the dialog is shown. */
	@Test
	void initializeCancelsWhenDatasetIsNotAPyramidalDataset()
	{
		try (Context context = new Context())
		{
			final OpenResolutionLevelCommand cmd = createCommand( context );
			cmd.dataset = Mockito.mock( Dataset.class );
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
			cmd.dataset = openPyramid( context );
			cmd.initialize();
			assertFalse( cmd.isCanceled() );
			final MutableModuleItem< ? > item = Cast.unchecked( cmd.getInfo().getInput( "resolutionLevel" ) );
			assertEquals( Arrays.asList( "Resolution 0", "Resolution 1" ), item.getChoices() );
		}
	}

	/** Selecting level 1 opens a new dataset at 32 px width (the half-resolution level). */
	@Test
	void runOpensDatasetAtRequestedResolutionLevel() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final OpenResolutionLevelCommand cmd = createCommand( context );
			cmd.dataset = openPyramid( context );
			cmd.setInput( "resolutionLevel", "Resolution 1" );
			cmd.run();
			final DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 1, datasetService.getDatasets().size() );
			assertEquals( 32, datasetService.getDatasets().get( 0 ).getImgPlus().dimension( 0 ) );
			closeDisplays( context );
		}
	}

	/** The newly opened dataset shares the same {@link PyramidalDataset#getPyramidal5DImageData()} instance as the source. */
	@Test
	void runReusesSamePyramidDataAcrossResolutionLevels() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PyramidalDataset< ? > pyramid = openPyramid( context );
			final OpenResolutionLevelCommand cmd = createCommand( context );
			cmd.dataset = pyramid;
			cmd.setInput( "resolutionLevel", "Resolution 1" );
			cmd.run();
			final DatasetService datasetService = context.getService( DatasetService.class );
			assertInstanceOf( PyramidalDataset.class, datasetService.getDatasets().get( 0 ) );
			final PyramidalDataset< ? > levelDataset = Cast.unchecked( datasetService.getDatasets().get( 0 ) );
			assertSame( pyramid.getPyramidal5DImageData(), levelDataset.getPyramidal5DImageData() );
			closeDisplays( context );
		}
	}

	// --- helpers ---

	private static OpenResolutionLevelCommand createCommand( final Context context )
	{
		final OpenResolutionLevelCommand cmd = new OpenResolutionLevelCommand();
		cmd.setContext( context );
		return cmd;
	}

	private static PyramidalDataset< ? > openPyramid( final Context context ) throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
		return Pyramidal5DImageData.openWithN5( context, path.toUri(), null ).asPyramidalDataset();
	}

	private static void closeDisplays( final Context context )
	{
		final DisplayService displayService = context.getService( DisplayService.class );
		if ( displayService != null )
			displayService.getDisplays().forEach( Display::close );
	}
}
