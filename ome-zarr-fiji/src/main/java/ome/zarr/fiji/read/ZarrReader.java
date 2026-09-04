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
package ome.zarr.fiji.read;

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

import bdv.util.BdvHandle;
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
import ome.zarr.fiji.read.exceptions.NonExistingResolutionLevelException;
import ome.zarr.fiji.util.BdvUtils;
import ome.zarr.imglib2.exceptions.ReaderLibraryUnavailableException;
import ome.zarr.imglib2.exceptions.S3SupportUnavailableException;
import ome.zarr.imglib2.exceptions.StoreAccessException;

/**
 * Backend-agnostic reader for OME-Zarr datasets.
 * Given a {@link URI} location, a {@link PyramidBackend} and an optional
 * preferred resolution width, it reads a {@link PyramidContents} and
 * opens it in ImageJ (as a {@link PyramidalDataset}) or in BigDataViewer (as a
 * {@link PyramidalBdv}, registered in both cases with the {@link PyramidalService} lifecycle).
 * <p>
 * The preferred width applies to {@link #openIJWithImage()} only, which shows one
 * resolution level at a time: it picks that level and, when not even the coarsest
 * one is narrow enough, asks the user for confirmation before opening it anyway.
 * {@link #openIJWithImage(int)} opens the level its caller named, and
 * BigDataViewer displays all levels and streams them lazily.
 * <p>
 * Independently of the width, every display path refuses to show an image whose
 * calibration is a placeholder ({@link PyramidContents#hasPlaceholderCalibration})
 * unless the user confirms.
 */
