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
package ome.zarr.fiji.util;

import java.awt.Dimension;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

import ome.zarr.imglib2.PyramidContents;

/**
 * Lets the user tick which of several open OME-Zarrs to show in one viewer window,
 * as BigDataViewer's "Open Current Image" does for ImageJ images.
 */
public class OmeZarrImagesChooser
{
	private OmeZarrImagesChooser()
	{
		// static utility
	}

	/**
	 * Shows a modal dialog with one checkbox per OME-Zarr in {@code candidates}, labeled by its name.
	 *
	 * @param candidates the OME-Zarrs to choose from
	 * @param preselected the one to be ticked when the dialog opens, typically the active pyramid, or {@code null} for none
	 * @return the ticked OME-Zarrs in the order of {@code candidates}, or an empty list if the user cancels
	 */
	public static List< PyramidContents< ? > > choose( final List< PyramidContents< ? > > candidates, final PyramidContents< ? > preselected )
	{
		final List< JCheckBox > checkboxes = candidates.stream()
				.map( contents -> new JCheckBox( contents.name(), contents == preselected ) )
				.collect( Collectors.toList() );
		final JOptionPane pane = new JOptionPane( checkboxes.toArray(), JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION );
		final JDialog dialog = pane.createDialog( "OME-Zarr Images to open" );
		dialog.setMinimumSize( new Dimension( 280, 0 ) );
		dialog.setVisible( true );
		dialog.dispose();
		if ( !Integer.valueOf( JOptionPane.OK_OPTION ).equals( pane.getValue() ) )
			return Collections.emptyList();
		return IntStream.range( 0, candidates.size() )
				.filter( i -> checkboxes.get( i ).isSelected() )
				.mapToObj( candidates::get )
				.collect( Collectors.toList() );
	}
}
