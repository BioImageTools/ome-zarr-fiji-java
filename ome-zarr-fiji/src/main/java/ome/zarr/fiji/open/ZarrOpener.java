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

import org.scijava.Context;
import org.scijava.ui.UIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.gson.JsonSyntaxException;

import ij.IJ;
import ij.gui.YesNoCancelDialog;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.exceptions.MultiImageDatasetException;
import ome.zarr.imglib2.exceptions.NotAMultiscaleImageException;
import ome.zarr.imglib2.exceptions.SingleArrayAxesUnknownException;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fiji.open.exceptions.NonExistingResolutionLevelException;
import ome.zarr.fiji.util.BdvUtils;
import ome.zarr.imglib2.exceptions.StoreAccessException;

/**
 * Backend-reader-agnostic opener for OME-Zarr datasets.
 * Given a {@link URI} location, a {@link PyramidBackend} and an optional
 * preferred resolution width, it loads a {@link PyramidContents} and
 * opens it in ImageJ (as a {@link PyramidalDataset}) or in BigDataViewer (as a
 * {@link PyramidalBdv}, registered in both cases with the {@link PyramidalService} lifecycle).
 * <p>
 * The preferred width applies to the ImageJ openers only, which show one
 * resolution level at a time: it picks that level and, when even the level to be
 * opened is wider than preferred, asks the user for confirmation before opening
 * it. BigDataViewer displays all levels and streams them lazily, so
 * {@link #openBDVWithImage()} ignores the preferred width entirely.
 */
