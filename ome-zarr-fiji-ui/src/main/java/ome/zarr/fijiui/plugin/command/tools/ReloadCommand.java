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
package ome.zarr.fijiui.plugin.command.tools;

import java.awt.Window;
import java.lang.invoke.MethodHandles;

import net.imagej.Dataset;

import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.viewer.AbstractViewerPanel;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.WindowManager;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.plugins.ActiveViewerService;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.PyramidContents;

/**
 * Reads the pixels of the active OME-Zarr image from storage again, all chunks of all levels, and
 * repaints the windows showing it (issue #156). Metadata, e.g. the image size, is not read again.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Reload Current OME-Zarr Image", description = "Read the pixels of the current OME-Zarr image again, e.g. while it is still being written" )
public class ReloadCommand implements Command
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@SuppressWarnings( "unused" )
	@Parameter
	private UIService uiService;

	@SuppressWarnings( "unused" )
	@Parameter
	private ConvertService convertService;

	@SuppressWarnings( "unused" )
	@Parameter
	private PyramidalService pyramidalService;

	@SuppressWarnings( "unused" )
	@Parameter( required = false )
	private Pyramidal pyramidal;

	@Override
	public void run()
	{
		if ( pyramidal == null )
		{
			final String message = "The active image is not an OME-Zarr dataset.";
			if ( uiService.isVisible() )
				uiService.showDialog( message, "Reload OME-Zarr" );
			else
				logger.warn( message );
			return;
		}
		final PyramidContents< ? > contents = pyramidal.getPyramidContents();
		// each PyramidalBdv has its own volatile caches on top of the shared ones.
		// Invalidating the shared ones repeatedly is harmless.
		for ( final Pyramidal p : pyramidalService.getPyramidals() )
			if ( p.getPyramidContents() == contents )
				p.invalidateCachedPixels();
		pyramidal.invalidateCachedPixels(); // in case it is not registered
		repaintBdvs();
		refreshImageJWindows( contents );
	}

	/**
	 * Repaints every BigDataViewer, whether it shows {@code contents} or not: it only reads what is
	 * invalid, and {@link PyramidalService} does not know every viewer showing an image.
	 */
	private static void repaintBdvs()
	{
		for ( final Window window : Window.getWindows() )
			ActiveViewerService.viewerPanels( window ).forEach( AbstractViewerPanel::requestRepaint );
	}

	/**
	 * Re-reads the shown plane of every ImageJ window showing {@code contents}, at any resolution
	 * level. Such an image is a virtual stack (imagej-legacy wraps a {@code CachedCellImg} that way),
	 * which copies a plane on each read. The fresh plane is copied into the shown processor rather
	 * than replacing it: {@link ImagePlus#updateVirtualSlice()} calls {@code setProcessor}, after which
	 * IJ1 drops the virtual stack of a single-plane image, so a second reload would skip it. Copying
	 * the pixels also keeps the display range, since {@code setPixels} leaves it alone. A
	 * {@link CompositeImage} keeps one plane per channel, which {@link CompositeImage#reset()} reads
	 * again.
	 */
	private void refreshImageJWindows( final PyramidContents< ? > contents )
	{
		final int[] ids = WindowManager.getIDList();
		if ( ids == null )
			return;
		for ( final int id : ids )
		{
			final ImagePlus imagePlus = WindowManager.getImage( id );
			if ( shows( imagePlus, contents ) )
			{
				imagePlus.getProcessor().setPixels( imagePlus.getStack().getPixels( imagePlus.getCurrentSlice() ) );
				if ( imagePlus instanceof CompositeImage )
					( ( CompositeImage ) imagePlus ).reset();
				imagePlus.updateAndDraw();
			}
		}
	}

	/** Whether {@code imagePlus} is a virtual stack of a {@link Pyramidal} on {@code contents}. */
	private boolean shows( final ImagePlus imagePlus, final PyramidContents< ? > contents )
	{
		if ( imagePlus == null || !imagePlus.getStack().isVirtual() )
			return false;
		final Dataset dataset = convertService.convert( imagePlus, Dataset.class );
		return dataset instanceof Pyramidal && ( ( Pyramidal ) dataset ).getPyramidContents() == contents;
	}
}
