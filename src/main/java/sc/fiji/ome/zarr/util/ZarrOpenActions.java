package sc.fiji.ome.zarr.util;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Cast;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;

import bdv.util.BdvFunctions;
import ij.IJ;

public class ZarrOpenActions
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final Path droppedInPath;

	private final Context context;

	public ZarrOpenActions( final Path droppedInPath, final Context context )
	{
		this.droppedInPath = droppedInPath;
		this.context = context;
	}

	/**
	 * Opens the N5 Importer dialog pointed at the dropped-in path.<br>
	 * Shortcut to File > Import > HDF5/N5/Zarr/OME-NGFF
	 */
	public void openImporterDialog()
	{
		new N5Importer().runWithDialog( droppedInPath.toString() );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 importer dialog with path: {}.", droppedInPath );
	}

	/**
	 * Opens the N5 Viewer (aka BigDataViewer) dialog pointed at the dropped-in path.<br>
	 * Shortcut to Plugins > BigDataViewer > HDF5/N5/Zarr/OME-NGFF Viewer
	 */
	public void openViewerDialog()
	{
		new N5ViewerCreator().runWithDialog( droppedInPath.toString(),
				e -> logger.warn( "Could not open viewer selection dialog: {}", e.getMessage() ) );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 viewer with path: {}.", droppedInPath );
	}

	public void openIJWithImage()
	{
		openImage( img -> ImageJFunctions.show( Cast.unchecked( img ) ) );
	}

	public void openBDVWithImage()
	{
		openImage( img -> BdvFunctions.show( img, droppedInPath.toString() ) );
	}

	void openImage( final Consumer< Img< ? > > imageOpener )
	{
		N5Reader reader = new N5Factory().openReader( ZarrOnFileSystemUtils.getUriFromPath( droppedInPath ).toString() );
		Img< ? > img;
		try
		{
			img = N5Utils.open( reader, "" );
		}
		catch ( Exception e )
		{
			IJ.error( "Could not open dataset as image: " + droppedInPath + "\n\n"
					+ "Consider opening one level higher or lower in the hierarchy instead." );
			logger.warn( "Could not open dataset as image: {}. Error message: {}", droppedInPath, e.getMessage() );
			return;
		}
		imageOpener.accept( img );
		logger.info( "Opened dataset: {}", droppedInPath );
	}

	public void runScript()
	{
		final String root = ZarrOnFileSystemUtils.getUriFromPath( droppedInPath ).toString();
		ScriptUtils.executePresetScript( context, root );
		logger.info( "Executed script with Zarr root {}", root );
	}

	public void showHelp()
	{
		try
		{
			Desktop.getDesktop().browse( new URI( HELP_URL ) );
		}
		catch ( Exception ex )
		{
			logger.warn( "Cannot open help link: {}", ex.getMessage() );
		}
	}
}
