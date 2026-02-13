package sc.fiji.ome.zarr.pyramid;

import net.imagej.ImageJ;

public class MultiscaleImageDemo {
	public static void main( String[] args )
	{
		// final String multiscalePath = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0";
		final String multiscalePath = "/Users/hahmann/Data/idr0079_images.zarr/0"; // https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0

		final MultiscaleImage< ?, ? > multiscaleImage = new MultiscaleImage<>( multiscalePath );
		multiscaleImage.dimensions();

		// Show as imagePlus
		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();
		final DefaultPyramidal5DImageData< ?, ? > dataset = new DefaultPyramidal5DImageData<>( imageJ.context(), "image", multiscaleImage );
		imageJ.ui().show( dataset.asPyramidalDataset() );

		// Also show the displayed image in BDV
		imageJ.command().run( OpenInBDVCommand.class, true );
	}
}
