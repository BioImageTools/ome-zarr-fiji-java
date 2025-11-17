package sc.fiji.ome.zarr.util;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccessibleInterval;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;

@Plugin( type = SciJavaService.class )
public class BdvHandleService extends AbstractService implements SciJavaService
{
	@Parameter
	private LogService logService;

	//TODO: could be a list of recentlyOpenedBdvs
	private BdvStackSource< ? > lastStartedBdv = null;

	// Called when service is initialized
	@Override
	public void initialize()
	{
		logService.info( "BdvHandleService initialized" );
		lastStartedBdv = null;
	}

	/**
	 * @return True if the service is aware of an opened BDV window.
	 */
	public boolean isLastBdvStillAlive()
	{
		//the function intentionally looses the reference
		//on the BDV at the first occasion...

		//is there any BDV registered at all?
		if ( lastStartedBdv == null )
			return false;

		//can a reference to a functional BDV window be retrieved?
		ViewerPanel panel;
		try
		{
			panel = lastStartedBdv.getBdvHandle().getViewerPanel();
		}
		catch ( Exception e )
		{
			lastStartedBdv = null;
			return false;
		}

		//is this "functional" BDV still up on the screen?
		if ( panel.isValid() )
			return true;

		lastStartedBdv = null;
		return false;
	}

	/**
	 * @return A handle on an opened BDV window, if the service is aware of any.
	 *         Else null.
	 */
	public BdvStackSource< ? > returnBdvStackOrNull()
	{
		return isLastBdvStillAlive() ? lastStartedBdv : null;
	}

	/**
	 * Always opens a new BDV, and remembers a handle to it (so that
	 * it can be manipulated later again).
	 */
	public void openNewBdv( RandomAccessibleInterval< ? > img, String name )
	{
		//TODO for more generic sources....
		lastStartedBdv = BdvFunctions.show( img, name );
	}

	/**
	 * Attempts to add another source into the most recently opened BDV.
	 * If the service is not aware of any BDV, it just opens a new one
	 * (and again remembers a handle to it).
	 */
	public void addToLastOrInNewBdv( RandomAccessibleInterval< ? > img, String name )
	{
		//TODO for more generic sources....
		if ( isLastBdvStillAlive() )
		{
			BdvFunctions.show( img, name, BdvOptions.options().addTo( lastStartedBdv ) );
		}
		else
		{
			openNewBdv( img, name );
		}
	}
}
