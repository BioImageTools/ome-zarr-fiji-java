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
package ome.zarr.fiji.open;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import ome.zarr.fiji.read.OmeZarr;

/**
 * Something that can open an OME-Zarr location — the extension point a Fiji
 * plugin implements to appear next to ImageJ and BigDataViewer wherever this
 * project offers a choice of what to do with a dataset.
 * <p>
 * An opener is a plain SciJava plugin, so registering one takes nothing but an
 * annotation on a class in your own jar:
 *
 * <pre>
 * &#64;Plugin( type = OmeZarrOpener.class, name = "my-opener", label = "My viewer",
 *          description = "Open the OME-Zarr in My viewer",
 *          iconPath = "/icons/my-opener.png", priority = Priority.VERY_HIGH )
 * public class MyOmeZarrOpener implements OmeZarrOpener
 * {
 *     &#64;Override
 *     public void open( final OmeZarr omeZarr )
 *     {
 *         MyViewer.open( omeZarr.uri() );
 *     }
 * }
 * </pre>
 *
 * {@code name} identifies the opener in the persisted settings and must stay
 * stable across releases; {@code label}, {@code description} and
 * {@code iconPath} are what the user sees, the icon being resolved against the
 * annotated class and hence out of the contributing jar. {@code priority}
 * orders the openers wherever they are listed and decides which one a user who
 * never configured a choice gets — an explicit choice always wins, and the
 * openers shipped here sit at {@link org.scijava.Priority#HIGH} and below.
 *
 * @see OmeZarrOpenerService
 */
public interface OmeZarrOpener extends SciJavaPlugin
{
	/**
	 * Opens the location {@code omeZarr} is configured for. Called on a background
	 * thread, never on the AWT event dispatch thread, so it may read and block.
	 * <p>
	 * An opener that brings its own reading code needs only {@link OmeZarr#uri()}
	 * and can ignore the rest.
	 * <br>
	 * Failures are the opener's own to report, through
	 * {@link OmeZarr#errorHandler()}.
	 *
	 * @param omeZarr the dataset to open, together with the settings to open it
	 *   with — it is the target of this call, not a collaborator the opener has
	 *   to drive
	 */
	void open( OmeZarr omeZarr );

	/**
	 * The tooltip for this opener's button in the selection dialog, when the
	 * static {@code description} of the {@link Plugin} annotation does not say
	 * enough — for an opener that names the script or the viewer it is currently
	 * configured for, say.
	 *
	 * @param omeZarr the OME-Zarr the dialog was raised for
	 * @return the tooltip text, or {@code null} to use the annotation's
	 *   {@code description}
	 */
	default String tooltip( final OmeZarr omeZarr )
	{
		return null;
	}
}
