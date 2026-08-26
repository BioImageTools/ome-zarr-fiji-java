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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTError;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.fijiui.util.ScriptUtils;

/**
 * Asks the user what to do with an OME-Zarr location that is already known:
 * an undecorated, non-modal popup at the mouse pointer offering one icon
 * button per {@link ZarrOpenActions} action (ImageJ and BigDataViewer at the
 * highest resolution, the N5 importer/viewer dialogs, the preset script, and
 * help). It closes on ESC or fades out once the pointer leaves it.
 * <p>
 * It is shown whenever the user configured
 * {@link ome.zarr.fijiui.open.options.ZarrOpenBehavior#SHOW_SELECTION_DIALOG},
 * independently of how the location arrived — drag-and-drop, a
 * {@code fiji://} link, or clipboard paste all reach it through
 * {@link ZarrOpenActions#openWithSettings(java.net.URI, Context)}.
 * <p>
 */
public class ZarrOpenActionChooser
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ZarrOpenActions actions;

	private final Context context;

	private final JButton zarrToIJDialog;

	private final JButton zarrToBDVDialog;

	private final JButton zarrIJHighestResolution;

	private final JButton zarrBDVHighestResolution;

	private final JButton zarrScript;

	private final JButton help;

	private boolean extendedVersion;

	JDialog currentDialog;

	public ZarrOpenActionChooser( final Context context, final ZarrOpenActions actions )
	{
		this.actions = actions;
		this.context = context;

		this.extendedVersion = true;

		ImageIcon zarrIJIcon = CreateIcon.getAndResizeIcon( "zarr_ij_icon.png" );
		zarrToIJDialog = new JButton( zarrIJIcon );
		ImageIcon zarrBDVIcon = CreateIcon.getAndResizeIcon( "zarr_bdv_icon.png" );
		zarrToBDVDialog = new JButton( zarrBDVIcon );
		ImageIcon ijIcon = CreateIcon.getAndResizeIcon( "ij_icon.png" );
		zarrIJHighestResolution = new JButton( ijIcon );
		ImageIcon bdvIcon = CreateIcon.getAndResizeIcon( "bdv_icon.png" );
		zarrBDVHighestResolution = new JButton( bdvIcon );
		ImageIcon scriptIcon = CreateIcon.getAndResizeIcon( "script_icon.png" );
		zarrScript = new JButton( scriptIcon );
		ImageIcon helpIcon = CreateIcon.getAndResizeIcon( "help_icon.png" );
		help = new JButton( helpIcon );
	}

	public void setShowExtendedVersion( boolean show )
	{
		this.extendedVersion = show;
		logger.debug( "Show extended version: {}", show );
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
		final JPanel panel = initLayout();
		initBehaviour( dialog );

		dialog.getContentPane().add( panel );
		dialog.pack();
		positionDialog( dialog, mouseLocation );

		dialog.setVisible( true );
		dialog.requestFocus();
	}

	/** Creates the layout and adds the pre-initialized buttons. */
	private JPanel initLayout()
	{
		JPanel panel;
		if ( extendedVersion )
		{
			panel = new JPanel( new GridLayout( 2, 3, 5, 5 ) );
			panel.add( zarrToIJDialog );
			panel.add( zarrIJHighestResolution );
			panel.add( zarrScript );
			panel.add( zarrToBDVDialog );
			panel.add( zarrBDVHighestResolution );
			panel.add( help );
		}
		else
		{
			panel = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 5 ) );
			panel.add( zarrToIJDialog );
			panel.add( zarrToBDVDialog );
		}
		return panel;
	}

	/** Adds listeners and global behaviour (keyboard, fade, etc.). */
	private void initBehaviour( final JDialog dialog )
	{

		// OME-Zarr to FIJI importer button
		zarrToIJDialog.addActionListener( e -> disposeAndRun( dialog, actions::openImporterDialog ) );
		zarrToIJDialog.setToolTipText( "Open OME-Zarr/N5 Importer dialog" );

		// OME-Zarr to BDV viewer button
		zarrToBDVDialog.addActionListener( e -> disposeAndRun( dialog, actions::openViewerDialog ) );
		zarrToBDVDialog.setToolTipText( "Open OME-Zarr/N5 BDV Viewer dialog" );

		// FIJI button
		zarrIJHighestResolution.addActionListener( e -> disposeAndRun( dialog, actions::openIJWithImage ) );
		zarrIJHighestResolution.setToolTipText( "Open OME-Zarr in ImageJ at highest resolution level" );

		// BDV button
		zarrBDVHighestResolution.addActionListener( e -> disposeAndRun( dialog, actions::openBDVWithImage ) );
		zarrBDVHighestResolution.setToolTipText( "Open OME-Zarr in BDV at highest resolution level" );

		// script button
		String scriptName = ScriptUtils.getTooltipText( context );
		zarrScript.setToolTipText( "Open OME-Zarr in user script:\n\n" + scriptName );
		zarrScript.addActionListener( e -> disposeAndRun( dialog, actions::runScript ) );

		// help button
		help.setToolTipText( "Help about OME-Zarr actions" );
		help.addActionListener( e -> disposeAndRun( dialog, actions::showHelp ) );

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
