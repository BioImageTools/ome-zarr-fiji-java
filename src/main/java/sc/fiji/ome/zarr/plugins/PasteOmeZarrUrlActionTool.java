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
	 *   <li>{@code R4499} — clipboard body outline. {@code Toolbar.drawIcon}
	 *       offsets {@code R} by {@code -scale} in both axes (no such offset
	 *       applies to {@code F} or {@code L}), so {@code 4,4} lands the
	 *       rectangle's top-left at pixel {@code (3,3)}; with {@code w=h=9}
	 *       it spans {@code (3,3)..(12,12)}, center {@code (7.5, 7.5)}.</li>
	 *   <li>{@code F6042} — filled 4x2 clip at {@code (6,0)..(9,1)},
	 *       center x {@code 7.5}, sitting just above the body.</li>
	 *   <li>{@code L7578} — vertical arrow stem at x=7 from y=5 to y=8.</li>
	 *   <li>{@code L5898} — horizontal top of the arrow head at y=8 from
	 *       x=5 to x=9.</li>
	 *   <li>{@code L5879}, {@code L9879} — diagonals from the head's
	 *       corners down to the tip at {@code (7,9)}.</li>
	 * </ul>
	 */
	private static final String ICON = "C000R4499F6042L7578L5898L5879L9879";

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
