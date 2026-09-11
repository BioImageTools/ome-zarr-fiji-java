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
import ome.zarr.fijiui.util.ScriptUtils;

/**
 * Opens the Fiji script editor on the user's preset script (or on the built-in
 * example) with the location handed in as the {@code path} variable, so the
 * dataset can be opened from a macro or script instead of by hand.
 */
@Plugin( type = ZarrOpener.class, name = ScriptEditorOpener.NAME,
		label = "Script editor",
		description = "Open the OME-Zarr in the script editor, using your preset script",
		iconPath = "/ome/zarr/fijiui/dialog/script_icon.png", priority = Priority.LOW - 2 )
public class ScriptEditorOpener implements ZarrOpener
{
	/** The stable identifier this opener is persisted under. */
	public static final String NAME = "script-editor";

	@Override
	public void open( final ZarrOpenRequest request )
	{
		new ZarrOpenActions( request ).runScript();
	}

	/** Names the script that is actually configured, which the label cannot. */
	@Override
	public String tooltip( final ZarrOpenRequest request )
	{
		return "Open OME-Zarr in user script:\n\n" + ScriptUtils.getTooltipText( request.context() );
	}
}
