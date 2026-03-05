package sc.fiji.ome.zarr.util;

import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;

import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZarrDefaultOpenSettings
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final ZarrOpenOptions DEFAULT_OPEN_OPTION = ZarrOpenOptions.IMAGEJ_CUSTOM_RESOLUTION;

	/**
	 * The default max width (in Pixels) for the {@link ZarrOpenOptions#IMAGEJ_CUSTOM_RESOLUTION} option. This is used if the user has not set a custom width in the preferences.
	 */
	public static final int DEFAULT_MAX_WIDTH = 1000;

	private ZarrOpenOptions option;

	private int preferredMaxWidth;

	private static final String ZARR_OPEN_OPTION_SETTING_NAME = "ZarrDefaultOpenOption";

	private static final String ZARR_CUSTOM_WIDTH_SETTING_NAME = "ZarrCustomWidth";

	public ZarrDefaultOpenSettings()
	{
		this( DEFAULT_OPEN_OPTION, DEFAULT_MAX_WIDTH );
	}

	public ZarrDefaultOpenSettings( final ZarrOpenOptions zarrOpenOptions, final int preferredMaxWidth )
	{
		this.option = zarrOpenOptions;
		this.preferredMaxWidth = preferredMaxWidth;
	}

	public ZarrOpenOptions getChosenOpenOption()
	{
		return option;
	}

	public void setChosenOpenOption( final ZarrOpenOptions zarrOpenOptions )
	{
		option = zarrOpenOptions;
	}

	/**
	 * Gets the preferred width (in Pixels) for the {@link ZarrOpenOptions#IMAGEJ_CUSTOM_RESOLUTION} option.
	 *
	 * @return  the preferred width (in Pixels) for the {@link ZarrOpenOptions#IMAGEJ_CUSTOM_RESOLUTION} option.
	 */
	public int getPreferredMaxWidth()
	{
		return preferredMaxWidth;
	}

	/**
	 * Set the preferred width (in Pixels) for the {@link ZarrOpenOptions#IMAGEJ_CUSTOM_RESOLUTION} option.
	 *
	 * @param preferredMaxWidth the preferred width (in Pixels) for the {@link ZarrOpenOptions#IMAGEJ_CUSTOM_RESOLUTION} option.
	 */
	public void setPreferredMaxWidth( final int preferredMaxWidth )
	{
		this.preferredMaxWidth = preferredMaxWidth;
	}

	public static ZarrDefaultOpenSettings loadSettingsFromPreferences( final PrefService prefs )
	{
		ZarrOpenOptions option;
		try
		{
			option = prefs == null ? DEFAULT_OPEN_OPTION : ZarrOpenOptions
					.getByName( prefs.get( ZarrDefaultOpenSettings.class, ZARR_OPEN_OPTION_SETTING_NAME, DEFAULT_OPEN_OPTION.name() ) );
		}
		catch ( NoSuchElementException e )
		{
			option = DEFAULT_OPEN_OPTION;
		}
		int customWidth = prefs == null ? DEFAULT_MAX_WIDTH
				: prefs.getInt( ZarrDefaultOpenSettings.class, ZARR_CUSTOM_WIDTH_SETTING_NAME, DEFAULT_MAX_WIDTH );
		logger.debug( "Loaded Zarr default opening option: {}", option );
		logger.debug( "Loaded zarr custom width: {}", customWidth );
		return new ZarrDefaultOpenSettings( option, customWidth );
	}

	/**
	 * Saves the setting to the user preferences.
	 */
	public void saveSettingsToPreferences( final PrefService prefs )
	{
		if ( prefs == null )
			return;
		prefs.put( ZarrDefaultOpenSettings.class, ZARR_OPEN_OPTION_SETTING_NAME, getChosenOpenOption().name() );
		prefs.put( ZarrDefaultOpenSettings.class, ZARR_CUSTOM_WIDTH_SETTING_NAME, getPreferredMaxWidth() );
		logger.debug( "Saved zarr default opening option to preferences: {}", getChosenOpenOption() );
		logger.debug( "Saved zarr custom width to preferences: {}", getPreferredMaxWidth() );
	}

	@Override
	public String toString()
	{
		return "ZarrDefaultOpenSetting{option=" + option + "}";
	}
}
