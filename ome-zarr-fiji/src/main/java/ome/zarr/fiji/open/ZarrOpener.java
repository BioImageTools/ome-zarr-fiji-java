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
package ome.zarr.fiji.open;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.Context;
import org.scijava.ui.UIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.JsonSyntaxException;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Cast;

import ij.IJ;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.exceptions.MultiImageDatasetException;
import ome.zarr.imglib2.exceptions.NoMatchingResolutionException;
import ome.zarr.imglib2.exceptions.NotAMultiscaleImageException;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fiji.open.exceptions.NonExistingResolutionLevelException;
import ome.zarr.fiji.open.exceptions.NotASingleScaleImageException;
import ome.zarr.fiji.util.BdvUtils;

/**
 * Backend-reader-agnostic opener for OME-Zarr datasets.
 * Given a {@link URI} location, a {@link PyramidBackend} and an optional
 * preferred resolution width, it loads a {@link PyramidContents} and
 * opens it in ImageJ (as a {@link PyramidalDataset}) or in BigDataViewer (as a
 * {@link PyramidalBdv}, registered in both cases with the {@link PyramidalService} lifecycle).
 */
public class ZarrOpener
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final URI inputUri;

	private final Context context;

	private final PyramidBackend backend;

	private final Integer preferredMaxWidth;

	private final Consumer< String > errorHandler;

	private PyramidContents< ? > cachedContents;

	private int preferredResolutionLevel;

	/**
	 * Opener for {@code inputUri} with an explicit backend and preferred
	 * resolution, reporting failures via {@code IJ::error}.
	 *
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the coarsest level whose width is still &le; this is
	 *   opened, or {@code null} for the highest resolution
	 */
	public ZarrOpener( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth )
	{
		this( inputUri, context, backend, preferredMaxWidth, IJ::error );
	}

	/**
	 * Opener for {@code inputUri} with an explicit backend, preferred
	 * resolution, and error sink.
	 *
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the coarsest level whose width is still &le; this is
	 *   opened, or {@code null} for the highest resolution
	 * @param errorHandler receives a user-facing message when opening fails
	 */
	public ZarrOpener( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth, final Consumer< String > errorHandler )
	{
		this.inputUri = inputUri;
		this.context = context;
		this.backend = backend;
		this.preferredMaxWidth = preferredMaxWidth;
		this.errorHandler = errorHandler;
	}

	/**
	 * Loads (once, then caches) the {@link PyramidContents} for the configured
	 * location using the configured {@link PyramidBackend}, and resolves the
	 * resolution level matching the preferred width.
	 */
	// java:S1452: the wildcard is intentional. The pixel type is only known once
	// the data is read, and callers use only type-independent members of the
	// returned PyramidContents; making this generic would push the
	// wildcard onto every use site.
	@SuppressWarnings( "java:S1452" )
	public PyramidContents< ? > getContents()
	{
		if ( cachedContents == null )
		{
			final PyramidContents< ? > contents = backend.load( inputUri );
			preferredResolutionLevel = contents.selectResolutionLevel( preferredMaxWidth );
			cachedContents = contents;
		}
		return cachedContents;
	}

	/**
	 * Opens the dataset in ImageJ as a {@link PyramidalDataset} at the preferred
	 * resolution level. Returns {@code null} for a multiscale image (it is shown
	 * via the {@code UIService}); the return value is reserved for the future
	 * single-scale path.
	 */
	public Object openIJWithImage()
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidContents< ? > contents = getContents();
						final PyramidalDataset dataset = new PyramidalDataset( context, contents, preferredResolutionLevel );
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
	 * {@link ome.zarr.imglib2.PyramidContents} object: the cached cell images
	 * and volatile images are the single source of truth and are never loaded more than once
	 * per resolution level.
	 *
	 * @param resolutionLevel 0-based index into the resolution pyramid
	 */
	public Object openIJWithImage( final int resolutionLevel )
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidContents< ? > contents = getContents();
						if ( resolutionLevel < 0 || resolutionLevel >= contents.numResolutionLevels() )
							throw new NonExistingResolutionLevelException( resolutionLevel, contents.numResolutionLevels() );
						final PyramidalDataset dataset = new PyramidalDataset( context, contents, resolutionLevel );
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

	/**
	 * Opens the dataset in BigDataViewer as a {@link PyramidalBdv} and registers
	 * it with the {@link PyramidalService} lifecycle.
	 *
	 * @return the resulting {@code BdvHandle}, or {@code null} if opening failed
	 */
	public Object openBDVWithImage()
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidalBdv< ? > dataset = new PyramidalBdv<>( context, getContents() );
						final PyramidalService pyramidalService = context.getService( PyramidalService.class );
						final Object result = BdvUtils.showBdvAndRegisterDataset( dataset, pyramidalService );
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

	/**
	 * Opens the dataset and applies a caller-supplied function to it: the
	 * multiscale function receives a {@link PyramidalDataset} at the preferred
	 * resolution level, the single-scale function an {@link Img} (single-scale
	 * support is still pending). Returns the function's result, or {@code null}
	 * if opening failed.
	 */
	public Object openImage( final Function< PyramidalDataset, Object > multiScaleImageOpener,
			final Function< Img< ? >, Object > singleScaleImageOpener )
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidContents< ? > contents = getContents();
						final PyramidalDataset dataset = new PyramidalDataset( context, contents, preferredResolutionLevel );
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
}
