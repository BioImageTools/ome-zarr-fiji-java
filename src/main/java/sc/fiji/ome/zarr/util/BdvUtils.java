package sc.fiji.ome.zarr.util;

import java.awt.Color;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import net.imglib2.type.numeric.ARGBType;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

public class BdvUtils
{
	private BdvUtils()
	{
		// prevent instantiation of this class
	}

	/**
	 * Displays the given pyramidal dataset in a BigDataViewer (BDV) window.<br>
	 * Increments the dataset's reference count.<br>
	 * Ensures that the dataset's reference count is properly decreased when the BDV window is closed.
	 * <br>
	 * @param pyramidalDataset the input dataset to be displayed in BDV; this dataset
	 *                         contains multi-resolution image data along with associated metadata.
	 * @return a {@code BdvHandle} instance representing the BDV window.
	 */
	public static BdvHandle showBdvAndRegisterDataset( final PyramidalDataset< ? > pyramidalDataset )
	{
		BdvHandle bdvHandle = BdvFunctions.show( pyramidalDataset.asSources(), pyramidalDataset.numTimepoints(),
				BdvOptions.options().frameTitle( pyramidalDataset.getName() ) ).getBdvHandle();

		setChannelProperties( pyramidalDataset, bdvHandle );

		Container topLevelContainer = bdvHandle.getViewerPanel().getRootPane().getParent();
		if ( topLevelContainer instanceof Window )
		{
			// notify scijava about "usage" (and "no longer usage" later) of this Dataset
			// only if we're able to listen for when Bdv window closes
			pyramidalDataset.incrementReferences();
			( ( Window ) topLevelContainer ).addWindowListener( new WindowAdapter()
			{
				@Override
				public void windowClosed( WindowEvent e )
				{
					pyramidalDataset.decrementReferences();
				}
			} );
		}
		return bdvHandle;
	}

	private static void setChannelProperties( final PyramidalDataset< ? > pyramidalDataset, final BdvHandle bdvHandle )
	{
		Omero omero = pyramidalDataset.getOmeroProperties();
		for ( int channelNumber = 0; channelNumber < pyramidalDataset.asSources().size(); channelNumber++ )
		{
			SourceAndConverter< ? > source = pyramidalDataset.asSources().get( channelNumber );
			final Omero.Channel omeroChannel = omero == null || omero.channels == null ? null : omero.channels.get( channelNumber );
			ConverterSetup converterSetup = bdvHandle.getConverterSetups().getConverterSetup( source );
			Color color = omeroChannel == null || omeroChannel.color == null ? Color.black : Color.decode( "#" + omeroChannel.color );
			int opaque = 255;
			converterSetup.setColor( new ARGBType( ARGBType.rgba( color.getRed(), color.getGreen(), color.getBlue(), opaque ) ) );
			if ( omeroChannel != null && omeroChannel.window != null )
				converterSetup.setDisplayRange( omeroChannel.window.start, omeroChannel.window.end );
			bdvHandle.getViewerPanel().state().setSourceActive( source, omeroChannel == null || omeroChannel.active );
		}
	}
}
