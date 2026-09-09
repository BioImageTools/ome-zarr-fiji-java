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

/**
 * File &gt; Import menu entry for opening a zipped OME-Zarr archive, a single
 * {@code .ozx} file; {@link OpenOmeZarrCommand} opens the folder form. It then
 * follows the user's {@link ome.zarr.fijiui.open.options.ZarrOpeningSettings}
 * like the other entry points, so the same open behavior, resolution and reader
 * backend apply.
 * <p>
 * Being a SciJava {@link Command} with one input, it is macro-recordable
 * ({@code run("OME-Zarr Archive...", "archive=/path/to/image.ozx")}).
 * <p>
 * Remote archives are not offered here – a file chooser cannot express them;
 * use Plugins &gt; OME-Zarr &gt; Paste OME-Zarr URI for those. Only the zarr-java
 * backend reads archives, so with the N5 backend selected the user gets that
 * backend's "cannot read zipped archives" message.
 */
@Plugin( type = Command.class, menuPath = "File > Import > OME-Zarr Archive (.ozx)..." )
public class OpenOmeZarrArchiveCommand implements Command
{
	@Parameter
	private Context context;

	@Parameter( label = "OME-Zarr archive", style = FileWidget.OPEN_STYLE + ",extensions:ozx",
			description = "The zipped OME-Zarr dataset, e.g. /path/to/image.ozx" )
	private File archive;

	@Override
	public void run()
	{
		open( archive, context, IJ::error );
	}

	/**
	 * Verifies that {@code archive} is a zipped OME-Zarr dataset and opens it
	 * with the user's configured settings.
	 *
	 * @param archive the selected {@code .ozx} archive; may be {@code null}
	 * @param context the SciJava context used to open the dataset
	 * @param errorHandler called with a user-facing message when nothing was
	 *   selected or the selection is not an OME-Zarr archive
	 * @return {@code true} if the archive was handed to the opening pipeline,
	 *   {@code false} if {@code errorHandler} was invoked instead
	 */
	static boolean open( final File archive, final Context context, final Consumer< String > errorHandler )
	{
		return OmeZarrOpener.open( archive, context, errorHandler, "archive" );
	}

	/**
	 * Why {@code archive} cannot be opened, as a user-facing message, or
	 * {@code null} if it is a readable OME-Zarr archive.
	 *
	 * @param archive the selected {@code .ozx} archive; may be {@code null}
	 * @return the rejection message, or {@code null} if the archive is acceptable
	 */
	static String validate( final File archive )
	{
		return OmeZarrOpener.validate( archive, "archive" );
	}
}
