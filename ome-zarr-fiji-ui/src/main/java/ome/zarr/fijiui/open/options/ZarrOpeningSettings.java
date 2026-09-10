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
package ome.zarr.fijiui.open.options;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.fijiui.open.openers.BdvMultiResolutionOpener;
import ome.zarr.fijiui.open.openers.ImageJHighestResolutionOpener;
import ome.zarr.fijiui.open.openers.ImageJPreferredResolutionOpener;
import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fiji.open.ZarrOpenerService;

/**
 * The user's OME-Zarr opening preferences: which {@link ZarrOpener} to use, up
 * to which width to open in ImageJ, and which library to read with.
 * <p>
 * The opener is stored as its plugin name rather than as a fixed set of
 * choices, so a plugin that registers its own {@link ZarrOpener} can be selected
 * here like the built-in ones.
 */
public class ZarrOpeningSettings
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * The default max width (in Pixels) for the
	 * {@link ImageJPreferredResolutionOpener}. This is used if the user has not set
	 * a custom width in the preferences.
	 */
	public static final int DEFAULT_PREFERRED_WIDTH = 1000;

	public static final ZarrBackend DEFAULT_BACKEND = ZarrBackend.ZARR_JAVA;

	/**
	 * The names the opening behavior was persisted under before openers were
	 * plugins, mapped to the opener that replaced each of them. Preferences
	 * written by version 0.7 and earlier are read through this; it can go once
	 * those are no longer in the field.
	 */
	private static final Map< String, String > LEGACY_NAMES = legacyNames();

	/**
	 * The chosen opener's plugin name, {@link ZarrOpenerService#ASK}, or
	 * {@code null} when the user never made a choice.
	 */
	private String openerName;

	private int preferredMaxWidth;

	private ZarrBackend backend;

	private static final String ZARR_OPEN_BEHAVIOR_SETTING_NAME = "ZarrOpenBehavior";

	private static final String ZARR_PREFERRED_WIDTH_SETTING_NAME = "ZarrPreferredWidth";

	private static final String ZARR_BACKEND_SETTING_NAME = "ZarrBackend";

	public ZarrOpeningSettings()
	{
		this( null, DEFAULT_PREFERRED_WIDTH, DEFAULT_BACKEND );
	}

	public ZarrOpeningSettings( final String openerName, final int preferredMaxWidth )
	{
		this( openerName, preferredMaxWidth, DEFAULT_BACKEND );
	}

	public ZarrOpeningSettings( final String openerName, final int preferredMaxWidth, final ZarrBackend backend )
	{
		this.openerName = openerName;
		this.preferredMaxWidth = preferredMaxWidth;
		this.backend = backend;
	}

	/**
	 * The opener the user picked.
	 *
	 * @return the {@link ZarrOpener} plugin name, {@link ZarrOpenerService#ASK}, or
	 *   {@code null} when nothing was ever configured — in which case
	 *   {@link ZarrOpenerService#effectiveOpenerName(String)} decides
	 */
	public String getOpenerName()
	{
		return openerName;
	}

	/**
	 * Sets the opener to use.
	 *
	 * @param openerName a {@link ZarrOpener} plugin name, or
	 *   {@link ZarrOpenerService#ASK} to be prompted every time
	 */
	public void setOpenerName( final String openerName )
	{
		this.openerName = openerName;
	}

	/**
	 * Gets the preferred width (in Pixels) for the
	 * {@link ImageJPreferredResolutionOpener}.
	 *
	 * @return the preferred maximum width in pixels
	 */
	public int getPreferredMaxWidth()
	{
		return preferredMaxWidth;
	}

	/**
	 * Sets the preferred width (in Pixels) for the
	 * {@link ImageJPreferredResolutionOpener}.
	 *
	 * @param preferredMaxWidth the preferred maximum width in pixels
	 */
	public void setPreferredMaxWidth( final int preferredMaxWidth )
	{
		this.preferredMaxWidth = preferredMaxWidth;
	}

	/**
	 * Gets the Zarr backend used to decode (encode) datasets.
	 *
	 * @return the configured backend
	 */
	public ZarrBackend getBackend()
	{
		return backend;
	}

	/**
	 * Sets the Zarr backend used to decode (encode) datasets.
	 *
	 * @param backend the backend to use
	 */
	public void setBackend( final ZarrBackend backend )
	{
		this.backend = backend;
	}

	/**
	 * Loads and returns the settings from the provided preference store.
	 *
	 * @param prefs If {@code null} is provided, default settings values from this class are used and returned.
	 * @return the settings from the provided preference store, or default values if {@code prefs} is {@code null} or if the provided preference store does not contain any information about the default settings.
	 */
	public static ZarrOpeningSettings loadSettingsFromPreferences( final PrefService prefs )
	{
		final String openerName = prefs == null ? null
				: migrateLegacyName( prefs.get( ZarrOpeningSettings.class, ZARR_OPEN_BEHAVIOR_SETTING_NAME, null ) );
		int preferredWidth = prefs == null ? DEFAULT_PREFERRED_WIDTH
				: prefs.getInt( ZarrOpeningSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME, DEFAULT_PREFERRED_WIDTH );
		ZarrBackend backend;
		try
		{
			backend = prefs == null ? DEFAULT_BACKEND : ZarrBackend.getByName(
					prefs.get( ZarrOpeningSettings.class, ZARR_BACKEND_SETTING_NAME, DEFAULT_BACKEND.name() ) );
		}
		catch ( NoSuchElementException e )
		{
			backend = DEFAULT_BACKEND;
		}
		logger.debug( "Loaded OME-Zarr opener: {}", openerName );
		logger.debug( "Loaded OME-Zarr preferred width: {}", preferredWidth );
		logger.debug( "Loaded OME-Zarr default backend: {}", backend );
		return new ZarrOpeningSettings( openerName, preferredWidth, backend );
	}

	/**
	 * Translates a value written by an older version into the opener name that
	 * replaced it; anything else is passed through unchanged.
	 */
	private static String migrateLegacyName( final String storedName )
	{
		final String migrated = LEGACY_NAMES.get( storedName );
		if ( migrated == null )
			return storedName;
		logger.debug( "Migrated the stored opening behavior '{}' to the opener '{}'.", storedName, migrated );
		return migrated;
	}

	private static Map< String, String > legacyNames()
	{
		final Map< String, String > names = new HashMap<>();
		names.put( "IMAGEJ_HIGHEST_RESOLUTION", ImageJHighestResolutionOpener.NAME );
		names.put( "IMAGEJ_CUSTOM_RESOLUTION", ImageJPreferredResolutionOpener.NAME );
		names.put( "BDV_MULTI_RESOLUTION", BdvMultiResolutionOpener.NAME );
		names.put( "SHOW_SELECTION_DIALOG", ZarrOpenerService.ASK );
		return Collections.unmodifiableMap( names );
	}

	/**
	 * Saves the setting to the user preferences.<br>
	 *
	 * @param prefs A handle to the {@link PrefService}. If {@code null} is provided, no other default place to keep the preferences is assumed, and the function thus saves nothing and silently finishes.
	 */
	public void saveSettingsToPreferences( final PrefService prefs )
	{
		if ( prefs == null )
			return;
		if ( openerName != null )
			prefs.put( ZarrOpeningSettings.class, ZARR_OPEN_BEHAVIOR_SETTING_NAME, openerName );
		prefs.put( ZarrOpeningSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME, getPreferredMaxWidth() );
		prefs.put( ZarrOpeningSettings.class, ZARR_BACKEND_SETTING_NAME, getBackend().name() );
		logger.debug( "Saved OME-Zarr opener to preferences: {}", openerName );
		logger.debug( "Saved OME-Zarr preferred width to preferences: {}", getPreferredMaxWidth() );
		logger.debug( "Saved OME-Zarr backend to preferences: {}", getBackend() );
	}

	@Override
	public String toString()
	{
		return "ZarrOpeningSettings{openerName=" + openerName
				+ ", preferredMaxWidth=" + preferredMaxWidth
				+ ", backend=" + backend + "}";
	}
}
