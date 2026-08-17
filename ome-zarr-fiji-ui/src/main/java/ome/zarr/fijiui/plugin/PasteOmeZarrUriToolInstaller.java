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
package ome.zarr.fijiui.plugin;

import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.net.URI;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import net.imagej.patcher.LegacyInjector;
import org.scijava.Context;
import org.scijava.event.EventHandler;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;
import org.scijava.ui.event.UIShownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.IJ;
import ij.gui.Toolbar;
import ij.plugin.tool.MacroToolRunner;
import ome.zarr.fijiui.open.PasteToOpenAction;
import ome.zarr.fijiui.util.ClipboardUtils;

/**
 * Installs {@code PasteOmeZarrUrlActionTool} into the legacy ImageJ1 toolbar
 * and registers a global keyboard shortcut (Ctrl/Cmd+Shift+V) once the UI is
 * shown.
 * <p>
 * Four subtleties shape this implementation:
 * <ul>
 *   <li><b>Why {@code @EventHandler(UIShownEvent)} and not
 *       {@code initialize()}</b>: {@link Toolbar#getInstance()} returns
 *       {@code null} until the IJ1 user interface is up, so during
 *       {@code Context} construction there is no toolbar to install into
 *       yet. By the time {@code UIShownEvent} fires, every service is
 *       initialized and the toolbar exists.</li>
 *   <li><b>Why the static initializer calls
 *       {@link LegacyInjector#preinit()}</b>: SciJava walks our declared
 *       fields and methods to discover {@code @Parameter}s and
 *       {@code @EventHandler} bindings while the {@code Context} is still
 *       being built, which resolves the {@code ij.*} types in our
 *       signatures. Without patching up front they would be loaded
 *       unpatched, and {@code LegacyInjector.injectHooks} would later fail
 *       with {@code "duplicate class definition for ij.gui.Toolbar"},
 *       leaving those classes without their IJ1/IJ2 bridge hooks.
 *       {@code preinit()} is idempotent, so running it early is harmless.</li>
 *   <li><b>Why we set {@code installingStartupTool=true} via reflection</b>:
 *       {@code Toolbar.addPlugInTool} ends with {@code setTool(ourId)}.
 *       Even though our {@code "Action Tool"} name causes {@code setTool}
 *       to early-return without changing {@code current}, the slot still
 *       comes up looking pressed. Setting the private
 *       {@code installingStartupTool} flag makes {@code addPlugInTool}
 *       skip that {@code setTool} call entirely; IJ1 itself clears the
 *       flag back to {@code false} inside {@code addPlugInTool}.</li>
 *   <li><b>Why the keyboard shortcut is Ctrl/Cmd+Shift+V and not
 *       Ctrl/Cmd+V</b>: IJ1 hard-codes Ctrl+V as its "Paste" command (for
 *       image clipboard content). Using Shift avoids that conflict. The
 *       shortcut is registered as a global {@link KeyEventDispatcher} rather
 *       than a menu accelerator so it fires regardless of which IJ1 window is
 *       in front. It is suppressed when a {@link JTextComponent} (script
 *       editor, macro editor) has focus so normal text paste is unaffected.
 *       The dispatcher is removed when the service is disposed.</li>
 * </ul>
 * No-op in headless environments and when {@code Toolbar.getInstance()} is
 * unavailable (e.g. unit tests that don't start IJ1).
 */
@Plugin( type = Service.class )
public class PasteOmeZarrUriToolInstaller extends AbstractService implements SciJavaService
{
	static
	{
		// Patch IJ1 before ij.* classes are loaded during Context startup:
		// unpatched IJ1 classes make the later patcher run fail with a LinkageError.
		LegacyInjector.preinit();
	}

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private KeyEventDispatcher keyEventDispatcher;

	@EventHandler
	void onUIShown( @SuppressWarnings( "unused" ) final UIShownEvent event )
	{
		if ( GraphicsEnvironment.isHeadless() )
			return;
		try
		{
			final Toolbar toolbar = Toolbar.getInstance();
			if ( toolbar == null )
				return;

			// Skip the trailing setTool(ourId) inside addPlugInTool so the
			// slot doesn't render in pressed state. installingStartupTool is
			// a private instance field; IJ1 itself resets it to false inside
			// addPlugInTool.
			suppressStartupToolSelection( toolbar );

			Toolbar.addPlugInTool( new PasteOmeZarrUriActionTool( getContext() ) );
		}
		catch ( RuntimeException e )
		{
			logger.warn( "Could not install OME-Zarr toolbar button: {}", e.getMessage() );
		}
		installKeyboardShortcut();
	}

