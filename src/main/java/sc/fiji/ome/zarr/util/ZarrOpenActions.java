package sc.fiji.ome.zarr.util;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
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
import java.util.function.Function;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Cast;

import bdv.util.BdvFunctions;
import ij.IJ;
import sc.fiji.ome.zarr.pyramid.DefaultPyramidal5DImageData;
import sc.fiji.ome.zarr.pyramid.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.NotASingleScaleImageException;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.settings.ZarrDragAndDropOpenSettings;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;

public class ZarrOpenActions
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final Path droppedInPath;

	private final Context context;

	private final Consumer< String > errorHandler;

	private final ZarrDragAndDropOpenSettings settings;

	public ZarrOpenActions( final Path droppedInPath, final Context context )
	{
		this( droppedInPath, context, null, IJ::error );
	}

	public ZarrOpenActions( final Path droppedInPath, final Context context, final ZarrDragAndDropOpenSettings settings )
	{
		this( droppedInPath, context, settings, IJ::error );
	}

	ZarrOpenActions( final Path droppedInPath, final Context context, final ZarrDragAndDropOpenSettings settings,
			final Consumer< String > errorHandler )
	{
		this.droppedInPath = droppedInPath;
		this.context = context;
		this.settings = settings;
		this.errorHandler = errorHandler;
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
		new N5ViewerCreator().runWithDialog( droppedInPath.toString(),
				e -> logger.warn( "Could not open viewer selection dialog: {}", e.getMessage() ) );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 viewer with path: {}.", droppedInPath );
	}

	public void openIJWithImage()
	{
		openImage(
				pyramidalDataset -> {
					context.getService( UIService.class ).show( pyramidalDataset );
					return null;
				},
				singleScaleImage -> ImageJFunctions.show( Cast.unchecked( singleScaleImage ) ),
				"ImageJ"
		);
	}

	public Object openBDVWithImage()
	{
		return openImage(
				BdvUtils::showBdvAndRegisterDataset,
				singleScaleImage -> BdvFunctions.show( singleScaleImage, "Image" ),
				"BigDataViewer"
		);
	}

	private void showSingleScaleError( final Exception e )
	{
		errorHandler.accept( "Could not open dataset as image: " + droppedInPath + "\n\n"
				+ "Consider opening one level higher or lower in the hierarchy instead." );
		logger.warn( "Could not open dataset as single scale image: {}. Error message: {}", droppedInPath, e.getMessage() );
	}

	private void showNonZarrError( final Exception e )
	{
		errorHandler.accept( "Could not open dataset as image: " + droppedInPath + "\n\n"
				+ "The drag & drop for Zarr folders only supports folders that contains zarr metadata, i.e. .zattrs, .zgroup, or zarr.json files." );
		logger.warn( "Could not open dataset image: {}. Error message: {}", droppedInPath, e.getMessage() );
	}

	private void showNonMatchingResolutionError( final Exception e )
	{
		errorHandler.accept( "Safety check failed when opening dataset: " + droppedInPath + "\n\r\n" + e.getMessage() + "\n\r\n"
				+ "If the image size is okay for this computer, please adjust the setting in\nPlugins > OME-Zarr > Zarr Drag And Drop Open Settings to still open the image." );
		logger.warn( "Not opening dataset: {}. Error message: {}", droppedInPath, e.getMessage() );
	}

	Object openImage( final Function< PyramidalDataset< ? >, Object > multiScaleImageOpener,
			final Function< Img< ? >, Object > singleScaleImageOpener,
			final String message )
	{
		try
		{
			Object result = openMultiScaleImage( multiScaleImageOpener );
			logger.info( "Opened dataset in {}: {}", message, droppedInPath );
			return result;
		}
		catch ( NotAMultiscaleImageException e )
		{
			logger.warn( "Not a multiscale image: {}", e.getMessage() );
			logger.info( "Try opening as single-scale image instead." );
			try
			{
				Object result = openSingleScaleImage( singleScaleImageOpener );
				logger.info( "Opened single scale image in {}: {}", message, droppedInPath );
				return result;
			}
			catch ( NotASingleScaleImageException ex )
			{
				showSingleScaleError( ex );
			}
		}
		catch ( NoMatchingResolutionException e )
		{
			showNonMatchingResolutionError( e );
		}
		catch ( IllegalArgumentException e )
		{
			showNonZarrError( e );
		}
		return null;
	}

	private Object openMultiScaleImage( final Function< PyramidalDataset< ? >, Object > multiScaleImageOpener )
			throws NotAMultiscaleImageException, NoMatchingResolutionException
	{
		Integer preferredWidth;
		if ( settings == null || settings.getOpenBehavior().equals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION ) )
			preferredWidth = null;
		else
			preferredWidth = settings.getPreferredMaxWidth();
		final DefaultPyramidal5DImageData< ?, ? > pyramidal5DImageData =
				new DefaultPyramidal5DImageData<>( context, droppedInPath.toString(), preferredWidth );
		PyramidalDataset< ? > pyramidalDataset = pyramidal5DImageData.asPyramidalDataset();
		Object result = multiScaleImageOpener.apply( pyramidalDataset );
		logger.info( "Opened multiscale image: {}", droppedInPath );
		return result;
	}

	private Object openSingleScaleImage( final Function< Img< ? >, Object > singleScaleImageOpener ) throws NotASingleScaleImageException
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
		Object result = singleScaleImageOpener.apply( img );
		logger.info( "Opened single scale image: {}", droppedInPath );
		return result;
	}

	public void runScript()
	{
		final String path = ZarrOnFileSystemUtils.getUriFromPath( droppedInPath ).toString();
		logger.info( "Attempt to execute script on path: {}.", path );
		ScriptUtils.executePresetScript( context, path, errorHandler );
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
