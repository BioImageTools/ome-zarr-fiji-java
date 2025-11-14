package sc.fiji.ome.zarr.ui.util;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccessibleInterval;

public class BdvSession {
	private BdvStackSource<?> lastStartedBdv = null;

	boolean isBdvAlive() {
		if (lastStartedBdv == null) return false;

		final ViewerPanel panel = lastStartedBdv.getBdvHandle().getViewerPanel(); //TODO: per-partes & test non-nullabity, or exceptions handling..
/*
		//testing flags:
		System.out.println("A "+panel.isVisible());
		System.out.println("B "+panel.isValid());
		System.out.println("C "+panel.isEnabled());
		System.out.println("D "+panel.isShowing());
*/
		boolean isAlive = panel.isValid();
		if (!isAlive) lastStartedBdv = null; //loose reference on the BDV at the first occasion
		return isAlive;
	}


	//TODO for more generic sources....
	public void openNewBdv(RandomAccessibleInterval<?> img, String name) {
		lastStartedBdv = BdvFunctions.show(img, name);
	}

	//TODO for more generic sources....
	//TODO not sure the dual behaviour is wanted...
	public void addToLastOrInNewBdv(RandomAccessibleInterval<?> img, String name) {
		if ( isBdvAlive() ) {
			BdvFunctions.show(img, name, BdvOptions.options().addTo(lastStartedBdv));
		} else {
			openNewBdv(img, name);
		}
	}
}
