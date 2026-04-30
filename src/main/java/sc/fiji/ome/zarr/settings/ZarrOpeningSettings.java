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
package sc.fiji.ome.zarr.settings;

import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;

import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZarrOpeningSettings
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final ZarrOpenBehavior DEFAULT_OPEN_BEHAVIOR = ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION;

	/**
	 * The default max width (in Pixels) for the {@link ZarrOpenBehavior#IMAGEJ_CUSTOM_RESOLUTION} option. This is used if the user has not set a custom width in the preferences.
	 */
	public static final int DEFAULT_PREFERRED_WIDTH = 1000;

	public static final ZarrReaderBackend DEFAULT_READER_BACKEND = ZarrReaderBackend.N5;

	private ZarrOpenBehavior zarrOpenBehavior;

	private int preferredMaxWidth;

	private ZarrReaderBackend readerBackend;

	private static final String ZARR_OPEN_BEHAVIOR_SETTING_NAME = "ZarrOpenBehavior";

	private static final String ZARR_PREFERRED_WIDTH_SETTING_NAME = "ZarrPreferredWidth";

	private static final String ZARR_READER_BACKEND_SETTING_NAME = "ZarrReaderBackend";

	public ZarrOpeningSettings()
	{
		this( DEFAULT_OPEN_BEHAVIOR, DEFAULT_PREFERRED_WIDTH, DEFAULT_READER_BACKEND );
	}

	public ZarrOpeningSettings( final ZarrOpenBehavior zarrOpenBehavior, final int preferredMaxWidth )
	{
		this( zarrOpenBehavior, preferredMaxWidth, DEFAULT_READER_BACKEND );
	}

	public ZarrOpeningSettings( final ZarrOpenBehavior zarrOpenBehavior, final int preferredMaxWidth,
			final ZarrReaderBackend readerBackend )
	{
		this.zarrOpenBehavior = zarrOpenBehavior;
		this.preferredMaxWidth = preferredMaxWidth;
		this.readerBackend = readerBackend;
	}

	public ZarrOpenBehavior getOpenBehavior()
	{
		return zarrOpenBehavior;
	}

	public void setCurrentChoice( final ZarrOpenBehavior zarrOpenBehavior )
	{
		this.zarrOpenBehavior = zarrOpenBehavior;
	}

	/**
	 * Gets the preferred width (in Pixels) for the {@link ZarrOpenBehavior#IMAGEJ_CUSTOM_RESOLUTION} behavior.
	 *
	 * @return  the preferred width (in Pixels) for the {@link ZarrOpenBehavior#IMAGEJ_CUSTOM_RESOLUTION} behavior.
	 */
	public int getPreferredMaxWidth()
	{
		return preferredMaxWidth;
	}

	/**
	 * Set the preferred width (in Pixels) for the {@link ZarrOpenBehavior#IMAGEJ_CUSTOM_RESOLUTION} behavior.
	 *
	 * @param preferredMaxWidth the preferred width (in Pixels) for the {@link ZarrOpenBehavior#IMAGEJ_CUSTOM_RESOLUTION} behavior.
	 */
	public void setPreferredMaxWidth( final int preferredMaxWidth )
	{
		this.preferredMaxWidth = preferredMaxWidth;
	}

	/**
	 * Gets the Zarr reader backend used to decode datasets.
	 */
	public ZarrReaderBackend getReaderBackend()
	{
		return readerBackend;
	}

	/**
	 * Sets the Zarr reader backend used to decode datasets.
	 */
	public void setReaderBackend( final ZarrReaderBackend readerBackend )
	{
		this.readerBackend = readerBackend;
	}

	/**
	 * Loads and returns the settings from the provided preference store.
	 *
	 * @param prefs If {@code null} is provided, default settings values from this class are used and returned.
	 * @return the settings from the provided preference store, or default values if {@code prefs} is {@code null} or if the provided preference store does not contain any information about the default settings.
	 */
	public static ZarrOpeningSettings loadSettingsFromPreferences( final PrefService prefs )
	{
		ZarrOpenBehavior behavior;
		try
		{
			behavior = prefs == null ? DEFAULT_OPEN_BEHAVIOR : ZarrOpenBehavior
					.getByName(
							prefs.get( ZarrOpeningSettings.class, ZARR_OPEN_BEHAVIOR_SETTING_NAME, DEFAULT_OPEN_BEHAVIOR.name() ) );
		}
		catch ( NoSuchElementException e )
		{
			behavior = DEFAULT_OPEN_BEHAVIOR;
		}
		int preferredWidth = prefs == null ? DEFAULT_PREFERRED_WIDTH
				: prefs.getInt(
						ZarrOpeningSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME,
						DEFAULT_PREFERRED_WIDTH
				);
		ZarrReaderBackend backend;
		try
		{
			backend = prefs == null ? DEFAULT_READER_BACKEND : ZarrReaderBackend
					.getByName(
							prefs.get( ZarrOpeningSettings.class, ZARR_READER_BACKEND_SETTING_NAME, DEFAULT_READER_BACKEND.name() ) );
		}
		catch ( NoSuchElementException e )
		{
			backend = DEFAULT_READER_BACKEND;
		}
		logger.debug( "Loaded OME-Zarr default opening behavior: {}", behavior );
		logger.debug( "Loaded OME-Zarr preferred width: {}", preferredWidth );
		logger.debug( "Loaded OME-Zarr reader backend: {}", backend );
		return new ZarrOpeningSettings( behavior, preferredWidth, backend );
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
		prefs.put( ZarrOpeningSettings.class, ZARR_OPEN_BEHAVIOR_SETTING_NAME, getOpenBehavior().name() );
		prefs.put( ZarrOpeningSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME, getPreferredMaxWidth() );
		prefs.put( ZarrOpeningSettings.class, ZARR_READER_BACKEND_SETTING_NAME, getReaderBackend().name() );
		logger.debug( "Saved OME-Zarr default opening behavior to preferences: {}", getOpenBehavior() );
		logger.debug( "Saved OME-Zarr preferred width to preferences: {}", getPreferredMaxWidth() );
		logger.debug( "Saved OME-Zarr reader backend to preferences: {}", getReaderBackend() );
	}

	@Override
	public String toString()
	{
		return "ZarrDefaultOpenSetting{zarrOpenBehavior=" + zarrOpenBehavior
				+ ", preferredMaxWidth=" + preferredMaxWidth
				+ ", readerBackend=" + readerBackend + "}";
	}
}
