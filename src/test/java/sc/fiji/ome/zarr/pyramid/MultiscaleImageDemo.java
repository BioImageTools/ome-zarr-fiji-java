package sc.fiji.ome.zarr.pyramid;

import net.imagej.ImageJ;

public class MultiscaleImageDemo {
	public static void main( String[] args )
	{
		// final String multiscalePath = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0";
		final String multiscalePath = "/Users/hahmann/Data/idr0079_images.zarr/0"; // https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0

		final MultiscaleImage< ?, ? > multiscaleImage = new MultiscaleImage<>( multiscalePath );

		// Show as imagePlus
		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();
		final DefaultPyramidal5DImageData< ?, ? > pyramidal5DImageData =
				new DefaultPyramidal5DImageData<>( imageJ.context(), "image", multiscaleImage );
		PyramidalDataset< ? > pyramidalDataset = pyramidal5DImageData.asPyramidalDataset();
		imageJ.ui().show( pyramidalDataset );

		// Also show the displayed image in BDV
		imageJ.command().run( OpenInBDVCommand.class, true, dataset );
	}
}
