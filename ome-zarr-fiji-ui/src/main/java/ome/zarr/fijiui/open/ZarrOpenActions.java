/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.fijiui.open;

import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Consumer;

import bdv.util.BdvHandle;
import ij.IJ;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.open.ZarrOpenRequest;
import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fiji.open.ZarrOpenerService;
import ome.zarr.fijiui.open.openers.ImageJPreferredResolutionOpener;
import ome.zarr.fijiui.dialog.ZarrOpenActionChooser;
import ome.zarr.fijiui.open.options.ZarrOpeningSettings;
import ome.zarr.fijiui.open.options.ZarrBackend;
import ome.zarr.fiji.read.ZarrReader;
import ome.zarr.fijiui.util.ScriptUtils;
import ome.zarr.imglib2.PyramidBackend;

/**
 * Fiji-ui orchestration of the OME-Zarr opening pipeline: it reads the user's
 * {@link ZarrOpeningSettings}, hands the location to the {@link ZarrOpener} the
 * user chose, and provides the UI-facing actions (N5 importer/viewer dialogs,
 * preset script, help) that the {@link ZarrOpenActionChooser} and the openers in
 * this package are built from.
 * <p>
 * The actual reading and ImageJ/BigDataViewer opening lives in
 * {@link ZarrReader}, so this class only adds the UI concerns on top.
 */
