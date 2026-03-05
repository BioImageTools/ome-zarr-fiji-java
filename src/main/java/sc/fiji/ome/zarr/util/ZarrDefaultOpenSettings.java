package sc.fiji.ome.zarr.util;

import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;

import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZarrDefaultOpenSettings
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final ZarrOpenChoice DEFAULT_OPEN_CHOICE = ZarrOpenChoice.IMAGEJ_CUSTOM_RESOLUTION;

	/**
	 * The default max width (in Pixels) for the {@link ZarrOpenChoice#IMAGEJ_CUSTOM_RESOLUTION} option. This is used if the user has not set a custom width in the preferences.
	 */
	public static final int DEFAULT_PREFERRED_WIDTH = 1000;

	private ZarrOpenChoice zarrOpenChoice;

	private int preferredMaxWidth;

	private static final String ZARR_OPEN_CHOICE_SETTING_NAME = "ZarrDefaultOpenChoice";

	private static final String ZARR_PREFERRED_WIDTH_SETTING_NAME = "ZarrPreferredWidth";

	public ZarrDefaultOpenSettings()
	{
		this( DEFAULT_OPEN_CHOICE, DEFAULT_PREFERRED_WIDTH );
	}

	public ZarrDefaultOpenSettings( final ZarrOpenChoice zarrOpenChoice, final int preferredMaxWidth )
	{
		this.zarrOpenChoice = zarrOpenChoice;
		this.preferredMaxWidth = preferredMaxWidth;
	}

	public ZarrOpenChoice getCurrentChoice()
	{
		return zarrOpenChoice;
	}

	public void setCurrentChoice( final ZarrOpenChoice zarrOpenChoice )
	{
		this.zarrOpenChoice = zarrOpenChoice;
	}

	/**
	 * Gets the preferred width (in Pixels) for the {@link ZarrOpenChoice#IMAGEJ_CUSTOM_RESOLUTION} option.
	 *
	 * @return  the preferred width (in Pixels) for the {@link ZarrOpenChoice#IMAGEJ_CUSTOM_RESOLUTION} option.
	 */
	public int getPreferredMaxWidth()
	{
		return preferredMaxWidth;
	}

	/**
	 * Set the preferred width (in Pixels) for the {@link ZarrOpenChoice#IMAGEJ_CUSTOM_RESOLUTION} option.
	 *
	 * @param preferredMaxWidth the preferred width (in Pixels) for the {@link ZarrOpenChoice#IMAGEJ_CUSTOM_RESOLUTION} option.
	 */
	public void setPreferredMaxWidth( final int preferredMaxWidth )
	{
		this.preferredMaxWidth = preferredMaxWidth;
	}

	public static ZarrDefaultOpenSettings loadSettingsFromPreferences( final PrefService prefs )
	{
		ZarrOpenChoice choice;
		try
		{
			choice = prefs == null ? DEFAULT_OPEN_CHOICE : ZarrOpenChoice
					.getByName( prefs.get( ZarrDefaultOpenSettings.class, ZARR_OPEN_CHOICE_SETTING_NAME, DEFAULT_OPEN_CHOICE.name() ) );
		}
		catch ( NoSuchElementException e )
		{
			choice = DEFAULT_OPEN_CHOICE;
		}
		int preferredWidth = prefs == null ? DEFAULT_PREFERRED_WIDTH
				: prefs.getInt( ZarrDefaultOpenSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME, DEFAULT_PREFERRED_WIDTH );
		logger.debug( "Loaded Zarr default opening choice: {}", choice );
		logger.debug( "Loaded zarr custom width: {}", preferredWidth );
		return new ZarrDefaultOpenSettings( choice, preferredWidth );
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
		prefs.put( ZarrDefaultOpenSettings.class, ZARR_OPEN_CHOICE_SETTING_NAME, getCurrentChoice().name() );
		prefs.put( ZarrDefaultOpenSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME, getPreferredMaxWidth() );
		logger.debug( "Saved zarr default opening option to preferences: {}", getCurrentChoice() );
		logger.debug( "Saved zarr custom width to preferences: {}", getPreferredMaxWidth() );
	}

	@Override
	public String toString()
	{
		return "ZarrDefaultOpenSetting{option=" + zarrOpenChoice + "}";
	}
}
