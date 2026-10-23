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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.GridLayout;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTable;

import net.imagej.patcher.LegacyInjector;

import org.junit.jupiter.api.Test;

import bdv.cache.CacheControl;
import bdv.viewer.ViewerPanel;
import ij.IJ;
import ij.ImagePlus;

/**
 * Which image viewer a focus owner belongs to, as seen in Fiji with a focus probe: ImageJ
 * focuses the {@code ImageWindow} or its canvas, BDV its display canvas or, in the side panel,
 * the source table or group tree, which lie outside the {@link ViewerPanel}.
 */
class ActiveViewerServiceTest
{
	static
	{
		// Patch IJ1 before ij.* classes are loaded, as ActiveViewerService does.
		LegacyInjector.preinit();
	}

	@Test
	void focusInsideABdvViewerPanelGivesThatPanel()
	{
		final ViewerPanel viewerPanel = bdvViewerPanel();
		try
		{
			assertSame( viewerPanel, ActiveViewerService.viewerOf( viewerPanel.getDisplayComponent() ) );
		}
		finally
		{
			viewerPanel.stop();
		}
	}

	@Test
	void focusInTheSidePanelGivesTheWindowsOnlyBdvViewerPanel()
	{
		final ViewerPanel viewerPanel = bdvViewerPanel();
		final JTable sourceTable = new JTable();
		final JFrame frame = frameWith( viewerPanel, sourceTable );
		try
		{
			assertSame( viewerPanel, ActiveViewerService.viewerOf( sourceTable ) );
		}
		finally
		{
			viewerPanel.stop();
			frame.dispose();
		}
	}

	@Test
	void focusBesideTwoBdvViewerPanelsGivesNone()
	{
		final ViewerPanel left = bdvViewerPanel();
		final ViewerPanel right = bdvViewerPanel();
		final JTable sourceTable = new JTable();
		final JFrame frame = frameWith( left, right, sourceTable );
		try
		{
			assertNull( ActiveViewerService.viewerOf( sourceTable ) );
			assertSame( right, ActiveViewerService.viewerOf( right.getDisplayComponent() ) );
		}
		finally
		{
			left.stop();
			right.stop();
			frame.dispose();
		}
	}

	@Test
	void focusOutsideAnyImageViewerGivesNone()
	{
		final JButton button = new JButton();
		final JFrame frame = frameWith( button );
		try
		{
			assertNull( ActiveViewerService.viewerOf( button ) );
			assertNull( ActiveViewerService.viewerOf( frame ) );
			assertNull( ActiveViewerService.viewerOf( null ) );
		}
		finally
		{
			frame.dispose();
		}
	}

	@Test
	void focusInAnImageJWindowGivesThatWindow()
	{
		final ImagePlus imagePlus = IJ.createImage( "Demo", "8-bit ramp", 64, 64, 1 );
		imagePlus.show();
		try
		{
			assertSame( imagePlus.getWindow(), ActiveViewerService.viewerOf( imagePlus.getWindow() ) );
			assertSame( imagePlus.getWindow(), ActiveViewerService.viewerOf( imagePlus.getCanvas() ) );
		}
		finally
		{
			imagePlus.close();
		}
	}

	private static ViewerPanel bdvViewerPanel()
	{
		return new ViewerPanel( Collections.emptyList(), 1, new CacheControl.Dummy() );
	}

	/** A window holding {@code components}, never shown: {@code viewerOf} only walks the component tree. */
	private static JFrame frameWith( final java.awt.Component... components )
	{
		final JFrame frame = new JFrame();
		frame.getContentPane().setLayout( new GridLayout( 1, components.length ) );
		for ( final java.awt.Component component : components )
			frame.getContentPane().add( component );
		return frame;
	}
}
