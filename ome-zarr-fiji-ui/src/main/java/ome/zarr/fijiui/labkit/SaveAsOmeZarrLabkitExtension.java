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
package ome.zarr.fijiui.labkit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Plugin;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.write.PyramidContentsUtils;
import ome.zarr.n5.N5PyramidSaver;
import sc.fiji.labkit.ui.Extensible;
import sc.fiji.labkit.ui.LabkitExtension;
import sc.fiji.labkit.ui.models.ImageLabelingModel;
import sc.fiji.labkit.ui.models.SegmentationItem;
import sc.fiji.labkit.ui.models.SegmentationResultsModel;

/**
 * Adds "Save as OME-Zarr" to Labkit: an entry in the Segmentation menu, next to
 * "Save Segmentation Result as TIF / HDF5", and a toolbar button.
 * <p>
 * Only an image opened from OME-Zarr into Labkit can be saved: dimensions, axes and
 * transform come from the {@link PyramidContents} shown in Labkit's viewer, at the
 * resolution level that was segmented, without the channel axis. The result is a
 * single-level OME-Zarr in a folder the user picks.
 * <p>
 * The toolbar button still only says it is not implemented: it gets no
 * {@link SegmentationItem}, and {@link LabkitExtension} offers no way to the selected one.
 */
@Plugin( type = LabkitExtension.class )
public class SaveAsOmeZarrLabkitExtension implements LabkitExtension
{
	/** Menu entry without the trailing " ...", toolbar tooltip and dialog title. */
	private static final String TITLE = "Save Segmentation Result as OME-Zarr";

	/** Sorts right after Labkit's "Save … as TIF / HDF5 …" items (200), in the same menu group. */
	private static final float MENU_PRIORITY = 200.5f;

	/** Height of the toolbar icon, close to Labkit's 33 px tool icons. */
	private static final int ICON_HEIGHT = 30;

	/** At most twice as wide as high. */
	private static final int ICON_WIDTH = 2 * ICON_HEIGHT;

	private static final int LOGO_HEIGHT = 22;

	@Override
	public void extend( final Extensible extensible, final ImageLabelingModel model, final BdvHandle bdvHandle )
	{
		final Runnable notImplemented = () -> JOptionPane.showMessageDialog( extensible.dialogParent(),
				"Saving the segmentation result as OME-Zarr is not implemented yet.", TITLE, JOptionPane.INFORMATION_MESSAGE );
		extensible.addMenuItem( SegmentationItem.SEGMENTER_MENU, TITLE + " ...", MENU_PRIORITY,
				item -> save( extensible, model, bdvHandle, item ), null, "" );
		extensible.addToolBarButton( TITLE, labkitToOmeZarrIcon(), notImplemented );
	}

