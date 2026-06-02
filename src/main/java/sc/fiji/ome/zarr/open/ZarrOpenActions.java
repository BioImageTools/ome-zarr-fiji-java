/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package sc.fiji.ome.zarr.open;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.JsonSyntaxException;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Cast;

import ij.IJ;
import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageDataImpl;
import sc.fiji.ome.zarr.pyramid.exceptions.MultiImageDatasetException;
import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.exceptions.NonExistingResolutionLevelException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotASingleScaleImageException;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.pyramid.backend.zarrjava.ZarrJavaPyramidBackend;
import sc.fiji.ome.zarr.open.options.ZarrOpeningSettings;
import sc.fiji.ome.zarr.open.options.ZarrOpenBehavior;
import sc.fiji.ome.zarr.open.options.ZarrReaderBackend;
import sc.fiji.ome.zarr.ui.DnDActionChooser;
import sc.fiji.ome.zarr.util.BdvFocusService;
import sc.fiji.ome.zarr.util.BdvUtils;
import sc.fiji.ome.zarr.util.ScriptUtils;

public class ZarrOpenActions
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final URI inputUri;

	private final Context context;

	private final Consumer< String > errorHandler;

	private final ZarrOpeningSettings settings;

	private Pyramidal5DImageDataImpl< ?, ? > cachedPyramid;

	/**
	 * Loads {@link ZarrOpeningSettings} from {@code context} and opens
	 * {@code inputUri} via the action selected by the user's configured
	 * {@link ZarrOpenBehavior}: ImageJ display, BigDataViewer display, or the
	 * {@link DnDActionChooser} selection dialog. Shared entry point for the
	 * drag-and-drop handler and the "paste URL" command.
	 */
	public static void openWithSettings( final URI inputUri, final Context context )
	{
		final PrefService prefService = context.getService( PrefService.class );
		final ZarrOpeningSettings settings = ZarrOpeningSettings.loadSettingsFromPreferences( prefService );
		final ZarrOpenActions actions = new ZarrOpenActions( inputUri, context, settings );
		switch ( settings.getOpenBehavior() )
		{
		case IMAGEJ_HIGHEST_RESOLUTION:
		case IMAGEJ_CUSTOM_RESOLUTION:
			actions.openIJWithImage();
			break;
		case BDV_MULTI_RESOLUTION:
			actions.openBDVWithImage();
			break;
		case SHOW_SELECTION_DIALOG:
		default:
			new DnDActionChooser( context, actions ).showDialog();
			break;
		}
	}

	public ZarrOpenActions( final URI inputUri, final Context context )
	{
		this( inputUri, context, null, IJ::error );
	}

	public ZarrOpenActions( final URI inputUri, final Context context, final ZarrOpeningSettings settings )
	{
		this( inputUri, context, settings, IJ::error );
	}

	ZarrOpenActions( final URI inputUri, final Context context, final ZarrOpeningSettings settings,
			final Consumer< String > errorHandler )
	{
		this.inputUri = inputUri;
		this.context = context;
		this.settings = settings;
		this.errorHandler = errorHandler;
	}

	/**
	 * String suitable for being shown to the user or pre-filled into a path
	 * field: an OS-native path for {@code file:} URIs, the URI string otherwise.
	 */
	private String displayLocation()
	{
		return "file".equalsIgnoreCase( inputUri.getScheme() )
				? Paths.get( inputUri ).toString()
				: inputUri.toString();
	}

	/**
	 * Opens the N5 Importer dialog pointed at the dropped-in path.<br>
	 * Shortcut to File &gt; Import &gt; HDF5/N5/Zarr/OME-NGFF
	 */
	public void openImporterDialog()
	{
		new N5Importer().runWithDialog( displayLocation(), Collections.emptyList() );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 importer dialog with location: {}.", inputUri );
	}

	/**
	 * Opens the N5 Viewer (aka BigDataViewer) dialog pointed at the dropped-in path.<br>
	 * Shortcut to Plugins &gt; BigDataViewer &gt; HDF5/N5/Zarr/OME-NGFF Viewer
	 */
	public void openViewerDialog()
	{
		new N5ViewerCreator().runWithDialog( displayLocation(),
				e -> logger.warn( "Could not open viewer selection dialog: {}", e.getMessage() ) );
		if ( logger.isInfoEnabled() )
			logger.info( "Opened Zarr/N5 viewer with location: {}.", inputUri );
	}

	public Object openIJWithImage()
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidalDataset< ? > dataset = getPyramid().asPyramidalDataset();
						context.getService( UIService.class ).show( dataset );
						logger.info( "Opened dataset in ImageJ: {}", inputUri );
						return null;
					},
					singleScaleImage -> ImageJFunctions.show( Cast.unchecked( singleScaleImage ) ) );
		}
		catch ( NoMatchingResolutionException e )
		{
			showNonMatchingResolutionError( e );
		}
		return null;
	}

	/**
	 * Opens the resolution level at {@code resolutionLevel} index as a new ImageJ dataset.
	 * Index 0 is the highest resolution; each increment is the next coarser level.
	 * <p>
	 * Multiple calls — whether at the same or different level indices — each produce a separate
	 * ImageJ {@code Dataset} (and a separate window), but all of them are backed by the same
	 * {@link sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData} object. The underlying
	 * cached cell images and volatile images in
	 * {@link sc.fiji.ome.zarr.pyramid.backend.PyramidContents} are the single source of truth
	 * and are never loaded more than once per resolution level.
	 *
	 * @param resolutionLevel 0-based index into the resolution pyramid
	 */
	public Object openIJWithImage( final int resolutionLevel )
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidalDataset< ? > dataset = getPyramid().asPyramidalDataset( resolutionLevel );
						context.getService( UIService.class ).show( dataset );
						logger.info( "Opened dataset at resolution level {} in ImageJ: {}", resolutionLevel, inputUri );
						return null;
					},
					singleScaleImage -> ImageJFunctions.show( Cast.unchecked( singleScaleImage ) ) );
		}
		catch ( NonExistingResolutionLevelException e )
		{
			showNonExistingResolutionLevelError( e );
		}
		return null;
	}

	public Object openBDVWithImage()
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidalDataset< ? > dataset = getPyramid().asPyramidalDataset();
						final BdvFocusService bdvFocusService = context.getService( BdvFocusService.class );
						final Object result = BdvUtils.showBdvAndRegisterDataset( Cast.unchecked( dataset ), bdvFocusService );
						logger.info( "Opened dataset in BigDataViewer: {}", inputUri );
						return result;
					},
					singleScaleImage -> null );
		}
		catch ( NoMatchingResolutionException e )
		{
			showNonMatchingResolutionError( e );
		}
		return null;
	}

	private Pyramidal5DImageDataImpl< ?, ? > getPyramid()
	{
		if ( cachedPyramid == null )
		{
			final Integer preferredWidth;
			if ( settings == null || settings.getOpenBehavior().equals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION ) )
				preferredWidth = null;
			else
				preferredWidth = settings.getPreferredMaxWidth();

			final ZarrReaderBackend backend = settings == null
					? ZarrOpeningSettings.DEFAULT_READER_BACKEND
					: settings.getReaderBackend();
			switch ( backend )
			{
			case ZARR_JAVA:
			{
				@SuppressWarnings( { "rawtypes", "unchecked" } )
				final Pyramidal5DImageDataImpl< ?, ? > zarrJavaData =
						new Pyramidal5DImageDataImpl( context, new ZarrJavaPyramidBackend( inputUri ) );
				cachedPyramid = zarrJavaData;
				break;
			}
			case N5:
			default:
				cachedPyramid = new Pyramidal5DImageDataImpl<>( context, inputUri, preferredWidth );
				break;
			}
		}
		return cachedPyramid;
	}

	private Object openPyramidImage( final Supplier< Object > multiScaleOpener, final Function< Img< ? >, Object > singleScaleOpener )
	{
		try
		{
			return multiScaleOpener.get();
		}
		catch ( MultiImageDatasetException e )
		{
			showMultiImageNotSupported( e );
		}
		catch ( NotAMultiscaleImageException e )
		{
			logger.warn( "Not a multiscale image: {}", e.getMessage() );
			showSingleScaleNotSupported();
			// TODO: openSingleScaleImage( singleScaleOpener ) when single-scale support is added
		}
		catch ( IllegalArgumentException | JsonSyntaxException e )
		{
			showNonZarrError( e );
		}
		return null;
	}

	private void showMultiImageNotSupported( final MultiImageDatasetException e )
	{
		errorHandler.accept( e.getMessage() );
		logger.info( e.getMessage() );
	}

	private void showSingleScaleNotSupported()
	{
		errorHandler.accept(
				"Opening a single resolution OME-Zarr dataset, as was found in: " + inputUri + ", is currently not supported.\n\n"
						+ "Consider opening one level higher in the hierarchy instead." );
		logger.info( "Opening a single resolution OME-Zarr dataset, as was found in: {}, is currently not supported.", inputUri );
	}

	private void showSingleScaleError( final Exception e )
	{
		errorHandler.accept( "Could not open dataset as image: " + inputUri + "\n\n"
				+ "Consider opening one level higher or lower in the hierarchy instead." );
		logger.warn( "Could not open dataset as single resolution image: {}. Error message: {}", inputUri, e.getMessage() );
	}

	private void showNonZarrError( final Exception e )
	{
		errorHandler.accept( "Could not open dataset as image: " + inputUri + "\n\n"
				+ "The opener for OME-Zarr only supports locations that contains OME-Zarr metadata, i.e. .zattrs, .zgroup, or zarr.json files." );
		logger.warn( "Could not open dataset image: {}. Error message: {}", inputUri, e.getMessage() );
	}

	private void showNonMatchingResolutionError( final Exception e )
	{
		errorHandler.accept( "Safety check failed when opening dataset: " + inputUri + "\n\r\n" + e.getMessage() + "\n\r\n"
				+ "If the image size is okay for this computer, please adjust the setting in\nPlugins > OME-Zarr > Settings > Opening Behavior Settings to still open the image." );
		logger.warn( "Not opening dataset: {}. Error message: {}", inputUri, e.getMessage() );
	}

	private void showNonExistingResolutionLevelError( final NonExistingResolutionLevelException e )
	{
		errorHandler.accept( "Could not open resolution level from dataset: " + inputUri + "\n\n" + e.getMessage() );
		logger.warn( "Could not open resolution level: {}. Error message: {}", inputUri, e.getMessage() );
	}

	Object openImage( final Function< PyramidalDataset< ? >, Object > multiScaleImageOpener,
			final Function< Img< ? >, Object > singleScaleImageOpener )
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidalDataset< ? > dataset = getPyramid().asPyramidalDataset();
						final Object result = multiScaleImageOpener.apply( dataset );
						logger.info( "Opened dataset: {}", inputUri );
						return result;
					},
					singleScaleImageOpener );
		}
		catch ( NoMatchingResolutionException e )
		{
			showNonMatchingResolutionError( e );
		}
		return null;
	}

	private Object openSingleScaleImage( final Function< Img< ? >, Object > singleScaleImageOpener ) throws NotASingleScaleImageException
	{
		N5Reader reader = new N5Factory().openReader( inputUri.toString() );
		Img< ? > img;
		try
		{
			img = N5Utils.open( reader, "" );
		}
		catch ( Exception e )
		{
			throw new NotASingleScaleImageException( inputUri.toString(), e );
		}
		Object result = singleScaleImageOpener.apply( img );
		logger.info( "Opened single scale image: {}", inputUri );
		return result;
	}

	public void runScript()
	{
		logger.info( "Attempt to execute script on location: {}.", inputUri );
		ScriptUtils.executePresetScript( context, inputUri, errorHandler );
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
