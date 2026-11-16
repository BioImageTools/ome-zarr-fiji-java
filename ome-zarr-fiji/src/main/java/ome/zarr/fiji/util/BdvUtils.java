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
package ome.zarr.fiji.util;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import org.scijava.Context;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.AbstractViewerPanel;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import ome.zarr.fiji.bdv.OmeroDisplay;
import ome.zarr.fiji.bdv.PyramidalBdv;
import ome.zarr.fiji.plugins.ActiveViewerService;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.Omero;

public class BdvUtils
{
	/** The client property of a {@link ViewerPanel} holding its {@link BdvHandle}, see {@link #bdvHandleOf}. */
	private static final String BDV_HANDLE_KEY = BdvHandle.class.getName();

	private BdvUtils()
	{
		// prevent instantiation of this class
	}

	/**
	 * Displays each of {@code contents} as a new {@link PyramidalBdv}, all in one new BigDataViewer
	 * (BDV) window, see {@link #show(PyramidalBdv, BdvHandle)}.
	 *
	 * @param contents the OME-Zarrs to display, in the order their sources are added
	 * @param context the SciJava context of the new pyramidals
	 * @return the {@code BdvHandle} of the new window, or {@code null} if {@code contents} is empty
	 */
	public static BdvHandle showInOneWindow( final List< PyramidContents< ? > > contents, final Context context )
	{
		BdvHandle bdvHandle = null;
		for ( final PyramidContents< ? > pyramidContents : contents )
			bdvHandle = show( new PyramidalBdv<>( context, pyramidContents ), bdvHandle );
		return bdvHandle;
	}

	/**
	 * Displays the given pyramidal in a new BigDataViewer (BDV) window, or adds it to the open one of
	 * {@code bdvHandle}, applies its OMERO channel properties and registers the viewer via
	 * {@link PyramidalBdv#registerViewer}. {@link #bdvHandleOf} finds its handle.<br>
	 * Only a new window is moved to the timepoint the OMERO metadata names. When adding, the
	 * viewer's timepoint is left alone, so the images already shown do not jump.
	 *
	 * @param pyramidalBdv the input {@link PyramidalBdv} to be displayed in BDV
	 * @param bdvHandle the BDV to add it to, or {@code null} to open a new window
	 * @return the {@code BdvHandle} of the window showing the pyramidal
	 */
	public static BdvHandle show( final PyramidalBdv< ? > pyramidalBdv, final BdvHandle bdvHandle )
	{
		final PyramidContents< ? > pyramidContents = pyramidalBdv.getPyramidContents();
		final BdvOptions options = bdvHandle == null
				? BdvOptions.options().frameTitle( pyramidalBdv.getName() )
				: BdvOptions.options().addTo( bdvHandle );
		final BdvHandle shownIn = BdvFunctions.show( pyramidalBdv.asSources(), pyramidContents.numTimepoints(), options ).getBdvHandle();
		shownIn.getViewerPanel().putClientProperty( BDV_HANDLE_KEY, shownIn );

		if ( bdvHandle == null )
			OmeroDisplay.setTimepoint( pyramidContents.omero(), shownIn.getViewerPanel().state() );
		final List< Omero.Channel > omeroChannels = OmeroDisplay.channels( pyramidContents.omero(), pyramidalBdv.asSources().size() );
		OmeroDisplay.apply( omeroChannels, pyramidalBdv.asSources(), shownIn.getConverterSetups(), shownIn.getViewerPanel().state() );

		pyramidalBdv.registerViewer( shownIn.getViewerPanel() );
		return shownIn;
	}

