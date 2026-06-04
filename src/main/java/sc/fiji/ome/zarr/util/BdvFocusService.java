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

import ij.ImagePlus;
import ij.gui.ImageWindow;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.imagej.Dataset;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.pyramid.Pyramidal;

/**
 * Tracks which image window — a BigDataViewer window or an ImageJ window — was the most
 * recently focused, so that commands can resolve the dataset the user actually means.
 * <p>
 * BDV windows are not ImageJ2 displays, and in a legacy Fiji session the image windows are
 * IJ1 {@link ImageWindow}s whose focus changes never reach the SciJava {@code EventService} at
 * all (the active image is resolved on demand from {@code ij.WindowManager}, not via events).
 * Listening to {@code DisplayActivatedEvent} therefore misses focus switches between already-open
 * windows. Instead this service listens at the AWT level — a single {@code "activeWindow"}
 * {@link PropertyChangeListener} on the {@link KeyboardFocusManager} fires for every real window:
 * <ul>
 * <li>focus moves to a registered BDV window → BDV gains precedence;</li>
 * <li>focus moves to an ImageJ {@link ImageWindow} → precedence returns to the IJ dataset;</li>
 * <li>focus moves to anything else (the main toolbar while navigating menus, dialogs) → ignored,
 *     so the last image-window state survives menu navigation.</li>
 * </ul>
 * Ignoring non-image windows mirrors how IJ1's {@code WindowManager} itself disregards focus on
 * non-image windows, which is what makes menu-invoked commands resolve the correct dataset.
 */
// TODO rename to "PyramidalService" or something
@Plugin( type = SciJavaService.class )
public class BdvFocusService extends AbstractService implements SciJavaService
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Parameter(required = false)
	private ConvertService convertService;

	/** Registered BDV windows and the dataset each one displays. */
	private final Map< Window, Pyramidal > bdvWindows = new ConcurrentHashMap<>();

	private final AtomicReference< Pyramidal > activePyramidal = new AtomicReference<>();

	private PropertyChangeListener focusListener;

	@Override
	public void initialize()
	{
		activePyramidal.set( null );
		focusListener = evt -> onActiveWindowChanged( ( Window ) evt.getNewValue() );
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addPropertyChangeListener( "activeWindow", focusListener );
	}

	@Override
	public void dispose()
	{
		if ( focusListener != null )
		{
			KeyboardFocusManager.getCurrentKeyboardFocusManager()
					.removePropertyChangeListener( "activeWindow", focusListener );
			focusListener = null;
		}
		bdvWindows.clear();
	}

	/**
	 * Updates focus precedence when the active AWT window changes. A registered BDV window gives
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
	 * Registers a newly opened BDV {@code window} and the {@code dataset} it displays, and gives it
	 * focus precedence. The {@link KeyboardFocusManager} listener uses the registration to recognise
	 * the window when focus later returns to it.
	 */
	public void registerBdvWindow( final Window window, final Pyramidal dataset )
	{
		bdvWindows.put( window, dataset );
		notifyBdvWindowFocused( dataset );
	}

	/**
	 * Unregisters a BDV {@code window} when it closes. If its dataset currently holds precedence,
	 * precedence is cleared so the next focus event (or the IJ2 injection) decides.
	 */
	public void unregisterBdvWindow( final Window window )
	{
		final Pyramidal removed = bdvWindows.remove(window);
		if (removed != null)
			activePyramidal.compareAndSet(removed, null);
	}

	/** Records {@code dataset} as the focused BDV dataset and gives BDV precedence. */
	// TODO: probably only for tests? make package-private?
	public void notifyBdvWindowFocused( final Pyramidal dataset )
	{
		logger.trace( "BDV window focused: {}", dataset );
		activePyramidal.set( dataset );
	}

	/** Hands precedence back to the IJ active-display injection when an ImageJ window is focused. */
	public void notifyImageJWindowFocused(final ImageWindow window) {
		logger.trace("ImageJ window focused");
		if (convertService == null) {
			activePyramidal.set(null);
		} else {
			final ImagePlus imp = window.getImagePlus();
			final Dataset dataset = convertService.convert(imp, Dataset.class);
			if (dataset instanceof Pyramidal) {
				activePyramidal.set((Pyramidal) dataset);
			}
		}
	}

	/**
	 * Returns the active BDV dataset when a BDV window was focused more recently than any ImageJ
	 * window, otherwise falls back to {@code dataset} (the IJ active-display injection).
	 * This makes the command operate on whichever window — BDV or IJ — the user last focused.
	 */
	public Pyramidal getActivePyramidal()
	{
		return activePyramidal.get();
	}
}
