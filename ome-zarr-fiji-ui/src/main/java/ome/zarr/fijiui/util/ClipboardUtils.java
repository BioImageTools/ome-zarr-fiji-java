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
package ome.zarr.fijiui.util;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for reading text from the system clipboard and parsing it
 * into a {@link URI}.
 */
public final class ClipboardUtils
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private ClipboardUtils()
	{
		// prevent instantiation
	}

	/**
	 * Reads the current text content of the system clipboard.
	 *
	 * @return the clipboard text, or {@code null} if the clipboard is empty,
	 *   contains no text, or cannot be accessed
	 */
	public static String readClipboard()
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

	/**
	 * Resolve text read from clipboard to a URI suitable for opening an OME-Zarr dataset.
	 * Handles three input forms:
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
	public static URI readClipboardAsUri( final Consumer< String > errorHandler )
	{
		return stringToUri( readClipboard(), errorHandler );
	}

	/**
	 * Converts a string to a {@link URI} suitable for opening an OME-Zarr dataset.
	 * Handles three input forms:
	 * <ul>
	 *   <li>{@code http://} or {@code https://} URLs &ndash; used as-is</li>
	 *   <li>{@code file:} URIs &ndash; used as-is</li>
	 *   <li>plain filesystem paths &ndash; converted with
	 *       {@link Paths#get(String, String...)}{@code .toUri()}</li>
	 * </ul>
	 * Reports a user-facing error via {@code errorHandler} and returns
	 * {@code null} when {@code possibleUri} is blank, uses an unsupported scheme,
	 * or cannot be interpreted as a path.
	 *
	 * @param possibleUri the string to parse; may be {@code null}
	 * @param errorHandler receives a human-readable message on failure
	 * @return the resolved {@link URI}, or {@code null} on failure
	 */
	public static URI stringToUri( final String possibleUri, final Consumer< String > errorHandler )
	{
		if ( possibleUri == null || possibleUri.trim().isEmpty() )
		{
			errorHandler.accept( "The clipboard does not contain any text." );
			return null;
		}
		final String text = possibleUri.trim();

		URI parsed = null;
		try
		{
			parsed = new URI( text );
		}
		catch ( Exception e )
		{
			logger.debug( "Text is not valid URI syntax, will try as a local path: {}", e.getMessage() );
		}

		// If parsing succeeded, check whether the scheme is one we support.
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
}
