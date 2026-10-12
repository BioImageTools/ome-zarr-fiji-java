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

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.BdvHandle;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fiji.util.BdvUtils;
import ome.zarr.fiji.util.OmeZarrImagesChooser;
import ome.zarr.fijiui.open.OmeZarrDropListener;
import ome.zarr.imglib2.PyramidContents;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Current OME-Zarr Image in BigDataViewer" )
public class OpenInBDVCommand implements Command
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Parameter
	private UIService uiService;

	@Parameter( required = false )
	private PyramidalService pyramidalService;

	@Parameter( required = false )
	private Pyramidal pyramidal;

	@Override
	public void run()
	{
		logger.trace( "Running OpenInBDVCommand. pyramidal={}", this.pyramidal );
		if ( this.pyramidal == null )
		{
			final String message = "The active image is not an OME-Zarr dataset.";
			if ( uiService.isVisible() )
				uiService.showDialog( message, "Open in BigDataViewer" );
			else
				logger.warn( message );
			return;
		}
		final BdvHandle bdvHandle = BdvUtils.showInOneWindow( selectContents(), this.pyramidal.getContext(), pyramidalService );
		if ( bdvHandle != null )
			OmeZarrDropListener.install( bdvHandle, this.pyramidal.getContext() );
	}

	/**
	 * The OME-Zarrs to show in one BDV window: just the active one, unless more than one
	 * is open, in ImageJ or BigDataViewer. Each is listed once, however many windows show it.
	 */
	private List< PyramidContents< ? > > selectContents()
	{
		final List< PyramidContents< ? > > open = pyramidalService.getPyramidContents();
		if ( open.isEmpty() )
			return Collections.emptyList();
		// for one OME-Zarr, this.pyramidal's contents should be the same as open.get( 0 ) and for headless mode, we don't want to show a chooser dialog, so just return the active one
		if ( open.size() == 1 || uiService.isHeadless() )
			return Collections.singletonList( this.pyramidal.getPyramidContents() );
		return OmeZarrImagesChooser.choose( open, this.pyramidal.getPyramidContents() ); // NB: the active one is pre-selected
	}
}
