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
package sc.fiji.ome.zarr.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class DnDActionChooserDemo
{

	private static void setupFrame( JFrame frame, DnDActionChooser menu )
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

		rootPane.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW )
				.put( KeyStroke.getKeyStroke( KeyEvent.VK_L, 0 ), "changeSubmenu" );

		//rootPane.getActionMap().put("changeSubmenu", (e) -> shouldShowCustomItems ^= true );
		rootPane.getActionMap().put( "changeSubmenu", new AbstractAction()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				menu.setShowExtendedVersion( false );
			}
		} );
	}

	public static void main( String[] args )
	{
		SwingUtilities.invokeLater( () -> {
			final JFrame mainFrame = new JFrame();
			final DnDActionChooser menu = new DnDActionChooser( null, null );
			setupFrame( mainFrame, menu );
		} );
	}
}
