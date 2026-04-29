/*-
 * #%L
 * OME-Zarr extras for Fiji
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
package sc.fiji.ome.zarr.plugins;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.function.Consumer;

import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.IJ;
import sc.fiji.ome.zarr.settings.ZarrDragAndDropOpenSettings;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;
import sc.fiji.ome.zarr.ui.DnDActionChooser;
import sc.fiji.ome.zarr.util.ZarrLocations;
import sc.fiji.ome.zarr.util.ZarrOpenActions;

/**
 * Reads a URL or path from the system clipboard and opens it as an OME-Zarr
 * dataset, using the same backend, resolution, and open-behavior settings as
 * the drag-and-drop pipeline. Mirrors napari's "paste URL to open" UX.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Paste OME-Zarr URL" )
public class PasteOmeZarrUrlCommand implements Command
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Parameter
	private Context context;

	@Parameter
	private PrefService prefService;

	@Override
	public void run()
	{
		final URI uri = parseClipboardUri( readClipboard(), IJ::error );
		if ( uri == null )
			return;
		open( uri );
	}

	/**
	 * Resolve the pasted clipboard text to a URI suitable for
	 * {@link ZarrOpenActions}. Handles three input forms:
	 * <ul>
	 *   <li>{@code http://} or {@code https://} URLs &ndash; used as-is</li>
	 *   <li>{@code file:} URIs &ndash; used as-is</li>
	 *   <li>plain filesystem paths &ndash; converted with
	 *       {@link Paths#get(String, String...)}{@code .toUri()}</li>
	 * </ul>
	 * Reports a user-facing error and returns {@code null} when the clipboard
	 * is empty, the text is not a recognizable URI/path, or the location does
	 * not appear to be an OME-Zarr dataset.
	 */
	static URI parseClipboardUri( final String clipboard, final Consumer< String > errorHandler )
	{
		if ( clipboard == null || clipboard.trim().isEmpty() )
		{
			errorHandler.accept( "The clipboard does not contain any text. "
					+ "Copy an OME-Zarr URL first, then try again." );
			return null;
		}
		final String text = clipboard.trim();

		final URI uri = toUri( text, errorHandler );
		if ( uri == null )
			return null;

		if ( !ZarrLocations.isZarr( uri ) )
		{
			errorHandler.accept( "The pasted location does not appear to be an OME-Zarr dataset:\n" + text + "\n\n"
					+ "Make sure the URL points at an OME-Zarr group root (containing zarr.json, .zgroup, or .zarray)." );
			return null;
		}
		return uri;
	}

	private static URI toUri( final String text, final Consumer< String > errorHandler )
	{
		final URI parsed = tryParseUri( text );
		if ( parsed != null )
		{
			final String scheme = parsed.getScheme();
			if ( "http".equalsIgnoreCase( scheme ) || "https".equalsIgnoreCase( scheme ) || "file".equalsIgnoreCase( scheme ) )
				return parsed;
			if ( scheme != null )
			{
				errorHandler.accept( "Unsupported URL scheme '" + scheme + "':\n" + text + "\n\n"
						+ "Supported schemes are http, https, and file." );
				return null;
			}
		}
		// No recognizable scheme: try treating it as a local path.
		try
		{
			return Paths.get( text ).toUri();
		}
		catch ( InvalidPathException e )
		{
			errorHandler.accept( "Could not interpret the clipboard contents as a URL or path:\n" + text );
			return null;
		}
	}

	private static URI tryParseUri( final String text )
	{
		try
		{
			return new URI( text );
		}
		catch ( Exception e )
		{
			return null;
		}
	}

	private String readClipboard()
	{
		try
		{
			final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			final Transferable contents = clipboard.getContents( null );
			if ( contents == null || !contents.isDataFlavorSupported( DataFlavor.stringFlavor ) )
				return null;
			return ( String ) contents.getTransferData( DataFlavor.stringFlavor );
		}
		catch ( IllegalStateException | UnsupportedFlavorException | IOException e )
		{
			logger.warn( "Could not read clipboard: {}", e.getMessage() );
			return null;
		}
	}

	private void open( final URI uri )
	{
		final ZarrDragAndDropOpenSettings settings = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );
		final ZarrOpenActions actions = createZarrOpenActions( uri, context, settings );
		switch ( settings.getOpenBehavior() )
		{
		case IMAGEJ_HIGHEST_RESOLUTION:
		case IMAGEJ_CUSTOM_RESOLUTION:
			actions.openIJWithImage();
			break;
		case BDV_MULTI_RESOLUTION:
			actions.openBDVWithImage();
			break;
		case SHOW_SELECTION_DIALOG:
		default:
			createDnDActionChooser( context, actions ).showDialog();
			break;
		}
	}

	protected ZarrOpenActions createZarrOpenActions( final URI uri, final Context context,
			final ZarrDragAndDropOpenSettings settings )
	{
		return new ZarrOpenActions( uri, context, settings );
	}

	protected DnDActionChooser createDnDActionChooser( final Context context, final ZarrOpenActions actions )
	{
		return new DnDActionChooser( context, actions );
	}
}