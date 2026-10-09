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
package ome.zarr.fijiui.open;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.BdvHandle;
import ij.IJ;
import ome.zarr.fiji.read.OmeZarr;
import ome.zarr.fijiui.open.options.OmeZarrOpeningSettings;
import ome.zarr.fijiui.util.ClipboardUtils;
import ome.zarr.imglib2.ZarrUtils;

/**
 * Lets OME-Zarrs be dropped onto the image area of a BigDataViewer window, which adds them to
 * that window via {@link OmeZarr#showInBdv(BdvHandle)}. Accepts files and folders, e.g., from
 * Finder or Explorer, and URLs, e.g., from a browser.
 * <p>
 * BDV itself accepts no drops on its image area, so nothing competes with this. Each dropped
 * location is parsed like typed or pasted text ({@link ClipboardUtils#stringToUri}) and checked
 * with {@link ZarrUtils#isZarr(URI)}, like every other entry route. Parsing, check and reading run
 * on a background thread, since each may go over the network or show an error dialog.
 */
public class OmeZarrDropListener extends DropTargetAdapter
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final BdvHandle bdvHandle;

	private final Context context;

	private OmeZarrDropListener( final BdvHandle bdvHandle, final Context context )
	{
		this.bdvHandle = bdvHandle;
		this.context = context;
	}

	/**
	 * Makes the image area of {@code bdvHandle}'s window accept dropped OME-Zarrs.
	 * <p>
	 * Dropped OME-Zarrs are read with the backend and preferred width the user has configured at
	 * the time of the drop, and failures are reported via {@code IJ::error}.
	 *
	 * @param bdvHandle the BigDataViewer to add dropped OME-Zarrs to
	 * @param context the SciJava context the settings are read from
	 */
	public static void install( final BdvHandle bdvHandle, final Context context )
	{
		new DropTarget( bdvHandle.getViewerPanel().getDisplayComponent(), DnDConstants.ACTION_COPY,
				new OmeZarrDropListener( bdvHandle, context ) );
	}

	@Override
	public void dragEnter( final DropTargetDragEvent event )
	{
		if ( event.isDataFlavorSupported( DataFlavor.javaFileListFlavor )
				|| event.isDataFlavorSupported( DataFlavor.stringFlavor ) )
			event.acceptDrag( DnDConstants.ACTION_COPY );
		else
			event.rejectDrag();
	}

	@Override
	public void drop( final DropTargetDropEvent event )
	{
		event.acceptDrop( DnDConstants.ACTION_COPY );
		final List< String > locations = droppedLocations( event.getTransferable() );
		event.dropComplete( !locations.isEmpty() );
		if ( !locations.isEmpty() )
			new Thread( () -> locations.forEach( this::add ), "OME-Zarr drop onto BigDataViewer" ).start();
	}

	private void add( final String location )
	{
		final URI uri = ClipboardUtils.stringToUri( location, IJ::error );
		if ( uri == null )
			return;
		if ( !ZarrUtils.isZarr( uri ) )
		{
			IJ.error( "The dropped location does not appear to be an OME-Zarr dataset:\n" + uri + "." );
			return;
		}
		final OmeZarrOpeningSettings settings =
				OmeZarrOpeningSettings.loadSettingsFromPreferences( context.getService( PrefService.class ) );
		OmeZarrOpenActions.omeZarrFor( uri, context, settings, IJ::error ).showInBdv( bdvHandle );
	}

	/**
	 * The locations in a drop, as text: a path per file or folder if the drop carries a file list,
	 * else one location per line of text, which is how URLs arrive from a browser (and files on some
	 * Linux desktops, as {@code file:} URIs). They are turned into URIs later, off the event thread,
	 * since {@link ClipboardUtils#stringToUri} may report an error in a dialog.
	 */
	static List< String > droppedLocations( final Transferable transferable )
	{
		final List< String > locations = new ArrayList<>();
		try
		{
			if ( transferable.isDataFlavorSupported( DataFlavor.javaFileListFlavor ) )
			{
				for ( final Object file : ( List< ? > ) transferable.getTransferData( DataFlavor.javaFileListFlavor ) )
					locations.add( file.toString() );
			}
			else if ( transferable.isDataFlavorSupported( DataFlavor.stringFlavor ) )
			{
				for ( final String line : ( ( String ) transferable.getTransferData( DataFlavor.stringFlavor ) ).split( "\\R" ) )
				{
					final String location = line.trim();
					// text/uri-list allows comment lines starting with '#'
					if ( !location.isEmpty() && !location.startsWith( "#" ) )
						locations.add( location );
				}
			}
		}
		catch ( final UnsupportedFlavorException | IOException e )
		{
			logger.warn( "Could not read the dropped data: {}", e.getMessage() );
			return Collections.emptyList();
		}
		return locations;
	}
}