	/**
	 * The {@link BdvHandle} of {@code viewer} if it is the viewer of a BigDataViewer shown via
	 * {@link #show(PyramidalBdv, BdvHandle)}, else {@code null}, e.g., for an
	 * ImageJ window or a BigDataViewer opened by another tool.
	 * <p>
	 * Mirrors the {@code BdvHandle.of( ViewerPanel )} proposed in
	 * <a href="https://github.com/bigdataviewer/bigdataviewer-core/issues/227">bigdataviewer-core#227</a>,
	 * including its client property key. Once that exists, this can delegate to it and reach every
	 * BigDataViewer.
	 *
	 * @param viewer an image viewer as returned by {@link ActiveViewerService#getActiveViewer()}, may be {@code null}
	 * @return the handle to add sources to {@code viewer} with, or {@code null}
	 */
	public static BdvHandle bdvHandleOf( final Component viewer )
	{
		return viewer instanceof ViewerPanel ? ( BdvHandle ) ( ( ViewerPanel ) viewer ).getClientProperty( BDV_HANDLE_KEY ) : null;
	}

	/**
	 * @param pyramidalBdv the {@link PyramidalBdv} the window displays
	 * @param viewer the viewer showing {@code pyramidalBdv}, whose top-level window is tracked.
	 * @param pyramidalService ignored, {@link PyramidalBdv#registerViewer} looks the service up in
	 *   the pyramidal's context
	 * @deprecated Will be removed with the 0.11 release. Use {@link PyramidalBdv#registerViewer}.
	 */
	@Deprecated
	public static void registerViewer( final PyramidalBdv< ? > pyramidalBdv, final AbstractViewerPanel viewer,
			final PyramidalService pyramidalService )
	{
		pyramidalBdv.registerViewer( viewer );
	}

	/**
	 * Increments the reference count for the given {@code pyramidal} and, if {@code pyramidalService} is non-null,
	 * registers {@code window} with it for focus tracking, unconditionally. Undoes both when the window closes.
	 *
	 * @param pyramidalBdv the {@link PyramidalBdv} the window displays
	 * @param window the top-level window to track
	 * @param pyramidalService the service to register with, or {@code null} to only reference-count
	 * @deprecated Will be removed with the 0.11 release. Use {@link PyramidalBdv#registerViewer}, which
	 *   also follows the viewer's current source.
	 */
	@Deprecated
	public static void registerBdvWindow( final PyramidalBdv< ? > pyramidalBdv, final Window window,
			final PyramidalService pyramidalService )
	{
		pyramidalBdv.incrementReferences();
		if ( pyramidalService != null )
			pyramidalService.registerBdvWindow( window, pyramidalBdv );
		window.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosed( final WindowEvent e )
			{
				if ( pyramidalService != null )
					pyramidalService.unregisterBdvWindow( window );
				pyramidalBdv.decrementReferences();
			}
		} );
	}

	/**
	 * @param omeroChannels the list of OMERO channels to apply, one per source and in source order
	 * @param sources the viewer's sources, one per channel and in channel order
	 * @param converterSetups the viewer's converter setups to look {@code sources} up in
	 * @param state the viewer state that decides which sources are visible
	 * @deprecated Will be removed with the 0.11 release. Use {@link OmeroDisplay#apply}.
	 */
	@Deprecated
	public static void setChannelProperties( final List< Omero.Channel > omeroChannels,
			final List< ? extends SourceAndConverter< ? > > sources,
			final ConverterSetups converterSetups, final ViewerState state )
	{
		OmeroDisplay.apply( omeroChannels, sources, converterSetups, state );
	}

	/**
	 * @param omero the dataset's OMERO metadata, may be {@code null}
	 * @param numChannels the number of display sources to be configured
	 * @return the channels to apply, empty if they cannot be applied
	 * @deprecated Will be removed with the 0.11 release. Use {@link OmeroDisplay#channels}.
	 */
	@Deprecated
	public static List< Omero.Channel > omeroChannels( final Omero omero, final int numChannels )
	{
		return OmeroDisplay.channels( omero, numChannels );
	}

	/**
	 * @param omero the dataset's OMERO metadata, may be {@code null}
	 * @param state the viewer state to move
	 * @deprecated Will be removed with the 0.11 release. Use {@link OmeroDisplay#setTimepoint}.
	 */
	@Deprecated
	public static void setTimepoint( final Omero omero, final ViewerState state )
	{
		OmeroDisplay.setTimepoint( omero, state );
	}
}
