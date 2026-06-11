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
package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import bdv.util.BdvHandle;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;

import javax.swing.SwingUtilities;

import sc.fiji.ome.zarr.open.ZarrOpenActions;
import sc.fiji.ome.zarr.pyramid.Pyramidal;

class PyramidalServiceTest
{
	private static final String ZARR_2D = "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr";

	private static final String ZARR_3D = "sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v4.ome.zarr";

	/**
	 * Opening an OME-Zarr in BigDataViewer automatically registers the dataset and sets it as active.
	 * Closing the BDV window unregisters it and clears the active pyramidal.
	 */
	@Test
	void openInBdv_setsActivePyramidal_closingClearsIt() throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( ZARR_2D );
		try (Context context = new Context())
		{
			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			BdvHandle bdvHandle = ( BdvHandle ) new ZarrOpenActions( path.toUri(), context ).openBDVWithImage();
			try
			{
				assertNotNull( pyramidalService.getActivePyramidal() );
				assertSame( pyramidalService.getPyramidals().get( 0 ), pyramidalService.getActivePyramidal() );
				assertEquals( 1, pyramidalService.getPyramidals().size() );
			}
			finally
			{
				bdvHandle.close();
				SwingUtilities.invokeAndWait( () -> {} );
			}
			assertNull( pyramidalService.getActivePyramidal() );
			assertTrue( pyramidalService.getPyramidals().isEmpty() );
		}
	}

	/**
	 * Focusing on a plain (non-OME-Zarr) IJ image clears the active pyramidal.
	 * Focusing the BDV window again restores it.
	 */
	@Test
	void focusingNonOmeZarrIJWindow_clearsActivePyramidal() throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path = ZarrTestUtils.resourcePath( ZARR_2D );
		try (Context context = new Context())
		{
			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			BdvHandle bdvHandle = ( BdvHandle ) new ZarrOpenActions( path.toUri(), context ).openBDVWithImage();
			ImagePlus nonOmeZarrImagePlus = null;
			try
			{
				Pyramidal bdvPyramidal = pyramidalService.getActivePyramidal();
				assertNotNull( bdvPyramidal );

				// Create and show a plain ImageJ image (FIJI built-in synthetic sample)
				nonOmeZarrImagePlus = IJ.createImage( "Demo", "8-bit ramp", 64, 64, 1 );
				nonOmeZarrImagePlus.show();
				SwingUtilities.invokeAndWait( () -> {} );
				ImageWindow nonOmeZarrWindow = nonOmeZarrImagePlus.getWindow();
				assertNotNull( nonOmeZarrWindow );

				// Focusing on a non-OME-Zarr window clears the active pyramidal
				pyramidalService.notifyImageJWindowFocused( nonOmeZarrWindow );
				assertNull( pyramidalService.getActivePyramidal() );
				assertEquals( 1, pyramidalService.getPyramidals().size() ); // BDV still tracked

				// Focusing BDV again restores it
				pyramidalService.notifyBdvWindowFocused( bdvPyramidal );
				assertSame( bdvPyramidal, pyramidalService.getActivePyramidal() );
			}
			finally
			{
				if ( nonOmeZarrImagePlus != null )
					nonOmeZarrImagePlus.close();
				bdvHandle.close();
				SwingUtilities.invokeAndWait( () -> {} );
			}
		}
	}

	/**
	 * With two OME-Zarr datasets open in BDV, the active pyramidal tracks focus correctly.
	 * Closing the non-active window preserves the active one; closing the active window clears it.
	 */
	@Test
	void openTwoBdvDatasets_switchFocusAndCloseInReverseOrder() throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		Path path1 = ZarrTestUtils.resourcePath( ZARR_2D );
		Path path2 = ZarrTestUtils.resourcePath( ZARR_3D );
		try (Context context = new Context())
		{
			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			BdvHandle bdvHandle1 = ( BdvHandle ) new ZarrOpenActions( path1.toUri(), context ).openBDVWithImage();
			BdvHandle bdvHandle2 = ( BdvHandle ) new ZarrOpenActions( path2.toUri(), context ).openBDVWithImage();
			try
			{
				assertEquals( 2, pyramidalService.getPyramidals().size() );
				Pyramidal bdv1 = pyramidalService.getPyramidals().get( 0 ); // first dataset opened
				Pyramidal bdv2 = pyramidalService.getPyramidals().get( 1 ); // second dataset opened, active
				assertSame( bdv2, pyramidalService.getActivePyramidal() );

				// Switch focus to first dataset
				pyramidalService.notifyBdvWindowFocused( bdv1 );
				assertSame( bdv1, pyramidalService.getActivePyramidal() );

				// Close the second (non-active) window — first remains active
				bdvHandle2.close();
				SwingUtilities.invokeAndWait( () -> {} );
				assertSame( bdv1, pyramidalService.getActivePyramidal() );
				assertEquals( 1, pyramidalService.getPyramidals().size() );
				assertSame( bdv1, pyramidalService.getPyramidals().get( 0 ) );
			}
			finally
			{
				bdvHandle1.close();
				SwingUtilities.invokeAndWait( () -> {} );
			}
			assertNull( pyramidalService.getActivePyramidal() );
			assertTrue( pyramidalService.getPyramidals().isEmpty() );
		}
	}
}
