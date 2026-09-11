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

import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.plugin.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTError;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;
import java.net.URL;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import ome.zarr.fiji.open.ZarrOpenRequest;
import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fiji.open.ZarrOpenerService;
import ome.zarr.fijiui.open.ZarrOpenActions;

/**
 * Asks the user what to do with an OME-Zarr location that is already known:
 * an undecorated, non-modal popup at the mouse pointer offering one icon
 * button per registered {@link ZarrOpener}, plus help. It closes on ESC or fades
 * out once the pointer leaves it.
 * <p>
 * The buttons are built from the {@link ZarrOpenerService}, so a
 * {@link ZarrOpener} contributed by another Fiji plugin appears here next to the
 * built-in ImageJ and BigDataViewer ones, with its own icon and description.
 * <p>
 * It is shown whenever the user configured {@link ZarrOpenerService#ASK},
 * independently of how the location arrived — drag-and-drop, a
 * {@code fiji://} link, or clipboard paste all reach it through
 * {@link ZarrOpenActions#openWithSettings(java.net.URI, Context)}.
 */
public class ZarrOpenActionChooser
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/** Buttons per row; the number of rows follows the number of openers. */
	private static final int COLUMNS = 3;

	private final ZarrOpenRequest request;

	private final Context context;

	JDialog currentDialog;

	/**
	 * A chooser for {@code request}, offering the openers registered in
	 * {@code context}.
	 *
	 * @param context the SciJava context the openers are looked up in
	 * @param request the location the chosen opener will be given
	 */
	public ZarrOpenActionChooser( final Context context, final ZarrOpenRequest request )
	{
		this.request = request;
		this.context = context;
	}

	public void showDialog()
	{
		if ( SwingUtilities.isEventDispatchThread() )
		{
			doShow();
		}
		else
		{
			SwingUtilities.invokeLater( this::doShow );
		}
	}

	public void dispose()
	{
		if ( SwingUtilities.isEventDispatchThread() )
			doDispose();
		else
			SwingUtilities.invokeLater( this::doDispose );
	}

	private void doDispose()
	{
		if ( currentDialog != null )
		{
			currentDialog.dispose();
			currentDialog = null;
		}
	}

	private void doShow()
	{
		final Point mouseLocation = getMouseLocation();
		if ( mouseLocation == null )
			return;

		final JDialog dialog = createDialog();
		currentDialog = dialog;
		final JPanel panel = initLayout( dialog );
		initBehaviour( dialog );

		dialog.getContentPane().add( panel );
		dialog.pack();
		positionDialog( dialog, mouseLocation );

		dialog.setVisible( true );
		dialog.requestFocus();
	}

	/** Creates the layout with one button per offered opener, and the help button. */
	private JPanel initLayout( final JDialog dialog )
	{
		final JPanel panel = new JPanel( new GridLayout( 0, COLUMNS, 5, 5 ) );
		final ZarrOpenerService openerService = openerService();
		if ( openerService == null )
			// A context without the service is a broken classpath; help still works.
			logger.warn( "No ZarrOpenerService in the SciJava context, so no opener can be offered." );
		else
			for ( final PluginInfo< ZarrOpener > info : openerService.getOpenerInfos() )
				panel.add( openerButton( dialog, openerService, info ) );
		panel.add( helpButton( dialog ) );
		return panel;
	}

	/** The service the openers come from, or {@code null} without a context. */
	private ZarrOpenerService openerService()
	{
		return context == null ? null : context.getService( ZarrOpenerService.class );
	}

	private JButton openerButton( final JDialog dialog, final ZarrOpenerService openerService,
			final PluginInfo< ZarrOpener > info )
	{
		final JButton button = new JButton( CreateIcon.getAndResizeIcon( iconUrl( info ) ) );
		button.setToolTipText( openerService.tooltipOf( info, request ) );
		button.addActionListener( e -> disposeAndRun( dialog, () -> openerService.open( info, request ) ) );
		return button;
	}

	private JButton helpButton( final JDialog dialog )
	{
		final JButton button = new JButton( CreateIcon.getAndResizeIcon( "help_icon.png" ) );
		button.setToolTipText( "Help about OME-Zarr actions" );
		button.addActionListener( e -> disposeAndRun( dialog, new ZarrOpenActions( request )::showHelp ) );
		return button;
	}

	/** The opener's own icon, or {@code null} when it declares none or it is unreadable. */
	private URL iconUrl( final PluginInfo< ZarrOpener > info )
	{
		try
		{
			return info.getIconURL();
		}
		catch ( final InstantiableException e )
		{
			logger.debug( "Could not resolve the icon of the OME-Zarr opener {}", info.getClassName(), e );
			return null;
		}
	}

	/** Adds global behaviour (keyboard, fade, etc.). */
	private void initBehaviour( final JDialog dialog )
	{
		setupCloseOnKeyboard( dialog );
		setupCloseOnMouseLeave( dialog );
	}

	private void disposeAndRun( final JDialog dialog, final Runnable action )
	{
		dialog.dispose();
		new Thread( action ).start();
	}

	private Point getMouseLocation()
	{
		try
		{
			return MouseInfo.getPointerInfo().getLocation();
		}
		catch ( AWTError e )
		{
			logger.warn( "Cannot get mouse pointer info", e );
			return null;
		}
	}

	private JDialog createDialog()
	{
		JDialog dialog = new JDialog();
		dialog.setUndecorated( true );
		dialog.setModal( false );
		dialog.setAlwaysOnTop( true );
		dialog.setOpacity( 1.0f );
		return dialog;
	}

	private void positionDialog( JDialog dialog, Point mouseLocation )
	{
		Dimension size = dialog.getSize();
		int x = mouseLocation.x - size.width / 2;
		int y = mouseLocation.y - size.height / 2;
		dialog.setLocation( x, y );
	}

	private void setupCloseOnKeyboard( final JDialog dialog )
	{
		dialog.getRootPane().registerKeyboardAction(
				e -> dialog.dispose(),
				KeyStroke.getKeyStroke( "ESCAPE" ),
				JComponent.WHEN_IN_FOCUSED_WINDOW
		);
	}

	private void setupCloseOnMouseLeave( final JDialog dialog )
	{
		final Timer checkMouse = new Timer( 200, e -> {
			PointerInfo pi = MouseInfo.getPointerInfo();
			if ( pi == null )
				return;
			Point p = pi.getLocation();
			if ( !dialog.getBounds().contains( p ) )
			{
				( ( Timer ) e.getSource() ).stop();
				startFadeOut( dialog );
			}
		} );
		checkMouse.start();

		dialog.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosed( WindowEvent e )
			{
				checkMouse.stop();
			}
		} );
	}

	private void startFadeOut( final JDialog dialog )
	{
		final float[] opacity = new float[] { 1.0f };
		final int steps = 10;
		final int duration = 300;
		final int interval = duration / steps;

		final Timer fade = new Timer( interval, e -> {
			opacity[ 0 ] -= 1.0f / steps;
			if ( opacity[ 0 ] <= 0f )
			{
				( ( Timer ) e.getSource() ).stop();
				dialog.dispose();
			}
			else
			{
				try
				{
					dialog.setOpacity( opacity[ 0 ] );
				}
				catch ( UnsupportedOperationException ex )
				{
					dialog.dispose();
					( ( Timer ) e.getSource() ).stop();
				}
			}
		} );
		fade.start();
	}
}
