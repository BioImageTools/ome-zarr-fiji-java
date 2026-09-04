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

import java.net.URI;
import java.util.function.Consumer;

import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.read.ZarrReader;
import ome.zarr.fijiui.open.options.ZarrOpeningSettings;
import ome.zarr.fijiui.util.ClipboardUtils;
import ome.zarr.imglib2.ZarrUtils;

/**
 * Opens an OME-Zarr location given as a single line of text and hands it back as
 * a {@link PyramidalDataset} output, so a macro or script can capture the image
 * and keep working with it:
 *
 * <pre>
 * run( "Open OME-Zarr as Dataset", "uri=/path/to/image.ome.zarr" );
 * </pre>
 *
 * The command itself displays nothing. When it is run interactively from the
 * menu, SciJava's output post-processing shows the returned dataset as usual; a
 * script that captures the output decides for itself.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open OME-Zarr as Dataset" )
public class OpenOmeZarrAsDatasetCommand extends ContextCommand
{
	@Parameter( label = "OME-Zarr URI or path", description = "Local path, file: or http(s): URI of the OME-Zarr dataset" )
	private String uri;

	@SuppressWarnings( "all" )
	@Parameter( type = ItemIO.OUTPUT )
	private PyramidalDataset dataset;

	@Override
	public void run()
	{
		// cancel( … ) rather than IJ.error( … ): as error handler as this command shall stay dialog-free.
		dataset = read( uri, getContext(), this::cancel );
	}

	/**
	 * Parses {@code location}, verifies it points at an OME-Zarr dataset and reads
	 * it, without displaying anything.
	 *
	 * @param location the dataset location as typed by the user; may be {@code null}
	 * @param context the SciJava context used to read the dataset
	 * @param errorHandler called with a user-facing message when {@code location}
	 *   is blank, cannot be parsed, or does not point at an OME-Zarr dataset
	 * @return the {@link PyramidalDataset}, or {@code null} if {@code errorHandler} was invoked
	 */
	static PyramidalDataset read( final String location, final Context context, final Consumer< String > errorHandler )
	{
		if ( location == null || location.trim().isEmpty() )
		{
			errorHandler.accept( "No OME-Zarr URI or path given." );
			return null;
		}
		final URI uri = ClipboardUtils.stringToUri( location, errorHandler );
		if ( uri == null )
			return null;
		if ( !ZarrUtils.isZarr( uri ) )
		{
			errorHandler.accept( "The given location does not appear to be an OME-Zarr dataset:\n" + uri + "." );
			return null;
		}
		final ZarrOpeningSettings settings = ZarrOpeningSettings.loadSettingsFromPreferences( context.getService( PrefService.class ) );
		final ZarrReader zarrReader = new ZarrReader( uri, context, settings.getBackend().createBackend(), null, errorHandler );
		return zarrReader.getPyramidalDataset();
	}
}
