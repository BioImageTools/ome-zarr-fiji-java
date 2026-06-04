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

//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotSame;
//import static org.junit.jupiter.api.Assertions.assertSame;
//
//import java.awt.Window;
//import java.net.URISyntaxException;
//import java.nio.file.Path;
//import java.util.List;
//
//import net.imagej.Dataset;
//import net.imagej.DatasetService;
//import net.imglib2.util.Cast;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Test;
//import org.scijava.Context;
//
//import sc.fiji.ome.zarr.open.ZarrOpenActions;
//import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData;
//import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
//import sc.fiji.ome.zarr.util.BdvFocusService;
//import sc.fiji.ome.zarr.util.ZarrTestUtils;

class OpenInBDVCommandTest
{
//	@AfterEach
//	void closeAllWindows()
//	{
//		for ( final Window window : Window.getWindows() )
//			window.dispose();
//	}
//
//	// 2 resolution levels: level 0 = 64x64x16, level 1 = 32x32x8
//	private static final String PYRAMID_RESOURCE = "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr";
//
//	/**
//	 * Opening an IJ2 dataset in BDV via the command should produce a second, distinct
//	 * {@link PyramidalDataset} that shares the same {@link sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData}.
//	 * The original IJ2 dataset must remain in {@link DatasetService} unchanged.
//	 */
//	@Test
//	void runAfterIj2OpenCreatesSecondBdvDataset() throws URISyntaxException
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
//			final OpenInBDVCommand cmd = new OpenInBDVCommand();
//			context.inject( cmd );
//			cmd.dataset = ij2Dataset;
//			cmd.run();
//
//			final List< Dataset > datasets = datasetService.getDatasets();
//			assertEquals( 2, datasets.size() );
//			final PyramidalDataset< ? > bdvDataset = Cast.unchecked( datasets.get( 1 ) );
//			assertNotSame( ij2Dataset, bdvDataset );
//			assertSame( ij2Dataset.getPyramidal5DImageData(), bdvDataset.getPyramidal5DImageData() );
//		}
//	}
//
//	/**
//	 * When a dataset is already open in BDV and the command is invoked again (with no active
//	 * {@link net.imagej.display.ImageDisplay}), it should fall back to the focused BDV dataset via
//	 * {@link BdvFocusService} and produce a second, distinct {@link PyramidalDataset} sharing
//	 * the same {@link sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData}.
//	 */
//	@Test
//	void runAfterBdvOpenCreatesSecondBdvDataset() throws URISyntaxException
//	{
//		try (Context context = new Context())
//		{
//			// Simulate the first BDV open: register the dataset and record focus.
//			final Path path = ZarrTestUtils.resourcePath( PYRAMID_RESOURCE );
//			final PyramidalDataset< ? > firstBdvDataset =
//					Pyramidal5DImageData.openWithN5( context, path.toUri(), null ).asPyramidalDataset();
//			firstBdvDataset.incrementReferences();
//
//			final BdvFocusService bdvFocusService = context.getService( BdvFocusService.class );
//			bdvFocusService.notifyBdvWindowFocused( firstBdvDataset );
//
//			final DatasetService datasetService = context.getService( DatasetService.class );
//			assertEquals( 1, datasetService.getDatasets().size() );
//
//			// Invoke the command without setting dataset – simulates invocation from a BDV context
//			// where no active ImageJ display is present.
//			final OpenInBDVCommand cmd = new OpenInBDVCommand();
//			context.inject( cmd );
//			cmd.run();
//
//			final List< Dataset > datasets = datasetService.getDatasets();
//			assertEquals( 2, datasets.size() );
//			final PyramidalDataset< ? > secondBdvDataset = Cast.unchecked( datasets.get( 1 ) );
//			assertNotSame( firstBdvDataset, secondBdvDataset );
//			assertSame( firstBdvDataset.getPyramidal5DImageData(), secondBdvDataset.getPyramidal5DImageData() );
//		}
//	}
}
