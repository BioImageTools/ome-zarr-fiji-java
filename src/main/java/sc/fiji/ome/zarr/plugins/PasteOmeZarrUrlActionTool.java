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

import org.scijava.Context;
import org.scijava.prefs.PrefService;

import ij.IJ;
import ij.plugin.tool.MacroToolRunner;

/**
 * Legacy ImageJ1 toolbar button that pastes an OME-Zarr URL from the clipboard
 * and opens it.
 * <p>
 * Lives in one of the legacy IJ1 {@code Toolbar} slots (between the arrow tool
 * and the {@code >>} switcher). The {@code "Action Tool"} suffix in
 * {@link #getToolName()} is what makes IJ1 treat this as a one-shot button:
 * each click invokes {@link #runMacroTool(String)} without changing the active
 * tool. {@link MacroToolRunner} is the only {@link ij.plugin.tool.PlugInTool}
 * subclass that {@code Toolbar.isMacroTool} recognizes for this dispatch path,
 * so we extend it (with a {@code null} installer) and override
 * {@code runMacroTool} ourselves.
 */
public class PasteOmeZarrUrlActionTool extends MacroToolRunner
{
	static final String NAME = "Paste OME-Zarr URL Action Tool";

	/**
	 * Macro-icon string drawn on the IJ1 toolbar slot. The macro icon language
	 * uses a 16x16 grid (positions 0..f as single hex digits) and supports a
	 * handful of drawing primitives. This string paints a black clipboard
	 * with a downward arrow inside — the standard "content arriving into the
	 * clipboard" idiom for a paste action:
	 * <ul>
	 *   <li>{@code C000} — drawing color black</li>
	 *   <li>{@code R00ee} — icon outline. {@code Toolbar.drawIcon}</li>
	 *   <li>{@code L7278} — vertical arrow stem at x=7 from y=2 to y=8.</li>
	 *   <li>{@code L48a8} — horizontal top of the arrow head at y=8 from
	 *       x=4 to x=a.</li>
	 *   <li>{@code L487b}, {@code La87b} — diagonals from the head's
	 *       corners down to the tip at {@code (7,b)}.</li>
	 * </ul>
	 */
	private static final String ICON = "C000 R00ee L7278 L48a8 L487b La87b";

	private final Context context;

	private final PrefService prefService;

	public PasteOmeZarrUrlActionTool( final Context context, final PrefService prefService )
	{
		super( null );
		this.context = context;
		this.prefService = prefService;
	}

	@Override
	public String getToolName()
	{
		return NAME;
	}

	@Override
	public String getToolIcon()
	{
		return ICON;
	}

	@Override
	public void runMacroTool( final String name )
	{
		PasteOmeZarrUrlCommand.pasteFromClipboard( context, prefService, IJ::error );
	}
}
