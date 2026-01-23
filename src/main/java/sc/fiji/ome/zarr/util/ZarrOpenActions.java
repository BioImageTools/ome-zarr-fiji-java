package sc.fiji.ome.zarr.util;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;

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
import java.util.List;

import bdv.util.BdvFunctions;

import javax.annotation.Nullable;

public class ZarrOpenActions
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final Path droppedInPath;

	private final Context context;

	private final @Nullable BdvHandleService bdvHandleService;

	public ZarrOpenActions( final Path droppedInPath, final Context context, @Nullable final BdvHandleService bdvHandleService )
	{
		this.droppedInPath = droppedInPath;
		this.context = context;
		this.bdvHandleService = bdvHandleService;
	}

	/**
	 * Opens the N5 Importer dialog pointed at the dropped-in path.<br>
	 * Shortcut to File > Import > HDF5/N5/Zarr/OME-NGFF
	 */
	public void openImporterDialog()
	{
		final URI droppedInURI = ZarrOnFileSystemUtils.getUriFromPath( droppedInPath );
		final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( droppedInPath );
		final List< String > relativePaths = ZarrOnFileSystemUtils.relativePathElements( rootPath, droppedInPath );

		new N5Importer().runWithDialog( droppedInURI.toString(), relativePaths );

		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 importer dialog at {} with relative paths: {}.", droppedInURI, String.join( "/", relativePaths ) );
	}

	/**
	 * Opens the N5 Viewer (aka BigDataViewer) dialog pointed at the dropped-in path.<br>
	 * Shortcut to Plugins > BigDataViewer > HDF5/N5/Zarr/OME-NGFF Viewer
	 */
	public void openViewerDialog()
	{
		final URI droppedInURI = ZarrOnFileSystemUtils.getUriFromPath( droppedInPath );
		final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( droppedInPath );
		final List< String > relativePaths = ZarrOnFileSystemUtils.relativePathElements( rootPath, droppedInPath );

		new N5ViewerCreator().runWithDialog( droppedInURI.toString(), relativePaths );

		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 viewer at {} with relative paths: {}.", droppedInURI, String.join( "/", relativePaths ) );
	}

	public void openIJHighestResolution()
	{
		final String root = ZarrOnFileSystemUtils.getUriFromPath( droppedInPath ).toString();

		N5Reader reader = new N5Factory().openReader( root );
		String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName( reader.deepListDatasets( "" ) );

		ImageJFunctions.show( ( RandomAccessibleInterval ) N5Utils.open( reader, dataset ), dataset );

		logger.info( "Opened ImageJ at highest resolution: {}", root );
	}

	public void openBDVHighestResolution()
	{
		final String root = ZarrOnFileSystemUtils.getUriFromPath( droppedInPath ).toString();

		N5Reader reader = new N5Factory().openReader( root );
		String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName( reader.deepListDatasets( "" ) );

		if ( bdvHandleService == null )
			BdvFunctions.show( ( Img< ? > ) N5Utils.open( reader, dataset ), dataset );
		else
			bdvHandleService.openNewBdv( N5Utils.open( reader, dataset ), dataset );

		logger.info( "Opened BDV at highest resolution: {}", root );
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
