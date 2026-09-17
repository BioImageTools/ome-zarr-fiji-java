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

import java.awt.Color;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;

import net.imglib2.type.numeric.ARGBType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.Bounds;
import bdv.viewer.ConverterSetupBounds;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.metadata.Omero;

public class BdvUtils
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private BdvUtils()
	{
		// prevent instantiation of this class
	}

	/**
	 * Displays the given pyramidal in a BigDataViewer (BDV) window and registers
	 * it with {@code pyramidalService} for focus tracking.<br>
	 * Increments the pyramidal's reference count and decrements it when the window closes.
	 * If {@code pyramidalService} is non-null, the pyramidal is immediately marked as active.
	 * Later focus changes are picked up centrally by {@link PyramidalService}.
	 *
	 * @param pyramidalBdv the input {@link PyramidalBdv} to be displayed in BDV
	 * @param pyramidalService the service to notify of focus changes, or {@code null} to skip tracking
	 * @return a {@code BdvHandle} instance representing the BDV window
	 */
	public static BdvHandle showBdvAndRegisterWindow( final PyramidalBdv< ? > pyramidalBdv, final PyramidalService pyramidalService )
	{
		BdvHandle bdvHandle = BdvFunctions.show( pyramidalBdv.asSources(), pyramidalBdv.getPyramidContents().numTimepoints(),
				BdvOptions.options().frameTitle( pyramidalBdv.getName() ) ).getBdvHandle();

		final Omero omero = pyramidalBdv.getPyramidContents().omero;
		final List< Omero.Channel > omeroChannels = omeroChannels( omero, pyramidalBdv.asSources().size() );
		setTimepoint( omero, bdvHandle.getViewerPanel().state() );
		setChannelProperties( omeroChannels, pyramidalBdv.asSources(), bdvHandle.getConverterSetups(), bdvHandle.getViewerPanel().state() );

		Container topLevelContainer = bdvHandle.getViewerPanel().getRootPane().getParent();
		if ( topLevelContainer instanceof Window )
		{
			final Window window = ( Window ) topLevelContainer;
			registerBdvWindow( pyramidalBdv, window, pyramidalService );
		}
		return bdvHandle;
	}

	/**
	 * Increments the reference count for the given {@code pyramidal} and, if {@code pyramidalService} is non-null,
	 * registers the BDV window with it for focus tracking. Also installs a listener to decrement the
	 * reference count (and unregister the window) when the BDV window closes.
	 * <p>
	 * Focus switches themselves are observed centrally by {@link PyramidalService} via the AWT
	 * {@link java.awt.KeyboardFocusManager}, so no per-window focus listener is needed here.
	 * @param pyramidalBdv the {@link PyramidalBdv} the window displays
	 * @param window the top-level window to track
	 * @param pyramidalService the service to register with, or {@code null} to only reference-count
	 */
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
	 * Colors each of {@code sources} the way the dataset's OMERO metadata asks
	 * for, gives it that channel's display range, and shows or hides it.
	 * <p>
	 * @param omeroChannels the channels to apply. Nothing is applied if it is empty
	 * @param sources the viewer's sources, one per channel and in channel order
	 * @param converterSetups the viewer's converter setups, to look {@code sources} up in
	 * @param state the viewer state that decides which sources are visible
	 */
	public static void setChannelProperties( final List< Omero.Channel > omeroChannels,
			final List< ? extends SourceAndConverter< ? > > sources,
			final ConverterSetups converterSetups, final ViewerState state )
	{
		if ( omeroChannels.isEmpty() )
			return;
		for ( int channelNumber = 0; channelNumber < sources.size(); channelNumber++ )
		{
			final SourceAndConverter< ? > source = sources.get( channelNumber );
			final Omero.Channel omeroChannel = omeroChannels.get( channelNumber );
			final ConverterSetup converterSetup = converterSetups.getConverterSetup( source );
			converterSetup.setColor( channelColor( omeroChannel ) );
			if ( omeroChannel != null && omeroChannel.window != null )
				setDisplayRangeAndBounds( converterSetup, converterSetups.getBounds(), omeroChannel.window );
			state.setSourceActive( source, isChannelActive( omeroChannel ) );
		}
	}

	/**
	 * Gives {@code converterSetup} the display range an OMERO channel window asks
	 * for — {@code start}/{@code end}, where the brightness slider sits — and
	 * {@code min}/{@code max} as the bounds, how far it may travel.
	 * <p>
	 * The bounds are only taken when the metadata really carries them: Gson leaves
	 * an absent {@code min}/{@code max} at 0/0, which would leave the slider no
	 * room at all. They need no widening to contain {@code start}/{@code end},
	 * which some datasets place outside them, because
	 * {@link ConverterSetupBounds#getBounds} joins the stored bounds with the
	 * current display range on the way out.
	 */
	private static void setDisplayRangeAndBounds( final ConverterSetup converterSetup,
			final ConverterSetupBounds converterSetupBounds, final Omero.Channel.Window window )
	{
		converterSetup.setDisplayRange( window.start, window.end );
		if ( window.max > window.min )
			converterSetupBounds.setBounds( converterSetup, new Bounds( window.min, window.max ) );
	}

	/**
	 * The OMERO channel metadata to apply to display sources. One entry per source and in source order.
	 * <p>
	 * Returns an empty list when there is nothing to apply, i.e., no OMERO metadata at
	 * all, or a channel count that disagrees with the number of sources. Callers must therefore test
	 * {@link List#isEmpty()}, not for {@code null}.
	 *
	 * @param omero the dataset's OMERO metadata, may be {@code null}
	 * @param numChannels the number of display sources to be configured
	 * @return the channels to apply, empty if they cannot be applied
	 */
	public static List< Omero.Channel > omeroChannels( final Omero omero, final int numChannels )
	{
		if ( omero == null || omero.channels == null || omero.channels.isEmpty() )
			return Collections.emptyList();
		if ( omero.channels.size() != numChannels )
		{
			logger.warn(
					"The number of channels in the Omero metadata ({}) does not match the number of sources in the dataset ({}). Channel properties will not be applied.",
					omero.channels.size(), numChannels );
			return Collections.emptyList();
		}
		return omero.channels;
	}

	/**
	 * The display color of an OMERO channel: its {@code color}, an {@code RRGGBB}
	 * hex string, made fully opaque. A channel without a color — or no channel at
	 * all — is white.
	 *
	 * @param omeroChannel the channel, may be {@code null}
	 * @return the color to give the channel's display source
	 */
	private static ARGBType channelColor( final Omero.Channel omeroChannel )
	{
		final Color color = omeroChannel == null || omeroChannel.color == null
				? Color.white
				: Color.decode( "#" + omeroChannel.color );
		final int opaque = 255;
		return new ARGBType( ARGBType.rgba( color.getRed(), color.getGreen(), color.getBlue(), opaque ) );
	}

	/**
	 * Whether a channel should start out visible. A channel the metadata says
	 * nothing about is shown, so a dataset without OMERO metadata does not open
	 * blank.
	 *
	 * @param omeroChannel the channel, may be {@code null}
	 * @return {@code true} if the channel's display source should be active
	 */
	private static boolean isChannelActive( final Omero.Channel omeroChannel )
	{
		return omeroChannel == null || omeroChannel.active;
	}

	/**
	 * The timepoint a dataset should open at, from its OMERO rendering defaults,
	 * or {@code 0} when it names none.
	 *
	 * @param omero the dataset's OMERO metadata, may be {@code null}
	 * @return the timepoint index to show first
	 */
	private static int defaultTimepoint( final Omero omero )
	{
		return omero == null || omero.rdefs == null ? 0 : omero.rdefs.defaultT;
	}

	/**
	 * Moves {@code state} to the timepoint the dataset's OMERO rendering defaults
	 * name, or to 0.
	 *
	 * @param omero the dataset's OMERO metadata, may be {@code null}
	 * @param state the viewer state to move
	 */
	public static void setTimepoint( final Omero omero, final ViewerState state )
	{
		state.setCurrentTimepoint( defaultTimepoint( omero ) );
	}
}