public class ZarrReader
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String CONFIRM_DIALOG_TITLE = "Open this OME-Zarr image anyway?";

	private final URI inputUri;

	private final Context context;

	private final PyramidBackend backend;

	private final Integer preferredMaxWidth;

	private final Consumer< String > errorHandler;

	private final Predicate< String > openAnywayConfirmation;

	private PyramidContents< ? > cachedContents;

	/**
	 * Reader for {@code inputUri} with an explicit backend.
	 * Will select highest resolution level und report failures via {@code IJ::error}.
	 *
	 * @param inputUri the location of the OME-Zarr dataset
	 * @param context the SciJava context to get services from
	 * @param backend the backend used to read the dataset
	 */
	public ZarrReader( final URI inputUri, final Context context, final PyramidBackend backend )
	{
		this( inputUri, context, backend, null );
	}

	/**
	 * Reader for {@code inputUri} with an explicit backend and preferred
	 * resolution, reporting failures via {@code IJ::error}.
	 *
	 * @param inputUri the location of the OME-Zarr dataset
	 * @param context the SciJava context to get services from
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the highest-resolution level that is still no wider
	 *   than this will be selected, or {@code null} for the highest resolution
	 */
	public ZarrReader( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth )
	{
		this( inputUri, context, backend, preferredMaxWidth, IJ::error );
	}

	/**
	 * Reader for {@code inputUri} with an explicit backend, preferred
	 * resolution, and error sink.
	 *
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the highest-resolution level that is still no wider
	 *   than this is opened in ImageJ, or {@code null} for the highest resolution
	 * @param errorHandler receives a user-facing message when opening fails
	 */
	public ZarrReader( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth, final Consumer< String > errorHandler )
	{
		this( inputUri, context, backend, preferredMaxWidth, errorHandler, ZarrReader::confirmWithDialog );
	}

	/**
	 * Reader for {@code inputUri} with an explicit backend, preferred resolution,
	 * error sink, and open-anyway confirmation.
	 *
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the highest-resolution level that is still no wider
	 *   than this is opened in ImageJ, or {@code null} for the highest resolution
	 * @param errorHandler receives a user-facing message when opening fails
	 * @param openAnywayConfirmation asked whether to open an image that has something
	 *   wrong with it after all.
	 *   Pass a non-interactive implementation for headless use — the default shows a
	 *   modal (modified) {@link YesNoCancelDialog}.
	 */
	public ZarrReader( final URI inputUri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth, final Consumer< String > errorHandler,
			final Predicate< String > openAnywayConfirmation )
	{
		this.inputUri = inputUri;
		this.context = context;
		this.backend = backend;
		this.preferredMaxWidth = preferredMaxWidth;
		this.errorHandler = errorHandler;
		this.openAnywayConfirmation = openAnywayConfirmation;
	}

	/**
	 * Reads (once, then caches) the {@link PyramidContents} for the configured
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
			final PyramidContents< ? > contents = backend.read( inputUri );
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
		return contents.hasAxis( axisName ) ? Long.toString( contents.sizeAlongAxis( axisName ) ) : "absent";
	}

	/**
	 * Opens the dataset in ImageJ as a {@link PyramidalDataset} at the preferred
	 * resolution level, shown via the {@code UIService}. A location pointing at a
	 * single resolution level opens as a one-level dataset.
	 * <p>
	 * When no level is narrow enough for the preferred width — which is always the
	 * case for a single-level location that is too wide — the coarsest level is
	 * offered instead and opened only if the user confirms.
	 *
	 * @return the opened {@link PyramidalDataset}, or {@code null} if reading
	 *   failed or the user declined to open the image
	 */
	// NB: the return value is for API and script users
	public PyramidalDataset openIJWithImage()
	{
		return openPyramidImage(
				() -> {
					final PyramidContents< ? > contents = getContents();
					if ( uncalibratedAndDeclined( contents ) )
						return null;
					final int suggestedLevel = contents.suggestResolutionLevel( preferredMaxWidth );
					if ( suggestedLevel != PyramidContents.NO_MATCHING_LEVEL )
						return showAsDataset( contents, suggestedLevel );
					if ( confirmOpeningSmallestLevel( contents ) )
						return showAsDataset( contents, contents.smallestResolutionLevel() );
					return null;
				} );
	}

	/**
	 * Whether the image must not be shown: {@code true} only when its calibration is
	 * a placeholder (see {@link PyramidContents#hasPlaceholderCalibration}) and the
	 * user declined to open it anyway.
	 * <p>
	 * Every display path asks this, because the invented scale and unit belong to
	 * the dataset rather than to the resolution level or the viewer: they mislead in
	 * BigDataViewer exactly as much as in ImageJ.
	 */
	private boolean uncalibratedAndDeclined( final PyramidContents< ? > contents )
	{
		if ( !contents.hasPlaceholderCalibration )
			return false;
		final boolean openAnyway = openAnywayConfirmation.test( uncalibratedMessage() );
		logger.info( "{} has no scale or unit of its own. Opening it anyway: {}.", inputUri, openAnyway );
		return !openAnyway;
	}

	/** The confirmation text for an image whose scale and unit are placeholders. */
	private String uncalibratedMessage()
	{
		return "This image has no calibration.\n\r\n"
				+ "Location: " + inputUri + "\n\r\n"
				+ "It is a single resolution level, and no parent OME-Zarr group states the size of a pixel or "
				+ "the unit to measure it in." + "\n\r\n"
				+ "Every axis would be reported as 1.0 with no unit, which looks the "
				+ "same as a genuinely unit-spaced image: measurements would be in pixels.\n\r\n"
				+ "Open it anyway?\n\r\n"
				+ "Opening the parent OME-Zarr group up in the hierarchy instead may give you the real calibration.";
	}

	/**
	 * Asks the user whether to open the coarsest level even though it is wider than
	 * the preferred width, which is the situation when
	 * {@link PyramidContents#suggestResolutionLevel} finds no matching level.
	 */
	private boolean confirmOpeningSmallestLevel( final PyramidContents< ? > contents )
	{
		final long width = contents.sizeAlongAxis( AxisCalibration.X, contents.smallestResolutionLevel() );
		final boolean openAnyway = openAnywayConfirmation.test( oversizeMessage( contents, width ) );
		logger.info( "No resolution level of {} is as narrow as the preferred maximum of {}, the coarsest one is {} "
				+ "pixels wide. Opening it anyway: {}.", inputUri, preferredMaxWidth, width, openAnyway );
		return openAnyway;
	}

	/**
	 * Opens the resolution level at {@code resolutionLevel} index as a new ImageJ dataset.
	 * Index 0 is the highest resolution; each increment is the next coarser level.
	 * <p>
	 * Multiple calls — whether at the same or different level indices — each produce a separate
	 * ImageJ {@code Dataset} (and a separate window), but all of them are backed by the same
	 * {@link ome.zarr.imglib2.PyramidContents} object: the cached cell images
	 * and volatile images are the single source of truth and are never read more than once
	 * per resolution level.
	 * <p>
	 * No level is selected here — the caller has already chosen one — so the
	 * preferred width does not apply and the named level is opened without asking.
	 *
	 * @param resolutionLevel 0-based index into the resolution pyramid
	 * @return the opened {@link PyramidalDataset}, or {@code null} if reading
	 *   failed, the level does not exist, or the user declined to open the image
	 */
	// NB: the return value is for API and script users
	public PyramidalDataset openIJWithImage( final int resolutionLevel )
	{
		try
		{
			return openPyramidImage(
					() -> {
						final PyramidContents< ? > contents = getContents();
						if ( resolutionLevel < 0 || resolutionLevel >= contents.numResolutionLevels() )
							throw new NonExistingResolutionLevelException( resolutionLevel, contents.numResolutionLevels() );
						if ( uncalibratedAndDeclined( contents ) )
							return null;
						return showAsDataset( contents, resolutionLevel );
					} );
		}
		catch ( NonExistingResolutionLevelException e )
		{
			showNonExistingResolutionLevelError( e );
			return null;
		}
	}

	/**
	 * Reads the dataset and wraps it into a {@link PyramidalDataset} <em>without</em>
	 * displaying it: no ImageJ window is opened and the {@link PyramidalService}
	 * active pyramidal is left alone, so the caller decides what to do with the
	 * result. This is the entry point for scripts and for commands that declare a
	 * {@code Dataset} output.
	 * <p>
	 * The resolution level is the one the preferred width selects.
	 *
	 * @return the dataset, or {@code null} if reading failed
	 */
	public PyramidalDataset getPyramidalDataset()
	{
		return openPyramidImage(
				() -> {
					final PyramidContents< ? > contents = getContents();
					final int suggestedLevel = contents.suggestResolutionLevel( preferredMaxWidth );
					if ( suggestedLevel != PyramidContents.NO_MATCHING_LEVEL )
						return new PyramidalDataset( context, contents, suggestedLevel );
					final int smallestLevel = contents.smallestResolutionLevel();
					logger.warn( "No resolution level of {} is as narrow as the preferred maximum of {}; returning the "
							+ "coarsest level {} instead.", inputUri, preferredMaxWidth, smallestLevel );
					return new PyramidalDataset( context, contents, smallestLevel );
				} );
	}

	/**
	 * Shows {@code resolutionLevel} of {@code contents} as a new ImageJ dataset,
	 * and makes it the active pyramidal of the {@link PyramidalService}.
	 */
	private PyramidalDataset showAsDataset( final PyramidContents< ? > contents, final int resolutionLevel )
	{
		final PyramidalDataset dataset = new PyramidalDataset( context, contents, resolutionLevel );
		context.getService( UIService.class ).show( dataset );
		context.getService( PyramidalService.class ).setActivePyramidal( dataset );
		logger.info( "Opened dataset at resolution level {} in ImageJ: {}", resolutionLevel, inputUri );
		return dataset;
	}

	/**
	 * The confirmation text for the coarsest level, which is {@code width} pixels
	 * wide and thus wider than the preferred width. For a pyramid of several levels
	 * the message spells out that none of them is narrow enough.
	 */
	private String oversizeMessage( final PyramidContents< ? > contents, final long width )
	{
		final int numLevels = contents.numResolutionLevels();
		final StringBuilder message = new StringBuilder( "This image is wider than your preferred maximum width.\n\r\n" )
				.append( "Location: " ).append( inputUri ).append( "\n" )
				.append( "Width: " ).append( width )
				.append( " pixels (preferred maximum: " ).append( preferredMaxWidth ).append( " pixels)\n\r\n" );
		if ( numLevels > 1 )
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
		return new YesNoCancelDialog( IJ.getInstance(), CONFIRM_DIALOG_TITLE, message,
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
	 * @return the resulting {@link BdvHandle}, or {@code null} if opening failed or
	 *   the user declined to open the image
	 */
	// NB: the return value is for API and script users
	public BdvHandle openBDVWithImage()
	{
		return openPyramidImage(
				() -> {
					if ( uncalibratedAndDeclined( getContents() ) )
						return null;
					final PyramidalBdv< ? > pyramidal = new PyramidalBdv<>( context, getContents() );
					final PyramidalService pyramidalService = context.getService( PyramidalService.class );
					final BdvHandle result = BdvUtils.showBdvAndRegisterDataset( pyramidal, pyramidalService );
					logger.info( "Opened pyramidal in BigDataViewer: {}", inputUri );
					return result;
				} );
	}

	/**
	 * Runs {@code imageOpener} and turns every expected reading failure into a
	 * user-facing message plus a log entry, returning {@code null} in that case.
	 */
	private < T > T openPyramidImage( final Supplier< T > imageOpener )
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
		catch ( S3SupportUnavailableException e )
		{
			showS3SupportUnavailable( e );
		}
		catch ( ReaderLibraryUnavailableException e )
		{
			showReaderLibraryUnavailable( e );
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

	private void showS3SupportUnavailable( final S3SupportUnavailableException e )
	{
		errorHandler.accept( "Could not open the dataset at: " + inputUri + "\n\r\n"
				+ "Reading from s3:// stores needs the AWS SDK, which is not installed here. "
				+ "It currently ships with Fiji-Latest only.\n\r\n"
				+ "Please download Fiji-latest here: https://fiji.sc/" );
		final String cause = String.valueOf( e.getCause() );
		logger.warn( "Cannot open {}: the AWS SDK is not on the classpath ({})", inputUri, cause );
	}

	private void showReaderLibraryUnavailable( final ReaderLibraryUnavailableException e )
	{
		errorHandler.accept( "Could not open the dataset at: " + inputUri + "\n\r\n"
				+ "The selected backend (" + backend.getName() + ") needs a class that its library "
				+ "does not provide here:\n"
				+ e.getMissingClass() + "\n\r\n"
				+ "Please try using Fiji-latest instead. Download here: https://fiji.sc/" );
		logger.warn( "Cannot open {} with the {} backend: reader library class missing ({})",
				inputUri, backend.getName(), e.getMissingClass() );
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
