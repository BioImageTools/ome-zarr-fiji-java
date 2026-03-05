package sc.fiji.ome.zarr.util;

import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

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
}
