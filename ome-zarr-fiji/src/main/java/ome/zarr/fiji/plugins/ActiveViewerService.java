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
package ome.zarr.fiji.plugins;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import net.imagej.patcher.LegacyInjector;

import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;

import bdv.viewer.AbstractViewerPanel;
import ij.gui.ImageWindow;

/**
 * Tracks the most recently focused image viewer: an ImageJ {@link ImageWindow} or the
 * {@link AbstractViewerPanel} of a BigDataViewer or a BigVolumeViewer, no matter which tool opened it.
 * <p>
 * Listens to the {@code "focusOwner"} property of the {@link KeyboardFocusManager} rather than to
 * {@code "activeWindow"}, so that the viewers of a window holding two of them (e.g. BigWarp) can be
 * told apart. Focus in a BDV window's side panel lies outside its {@link AbstractViewerPanel}, so a window
 * holding exactly one viewer counts as that viewer wherever its focus is. All other windows (Fiji's
 * main window, dialogs) are ignored, so the last viewer stays active while the user navigates menus.
 * <p>
 * The focus event of a newly shown window arrives only after the call that showed it has returned,
 * so whoever opens a viewer reports it via {@link #setActiveViewer}.
 */
@Plugin( type = SciJavaService.class )
public class ActiveViewerService extends AbstractService implements SciJavaService
{
	static
	{
		// Patch IJ1 before ij.* classes are loaded during Context startup:
		// unpatched IJ1 classes make the later patcher run fail with a LinkageError.
		LegacyInjector.preinit();
	}

	/** Weak, so that a closed viewer, and the image data it holds, is not kept alive until the next one becomes active. */
	private final AtomicReference< WeakReference< Component > > activeViewer = new AtomicReference<>( new WeakReference<>( null ) );

	private final List< Consumer< Component > > listeners = new CopyOnWriteArrayList<>();

	private PropertyChangeListener focusListener;

	@Override
	public void initialize()
	{
		focusListener = event -> {
			final Component viewer = viewerOf( ( Component ) event.getNewValue() );
			if ( viewer != null && viewer != activeViewer.get().get() )
				setActiveViewer( viewer );
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener( "focusOwner", focusListener );
	}

	@Override
	public void dispose()
	{
		if ( focusListener != null )
		{
			KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener( "focusOwner", focusListener );
			focusListener = null;
		}
		listeners.clear();
		activeViewer.set( new WeakReference<>( null ) );
	}

	/**
	 * @return the most recently focused or reported image viewer, an {@link ImageWindow} or a
	 *   {@link AbstractViewerPanel}, or {@code null} if there is none or it has been closed since
	 */
	public Component getActiveViewer()
	{
		final Component viewer = activeViewer.get().get();
		return viewer != null && viewer.isShowing() ? viewer : null;
	}

	/**
	 * Makes {@code viewer} the active one, without waiting for its focus event, and tells the
	 * listeners.
	 *
	 * @param viewer an {@link ImageWindow} or an {@link AbstractViewerPanel}, or {@code null} if the viewer
	 *   about to become active is not known yet
	 */
	public void setActiveViewer( final Component viewer )
	{
		activeViewer.set( new WeakReference<>( viewer ) );
		listeners.forEach( listener -> listener.accept( viewer ) );
	}

	/** Calls {@code listener} with each newly active viewer, see {@link #setActiveViewer}. */
	public void addListener( final Consumer< Component > listener )
	{
		listeners.add( listener );
	}

	public void removeListener( final Consumer< Component > listener )
	{
		listeners.remove( listener );
	}

	/**
	 * The image viewer {@code focusOwner} belongs to: its {@link ImageWindow}, the
	 * {@link AbstractViewerPanel} containing it, or else the only one in its window. {@code null} for
	 * anything else.
	 */
	static Component viewerOf( final Component focusOwner )
	{
		if ( focusOwner == null )
			return null;
		final Window window = focusOwner instanceof Window ? ( Window ) focusOwner : SwingUtilities.getWindowAncestor( focusOwner );
		if ( window instanceof ImageWindow )
			return window;
		final Component viewerPanel = SwingUtilities.getAncestorOfClass( AbstractViewerPanel.class, focusOwner );
		if ( viewerPanel != null )
			return viewerPanel;
		if ( window == null )
			return null;
		final List< AbstractViewerPanel > viewerPanels = new ArrayList<>();
		collectViewerPanels( window, viewerPanels );
		return viewerPanels.size() == 1 ? viewerPanels.get( 0 ) : null;
	}

	private static void collectViewerPanels( final Container container, final List< AbstractViewerPanel > viewerPanels )
	{
		for ( final Component child : container.getComponents() )
		{
			if ( child instanceof AbstractViewerPanel )
				viewerPanels.add( ( AbstractViewerPanel ) child );
			else if ( child instanceof Container )
				collectViewerPanels( ( Container ) child, viewerPanels );
		}
	}
}
