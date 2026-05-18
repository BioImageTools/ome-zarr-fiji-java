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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.function.Consumer;

import ij.plugin.tool.MacroToolRunner;
import javax.swing.JTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.scijava.Context;

import sc.fiji.ome.zarr.open.ClipboardActions;
import sc.fiji.ome.zarr.util.ClipboardUtils;

class PasteOmeZarrUriToolInstallerTest
{
	private Context context;

	private PasteOmeZarrUriToolInstaller installer;

	@BeforeEach
	void setUp()
	{
		context = new Context();
		installer = new PasteOmeZarrUriToolInstaller();
		installer.setContext( context );
	}

	@AfterEach
	void tearDown()
	{
		installer.dispose();
		context.close();
	}

	// ---- tool name and icon ----

	@Test
	void toolNameContainsNonBreakingHyphen() throws Exception
	{
		assertTrue( toolName().contains( "‑" ), "Tool name must use non-breaking hyphen (U+2011); an ASCII hyphen would be mistaken "
				+ "for the IJ1 Toolbar name/icon separator and truncate the name" );
	}

	@Test
	void toolNameDoesNotContainAsciiHyphen() throws Exception
	{
		assertFalse( toolName().contains( "-" ), "ASCII hyphen in the tool name would break IJ1 Toolbar.addTool name/icon parsing" );
	}

	@Test
	void toolNameEndsWithActionTool() throws Exception
	{
		assertTrue( toolName().endsWith( "Action Tool" ),
				"'Action Tool' suffix is required for IJ1 to treat the slot as a one-shot button" );
	}

	@Test
	void getToolIconReturnsNonEmpty() throws Exception
	{
		Object tool = createActionTool();
		String icon = ( String ) tool.getClass().getMethod( "getToolIcon" ).invoke( tool );
		assertNotNull( icon );
		assertFalse( icon.isEmpty() );
	}

	// ---- dispose safety ----

	@Test
	void disposeIsNoOpWhenNoShortcutInstalled()
	{
		assertDoesNotThrow( () -> installer.dispose() );
	}

	// ---- onUIShown ----

	@Test
	void onUIShownDoesNotThrow()
	{
		// In headless the method returns immediately; in non-headless it returns
		// early because Toolbar.getInstance() is null when IJ1 is not running.
		assertDoesNotThrow( () -> installer.onUIShown( null ) );
	}

	// ---- keyboard event dispatcher filter logic ----

	@Test
	void dispatcherIgnoresNonKeyPressedEvent() throws Exception
	{
		callInstallKeyboardShortcut( installer );
		KeyEventDispatcher dispatcher = getDispatcherField( installer );
		KeyEvent released = keyEvent( KeyEvent.KEY_RELEASED, ctrlShift(), KeyEvent.VK_V );

		try (MockedStatic< ClipboardActions > mock = mockStatic( ClipboardActions.class ))
		{
			assertFalse( dispatcher.dispatchKeyEvent( released ) );
			mock.verifyNoInteractions();
		}
	}

	@Test
	void dispatcherIgnoresNonVKey() throws Exception
	{
		callInstallKeyboardShortcut( installer );
		KeyEventDispatcher dispatcher = getDispatcherField( installer );
		KeyEvent wrongKey = keyEvent( KeyEvent.KEY_PRESSED, ctrlShift(), KeyEvent.VK_C );

		try (MockedStatic< ClipboardActions > mock = mockStatic( ClipboardActions.class ))
		{
			assertFalse( dispatcher.dispatchKeyEvent( wrongKey ) );
			mock.verifyNoInteractions();
		}
	}

	@Test
	void dispatcherIgnoresVWithoutModifiers() throws Exception
	{
		callInstallKeyboardShortcut( installer );
		KeyEventDispatcher dispatcher = getDispatcherField( installer );
		KeyEvent noModifier = keyEvent( KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_V );

		try (MockedStatic< ClipboardActions > mock = mockStatic( ClipboardActions.class ))
		{
			assertFalse( dispatcher.dispatchKeyEvent( noModifier ) );
			mock.verifyNoInteractions();
		}
	}