public class ZarrOpenActions
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final ZarrOpenRequest request;

	/**
	 * Loads {@link ZarrOpeningSettings} from {@code context} and opens
	 * {@code inputUri} with the {@link ZarrOpener} the user configured — or, when
	 * the user asked to be prompted, through the {@link ZarrOpenActionChooser}
	 * selection dialog.
	 * <p>
	 * This is the single funnel every entry path ends in (drag-and-drop,
	 * {@code fiji://} links, clipboard paste and the File &gt; Import commands), so
	 * a registered opener is reachable from all of them.
	 *
	 * @param inputUri the OME-Zarr location to open
	 * @param context the SciJava context the settings and openers are read from
	 */
	public static void openWithSettings( final URI inputUri, final Context context )
	{
		final PrefService prefService = context.getService( PrefService.class );
		final ZarrOpeningSettings settings = ZarrOpeningSettings.loadSettingsFromPreferences( prefService );
		final ZarrOpenRequest request = requestFor( inputUri, context, settings, IJ::error );
		final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
		if ( openerService == null )
		{
			// A context without the service is a broken classpath, not a user choice.
			logger.warn( "No ZarrOpenerService in the SciJava context. Opening {} with the built-in ImageJ opener.",
					inputUri );
			new ImageJPreferredResolutionOpener().open( request );
			return;
		}
		final String openerName = openerService.effectiveOpenerName( settings.getOpenerName() );
		if ( ZarrOpenerService.ASK.equals( openerName ) )
		{
			new ZarrOpenActionChooser( context, request ).showDialog();
			return;
		}
		if ( !openerService.open( openerName, request ) )
		{
			// The chosen opener came from a plugin that is no longer installed.
			final String fallback = openerService.effectiveOpenerName( null );
			logger.info( "The configured OME-Zarr opener '{}' is not installed, using '{}' instead.",
					openerName, fallback );
			if ( !openerService.open( fallback, request ) )
				new ImageJPreferredResolutionOpener().open( request );
		}
	}

	/**
	 * Convenience factory for a backend-agnostic {@link ZarrReader} that uses the
	 * default backend ({@link ZarrOpeningSettings#DEFAULT_BACKEND})
	 * at the highest resolution, reporting failures via {@code IJ::error}.
	 * <p>
	 * This lives in the fiji-ui layer because picking a concrete backend is a
	 * fiji-ui concern: {@link ZarrReader} itself only knows {@link PyramidBackend}
	 * and the fiji layer therefore depends on no concrete backend. It restores the
	 * one-liner ergonomics of the former no-backend {@code ZarrReader}
	 * constructor.
	 *
	 * @param inputUri the OME-Zarr location to read
	 * @param context the SciJava context used for display and services
	 * @return a reader for {@code inputUri} using the default backend
	 */
	public static ZarrReader defaultOpener( final URI inputUri, final Context context )
	{
		return new ZarrReader( inputUri, context, ZarrOpeningSettings.DEFAULT_BACKEND.createBackend(), null );
	}

	/**
	 * Actions for {@code inputUri} with default opening settings, reporting
	 * failures via {@code IJ::error}.
	 *
	 * @param inputUri the OME-Zarr location the actions operate on
	 * @param context the SciJava context used for display and services
	 */
	public ZarrOpenActions( final URI inputUri, final Context context )
	{
		this( inputUri, context, null, IJ::error );
	}

	/**
	 * Actions for {@code inputUri} using the given opening {@code settings}
	 * (reader backend and preferred resolution), reporting failures via
	 * {@code IJ::error}. Pass {@code null} settings to use the defaults.
	 *
	 * @param inputUri the OME-Zarr location the actions operate on
	 * @param context the SciJava context used for display and services
	 * @param settings the opening settings, or {@code null} for the defaults
	 */
	public ZarrOpenActions( final URI inputUri, final Context context, final ZarrOpeningSettings settings )
	{
		this( inputUri, context, settings, IJ::error );
	}

	/**
	 * Actions for {@code inputUri} using the given opening {@code settings} and an
	 * explicit error sink. Pass {@code null} settings to use the defaults.
	 *
	 * @param errorHandler receives a user-facing message when opening fails
	 */
	ZarrOpenActions( final URI inputUri, final Context context, final ZarrOpeningSettings settings,
			final Consumer< String > errorHandler )
	{
		this( requestFor( inputUri, context, settings, errorHandler ) );
	}

	/**
	 * Actions for an open request, sharing its lazily built {@link ZarrReader} —
	 * the constructor a {@link ZarrOpener} in this package uses.
	 *
	 * @param request the location to act on and the settings to act with
	 */
	public ZarrOpenActions( final ZarrOpenRequest request )
	{
		this.request = request;
	}

	/**
	 * Request for {@code inputUri} carrying the backend and preferred width from
	 * {@code settings}, or the defaults when no settings are given.
	 */
	private static ZarrOpenRequest requestFor( final URI inputUri, final Context context,
			final ZarrOpeningSettings settings, final Consumer< String > errorHandler )
	{
		final ZarrBackend backend = settings == null ? ZarrOpeningSettings.DEFAULT_BACKEND : settings.getBackend();
		final Integer preferredMaxWidth = settings == null ? null : settings.getPreferredMaxWidth();
		return new ZarrOpenRequest( inputUri, context, backend.createBackend(), preferredMaxWidth, errorHandler );
	}

	/**
	 * String suitable for being shown to the user or pre-filled into a path
	 * field: an OS-native path for {@code file:} URIs, the URI string otherwise.
	 */
	private String displayLocation()
	{
		final URI inputUri = request.uri();
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
			logger.info( "Opened Zarr/N5 importer dialog with location: {}.", request.uri() );
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
			logger.info( "Opened Zarr/N5 viewer with location: {}.", request.uri() );
	}

	/**
	 * Opens the dataset in ImageJ at the resolution level selected by the settings.
	 * Delegates to {@link ZarrReader#openIJWithImage()}.
	 *
	 * @return the opened {@link PyramidalDataset}, or {@code null} if opening failed
	 */
	// UnusedReturnValue: the dataset is returned for API and script users
	@SuppressWarnings( "UnusedReturnValue" )
	public PyramidalDataset openIJWithImage()
	{
		return request.reader().openIJWithImage();
	}

	/**
	 * Opens the given resolution level of the dataset in ImageJ (0 = highest
	 * resolution). Delegates to {@link ZarrReader#openIJWithImage(int)}.
	 *
	 * @param resolutionLevel 0-based index into the resolution pyramid
	 * @return the opened {@link PyramidalDataset}, or {@code null} if opening failed
	 */
	// UnusedReturnValue: the dataset is returned for API and script users
	@SuppressWarnings( "UnusedReturnValue" )
	public PyramidalDataset openIJWithImage( final int resolutionLevel )
	{
		return request.reader().openIJWithImage( resolutionLevel );
	}

	/**
	 * Opens the dataset in BigDataViewer. Delegates to
	 * {@link ZarrReader#openBDVWithImage()}.
	 *
	 * @return the resulting {@link BdvHandle}, or {@code null} if opening failed
	 */
	// UnusedReturnValue: the dataset is returned for API and script users
	@SuppressWarnings( "UnusedReturnValue" )
	public BdvHandle openBDVWithImage()
	{
		return request.reader().openBDVWithImage();
	}

	/**
	 * Opens the Fiji script editor pre-filled with a scriptlet that opens the
	 * dataset, so the user can adapt it for macros/scripts.
	 */
	public void runScript()
	{
		logger.info( "Attempt to execute script on location: {}.", request.uri() );
		ScriptUtils.executePresetScript( request.context(), request.uri(), request.errorHandler() );
	}

	/**
	 * Opens the project's help page in the system web browser.
	 */
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
