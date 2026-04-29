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

import java.awt.GraphicsEnvironment;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.scijava.event.EventHandler;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;
import org.scijava.ui.event.UIShownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs {@link PasteOmeZarrUrlActionTool} into the legacy ImageJ1 toolbar
 * once the UI is shown, so the button occupies one of the empty slots between
 * the arrow tool and the {@code >>} switcher without the user having to add
 * it via the More Tools menu.
 * <p>
 * Three subtleties shape this implementation:
 * <ul>
 *   <li><b>Why {@code @EventHandler(UIShownEvent)} and not
 *       {@code initialize()}</b>: scheduling the install via
 *       {@code SwingUtilities.invokeLater} from {@code initialize} lets the
 *       EDT pick up the task <em>during</em> {@code Context} construction,
 *       before {@code LegacyService.<clinit>} has run
 *       {@code LegacyInjector.preinit()} — the first {@code ij.IJ}
 *       reference would then load it unpatched
 *       ({@code "No _hooks field found in ij.IJ"}). By the time
 *       {@code UIShownEvent} fires, every service is initialized, the
 *       patcher has run, and the toolbar is up.</li>
 *   <li><b>Why all {@code ij.*} access is reflective</b>: SciJava walks our
 *       declared methods to discover {@code @EventHandler} bindings, which
 *       resolves the parameter types of every method on this class. Putting
 *       {@code ij.gui.Toolbar} in a signature, field type, or class literal
 *       loads {@code ij.gui.Toolbar} into the AppClassLoader before the IJ1
 *       patcher gets to it, producing a
 *       {@code "duplicate class definition for ij.gui.Toolbar"} warning when
 *       {@code LegacyInjector.injectHooks} later tries to redefine it.
 *       Routing through {@code Class.forName} keeps the installer's
 *       constant pool free of {@code ij.*} types until the event fires.</li>
 *   <li><b>Why we set {@code installingStartupTool=true} via reflection</b>:
 *       {@code Toolbar.addPlugInTool} ends with {@code setTool(ourId)}.
 *       Even though our {@code "Action Tool"} name causes {@code setTool}
 *       to early-return without changing {@code current}, the slot still
 *       comes up looking pressed. Setting the private
 *       {@code installingStartupTool} flag makes {@code addPlugInTool}
 *       skip that {@code setTool} call entirely; IJ1 itself clears the
 *       flag back to {@code false} inside {@code addPlugInTool}.</li>
 * </ul>
 * No-op in headless environments and when {@code Toolbar.getInstance()} is
 * unavailable (e.g. unit tests that don't start IJ1).
 */
@Plugin( type = Service.class )
public class PasteOmeZarrUrlToolInstaller extends AbstractService implements SciJavaService
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Parameter
	private PrefService prefService;

	@EventHandler
	void onUIShown( @SuppressWarnings( "unused" ) final UIShownEvent event )
	{
		if ( GraphicsEnvironment.isHeadless() )
			return;
		try
		{
			final Class< ? > toolbarClass = Class.forName( "ij.gui.Toolbar" );
			final Object toolbar = toolbarClass.getMethod( "getInstance" ).invoke( null );
			if ( toolbar == null )
				return;

			// Skip the trailing setTool(ourId) inside addPlugInTool so the
			// slot doesn't render in pressed state. installingStartupTool is
			// a private instance field; IJ1 itself resets it to false inside
			// addPlugInTool.
			try
			{
				final Field flag = toolbarClass.getDeclaredField( "installingStartupTool" );
				flag.setAccessible( true );
				flag.setBoolean( toolbar, true );
			}
			catch ( ReflectiveOperationException | RuntimeException e )
			{
				logger.debug( "Could not set Toolbar.installingStartupTool: {}", e.getMessage() );
			}

			final Class< ? > pluginToolClass = Class.forName( "ij.plugin.tool.PlugInTool" );
			final Method addPlugInTool = toolbarClass.getMethod( "addPlugInTool", pluginToolClass );
			addPlugInTool.invoke( null, new PasteOmeZarrUrlActionTool( getContext(), prefService ) );
		}
		catch ( ReflectiveOperationException | RuntimeException e )
		{
			logger.warn( "Could not install OME-Zarr toolbar button: {}", e.getMessage() );
		}
	}
}
