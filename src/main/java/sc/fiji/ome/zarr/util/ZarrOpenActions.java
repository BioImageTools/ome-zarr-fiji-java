package sc.fiji.ome.zarr.util;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.Context;
import org.scijava.ui.UIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Cast;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import ij.IJ;
import sc.fiji.ome.zarr.pyramid.DefaultPyramidal5DImageData;
import sc.fiji.ome.zarr.pyramid.MultiscaleImage;
import sc.fiji.ome.zarr.pyramid.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.NotASingleScaleImageException;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

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
	 * Shortcut to File &gt; Import &gt; HDF5/N5/Zarr/OME-NGFF
	 */
	public void openImporterDialog()
	{
		new N5Importer().runWithDialog( droppedInPath.toString(), Collections.emptyList() );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 importer dialog with path: {}.", droppedInPath );
	}

	/**
	 * Opens the N5 Viewer (aka BigDataViewer) dialog pointed at the dropped-in path.<br>
	 * Shortcut to Plugins &gt; BigDataViewer &gt; HDF5/N5/Zarr/OME-NGFF Viewer
	 */
	public void openViewerDialog()
	{
		/*
		// NB this can be used after n5-viewer_fiji 6.1.3 has been released
		new N5ViewerCreator().runWithDialog( droppedInPath.toString(),
				e -> logger.warn( "Could not open viewer selection dialog: {}", e.getMessage() ) );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 viewer with path: {}.", droppedInPath );
		 */
	}

	public void openIJWithImage()
	{
		try
		{
			openImage(
					pyramidalDataset -> context.getService( UIService.class ).show( pyramidalDataset ),
					singleScaleImage -> ImageJFunctions.show( Cast.unchecked( singleScaleImage ) ), "ImageJ"
			);
		}
		catch ( NotASingleScaleImageException e )
		{
			showError( e );
		}
	}

	public void openBDVWithImage()
	{
		try
		{
			openImage( pyramidalDataset -> BdvFunctions.show(
					pyramidalDataset.asSources(), pyramidalDataset.numTimepoints(),
					BdvOptions.options().frameTitle( pyramidalDataset.getName() )
			), singleScaleImage -> BdvFunctions.show( singleScaleImage, "Image" ), "BigDataViewer" );
		}
		catch ( NotASingleScaleImageException e )
		{
			showError( e );
		}
	}

	private void showError( final Exception e )
	{
		IJ.error( "Could not open dataset as image: " + droppedInPath + "\n\n"
				+ "Consider opening one level higher or lower in the hierarchy instead." );
		logger.warn( "Could not open dataset as single scale image: {}. Error message: {}", droppedInPath, e.getMessage() );
	}

	void openImage( final Consumer< PyramidalDataset< ? > > multiScaleImageOpener, final Consumer< Img< ? > > singleScaleImageOpener,
			final String message ) throws NotASingleScaleImageException
	{
		try
		{
			openMultiScaleImage( multiScaleImageOpener );
			logger.info( "Opened dataset in {}: {}", message, droppedInPath );
		}
		catch ( NotAMultiscaleImageException e )
		{
			logger.warn( "Not a multiscale image: {}", e.getMessage() );
			logger.info( "Try opening as single-scale image instead." );
			openSingleScaleImage( singleScaleImageOpener );
		}
	}

	private void openMultiScaleImage( final Consumer< PyramidalDataset< ? > > multiScaleImageOpener ) throws NotAMultiscaleImageException
	{

		// final MultiscaleImage< ?, ? > multiscaleImage = new MultiscaleImage<>( droppedInPath.toString() );
		final DefaultPyramidal5DImageData< ?, ? > pyramidal5DImageData =
				new DefaultPyramidal5DImageData<>( context, droppedInPath.toString() );
		PyramidalDataset< ? > pyramidalDataset = pyramidal5DImageData.asPyramidalDataset();
		multiScaleImageOpener.accept( pyramidalDataset );
		logger.info( "Opened multiscale image: {}", droppedInPath );
	}

	private void openSingleScaleImage( final Consumer< Img< ? > > singleScaleImageOpener ) throws NotASingleScaleImageException
	{
		N5Reader reader = new N5Factory().openReader( ZarrOnFileSystemUtils.getUriFromPath( droppedInPath ).toString() );
		Img< ? > img;
		try
		{
			img = N5Utils.open( reader, "" );
		}
		catch ( Exception e )
		{
			throw new NotASingleScaleImageException( droppedInPath.toString(), e );
		}
		singleScaleImageOpener.accept( img );
		logger.info( "Opened single scale image: {}", droppedInPath );
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