	@Override
	public void dispose()
	{
		if ( keyEventDispatcher != null )
		{
			KeyboardFocusManager.getCurrentKeyboardFocusManager()
					.removeKeyEventDispatcher( keyEventDispatcher );
			keyEventDispatcher = null;
		}
		super.dispose();
	}

	@SuppressWarnings( "java:S3011" ) // setAccessible is the only way to reach this private IJ1 field
	private void suppressStartupToolSelection( final Toolbar toolbar )
	{
		try
		{
			final Field flag = Toolbar.class.getDeclaredField( "installingStartupTool" );
			flag.setAccessible( true );
			flag.setBoolean( toolbar, true );
		}
		catch ( ReflectiveOperationException | RuntimeException e )
		{
			logger.debug( "Could not set Toolbar.installingStartupTool: {}", e.getMessage() );
		}
	}

	private void installKeyboardShortcut()
	{
		if ( keyEventDispatcher != null )
			return;
		keyEventDispatcher = e -> {
			if ( e.getID() != KeyEvent.KEY_PRESSED )
				return false;
			if ( e.getKeyCode() != KeyEvent.VK_V )
				return false;
			if ( !( e.isControlDown() || e.isMetaDown() || e.isShiftDown() ) )
				return false;
			if ( e.getSource() instanceof JTextComponent )
				return false;
			final URI uri = ClipboardUtils.readClipboardAsUri( logger::debug );
			if ( uri == null )
				return false;
			return PasteToOpenAction.pasteFromClipboard( getContext(), null );
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher( keyEventDispatcher );
	}

	/**
	 * Legacy ImageJ1 toolbar button that pastes an OME-Zarr URL from the
	 * clipboard and opens it.
	 * <p>
	 * Lives in one of the legacy IJ1 {@code Toolbar} slots (between the arrow
	 * tool and the {@code >>} switcher). The {@code "Action Tool"} suffix in
	 * {@link #getToolName()} is what makes IJ1 treat this as a one-shot button:
	 * each click invokes {@link #runMacroTool(String)} without changing the
	 * active tool. {@link MacroToolRunner} is the only
	 * {@link ij.plugin.tool.PlugInTool} subclass that
	 * {@code Toolbar.isMacroTool} recognizes for this dispatch path, so we
	 * extend it (with a {@code null} installer) and override
	 * {@code runMacroTool} ourselves.
	 */
	private static class PasteOmeZarrUriActionTool extends MacroToolRunner
	{
		// Non-breaking hyphen (‑) instead of an ASCII hyphen: IJ1's Toolbar.addTool
		// parses the concatenated "name - iconMacro" string by calling indexOf('-'), so an
		// ASCII hyphen in "OME-Zarr" would be mistaken for the name/icon separator.  That
		// would truncate names[slot] to "Paste OME", hide the "Action Tool" marker, and
		// make the button behave as a sticky (mode-switching) tool rather than a one-shot
		// button — every subsequent click would show another error dialog.
		static final String NAME = "Paste OME‑Zarr URI Action Tool";

		/**
		 * Macro-icon string drawn on the IJ1 toolbar slot. The macro icon
		 * language uses a 16x16 grid (positions 0..f as single hex digits) and
		 * supports a handful of drawing primitives. This string paints a black
		 * clipboard with a downward arrow inside — the standard "content
		 * arriving into the clipboard" idiom for a paste action:
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

		public PasteOmeZarrUriActionTool( final Context context )
		{
			super( null );
			this.context = context;
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
			// IJ1 calls runMacroTool twice per click: once from mousePressed (EDT) and
			// once from a popup-menu timer (TimerThread). Only the EDT call is genuine.
			if ( !SwingUtilities.isEventDispatchThread() )
				return;
			PasteToOpenAction.pasteFromClipboard( context, IJ::error );
		}
	}
}
