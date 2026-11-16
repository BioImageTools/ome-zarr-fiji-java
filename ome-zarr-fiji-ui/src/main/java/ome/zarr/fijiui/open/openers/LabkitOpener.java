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
package ome.zarr.fijiui.open.openers;

import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.bdv.OmeroDisplay;
import ome.zarr.fiji.bdv.PyramidalBdv;
import ome.zarr.fiji.open.OmeZarrOpener;
import ome.zarr.fiji.plugins.ActiveViewerService;
import ome.zarr.fiji.read.OmeZarr;
import ome.zarr.fiji.util.BdvUtils;
import ome.zarr.fijiui.util.PyramidTexts;
import ome.zarr.imglib2.PyramidContents;

import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.scijava.Context;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

import sc.fiji.labkit.ui.LabkitFrame;
import sc.fiji.labkit.ui.bdv.BdvShowable;
import sc.fiji.labkit.ui.inputimage.DatasetInputImage;

/**
 * Opens the image in Labkit. Labkit segments the resolution level the user picks,
 * but displays the whole pyramid within its BigDataViewer.
 * <p>
 * The image inside Labkit is the active pyramidal while labkit has focus.
 */
@Plugin( type = OmeZarrOpener.class, name = LabkitOpener.NAME, label = "Labkit", description = "Open in Labkit, asking for the resolution level to segment but displaying all levels in BigDataViewer", iconPath = "/ome/zarr/fijiui/dialog/labkit_icon.png", priority = Priority.NORMAL
		- 2 )
public class LabkitOpener implements OmeZarrOpener
{
	/** The stable identifier this opener is persisted under. */
	public static final String NAME = "labkit";

	@Override
	public void open( final OmeZarr omeZarr )
	{
		final PyramidalDataset suggested = omeZarr.readPyramidalDataset();
		if ( suggested == null )
			return;
		final PyramidContents< ? > contents = suggested.getPyramidContents();
		final Integer level = askResolutionLevel( contents, suggested.resolutionLevel() );
		if ( level == null )
			return;
		final Context context = omeZarr.context();
		final PyramidalDataset dataset = new PyramidalDataset( context, contents, level );
		final PyramidalBdvShowable< ? > showable = new PyramidalBdvShowable<>( new PyramidalBdv<>( context, contents ) );
		LabkitFrame.showForImage( context, new DatasetInputImage( dataset.getImgPlus(), showable ) );
		showable.registerViewer( context );
	}

	/**
	 * Asks which resolution level to segment, as Labkit itself does for BigDataViewer
	 * XML/HDF5 files. Preselects {@code preselected}, the level the preferred width
	 * selects. A single-level image is not asked about.
	 *
	 * @return the 0-based level, or {@code null} if the user cancelled
	 */
	private static Integer askResolutionLevel( final PyramidContents< ? > contents, final int preselected )
	{
		if ( contents.numResolutionLevels() == 1 )
			return 0;
		final String[] choices = new String[ contents.numResolutionLevels() ];
		Arrays.setAll( choices, level -> PyramidTexts.describeSegmentationLevel( contents, level ) );
		final Object[] message = { "Select Resolution for Segmentation", PyramidTexts.describeImage( contents ) };
		final Object choice = JOptionPane.showInputDialog( null, message, "Labkit",
				JOptionPane.PLAIN_MESSAGE, null, choices, choices[ preselected ] );
		return choice == null ? null : Arrays.asList( choices ).indexOf( choice );
	}

	/**
	 * Shows all channels of a {@link PyramidalBdv} as one {@link BdvStackSource},
	 * set up from the OMERO metadata.
	 * <p>
	 * Interval and transformation are those of the full resolution level. Labkit
	 * scales the labeling, which has the size of the segmented level, onto them.
	 */
	private static class PyramidalBdvShowable< T extends NativeType< T > & RealType< T > > implements BdvShowable
	{
		private final PyramidalBdv< T > pyramidal;

		/** The BigDataViewer Labkit showed the pyramid in. */
		private BdvHandle handle;

		PyramidalBdvShowable( final PyramidalBdv< T > pyramidal )
		{
			this.pyramidal = pyramidal;
		}

		private Source< T > firstChannel()
		{
			return pyramidal.asSources().get( 0 ).getSpimSource();
		}

		@Override
		public Interval interval()
		{
			return new FinalInterval( firstChannel().getSource( 0, 0 ) );
		}

		@Override
		public AffineTransform3D transformation()
		{
			final AffineTransform3D transformation = new AffineTransform3D();
			firstChannel().getSourceTransform( 0, 0, transformation );
			return transformation;
		}

		@Override
		public BdvStackSource< ? > show( final String title, final BdvOptions options )
		{
			final List< SourceAndConverter< T > > sources = pyramidal.asSources();
			final BdvStackSource< T > stackSource = BdvFunctions.show( sources, pyramidal.getPyramidContents().numTimepoints(), options );
			handle = stackSource.getBdvHandle();
			OmeroDisplay.apply( OmeroDisplay.channels( pyramidal.getPyramidContents().omero(), sources.size() ), sources,
					handle.getConverterSetups(), handle.getViewerPanel().state() );
			return stackSource;
		}

		/**
		 * Registers the viewer the pyramid was shown in, as {@link BdvUtils#show} does,
		 * and makes it the active viewer. Needs the viewer to be in its window
		 * already, i.e. to be called after the Labkit frame is shown.
		 */
		void registerViewer( final Context context )
		{
			final ViewerPanel viewer = handle.getViewerPanel();
			pyramidal.registerViewer( viewer );
			context.getService( ActiveViewerService.class ).setActiveViewer( viewer );
		}
	}
}
