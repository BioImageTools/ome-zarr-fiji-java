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
package ome.zarr.fijiui.open;

import java.net.URI;
import java.util.function.Consumer;

import org.scijava.Context;

import ome.zarr.fijiui.util.ClipboardUtils;
import ome.zarr.imglib2.ZarrUtils;

/**
 * Opening orchestration for the "paste an OME-Zarr location and open it" flow,
 * shared by the three entry points that trigger it: the keyboard shortcut
 * (Ctrl/Cmd/Shift+V), the toolbar button, and the menu command. Reads a URI
 * from the clipboard (via {@link ome.zarr.fijiui.util.ClipboardUtils}),
 * verifies it points at an OME-Zarr dataset, and opens it using the configured
 * settings.
 */
public class PasteToOpenAction
{
	private PasteToOpenAction()
	{
		// static utility class; do not instantiate
	}

	/**
	 * Reads a URL or path from the system clipboard and opens it as an OME-Zarr
	 * dataset, using the same backend, resolution, and open-behavior settings
	 * as the drag-and-drop pipeline. Reused by both the menu command and the
	 * toolbar button.
	 *
	 * @param errorHandler called with a user-facing message when the clipboard
	 *   is empty, the contents can't be parsed, or the location does not point
	 *   at an OME-Zarr dataset
	 */
	public static boolean pasteFromClipboard( final Context context, final Consumer< String > errorHandler )
	{
		final URI uri = ClipboardUtils.readClipboardAsUri( errorHandler );
		if ( uri == null )
			return false;
		// For s3:// URIs the probe would require its own short-lived S3Client purely
		// for detection.
		// It is skipped because the actual open method creates the client it needs anyway
		// and reports and error if the location turns out not to be OME-Zarr.
		if ( !"s3".equalsIgnoreCase( uri.getScheme() ) && !ZarrUtils.isZarr( uri ) )
		{
			if ( errorHandler != null )
				errorHandler.accept( "The pasted location does not appear to be an OME-Zarr dataset:\n" + uri + "." );
			return false;
		}
		ZarrOpenActions.openWithSettings( uri, context );
		return true;
	}
}
