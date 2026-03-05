package sc.fiji.ome.zarr.util;

import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;

import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZarrDragAndDropOpenSettings
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final ZarrOpenBehavior DEFAULT_OPEN_BEHAVIOR = ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION;

	/**
	 * The default max width (in Pixels) for the {@link ZarrOpenBehavior#IMAGEJ_CUSTOM_RESOLUTION} option. This is used if the user has not set a custom width in the preferences.
	 */
	public static final int DEFAULT_PREFERRED_WIDTH = 1000;

	private ZarrOpenBehavior zarrOpenBehavior;

	private int preferredMaxWidth;

	private static final String ZARR_OPEN_BEHAVIOR_SETTING_NAME = "ZarrOpenBehavior";

	private static final String ZARR_PREFERRED_WIDTH_SETTING_NAME = "ZarrPreferredWidth";

	public ZarrDragAndDropOpenSettings()
	{
		this( DEFAULT_OPEN_BEHAVIOR, DEFAULT_PREFERRED_WIDTH );
	}

	public ZarrDragAndDropOpenSettings( final ZarrOpenBehavior zarrOpenBehavior, final int preferredMaxWidth )
	{
		this.zarrOpenBehavior = zarrOpenBehavior;
		this.preferredMaxWidth = preferredMaxWidth;
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
	 * Loads and returns the settings from the provided preference store.
	 *
	 * @param prefs If {@code null} is provided, default settings values from this class are used and returned.
	 * @return the settings from the provided preference store, or default values if {@code prefs} is {@code null} or if the provided preference store does not contain any information about the default settings.
	 */
	public static ZarrDragAndDropOpenSettings loadSettingsFromPreferences( final PrefService prefs )
	{
		ZarrOpenBehavior behavior;
		try
		{
			behavior = prefs == null ? DEFAULT_OPEN_BEHAVIOR : ZarrOpenBehavior
					.getByName(
							prefs.get( ZarrDragAndDropOpenSettings.class, ZARR_OPEN_BEHAVIOR_SETTING_NAME, DEFAULT_OPEN_BEHAVIOR.name() ) );
		}
		catch ( NoSuchElementException e )
		{
			behavior = DEFAULT_OPEN_BEHAVIOR;
		}
		int preferredWidth = prefs == null ? DEFAULT_PREFERRED_WIDTH
				: prefs.getInt(
						ZarrDragAndDropOpenSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME,
						DEFAULT_PREFERRED_WIDTH
				);
		logger.debug( "Loaded Zarr default opening behavior: {}", behavior );
		logger.debug( "Loaded zarr preferred width: {}", preferredWidth );
		return new ZarrDragAndDropOpenSettings( behavior, preferredWidth );
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
		prefs.put( ZarrDragAndDropOpenSettings.class, ZARR_OPEN_BEHAVIOR_SETTING_NAME, getOpenBehavior().name() );
		prefs.put( ZarrDragAndDropOpenSettings.class, ZARR_PREFERRED_WIDTH_SETTING_NAME, getPreferredMaxWidth() );
		logger.debug( "Saved zarr default opening behavior to preferences: {}", getOpenBehavior() );
		logger.debug( "Saved zarr preferred width to preferences: {}", getPreferredMaxWidth() );
	}

	@Override
	public String toString()
	{
		return "ZarrDefaultOpenSetting{zarrOpenBehavior=" + zarrOpenBehavior + ", preferredMaxWidth=" + preferredMaxWidth + "}";
	}
}
