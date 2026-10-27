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
package ome.zarr.fijiui.open.openers;

import ome.zarr.fiji.plugins.ActiveViewerService;
import ome.zarr.fiji.read.OmeZarr;
import ome.zarr.fiji.open.OmeZarrOpener;
import ome.zarr.fiji.util.BdvUtils;
import ome.zarr.fijiui.open.OmeZarrBdvDropListener;

import bdv.util.BdvHandle;

import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 * Adds the image to the active BigDataViewer window, e.g. to overlay a
 * segmentation on its raw data. A BigDataViewer window is active if it is the
 * image window focused or opened most recently. Focusing Fiji's main window,
 * toolbar or a dialog keeps it active. Focusing an ImageJ image window, or a
 * BigDataViewer window another tool opened, does not, and then, as when no
 * BigDataViewer window is open, a new one opens. See {@link BdvUtils#bdvHandleOf}.
 */
@Plugin( type = OmeZarrOpener.class, name = BdvAddToActiveOpener.NAME,
		label = "BigDataViewer (add to active window)",
		description = "Add as a multi-resolution source to the active BigDataViewer window, or open a new one if there is none",
		iconPath = "/ome/zarr/fijiui/dialog/bdv_add_icon.png", priority = Priority.NORMAL - 1 )
public class BdvAddToActiveOpener implements OmeZarrOpener
{
	/** The stable identifier this opener is persisted under. */
	public static final String NAME = "bdv-add-to-active";

	@Override
	public void open( final OmeZarr omeZarr )
	{
		final ActiveViewerService activeViewerService = omeZarr.context().getService( ActiveViewerService.class );
		final BdvHandle active = BdvUtils.bdvHandleOf( activeViewerService.getActiveViewer() );
		final BdvHandle bdvHandle = omeZarr.showInBdv( active );
		if ( active == null && bdvHandle != null ) // NB: only install the drop target on a new window, not on an existing one
			OmeZarrBdvDropListener.install( bdvHandle, omeZarr.context() );
	}
}