	@Test
	void dispatcherIgnoresEventFromTextComponent() throws Exception
	{
		callInstallKeyboardShortcut( installer );
		KeyEventDispatcher dispatcher = getDispatcherField( installer );
		JTextField textField = mock( JTextField.class );
		KeyEvent e = new KeyEvent( textField, KeyEvent.KEY_PRESSED, 0L, ctrlShift(), KeyEvent.VK_V, 'V' );

		try (MockedStatic< ClipboardActions > mock = mockStatic( ClipboardActions.class ))
		{
			assertFalse( dispatcher.dispatchKeyEvent( e ) );
			mock.verifyNoInteractions();
		}
	}

	@Test
	void dispatcherReturnsFalseWhenClipboardHasNoUri() throws Exception
	{
		callInstallKeyboardShortcut( installer );
		KeyEventDispatcher dispatcher = getDispatcherField( installer );
		KeyEvent e = keyEvent( KeyEvent.KEY_PRESSED, ctrlShift(), KeyEvent.VK_V );

		try (MockedStatic< ClipboardUtils > mockUtils = mockStatic( ClipboardUtils.class );
				MockedStatic< ClipboardActions > mockActions = mockStatic( ClipboardActions.class ))
		{
			mockUtils.when( () -> ClipboardUtils.readClipboardAsUri( any( Consumer.class ) ) ).thenReturn( null );

			assertFalse( dispatcher.dispatchKeyEvent( e ) );
			mockActions.verifyNoInteractions();
		}
	}

	@Test
	void dispatcherCallsPasteFromClipboardWhenUriPresent() throws Exception
	{
		callInstallKeyboardShortcut( installer );
		KeyEventDispatcher dispatcher = getDispatcherField( installer );
		KeyEvent e = keyEvent( KeyEvent.KEY_PRESSED, ctrlShift(), KeyEvent.VK_V );
		URI uri = URI.create( "https://example.com/data.zarr" );

		try (MockedStatic< ClipboardUtils > mockUtils = mockStatic( ClipboardUtils.class );
				MockedStatic< ClipboardActions > mockActions = mockStatic( ClipboardActions.class ))
		{
			mockUtils.when( () -> ClipboardUtils.readClipboardAsUri( any( Consumer.class ) ) ).thenReturn( uri );
			mockActions.when( () -> ClipboardActions.pasteFromClipboard( any(), isNull() ) ).thenReturn( true );

			assertTrue( dispatcher.dispatchKeyEvent( e ) );
			mockActions.verify( () -> ClipboardActions.pasteFromClipboard( context, null ) );
		}
	}

	// ---- helpers ----

	private void callInstallKeyboardShortcut( final PasteOmeZarrUriToolInstaller target ) throws Exception
	{
		Method m = PasteOmeZarrUriToolInstaller.class.getDeclaredMethod( "installKeyboardShortcut" );
		m.setAccessible( true );
		m.invoke( target );
	}

	private KeyEventDispatcher getDispatcherField( final PasteOmeZarrUriToolInstaller target ) throws Exception
	{
		Field f = PasteOmeZarrUriToolInstaller.class.getDeclaredField( "keyEventDispatcher" );
		f.setAccessible( true );
		return ( KeyEventDispatcher ) f.get( target );
	}

	private KeyEvent keyEvent( final int id, final int modifiers, final int keyCode )
	{
		Component source = mock( Component.class );
		return new KeyEvent( source, id, 0L, modifiers, keyCode, ( char ) keyCode );
	}

	private int ctrlShift()
	{
		return KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
	}

	private String toolName() throws Exception
	{
		Field f = actionToolClass().getDeclaredField( "NAME" );
		f.setAccessible( true );
		return ( String ) f.get( null );
	}

	private Class< ? > actionToolClass()
	{
		return Arrays.stream( PasteOmeZarrUriToolInstaller.class.getDeclaredClasses() )
				.filter( MacroToolRunner.class::isAssignableFrom )
				.findFirst()
				.orElseThrow( () -> new AssertionError( "PasteOmeZarrUrlActionTool inner class not found" ) );
	}

	private Object createActionTool() throws Exception
	{
		Constructor< ? > ctor = actionToolClass().getDeclaredConstructor( Context.class );
		ctor.setAccessible( true );
		return ctor.newInstance( context );
	}
}
