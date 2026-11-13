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
package ome.zarr.fijiui.open.openers;

import static ome.zarr.fijiui.open.options.OmeZarrOpeningSettings.DEFAULT_BACKEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Window;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;

import ome.zarr.ZarrTestUtils;
import ome.zarr.fiji.bdv.PyramidalBdv;
import ome.zarr.fiji.plugins.ActiveViewerService;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fiji.read.OmeZarr;

/**
 * Opens a single-level OME-Zarr in Labkit. A single level needs no level dialog, so the whole
 * {@link LabkitOpener#open} runs, up to registering Labkit's viewer.
 */
class LabkitOpenerTest
{
	private static final String SINGLE_LEVEL = "ome/zarr/testdata/single_resolution_testing/single_resolution_dataset_v5.ome.zarr";

	@Test
	void registersLabkitsViewerUntilItsWindowCloses() throws Exception
	{
		try (Context context = new Context())
		{
			final OmeZarr omeZarr = new OmeZarr( ZarrTestUtils.resourcePath( SINGLE_LEVEL ).toUri(), context,
					DEFAULT_BACKEND.createBackend() );
			final PyramidalService pyramidalService = context.getService( PyramidalService.class );

			new LabkitOpener().open( omeZarr );

			final Component viewer = context.getService( ActiveViewerService.class ).getActiveViewer();
			assertInstanceOf( ViewerPanel.class, viewer );
			final Window window = SwingUtilities.getWindowAncestor( viewer );
			assertNotNull( window );
			final PyramidalBdv< ? > pyramidal = assertInstanceOf( PyramidalBdv.class, pyramidalService.getActivePyramidal() );
			assertSame( omeZarr.readContents(), pyramidal.getPyramidContents() );
			assertEquals( 1, pyramidalService.getPyramidals().size() );
			assertEquals( pyramidal.getPyramidContents().numChannels(), countShown( pyramidal, ( ViewerPanel ) viewer ) );

			window.dispose();
			SwingUtilities.invokeAndWait( () -> {} ); // wait until windowClosed has been processed
			assertTrue( pyramidalService.getPyramidals().isEmpty() );
			assertEquals( 0, pyramidal.getReferencesCount() );
		}
	}

	/** How many of the viewer's sources show {@code pyramidal}, next to the ones Labkit adds for labels. */
	private static long countShown( final PyramidalBdv< ? > pyramidal, final ViewerPanel viewer )
	{
		return viewer.state().getSources().stream().map( SourceAndConverter::getSpimSource )
				.filter( spimSource -> pyramidal.asSources().stream().anyMatch( own -> own.getSpimSource() == spimSource ) )
				.count();
	}
}
