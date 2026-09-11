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
package ome.zarr.examples.demo;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.net.URI;

import org.scijava.Context;
import org.scijava.Priority;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;

import ome.zarr.fiji.open.ZarrOpenRequest;
import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fijiui.dialog.ZarrOpenActionChooser;

@SuppressWarnings( "all" )
public class ZarrOpenActionChooserDemo
{

	private static void setupFrame( JFrame frame, ZarrOpenActionChooser menu )
	{
		frame.setTitle( "Keyboard Submenu Example" );
		frame.setSize( 600, 400 );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setLocationRelativeTo( null );

		// Add a label for visual feedback
		JLabel label = new JLabel( "Press 'K' to show submenu at cursor position", JLabel.CENTER );
		frame.add( label );
		frame.setVisible( true );

		// Use key bindings instead of KeyListener for better reliability
		JRootPane rootPane = frame.getRootPane();

		rootPane.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW )
				.put( KeyStroke.getKeyStroke( KeyEvent.VK_K, 0 ), "showSubmenu" );

		rootPane.getActionMap().put( "showSubmenu", new AbstractAction()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				menu.showDialog();
			}
		} );
	}

	public static void main( String[] args )
	{
		// Fiji runs on the system look and feel; the default Metal one gives its buttons
		// fatter margins, which would show the dialog roomier here than it really is.
		try
		{
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		}
		catch ( final Exception e )
		{
			System.out.println( "Staying on the default look and feel: " + e );
		}
		// A real context, because the buttons are the ZarrOpeners registered in it.
		final Context context = new Context();
		// More than the six shipped ones, to see the dialog wrap into a further row.
		registerDummyOpeners( context );
		SwingUtilities.invokeLater( () -> {
			final JFrame mainFrame = new JFrame();
			// A real request: an opener's tooltip may read it, as the script editor's does.
			final ZarrOpenRequest request =
					new ZarrOpenRequest( URI.create( "file:/tmp/demo.ome.zarr" ), context, null );
			final ZarrOpenActionChooser menu = new ZarrOpenActionChooser( context, request );
			setupFrame( mainFrame, menu );
		} );
	}

	/**
	 * Adds three openers that open nothing, so the dialog can be eyeballed with more
	 * openers installed than fit into its three columns.
	 * <p>
	 * They are registered at runtime rather than through a {@code @Plugin}
	 * annotation, so they exist only while this demo runs and never turn up in a test
	 * or in a real Fiji. Their priority puts them behind every shipped opener, so
	 * they also cannot become anybody's default.
	 */
	private static void registerDummyOpeners( final Context context )
	{
		final PluginService pluginService = context.getService( PluginService.class );
		pluginService.addPlugin( dummyInfo( DummyOpenerOne.class, "dummy-one", "Dummy one", "dummy_one_icon.png" ) );
		pluginService.addPlugin( dummyInfo( DummyOpenerTwo.class, "dummy-two", "Dummy two", "dummy_two_icon.png" ) );
		pluginService.addPlugin(
				dummyInfo( DummyOpenerThree.class, "dummy-three", "Dummy three", "dummy_three_icon.png" ) );
	}

	private static PluginInfo< ZarrOpener > dummyInfo( final Class< ? extends ZarrOpener > openerClass,
			final String name, final String label, final String iconName )
	{
		final PluginInfo< ZarrOpener > info = new PluginInfo<>( openerClass, ZarrOpener.class );
		info.setName( name );
		info.setLabel( label );
		info.setDescription( label + " – a demo opener that does nothing" );
		// Resolved against the contributing class, exactly as a third-party opener's is.
		info.setIconPath( "/ome/zarr/examples/demo/" + iconName );
		info.setPriority( Priority.VERY_LOW );
		return info;
	}

	public static class DummyOpenerOne implements ZarrOpener
	{
		@Override
		public void open( final ZarrOpenRequest request )
		{
			System.out.println( "Dummy one would open " + request.uri() );
		}
	}

	public static class DummyOpenerTwo implements ZarrOpener
	{
		@Override
		public void open( final ZarrOpenRequest request )
		{
			System.out.println( "Dummy two would open " + request.uri() );
		}
	}

	public static class DummyOpenerThree implements ZarrOpener
	{
		@Override
		public void open( final ZarrOpenRequest request )
		{
			System.out.println( "Dummy three would open " + request.uri() );
		}
	}
}
