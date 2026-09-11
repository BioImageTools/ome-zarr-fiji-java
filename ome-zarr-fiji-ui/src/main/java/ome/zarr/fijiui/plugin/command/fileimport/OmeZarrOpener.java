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
package ome.zarr.fijiui.plugin.command.fileimport;

import java.io.File;
import java.util.function.Consumer;

import org.scijava.Context;

import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.imglib2.ZarrUtils;

/**
 * Checks a selected location and hands it to the opening pipeline, for both
 * File &gt; Import entries: {@link OpenOmeZarrCommand} (a dataset folder) and
 * {@link OpenOmeZarrArchiveCommand} (a zipped {@code .ozx} archive).
 * <p>
 * They are two commands, and not one, because a Swing file chooser browses
 * either folders or files – never both.
 */
final class OmeZarrOpener
{
	private OmeZarrOpener()
	{
		// prevent instantiation
	}

	/**
	 * Verifies that {@code location} is an OME-Zarr dataset and opens it with the
	 * user's configured settings.
	 *
	 * @param location the selection; may be {@code null}
	 * @param context the SciJava context used to open the dataset
	 * @param errorHandler called with a user-facing message when nothing was
	 *   selected or the selection is not an OME-Zarr dataset
	 * @param noun what the command asked for, e.g. {@code "folder"}, as used in
	 *   those messages
	 * @return {@code true} if the location was handed to the opening pipeline,
	 *   {@code false} if {@code errorHandler} was invoked instead
	 */
	static boolean open( final File location, final Context context, final Consumer< String > errorHandler,
			final String noun )
	{
		final String error = validate( location, noun );
		if ( error != null )
		{
			if ( errorHandler != null )
				errorHandler.accept( error );
			return false;
		}
		ZarrOpenActions.openWithSettings( location.toURI(), context );
		return true;
	}

	/**
	 * Why {@code location} cannot be opened, as a user-facing message, or
	 * {@code null} if it is an OME-Zarr dataset. Split out from {@link #open} so
	 * the decision can be tested without opening a window.
	 *
	 * @param location the selection; may be {@code null}
	 * @param noun what the command asked for, used in the message
	 * @return the rejection message, or {@code null} if the location is acceptable
	 */
	static String validate( final File location, final String noun )
	{
		if ( location == null )
			return "No " + noun + " selected.";
		if ( !ZarrUtils.isZarr( location.toURI() ) )
			return "The selected " + noun + " does not appear to be an OME-Zarr dataset:\n" + location + ".";
		return null;
	}
}