public class ZarrOpener
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String OVERSIZE_DIALOG_TITLE = "Image larger than preferred size";

	private final URI inputUri;

	private final Context context;

	private final PyramidBackend backend;

	private final Integer preferredMaxWidth;

	private final Consumer< String > errorHandler;

	private final Predicate< String > oversizeConfirmation;

	private PyramidContents< ? > cachedContents;

	/**
	 * Opener for {@code inputUri} with an explicit backend and preferred
	 * resolution, reporting failures via {@code IJ::error}.
	 *
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the highest-resolution level that is still no wider
	 *   than this is opened in ImageJ, or {@code null} for the highest resolution
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
	 * @param preferredMaxWidth the highest-resolution level that is still no wider
	 *   than this is opened in ImageJ, or {@code null} for the highest resolution
	 * @param errorHandler receives a user-facing message when opening fails
	 */
	public ZarrOpener( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth, final Consumer< String > errorHandler )
	{
		this( inputUri, context, backend, preferredMaxWidth, errorHandler, ZarrOpener::confirmWithDialog );
	}

	/**
	 * Opener for {@code inputUri} with an explicit backend, preferred resolution,
	 * error sink, and oversize confirmation.
	 *
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the highest-resolution level that is still no wider
	 *   than this is opened in ImageJ, or {@code null} for the highest resolution
	 * @param errorHandler receives a user-facing message when opening fails
	 * @param oversizeConfirmation asked whether to open a level that is wider than
	 *   {@code preferredMaxWidth} after all; receives the user-facing message and
	 *   returns whether to go ahead. Never consulted when {@code preferredMaxWidth}
	 *   is {@code null} or the level fits. Pass a non-interactive implementation
	 *   for headless use — the default shows a modal {@link YesNoCancelDialog}.
	 */
	public ZarrOpener( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth, final Consumer< String > errorHandler,
			final Predicate< String > oversizeConfirmation )
	{
		this.inputUri = inputUri;
		this.context = context;
		this.backend = backend;
		this.preferredMaxWidth = preferredMaxWidth;
		this.errorHandler = errorHandler;
		this.oversizeConfirmation = oversizeConfirmation;
	}

	/**
	 * Loads (once, then caches) the {@link PyramidContents} for the configured
	 * location using the configured {@link PyramidBackend}.
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
			cachedContents = contents;
			logDimensions( contents );
		}
		return cachedContents;
	}

	/**
	 * Logs, at debug level, the full-resolution extent along every OME-Zarr axis
	 * (x, y, z, c, t) plus the number of resolution levels. Axes that are not
	 * present in the dataset are reported as {@code absent}.
	 */
	private void logDimensions( final PyramidContents< ? > contents )
	{
		if ( !logger.isDebugEnabled() )
			return;
		logger.debug( "Opened image {} with dimensions x={}, y={}, z={}, c={}, t={}, resolution levels={}",
				inputUri,
				axisSize( contents, AxisCalibration.X ),
				axisSize( contents, AxisCalibration.Y ),
				axisSize( contents, AxisCalibration.Z ),
				axisSize( contents, AxisCalibration.C ),
				axisSize( contents, AxisCalibration.T ),
				contents.numResolutionLevels() );
	}

	private static String axisSize( final PyramidContents< ? > contents, final String axisName )
	{
		final int index = contents.axisIndex( axisName );
		return index < 0 ? "absent" : Long.toString( contents.asImg().dimension( index ) );
	}

	/**
	 * Opens the dataset in ImageJ as a {@link PyramidalDataset} at the preferred
	 * resolution level, shown via the {@code UIService}. A location pointing at a
	 * single resolution level opens as a one-level dataset.
	 * <p>
	 * When no level is narrow enough for the preferred width — which is always the
	 * case for a single-level location that is too wide — the coarsest level is
	 * offered instead, and opened only if the user confirms.
	 */
	public void openIJWithImage()
	{
		openPyramidImage(
				() -> {
					final PyramidContents< ? > contents = getContents();
					showAsDataset( contents, contents.selectResolutionLevel( preferredMaxWidth ) );
					return null;
				} );
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
	 * <p>
	 * No level is selected here — the caller has already chosen one — but the level
	 * is still opened only if the user confirms when it is wider than the preferred
	 * width.
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
						showAsDataset( contents, resolutionLevel );
						return null;
					} );
		}
		catch ( NonExistingResolutionLevelException e )
		{
			showNonExistingResolutionLevelError( e );
		}
		return null;
	}

	/**
	 * Shows {@code resolutionLevel} of {@code contents} as a new ImageJ dataset,
	 * registered with the {@link PyramidalService} lifecycle — unless the level is
	 * wider than the preferred width and the user declines to open it anyway, in
	 * which case nothing is opened.
	 */
	private void showAsDataset( final PyramidContents< ? > contents, final int resolutionLevel )
	{
		if ( !mayOpenLevel( contents, resolutionLevel ) )
			return;
		final PyramidalDataset dataset = new PyramidalDataset( context, contents, resolutionLevel );
		context.getService( UIService.class ).show( dataset );
		context.getService( PyramidalService.class ).registerImageJDataset( dataset );
		logger.info( "Opened dataset at resolution level {} in ImageJ: {}", resolutionLevel, inputUri );
	}

	/**
	 * Whether {@code resolutionLevel} may be opened: {@code true} when no preferred
	 * width is configured or the level is no wider than it, otherwise the user's
	 * answer to the oversize confirmation.
	 */
	private boolean mayOpenLevel( final PyramidContents< ? > contents, final int resolutionLevel )
	{
		final long width = contents.widthAtLevel( resolutionLevel );
		if ( preferredMaxWidth == null || width <= preferredMaxWidth )
			return true;
		final boolean openAnyway = oversizeConfirmation.test( oversizeMessage( contents, resolutionLevel, width ) );
		logger.info( "Resolution level {} of {} is {} pixels wide, preferred maximum is {}. Opening anyway: {}.",
				resolutionLevel, inputUri, width, preferredMaxWidth, openAnyway );
		return openAnyway;
	}

	/**
	 * The confirmation text for a level that is wider than the preferred width. The
	 * hint that no level of the pyramid is narrow enough is added only when the
	 * level in question is the coarsest one of several — for a finer level, a
	 * narrower one does exist and the hint would contradict the offer.
	 */
	private String oversizeMessage( final PyramidContents< ? > contents, final int resolutionLevel, final long width )
	{
		final int numLevels = contents.numResolutionLevels();
		final StringBuilder message = new StringBuilder( "This image is wider than your preferred maximum width.\n\r\n" )
				.append( "Location: " ).append( inputUri ).append( "\n" )
				.append( "Width: " ).append( width )
				.append( " pixels (preferred maximum: " ).append( preferredMaxWidth ).append( " pixels)\n\r\n" );
		if ( numLevels > 1 && resolutionLevel == numLevels - 1 )
			message.append( "The dataset has " ).append( numLevels )
					.append( " resolution levels, but even the smallest one is " )
					.append( width ).append( " pixels wide.\n\r\n" );
		return message.append( "Open it anyway?\n\r\n" )
				.append( "Large images can be slow to display and may exceed available memory.\n" )
				.append( "You can change the settings under:\n" )
				.append( "Plugins > OME-Zarr > Settings > Opening Behavior Settings." )
				.toString();
	}

	/**
	 * Default oversize confirmation: a modal ImageJ dialog whose "Cancel" button is
	 * a synonym for "Don't open", so only "Open anyway" opens the image. Requires a
	 * display and therefore cannot be used headlessly.
	 */
	private static boolean confirmWithDialog( final String message )
	{
		return new YesNoCancelDialog( IJ.getInstance(), OVERSIZE_DIALOG_TITLE, message,
				"Open anyway", "Don't open" ).yesPressed();
	}

	/**
	 * Opens the {@link ome.zarr.fiji.Pyramidal} in BigDataViewer as a {@link PyramidalBdv} and registers
	 * it with the {@link PyramidalService} lifecycle.
	 * <p>
	 * BigDataViewer shows the whole pyramid and loads from the level that suits the
	 * current zoom, so the preferred width — a limit on the single level an ImageJ
	 * window would hold — does not apply and is not checked here.
	 *
	 * @return the resulting {@code BdvHandle}, or {@code null} if opening failed
	 */
	public Object openBDVWithImage()
	{
		return openPyramidImage(
				() -> {
					final PyramidalBdv< ? > pyramidal = new PyramidalBdv<>( context, getContents() );
					final PyramidalService pyramidalService = context.getService( PyramidalService.class );
					final Object result = BdvUtils.showBdvAndRegisterDataset( pyramidal, pyramidalService );
					logger.info( "Opened pyramidal in BigDataViewer: {}", inputUri );
					return result;
				} );
	}

	private Object openPyramidImage( final Supplier< Object > imageOpener )
	{
		try
		{
			return imageOpener.get();
		}
		catch ( MultiImageDatasetException e )
		{
			showMultiImageNotSupported( e );
		}
		catch ( SingleArrayAxesUnknownException e )
		{
			showSingleArrayAxesUnknown( e );
		}
		catch ( NotAMultiscaleImageException e )
		{
			showNotAMultiscaleError( e );
		}
		catch ( StoreAccessException e )
		{
			showStoreAccessError( e );
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

	private void showSingleArrayAxesUnknown( final SingleArrayAxesUnknownException e )
	{
		errorHandler.accept( "Could not determine the axes of the single OME-Zarr array at: " + inputUri + "\n\r\n"
				+ "The array declares no axis names of its own and no parent multiscales metadata listing it could be read."
				+ "\n\r\nConsider opening one level higher in the hierarchy instead." );
		logger.info( "Cannot determine axes of single resolution level at {}: {}", inputUri, e.getMessage() );
	}

	private void showNotAMultiscaleError( final NotAMultiscaleImageException e )
	{
		errorHandler.accept( "Could not open dataset as image: " + inputUri + "\n\n"
				+ "The location is not a readable OME-Zarr multiscale image and could not be opened as a single resolution level." );
		logger.warn( "Not a multiscale image: {}. Error message: {}", inputUri, e.getMessage() );
	}

	private void showStoreAccessError( final Exception e )
	{
		errorHandler.accept( "Could not access the dataset at: " + inputUri + "\n\n" + e.getMessage() );
		logger.warn( "Store access failed for {}: {}", inputUri, e.getMessage() );
	}

	private void showNonZarrError( final Exception e )
	{
		errorHandler.accept( "Could not open dataset as image: " + inputUri + "\n\n"
				+ "The opener for OME-Zarr only supports locations that contains OME-Zarr metadata, i.e. .zattrs, .zgroup, or zarr.json files." );
		logger.warn( "Could not open dataset image: {}. Error message: {}", inputUri, e.getMessage() );
	}

	private void showNonExistingResolutionLevelError( final NonExistingResolutionLevelException e )
	{
		errorHandler.accept( "Could not open resolution level from dataset: " + inputUri + "\n\n" + e.getMessage() );
		logger.warn( "Could not open resolution level: {}. Error message: {}", inputUri, e.getMessage() );
	}
}
