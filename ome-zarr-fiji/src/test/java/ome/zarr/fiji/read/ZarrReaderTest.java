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
package ome.zarr.fiji.read;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.imagej.DatasetService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;
import org.scijava.display.DisplayService;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import bdv.util.BdvHandle;

import javax.swing.SwingUtilities;

import ome.zarr.ZarrTestUtils;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.n5.N5PyramidBackend;
import ome.zarr.zarrjava.ZarrJavaPyramidBackend;

/**
 * Direct coverage of the fiji-layer {@link ZarrReader}: it reads and opens a
 * dataset in ImageJ and BigDataViewer for either backend implementation,
 * without going through any fiji-ui settings or dialogs.
 * <p>
 */
class ZarrReaderTest
{
	private static final String DATASET = "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr";

	static Stream< PyramidBackend > backends()
	{
		return Stream.of( new N5PyramidBackend(), new ZarrJavaPyramidBackend() );
	}

	static Stream< String > omeZarrExamples()
	{
		return ZarrTestUtils.omeZarrExamples();
	}

	@ParameterizedTest
	@MethodSource( "backends" )
	void getContentsLoadsWithChosenBackend( PyramidBackend backend ) throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( DATASET );
		try (Context context = new Context())
		{
			ZarrReader opener = new ZarrReader( path.toUri(), context, backend, null );
			PyramidContents< ? > contents = opener.getContents();
			assertNotNull( contents );
			assertEquals( 2, contents.numResolutionLevels() );
			assertEquals( 3, contents.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void openIJWithImageShowsDataset( String resource ) throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		try (Context context = new Context())
		{
			new ZarrReader( path.toUri(), context, new N5PyramidBackend(), null ).openIJWithImage();

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
			final ZarrReader opener = new ZarrReader( path.toUri(), context, new N5PyramidBackend(), null );
			BdvHandle bdvHandle = assertInstanceOf( BdvHandle.class, opener.openBDVWithImage() );

			PyramidalService pyramidalService = context.getService( PyramidalService.class );
			assertEquals( 1, pyramidalService.getPyramidals().size() );

			bdvHandle.close();
			SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the close
			assertEquals( 0, pyramidalService.getPyramidals().size() );
		}
	}

	@ParameterizedTest
	@MethodSource( "backends" )
	void openIJWithImageOpensSingleResolutionLevelAsImage( PyramidBackend backend ) throws Exception
	{
		String[] singleLevelPaths = {
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/0",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr/0"
		};
		try (Context context = new Context())
		{
			DatasetService datasetService = context.getService( DatasetService.class );
			DisplayService displayService = context.getService( DisplayService.class );
			for ( String levelPath : singleLevelPaths )
			{
				Path path = ZarrTestUtils.resourcePath( levelPath );
				AtomicReference< String > capturedError = new AtomicReference<>();
				new ZarrReader( path.toUri(), context, backend, null, capturedError::set ).openIJWithImage();

				assertEquals( 1, datasetService.getDatasets().size(),
						"Single resolution level should open as a one-level dataset: " + levelPath );
				assertNull( capturedError.get(), "Error handler should not have been called for " + levelPath );

				SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the show
				displayService.getActiveDisplay().close();
				assertEquals( 0, datasetService.getDatasets().size() ); // dereferenced again, so the next level starts clean
			}
		}
	}

	/**
	 * A nested Zarr v3 level has no parent multiscales group to state a scale or
	 * unit, so its calibration is a placeholder and the user is asked before it is
	 * shown — declining opens nothing, and is not an error.
	 * <p>
	 * NB: for OME-Zarr v0.4 the code does not support nested multiscales at all, so
	 * only the v0.5 dataset can exercise this.
	 */
	@ParameterizedTest
	@MethodSource( "backends" )
	@SuppressWarnings( "java:S1612" )
	void openIJWithImageAsksBeforeOpeningUncalibratedImage( PyramidBackend backend ) throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/single_resolution_testing/nested_multiscale_v5.ome.zarr/sub/0" );
		try (Context context = new Context())
		{
			DatasetService datasetService = context.getService( DatasetService.class );
			DisplayService displayService = context.getService( DisplayService.class );
			AtomicReference< String > capturedError = new AtomicReference<>();
			AtomicReference< String > shownMessage = new AtomicReference<>();

			ZarrReader declining = new ZarrReader( path.toUri(), context, backend, null, capturedError::set,
					message -> {
						shownMessage.set( message );
						return false;
					} );
			assertDoesNotThrow( () -> declining.openIJWithImage() );

			assertTrue( datasetService.getDatasets().isEmpty(),
					"Nothing must be opened when the confirmation is declined" );
			assertNull( capturedError.get(), "Declining is a user choice, not an error" );
			assertNotNull( shownMessage.get(), "The user should have been asked" );
			assertTrue( shownMessage.get().contains( "no calibration" ),
					"Unexpected confirmation message: " + shownMessage.get() );

			new ZarrReader( path.toUri(), context, backend, null, capturedError::set, message -> true )
					.openIJWithImage();

			assertEquals( 1, datasetService.getDatasets().size(), "Accepting must open the image" );
			PyramidalDataset opened =
					assertInstanceOf( PyramidalDataset.class, context.getService( PyramidalService.class ).getPyramidals().get( 0 ) );
			assertTrue( opened.getPyramidContents().hasPlaceholderCalibration,
					"The opened image still knows its calibration was made up" );

			SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the show
			displayService.getActiveDisplay().close();
		}
	}

