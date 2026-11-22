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
package ome.zarr.fijiui.labkit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.scijava.plugin.Plugin;

import bdv.util.BdvHandle;
import sc.fiji.labkit.ui.Extensible;
import sc.fiji.labkit.ui.LabkitExtension;
import sc.fiji.labkit.ui.models.ImageLabelingModel;
import sc.fiji.labkit.ui.models.SegmentationItem;

/**
 * Adds "Save as OME-Zarr" to Labkit: an entry in the Segmentation menu, next to
 * "Save Segmentation Result as TIF / HDF5", and a toolbar button. Saving is not
 * implemented yet, so both only say so.
 */
@Plugin( type = LabkitExtension.class )
public class SaveAsOmeZarrLabkitExtension implements LabkitExtension
{
	/** Menu entry without the trailing " ...", toolbar tooltip and dialog title. */
	private static final String TITLE = "Save Segmentation Result as OME-Zarr";

	/** Sorts right after Labkit's "Save … as TIF / HDF5 …" items (200), in the same menu group. */
	private static final float MENU_PRIORITY = 200.5f;

	/** Height of the toolbar icon, close to Labkit's 33 px tool icons. */
	private static final int ICON_HEIGHT = 30;

	/** At most twice as wide as high. */
	private static final int ICON_WIDTH = 2 * ICON_HEIGHT;

	private static final int LOGO_HEIGHT = 22;

	@Override
	public void extend( final Extensible extensible, final ImageLabelingModel model, final BdvHandle bdvHandle )
	{
		final Runnable notImplemented = () -> JOptionPane.showMessageDialog( extensible.dialogParent(),
				"Saving the segmentation result as OME-Zarr is not implemented yet.", TITLE, JOptionPane.INFORMATION_MESSAGE );
		extensible.addMenuItem( SegmentationItem.SEGMENTER_MENU, TITLE + " ...", MENU_PRIORITY,
				ignore -> notImplemented.run(), null, "" );
		extensible.addToolBarButton( TITLE, labkitToOmeZarrIcon(), notImplemented );
	}

	/**
	 * The Labkit logo, an arrow, and the OME-Zarr logo. {@code ome_zarr_icon.png} puts
	 * the Zarr cube of {@code dialog/unused/zarr_icon.png} into the ring of the OME
	 * logomark (openmicroscopy.org/img/logos/ome-logomark.svg).
	 */
	private static Icon labkitToOmeZarrIcon()
	{
		final Image labkit = logo( "/ome/zarr/fijiui/dialog/labkit_icon.png" );
		final Image omeZarr = logo( "/ome/zarr/fijiui/labkit/ome_zarr_icon.png" );
		final int y = ( ICON_HEIGHT - LOGO_HEIGHT ) / 2;
		final int arrowStart = labkit.getWidth( null ) + 2;
		final int omeZarrX = ICON_WIDTH - omeZarr.getWidth( null );
		final int arrowEnd = omeZarrX - 2;
		final int arrowY = ICON_HEIGHT / 2;
		final BufferedImage image = new BufferedImage( ICON_WIDTH, ICON_HEIGHT, BufferedImage.TYPE_INT_ARGB );
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g.drawImage( labkit, 0, y, null );
		g.drawImage( omeZarr, omeZarrX, y, null );
		g.setColor( Color.DARK_GRAY );
		g.setStroke( new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
		g.drawLine( arrowStart, arrowY, arrowEnd, arrowY );
		g.drawLine( arrowEnd - 4, arrowY - 4, arrowEnd, arrowY );
		g.drawLine( arrowEnd - 4, arrowY + 4, arrowEnd, arrowY );
		g.dispose();
		return new ImageIcon( image );
	}

	/**
	 * The logo at {@code path}, scaled to a height of {@link #LOGO_HEIGHT}. {@code getScaledInstance}
	 * scales lazily, and drawing it before it is done draws nothing: wrapping it in an
	 * {@link ImageIcon} waits for it.
	 */
	// ponytail: 1x bitmap, blurry on HiDPI screens. Render a multi-resolution image once saving is implemented.
	private static Image logo( final String path )
	{
		final Image original = new ImageIcon( SaveAsOmeZarrLabkitExtension.class.getResource( path ) ).getImage();
		return new ImageIcon( original.getScaledInstance( -1, LOGO_HEIGHT, Image.SCALE_SMOOTH ) ).getImage();
	}
}
