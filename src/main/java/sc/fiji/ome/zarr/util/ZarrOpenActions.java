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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Cast;

import bdv.util.BdvFunctions;
import ij.IJ;
import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageDataImpl;
import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotASingleScaleImageException;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.pyramid.backend.zarrjava.ZarrJavaPyramidBackend;
import sc.fiji.ome.zarr.settings.ZarrOpeningSettings;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;
import sc.fiji.ome.zarr.settings.ZarrReaderBackend;

public class ZarrOpenActions
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final URI inputUri;

	private final Context context;

	private final Consumer< String > errorHandler;

	private final ZarrOpeningSettings settings;

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
		return openImage(
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
				+ "The opener for OME-Zarr folders only supports folders that contains OME-Zarr metadata, i.e. .zattrs, .zgroup, or zarr.json files." );
		logger.warn( "Could not open dataset image: {}. Error message: {}", inputUri, e.getMessage() );
	}

	private void showNonMatchingResolutionError( final Exception e )
	{
		errorHandler.accept( "Safety check failed when opening dataset: " + inputUri + "\n\r\n" + e.getMessage() + "\n\r\n"
				+ "If the image size is okay for this computer, please adjust the setting in\nPlugins > OME-Zarr > Settings > Opening Behavior Settings to still open the image." );
		logger.warn( "Not opening dataset: {}. Error message: {}", inputUri, e.getMessage() );
	}

	Object openImage( final Function< PyramidalDataset< ? >, Object > multiScaleImageOpener,
			final Function< Img< ? >, Object > singleScaleImageOpener,
			final String message )
	{
		try
		{
			Object result = openMultiScaleImage( multiScaleImageOpener );
			logger.info( "Opened dataset in {}: {}", message, inputUri );
			return result;
		}
		catch ( NotAMultiscaleImageException e )
		{
			logger.warn( "Not a multiscale image: {}", e.getMessage() );
			logger.info( "Try opening as single-scale image instead." );
			try
			{
				showSingleScaleNotSupported();
				//Object result = openSingleScaleImage( singleScaleImageOpener ); // currently not supported
				//logger.info( "Opened single scale image in {}: {}", message, inputUri );
				return null;
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
		final Integer preferredWidth;
		if ( settings == null || settings.getOpenBehavior().equals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION ) )
			preferredWidth = null;
		else
			preferredWidth = settings.getPreferredMaxWidth();

		final ZarrReaderBackend backend = settings == null
				? ZarrOpeningSettings.DEFAULT_READER_BACKEND
				: settings.getReaderBackend();

		final Pyramidal5DImageDataImpl< ?, ? > data;
		switch ( backend )
		{
		case ZARR_JAVA:
		{
			@SuppressWarnings( { "rawtypes", "unchecked" } )
			final Pyramidal5DImageDataImpl< ?, ? > zarrJavaData =
					new Pyramidal5DImageDataImpl( context, new ZarrJavaPyramidBackend( inputUri, preferredWidth ) );
			data = zarrJavaData;
			break;
		}
		case N5:
		default:
			data = new Pyramidal5DImageDataImpl<>( context, inputUri, preferredWidth );
			break;
		}

		final Object result = multiScaleImageOpener.apply( data.asPyramidalDataset() );
		logger.info( "Opened multiscale image with {} backend: {}", backend, inputUri );
		return result;
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
		final String location = inputUri.toString();
		logger.info( "Attempt to execute script on location: {}.", location );
		ScriptUtils.executePresetScript( context, location, errorHandler );
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
