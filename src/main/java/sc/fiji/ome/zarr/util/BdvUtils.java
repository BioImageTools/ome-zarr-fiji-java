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
					System.out.println( "Closing BDV Window..." );
					pyramidalDataset.decrementReferences();
					System.out.println( "Decremented references of pyramidal dataset" );
				}
			} );
		}
		return bdvHandle;
	}
}