	@ParameterizedTest
	@MethodSource( "backends" )
	@SuppressWarnings( "java:S1612" )
	void openIJWithImageReportsInvalidImagePaths( PyramidBackend backend ) throws Exception
	{
		// These are chunk files inside an array, not openable OME-Zarr nodes.
		String[] invalidPaths = {
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/0/0",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr/0/c/0"
		};
		try (Context context = new Context())
		{
			DatasetService datasetService = context.getService( DatasetService.class );
			for ( String invalidPath : invalidPaths )
			{
				Path path = ZarrTestUtils.resourcePath( invalidPath );
				AtomicReference< String > capturedError = new AtomicReference<>();
				ZarrReader opener = new ZarrReader( path.toUri(), context, backend, null, capturedError::set );

				assertDoesNotThrow( () -> opener.openIJWithImage(), "Opening " + invalidPath + " should not throw" );
				assertNotNull( capturedError.get(), "Error handler should have been called for " + invalidPath );
				assertTrue( datasetService.getDatasets().isEmpty(),
						"Nothing must be opened for the non-image path " + invalidPath );
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "backends" )
	@SuppressWarnings( "java:S1612" )
	void openIJWithImageReportsBioformats2rawCollectionRootAsMultiImage( PyramidBackend backend ) throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/bioformats2raw_testing/bf2raw_dataset_v5.ome.zarr" );
		try (Context context = new Context())
		{
			AtomicReference< String > capturedError = new AtomicReference<>();
			ZarrReader opener = new ZarrReader( path.toUri(), context, backend, null, capturedError::set );

			assertDoesNotThrow( () -> opener.openIJWithImage() );
			assertTrue( context.getService( DatasetService.class ).getDatasets().isEmpty(),
					"Multi-image collection must not be opened as a single multiscale image" );
			assertNotNull( capturedError.get(), "Error handler should have been called for backend " + backend );
			assertTrue( capturedError.get().contains( "multiple images" ),
					"Expected multi-image message from backend, got: " + capturedError.get() );
		}
	}

	@ParameterizedTest
	@MethodSource( "backends" )
	@SuppressWarnings( "java:S1612" )
	void openIJWithImageOpensBioformats2rawCollectionChild( PyramidBackend backend ) throws Exception
	{
		String[] childPaths = {
				"ome/zarr/testdata/bioformats2raw_testing/bf2raw_dataset_v5.ome.zarr/0",
				"ome/zarr/testdata/bioformats2raw_testing/bf2raw_dataset_v5.ome.zarr/1"
		};
		try (Context context = new Context())
		{
			DatasetService datasetService = context.getService( DatasetService.class );
			DisplayService displayService = context.getService( DisplayService.class );
			for ( String childPath : childPaths )
			{
				Path path = ZarrTestUtils.resourcePath( childPath );
				AtomicReference< String > capturedError = new AtomicReference<>();
				ZarrReader opener = new ZarrReader( path.toUri(), context, backend, null, capturedError::set );

				assertDoesNotThrow( () -> opener.openIJWithImage(), "Opening child image " + childPath + " should not throw" );
				assertEquals( 1, datasetService.getDatasets().size(),
						"Child image " + childPath + " should be opened as a multiscale image" );
				assertNull( capturedError.get(),
						"Error handler should not have been called for child " + childPath + ", got: " + capturedError.get() );

				SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the show
				displayService.getActiveDisplay().close();
				assertEquals( 0, datasetService.getDatasets().size() ); // dereferenced again, so the next child starts clean
			}
		}
	}

	/**
	 * No level of {@link #DATASET} is 10 pixels wide or narrower (level 0 is 64,
	 * level 1 is 32), so the opener offers the coarsest level and opens it only when
	 * the confirmation is accepted.
	 */
	@Test
	@SuppressWarnings( "java:S1612" )
	void openIJWithImageAsksBeforeOpeningLevelWiderThanPreferred() throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( DATASET );
		try (Context context = new Context())
		{
			DatasetService datasetService = context.getService( DatasetService.class );
			DisplayService displayService = context.getService( DisplayService.class );
			AtomicReference< String > capturedError = new AtomicReference<>();
			AtomicReference< String > shownMessage = new AtomicReference<>();

			ZarrReader declining = new ZarrReader( path.toUri(), context, new N5PyramidBackend(), 10, capturedError::set,
					message -> {
						shownMessage.set( message );
						return false;
					} );
			assertDoesNotThrow( () -> declining.openIJWithImage() );

			assertTrue( datasetService.getDatasets().isEmpty(),
					"Nothing must be opened when the confirmation is declined" );
			assertNull( capturedError.get(), "Declining is a user choice, not an error" );
			assertNotNull( shownMessage.get(), "The user should have been asked" );
			assertTrue( shownMessage.get().contains( "wider than your preferred maximum width" ),
					"Unexpected confirmation message: " + shownMessage.get() );
			assertTrue( shownMessage.get().contains( "2 resolution levels" ),
					"A multi-level dataset should say that no level is narrow enough: " + shownMessage.get() );

			ZarrReader accepting = new ZarrReader( path.toUri(), context, new N5PyramidBackend(), 10, capturedError::set,
					message -> true );
			accepting.openIJWithImage();

			assertEquals( 1, datasetService.getDatasets().size(), "Accepting must open the image" );
			PyramidalDataset opened =
					assertInstanceOf( PyramidalDataset.class, context.getService( PyramidalService.class ).getPyramidals().get( 0 ) );
			assertArrayEquals( new long[] { 32, 32, 8, 3, 4 }, opened.getImgPlus().dimensionsAsLongArray(),
					"The coarsest level is the closest match to the preferred width" );

			SwingUtilities.invokeAndWait( () -> {} );
			displayService.getActiveDisplay().close();
		}
	}

	/**
	 * BigDataViewer shows the whole pyramid, so the preferred width — a limit on the
	 * single level an ImageJ window holds — must neither be checked nor asked about.
	 */
	@Test
	void openBDVWithImageIgnoresPreferredWidth() throws Exception
	{
		Path path = ZarrTestUtils.resourcePath( DATASET );
		try (Context context = new Context())
		{
			AtomicReference< String > capturedError = new AtomicReference<>();
			ZarrReader opener = new ZarrReader( path.toUri(), context, new N5PyramidBackend(), 10, capturedError::set,
					message -> {
						throw new AssertionError( "BDV must not ask about the preferred width: " + message );
					} );
			Object opened = opener.openBDVWithImage();

			assertNull( capturedError.get(), "Opening should not have failed, got: " + capturedError.get() );
			BdvHandle bdvHandle = assertInstanceOf( BdvHandle.class, opened, "BDV should open regardless of the preferred width" );
			bdvHandle.close();
			SwingUtilities.invokeAndWait( () -> {} ); // let Swing process the close
		}
	}
}
