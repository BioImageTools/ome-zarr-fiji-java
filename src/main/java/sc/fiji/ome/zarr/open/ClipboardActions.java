package sc.fiji.ome.zarr.open;

import java.net.URI;
import java.util.function.Consumer;

import org.scijava.Context;

import sc.fiji.ome.zarr.util.ClipboardUtils;
import sc.fiji.ome.zarr.util.ZarrUriUtils;

public class ClipboardActions
{
	private ClipboardActions()
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
		final URI uri = ClipboardUtils.parseClipboardUri( errorHandler );
		if ( uri == null )
			return false;
		if ( !ZarrUriUtils.isZarr( uri ) )
		{
			if ( errorHandler != null )
				errorHandler.accept( "The pasted location does not appear to be an OME-Zarr dataset:\n" + uri + "." );
			return false;
		}
		ZarrOpenActions.openWithSettings( uri, context );
		return true;
	}
}
