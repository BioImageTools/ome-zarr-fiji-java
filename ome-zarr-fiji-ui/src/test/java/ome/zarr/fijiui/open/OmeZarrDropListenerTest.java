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
package ome.zarr.fijiui.open;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import bdv.util.BdvHandle;
import bdv.viewer.ViewerStateChange;
import ome.zarr.ZarrTestUtils;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fijiui.open.openers.BdvAddToActiveOpener;

class OmeZarrDropListenerTest
{
	private static final String SPOTS = "ome/zarr/testdata/bdv_testing/logo_spots.ome.zarr";

	private static final String LABELS = "ome/zarr/testdata/bdv_testing/logo_labels.ome.zarr";

	/**
	 * Open the spots with "BigDataViewer (add to active window)", then drop
	 * the labels folder onto the window's image area, and try to find both in its sources.
	 */
	@Test
	void droppingAFolderAddsItToTheWindow() throws Exception
	{
		try (Context context = new Context())
		{
			new BdvAddToActiveOpener()
					.open( OmeZarrOpenActions.withDefaultBackend( ZarrTestUtils.resourcePath( SPOTS ).toUri(), context ) );
			PyramidalBdv< ? > spots =
					assertInstanceOf( PyramidalBdv.class, context.getService( PyramidalService.class ).getActivePyramidal() );
			BdvHandle bdvHandle = spots.getBdvHandle();
			assertEquals( 1, bdvHandle.getViewerPanel().state().getSources().size() );

			DropTarget dropTarget = bdvHandle.getViewerPanel().getDisplayComponent().getDropTarget();
			assertNotNull( dropTarget, "a BDV window opened by this plugin accepts drops" );
			CountDownLatch sourceAdded = new CountDownLatch( 1 );
			bdvHandle.getViewerPanel().state().changeListeners().add( change -> {
				if ( change == ViewerStateChange.NUM_SOURCES_CHANGED )
					sourceAdded.countDown();
			} );
			File labels = ZarrTestUtils.resourcePath( LABELS ).toFile();
			SwingUtilities.invokeAndWait( () -> dropTarget.drop( dropEvent( dropTarget, labels ) ) );

			assertTrue( sourceAdded.await( 30, TimeUnit.SECONDS ), "the drop is read on a background thread" );
			assertEquals( 2, bdvHandle.getViewerPanel().state().getSources().size() );

			bdvHandle.close();
			SwingUtilities.invokeAndWait( () -> {} ); // wait until all Swing events are processed
		}
	}

	@Test
	void droppedLocationsGivesAPathPerFile()
	{
		File a = new File( "/data/a.ome.zarr" ), b = new File( "/data/b.ome.zarr" );
		assertEquals( Arrays.asList( a.toString(), b.toString() ),
				OmeZarrDropListener.droppedLocations( fileList( Arrays.asList( a, b ) ) ) );
	}

	/** Browsers send URLs as text, one per line, as in {@code text/uri-list}, where '#' starts a comment. */
	@Test
	void droppedLocationsGivesALocationPerLineOfText()
	{
		String uriList = "# dragged from a browser\r\nhttps://example.com/a.ome.zarr\n\n  /data/b.ome.zarr  \n";
		assertEquals( Arrays.asList( "https://example.com/a.ome.zarr", "/data/b.ome.zarr" ),
				OmeZarrDropListener.droppedLocations( new StringSelection( uriList ) ) );
	}

	@Test
	void droppedLocationsIsEmptyForNeitherFilesNorText()
	{
		assertEquals( Collections.emptyList(), OmeZarrDropListener.droppedLocations( mock( Transferable.class ) ) );
	}

	@Test
	void droppedLocationsIsEmptyIfTheDataCannotBeRead() throws Exception
	{
		Transferable unreadable = mock( Transferable.class );
		when( unreadable.isDataFlavorSupported( DataFlavor.stringFlavor ) ).thenReturn( true );
		when( unreadable.getTransferData( DataFlavor.stringFlavor ) ).thenThrow( new IOException( "source app quit" ) );
		assertEquals( Collections.emptyList(), OmeZarrDropListener.droppedLocations( unreadable ) );
	}

	/** {@code files} as Finder or Explorer drag them. */
	private static Transferable fileList( final List< File > files )
	{
		return new Transferable()
		{
			@Override
			public DataFlavor[] getTransferDataFlavors()
			{
				return new DataFlavor[] { DataFlavor.javaFileListFlavor };
			}

			@Override
			public boolean isDataFlavorSupported( final DataFlavor flavor )
			{
				return DataFlavor.javaFileListFlavor.equals( flavor );
			}

			@Override
			public @Nonnull Object getTransferData( final DataFlavor flavor )
			{
				return files;
			}
		};
	}

	/** A drop of {@code file} onto {@code dropTarget}, as Finder or Explorer would make it. */
	private static DropTargetDropEvent dropEvent( final DropTarget dropTarget, final File file )
	{
		final Transferable fileList = fileList( Collections.singletonList( file ) );
		return new DropTargetDropEvent( dropTarget.getDropTargetContext(), new Point( 10, 10 ),
				DnDConstants.ACTION_COPY, DnDConstants.ACTION_COPY )
		{
			@Override
			public Transferable getTransferable()
			{
				return fileList;
			}
		};
	}
}
