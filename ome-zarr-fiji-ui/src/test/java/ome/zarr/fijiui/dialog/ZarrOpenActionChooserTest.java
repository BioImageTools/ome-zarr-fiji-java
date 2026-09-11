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
package ome.zarr.fijiui.dialog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.Priority;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;

import ome.zarr.fiji.open.ZarrOpenRequest;
import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fiji.open.ZarrOpenerService;

class ZarrOpenActionChooserTest
{
	@BeforeEach
	void requireDisplay()
	{
		assumeFalse( GraphicsEnvironment.isHeadless(), "Skipped in headless environment" );
	}

	@Test
	void showDialogDoesNotThrow()
	{
		assertDoesNotThrow( () -> new ZarrOpenActionChooser( null, null ) );
	}

	@Test
	void showDialogCreatesVisibleDialog() throws Exception
	{
		ZarrOpenActionChooser chooser = new ZarrOpenActionChooser( null, null );
		SwingUtilities.invokeAndWait( chooser::showDialog );

		JDialog dialog = chooser.currentDialog;
		assertNotNull( dialog, "showDialog should have created a JDialog" );
		assertTrue( dialog.isShowing() );

		SwingUtilities.invokeAndWait( chooser::dispose );
		assertFalse( dialog.isShowing() );
	}

	@Test
	void theDialogOffersOneButtonPerRegisteredOpenerPlusHelp() throws Exception
	{
		try (Context context = new Context())
		{
			final ZarrOpenRequest request = requestIn( context );
			final int openerCount = context.getService( ZarrOpenerService.class ).getOpenerInfos().size();
			assertTrue( openerCount > 1, "The shipped openers should all be registered" );

			final ZarrOpenActionChooser chooser = new ZarrOpenActionChooser( context, request );
			SwingUtilities.invokeAndWait( chooser::showDialog );
			try
			{
				assertEquals( openerCount + 1, buttonsOf( chooser ).size(),
						"One button per offered opener, plus the help button" );
			}
			finally
			{
				SwingUtilities.invokeAndWait( chooser::dispose );
			}
		}
	}

	@Test
	void pressingAnOpenerButtonRunsThatOpenerAndClosesTheDialog() throws Exception
	{
		try (Context context = new Context())
		{
			// Highest priority, so it is the first button.
			final PluginInfo< ZarrOpener > info = new PluginInfo<>( LatchOpener.class, ZarrOpener.class );
			info.setName( "test-latch-opener" );
			info.setPriority( Priority.FIRST );
			context.getService( PluginService.class ).addPlugin( info );

			LatchOpener.opened = new CountDownLatch( 1 );
			final ZarrOpenActionChooser chooser = new ZarrOpenActionChooser( context, requestIn( context ) );
			SwingUtilities.invokeAndWait( chooser::showDialog );
			final JDialog dialog = chooser.currentDialog;

			SwingUtilities.invokeAndWait( () -> press( buttonsOf( chooser ).get( 0 ) ) );

			assertTrue( LatchOpener.opened.await( 10, TimeUnit.SECONDS ), "The chosen opener should have run" );
			assertFalse( dialog.isShowing(), "Choosing an opener closes the dialog" );
		}
	}

	/** Counts down instead of opening anything, so no dataset is read. */
	public static class LatchOpener implements ZarrOpener
	{
		static CountDownLatch opened;

		@Override
		public void open( final ZarrOpenRequest request )
		{
			opened.countDown();
		}
	}

	/**
	 * Fires the button's listeners rather than calling {@code doClick()}, which
	 * would repaint the button — and the macOS Aqua look and feel throws when
	 * painting the empty icon of an opener that declares none.
	 */
	private static void press( final JButton button )
	{
		for ( final ActionListener listener : button.getActionListeners() )
			listener.actionPerformed( new ActionEvent( button, ActionEvent.ACTION_PERFORMED, "" ) );
	}

	private static ZarrOpenRequest requestIn( final Context context )
	{
		return new ZarrOpenRequest( URI.create( "file:/tmp/not-read-by-this-test.ome.zarr" ), context, null, null,
				message -> {} );
	}

	/** The chooser's buttons, in the order they were added. */
	private static List< JButton > buttonsOf( final ZarrOpenActionChooser chooser )
	{
		final List< JButton > buttons = new ArrayList<>();
		collectButtons( chooser.currentDialog.getContentPane(), buttons );
		return buttons;
	}

	private static void collectButtons( final Container container, final List< JButton > buttons )
	{
		for ( final Component component : container.getComponents() )
		{
			if ( component instanceof JButton )
				buttons.add( ( JButton ) component );
			else if ( component instanceof Container )
				collectButtons( ( Container ) component, buttons );
		}
	}
}
