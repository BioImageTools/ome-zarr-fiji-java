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
package ome.zarr.fiji.open;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.display.DisplayService;

import bdv.util.BdvHandle;
import net.imagej.DatasetService;
import net.imglib2.util.Cast;

import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.n5.N5PyramidBackend;
import ome.zarr.zarrjava.ZarrJavaPyramidBackend;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.ZarrTestUtils;

/**
 * Direct coverage of the fiji-layer {@link ZarrOpener}: it loads and opens a
 * dataset in ImageJ and BigDataViewer for either backend implementation,
 * without going through any fiji-ui settings or dialogs.
 */
class ZarrOpenerTest
{
	private static final String DATASET = "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr";

	static Stream< PyramidBackend > backends()
	{
		return Stream.of( new N5PyramidBackend(), new ZarrJavaPyramidBackend() );
	}

	@ParameterizedTest
	@MethodSource( "backends" )
	void getContentsLoadsWithChosenBackend( PyramidBackend backend ) throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( DATASET );
		try (Context context = new Context())
		{
			ZarrOpener opener = new ZarrOpener( path.toUri(), context, backend, null );
			PyramidContents< ? > contents = opener.getContents();
			assertNotNull( contents );
			assertEquals( 2, contents.numResolutionLevels() );
			assertEquals( 3, contents.numChannels() );
		}
	}

	@Test
	void openIJWithImageShowsDataset() throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( DATASET );
		try (Context context = new Context())
		{
			new ZarrOpener( path.toUri(), context, new N5PyramidBackend(), null ).openIJWithImage();

			DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 1, datasetService.getDatasets().size() );

			DisplayService displayService = context.getService( DisplayService.class );
			SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the show
			assertNotNull( displayService.getActiveDisplay() );
			displayService.getActiveDisplay().close();
		}
	}

	@Test
	void openBDVWithImageRegistersWithPyramidalService() throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( DATASET );
		try (Context context = new Context())
		{
			BdvHandle bdvHandle =
					Cast.unchecked( new ZarrOpener( path.toUri(), context, new N5PyramidBackend(), null ).openBDVWithImage() );
			assertNotNull( bdvHandle );

			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 1, pyramidalService.getPyramidals().size() );

			bdvHandle.close();
			SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the close
			assertEquals( 0, pyramidalService.getPyramidals().size() );
		}
	}
}
