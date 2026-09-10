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

import org.scijava.Priority;
import org.scijava.plugin.Plugin;

import ome.zarr.fiji.open.ZarrOpenRequest;
import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fijiui.open.ZarrOpenActions;

/**
 * Hands the location to the N5 viewer dialog, i.e. to
 * Plugins &gt; BigDataViewer &gt; HDF5/N5/Zarr/OME-NGFF Viewer with the path
 * already filled in. Like {@link N5ImporterDialogOpener} it reads nothing
 * itself.
 */
@Plugin( type = ZarrOpener.class, name = N5ViewerDialogOpener.NAME,
		label = "N5 viewer dialog",
		description = "Open the OME-Zarr/N5 BigDataViewer dialog on this location",
		iconPath = "/ome/zarr/fijiui/dialog/zarr_bdv_icon.png", priority = Priority.LOW - 1 )
public class N5ViewerDialogOpener implements ZarrOpener
{
	/** The stable identifier this opener is persisted under. */
	public static final String NAME = "n5-viewer-dialog";

	@Override
	public void open( final ZarrOpenRequest request )
	{
		new ZarrOpenActions( request ).openViewerDialog();
	}
}
