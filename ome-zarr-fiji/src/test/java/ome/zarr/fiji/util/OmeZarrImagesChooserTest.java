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
package ome.zarr.fiji.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import ome.zarr.ZarrTestUtils;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.n5.N5PyramidBackend;

class OmeZarrImagesChooserTest
{
	private static final String SPOTS = "ome/zarr/testdata/bdv_testing/logo_spots.ome.zarr";

	private static final String LABELS = "ome/zarr/testdata/bdv_testing/logo_labels.ome.zarr";

	/**
	 * Opens the real dialog and, once it is shown, notes which boxes are ticked, then unticks the
	 * preselected spots, ticks the labels and presses OK. Asserting happens back on the test thread.
	 */
	@Test
	void chooseGivesTheTickedOmeZarrs() throws Exception
	{
		PyramidContents< ? > spots = N5PyramidBackend.readPyramid( ZarrTestUtils.resourcePath( SPOTS ).toUri() );
		PyramidContents< ? > labels = N5PyramidBackend.readPyramid( ZarrTestUtils.resourcePath( LABELS ).toUri() );

		List< Boolean > ticksWhenShown = new ArrayList<>();
		AWTEventListener user = event -> {
			if ( event.getID() != WindowEvent.WINDOW_OPENED || !( event.getSource() instanceof JDialog ) )
				return;
			JOptionPane pane = ( JOptionPane ) ( ( JDialog ) event.getSource() ).getContentPane().getComponent( 0 );
			JCheckBox spotsBox = ( JCheckBox ) ( ( Object[] ) pane.getMessage() )[ 0 ];
			JCheckBox labelsBox = ( JCheckBox ) ( ( Object[] ) pane.getMessage() )[ 1 ];
			ticksWhenShown.addAll( Arrays.asList( spotsBox.isSelected(), labelsBox.isSelected() ) );
			spotsBox.setSelected( false );
			labelsBox.setSelected( true );
			SwingUtilities.invokeLater( () -> pane.setValue( JOptionPane.OK_OPTION ) ); // press OK
		};
		Toolkit.getDefaultToolkit().addAWTEventListener( user, AWTEvent.WINDOW_EVENT_MASK );
		try
		{
			List< PyramidContents< ? > > chosen = OmeZarrImagesChooser.choose( Arrays.asList( spots, labels ), spots );
			assertEquals( Arrays.asList( true, false ), ticksWhenShown, "the spots are preselected" );
			assertEquals( Collections.singletonList( labels ), chosen ); // the labels are chosen
		}
		finally
		{
			Toolkit.getDefaultToolkit().removeAWTEventListener( user );
		}
	}
}