	private static void save( final Extensible extensible, final ImageLabelingModel model, final BdvHandle bdvHandle,
			final SegmentationItem item )
	{
		final SegmentationResultsModel results = item.results( model );
		if ( !results.hasResults() )
		{
			message( extensible, "Train the classifier first.", JOptionPane.INFORMATION_MESSAGE );
			return;
		}
		final PyramidContents< ? > source = pyramidContentsShownIn( extensible.context(), bdvHandle );
		final int level = source == null ? -1 : segmentedLevel( source, model.imageForSegmentation().get() );
		if ( level < 0 )
		{
			message( extensible, "Only an image opened from OME-Zarr into Labkit can be saved as OME-Zarr.",
					JOptionPane.INFORMATION_MESSAGE );
			return;
		}

		final JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle( TITLE );
		chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
		chooser.setSelectedFile( new File( ( source.name() == null ? "" : source.name() ) + "_segmentation.ome.zarr" ) );
		if ( chooser.showSaveDialog( extensible.dialogParent() ) != JFileChooser.APPROVE_OPTION )
			return;
		final File folder = chooser.getSelectedFile();

		final RandomAccessibleInterval< UnsignedByteType > segmentation = results.segmentation();
		// ponytail: single-threaded, no progress bar. Labkit's HDF5 export shows a SwingProgressWriter if users need one.
		new Thread( () -> {
			try
			{
				final N5PyramidSaver< UnsignedByteType > saver = new N5PyramidSaver<>( folder.toURI() );
				saver.createMultiscales( "", segmentationContents( source, level ) );
				saver.writeRegion( segmentation, 0 );
				message( extensible, "Saved the segmentation to\n" + folder, JOptionPane.INFORMATION_MESSAGE );
			}
			catch ( final RuntimeException e )
			{
				extensible.context().service( LogService.class ).error( "Saving the segmentation to " + folder + " failed", e );
				message( extensible, "Saving the segmentation failed:\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE );
			}
		}, TITLE ).start();
	}

	/**
	 * The contents of the {@link PyramidalBdv} whose sources {@code bdvHandle} shows,
	 * i.e. the one {@code LabkitOpener} handed to Labkit, or {@code null} if Labkit
	 * was opened some other way.
	 */
	private static PyramidContents< ? > pyramidContentsShownIn( final Context context, final BdvHandle bdvHandle )
	{
		final List< SourceAndConverter< ? > > shown = bdvHandle.getViewerPanel().state().getSources();
		for ( final PyramidalBdv< ? > pyramidal : context.service( ObjectService.class ).getObjects( PyramidalBdv.class ) )
			for ( final SourceAndConverter< ? > source : pyramidal.asSources() )
				for ( final SourceAndConverter< ? > s : shown )
					if ( s.getSpimSource() == source.getSpimSource() )
						return pyramidal.getPyramidContents();
		return null;
	}

	/** The level Labkit segments, i.e. the one whose size {@code image} has, or -1. */
	private static int segmentedLevel( final PyramidContents< ? > contents, final Interval image )
	{
		for ( int level = 0; level < contents.numResolutionLevels(); level++ )
			if ( Intervals.equalDimensions( contents.asImg( level ), image ) )
				return level;
		return -1;
	}

	/**
	 * Level {@code level} of {@code source} as a single-level pyramid without the
	 * channel axis, in the axis order of Labkit's segmentation.
	 */
	private static PyramidContents< UnsignedByteType > segmentationContents( final PyramidContents< ? > source,
			final int level )
	{
		final List< AxisCalibration > sourceAxes = source.axes( level );
		final long[] sourceDims = source.asImg( level ).dimensionsAsLongArray();
		final List< AxisCalibration > axes = sourceAxes.stream()
				.filter( a -> !a.name.equals( AxisCalibration.C ) )
				.collect( Collectors.toList() );
		final long[] xyz = Stream.of( AxisCalibration.X, AxisCalibration.Y, AxisCalibration.Z )
				.mapToInt( name -> indexOf( sourceAxes, name ) )
				.filter( i -> i >= 0 )
				.mapToLong( i -> sourceDims[ i ] )
				.toArray();
		final int t = indexOf( sourceAxes, AxisCalibration.T );
		return PyramidContentsUtils.create( source.name(), new UnsignedByteType(), xyz, 0, t < 0 ? 0 : sourceDims[ t ],
				new AxisCalibration[][] { axes.toArray( new AxisCalibration[ 0 ] ) }, new double[ 0 ][], source.transform( level ), null );
	}

	private static int indexOf( final List< AxisCalibration > axes, final String name )
	{
		for ( int i = 0; i < axes.size(); i++ )
			if ( axes.get( i ).name.equals( name ) )
				return i;
		return -1;
	}

	private static void message( final Extensible extensible, final String text, final int type )
	{
		SwingUtilities.invokeLater( () -> JOptionPane.showMessageDialog( extensible.dialogParent(), text, TITLE, type ) );
	}

	/**
	 * The Labkit logo, an arrow, and the OME-Zarr logo. {@code ome_zarr_icon.png} puts
	 * the Zarr cube of {@code dialog/unused/zarr_icon.png} into the ring of the OME
	 * logomark (openmicroscopy.org/img/logos/ome-logomark.svg).
	 */
	private static Icon labkitToOmeZarrIcon()
	{
		final Image labkit = logo( "/ome/zarr/fijiui/dialog/labkit_icon.png" );
		final Image omeZarr = logo( "/ome/zarr/fijiui/labkit/ome_zarr_icon.png" );
		final int y = ( ICON_HEIGHT - LOGO_HEIGHT ) / 2;
		final int arrowStart = labkit.getWidth( null ) + 2;
		final int omeZarrX = ICON_WIDTH - omeZarr.getWidth( null );
		final int arrowEnd = omeZarrX - 2;
		final int arrowY = ICON_HEIGHT / 2;
		final BufferedImage image = new BufferedImage( ICON_WIDTH, ICON_HEIGHT, BufferedImage.TYPE_INT_ARGB );
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g.drawImage( labkit, 0, y, null );
		g.drawImage( omeZarr, omeZarrX, y, null );
		g.setColor( Color.DARK_GRAY );
		g.setStroke( new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
		g.drawLine( arrowStart, arrowY, arrowEnd, arrowY );
		g.drawLine( arrowEnd - 4, arrowY - 4, arrowEnd, arrowY );
		g.drawLine( arrowEnd - 4, arrowY + 4, arrowEnd, arrowY );
		g.dispose();
		return new ImageIcon( image );
	}

	/**
	 * The logo at {@code path}, scaled to a height of {@link #LOGO_HEIGHT}. {@code getScaledInstance}
	 * scales lazily, and drawing it before it is done draws nothing: wrapping it in an
	 * {@link ImageIcon} waits for it.
	 */
	// ponytail: 1x bitmap, blurry on HiDPI screens. Render a multi-resolution image once saving is implemented.
	private static Image logo( final String path )
	{
		final Image original = new ImageIcon( SaveAsOmeZarrLabkitExtension.class.getResource( path ) ).getImage();
		return new ImageIcon( original.getScaledInstance( -1, LOGO_HEIGHT, Image.SCALE_SMOOTH ) ).getImage();
	}
}
