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
package ome.zarr.fijiui.plugin.command;

import java.io.File;
import java.util.function.Consumer;

import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import ij.IJ;
import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.imglib2.ZarrUtils;

/**
 * Classical File &gt; Import menu entry for opening a local OME-Zarr dataset,
 * complementary to drag-and-drop and clipboard paste. It takes a single
 * explicit parameter – the dataset folder – and then follows the user's
 * {@link ome.zarr.fijiui.open.options.ZarrOpeningSettings} exactly like the
 * other entry points do, so the same open behavior, resolution and reader
 * backend apply.
 * <p>
 * Being a SciJava {@link Command} with one input, it is macro-recordable
 * ({@code run("OME-Zarr...", "directory=/path/to/img.ome.zarr")}).
 * <p>
 * The parameter is a directory rather than a file because a local OME-Zarr
 * dataset is always a folder (a {@code .ome.zarr} group or a single array
 * node). Remote locations are not offered here – a file chooser cannot express
 * them; use Plugins &gt; OME-Zarr &gt; Paste OME-Zarr URI for those.
 */
@Plugin( type = Command.class, menuPath = "File > Import > OME-Zarr..." )
public class OpenOmeZarrCommand implements Command
{
	@Parameter
	private Context context;

	@Parameter( label = "OME-Zarr folder", style = FileWidget.DIRECTORY_STYLE,
			description = "The folder holding the OME-Zarr dataset, e.g. /path/to/image.ome.zarr" )
	private File directory;

	@Override
	public void run()
	{
		open( directory, context, IJ::error );
	}

	/**
	 * Verifies that {@code folder} is an OME-Zarr dataset folder and opens it
	 * with the user's configured settings.
	 *
	 * @param folder the selected folder; may be {@code null}
	 * @param context the SciJava context used to open the dataset
	 * @param errorHandler called with a user-facing message when nothing was
	 *   selected or the selection is not an OME-Zarr dataset folder
	 * @return {@code true} if the folder was handed to the opening pipeline,
	 *   {@code false} if {@code errorHandler} was invoked instead
	 */
	static boolean open( final File folder, final Context context, final Consumer< String > errorHandler )
	{
		final String error = validate( folder );
		if ( error != null )
		{
			if ( errorHandler != null )
				errorHandler.accept( error );
			return false;
		}
		ZarrOpenActions.openWithSettings( folder.toURI(), context );
		return true;
	}

	/**
	 * Why {@code folder} cannot be opened, as a user-facing message, or
	 * {@code null} if it is an OME-Zarr dataset folder. Split out from
	 * {@link #open} so the acceptance decision can be tested without opening a
	 * window.
	 *
	 * @param folder the selected folder; may be {@code null}
	 * @return the rejection message, or {@code null} if the folder is acceptable
	 */
	static String validate( final File folder )
	{
		if ( folder == null )
			return "No folder selected.";
		// isZarr( URI ) probes for the well-known metadata files on the local
		// filesystem, which also covers "the folder does not exist at all".
		if ( !ZarrUtils.isZarr( folder.toURI() ) )
			return "The selected folder does not appear to be an OME-Zarr dataset:\n" + folder + ".";
		return null;
	}
}
