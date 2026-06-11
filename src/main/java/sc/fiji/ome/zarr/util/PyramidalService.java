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

import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.imagej.Dataset;
import org.scijava.convert.ConvertService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.pyramid.Pyramidal;

/**
 * Tracks which BDV or ImageJ window holding a {@link Pyramidal} was most recently focused.
 * <p>
 * BDV windows are not ImageJ2 displays, and IJ1 {@link ImageWindow} focus changes never reach
 * the SciJava {@code EventService}. This service therefore listens at the AWT level via a
 * {@code "activeWindow"} {@link PropertyChangeListener} on the {@link KeyboardFocusManager}:
 * BDV windows set the active pyramidal directly; ImageJ windows resolve it from the displayed
 * image; all other windows (toolbar, dialogs) are ignored so menu navigation doesn't clear state.
 */
@Plugin( type = SciJavaService.class )
public class PyramidalService extends AbstractService implements SciJavaService
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@SuppressWarnings( "unused" )
	@Parameter( required = false )
	private ConvertService convertService;

	@SuppressWarnings( "unused" )
	@Parameter
	private ObjectService objectService;

	/** Registered BDV windows and the dataset each one displays. */
	private final Map< Window, Pyramidal > bdvWindows = new ConcurrentHashMap<>();

	/** ImagePlus instances known to wrap a {@link Pyramidal}, populated on IJ window focus. */
	private final Map< ImagePlus, Pyramidal > ijImages = new ConcurrentHashMap<>();

	private final AtomicReference< Pyramidal > activePyramidal = new AtomicReference<>();

	private PropertyChangeListener focusListener;

	private ImageListener imageCloseListener;

	@Override
	public void initialize()
	{
		activePyramidal.set( null );
		focusListener = event -> onActiveWindowChanged( ( Window ) event.getNewValue() );
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener( "activeWindow", focusListener );
		imageCloseListener = new ImageListener()
		{
			@Override
			public void imageOpened( final ImagePlus imp )
			{ /* not needed */ }

			@Override
			public void imageUpdated( final ImagePlus imp )
			{ /* not needed */ }

			@Override
			public void imageClosed( final ImagePlus imagePlus )
			{
				notifyImageClosed( imagePlus );
			}
		};
		ImagePlus.addImageListener( imageCloseListener );
	}

	@Override
	public void dispose()
	{
		if ( focusListener != null )
		{
			KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener( "activeWindow", focusListener );
			focusListener = null;
		}

		if ( imageCloseListener != null )
		{
			ImagePlus.removeImageListener( imageCloseListener );
			imageCloseListener = null;
		}
		bdvWindows.clear();
		ijImages.clear();
	}

	/**
	 * Updates focus precedence when the active window changes. A registered BDV window gives
	 * BDV precedence; an ImageJ {@link ImageWindow} returns precedence to the IJ dataset; any other
	 * window (toolbar, dialogs) is ignored so the last image-window state persists.
	 */
	private void onActiveWindowChanged( final Window window )
	{
		if ( window == null )
			return;
		final Pyramidal dataset = bdvWindows.get( window );
		if ( dataset != null )
			notifyBdvWindowFocused( dataset );
		else if ( window instanceof ImageWindow )
			notifyImageJWindowFocused( ( ImageWindow ) window );
	}

	/**
	 * Registers a BDV {@code window} and the {@code dataset} it displays, and makes it the active pyramidal.
	 */
	public void registerBdvWindow( final Window window, final Pyramidal dataset )
	{
		bdvWindows.put( window, dataset );
		notifyBdvWindowFocused( dataset );
	}

	/**
	 * Unregisters a BDV {@code window} when it closes. Clears the active pyramidal if it belonged
	 * to that window.
	 */
	public void unregisterBdvWindow( final Window window )
	{
		logger.trace( "BDV window closed" );
		logger.trace( "Active before unregister: {}", activePyramidal.get() );
		final Pyramidal removed = bdvWindows.remove( window );
		logger.trace( "Removing {}", removed );
		if ( removed != null )
			activePyramidal.compareAndSet( removed, null );
		logger.trace( "Active after unregister: {}", activePyramidal.get() );
	}

	/** Records {@code dataset} as the active pyramidal, replacing any previously active one. */
	void notifyBdvWindowFocused( final Pyramidal dataset )
	{
		logger.trace( "BDV window focused: {}", dataset );
		activePyramidal.set( dataset );
		logger.trace( "Active pyramidal set to: {}", activePyramidal.get() );
	}

	/** Sets the active pyramidal from the focused ImageJ window's image, or clears it if the image is not a {@link Pyramidal}. */
	void notifyImageJWindowFocused( final ImageWindow window )
	{
		logger.trace( "ImageJ window focused" );
		Pyramidal active = null;
		if ( convertService != null )
		{
			final ImagePlus imagePlus = window.getImagePlus();
			final Dataset dataset = convertService.convert( imagePlus, Dataset.class );
			if ( dataset instanceof Pyramidal )
			{
				active = ( Pyramidal ) dataset;
				ijImages.put( imagePlus, active );
			}
			else
			{
				ijImages.remove( imagePlus );
			}
		}
		activePyramidal.set( active );
		logger.trace( "Active pyramidal resolved from IJ window: {}", activePyramidal.get() );
	}

	/** Removes {@code imagePlus} from tracked IJ images and clears the active pyramidal if it belonged to that image. */
	void notifyImageClosed( final ImagePlus imagePlus )
	{
		logger.trace( "Image closed: {}", imagePlus );
		final Pyramidal pyramidal = ijImages.remove( imagePlus );
		if ( pyramidal != null )
			activePyramidal.compareAndSet( pyramidal, null );
		logger.trace( "Active pyramidal: {}", activePyramidal.get() );
	}

	/** Returns the most recently focused {@link Pyramidal}, or {@code null} if none has been focused. */
	public Pyramidal getActivePyramidal()
	{
		return activePyramidal.get();
	}

	/** Returns all registered {@link Pyramidal}s via {@link ObjectService}. */
	public List< Pyramidal > getPyramidals()
	{
		return objectService.getObjects( Pyramidal.class );
	}
}
