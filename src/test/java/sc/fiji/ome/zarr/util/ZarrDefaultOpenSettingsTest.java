package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

/**
 * Unit tests for the {@link ZarrDefaultOpenSettings#getChosenOpenOption()} method.
 * This method retrieves the current chosen open option for Zarr datasets.
 */
class ZarrDefaultOpenSettingsTest
{

	@Test
	void testGetChosenOpenOptionReturnsDefaultWhenNoOptionSet()
	{
		// Instantiate ZarrDefaultOpenSettings without custom values
		ZarrDefaultOpenSettings settings = new ZarrDefaultOpenSettings();

		// Verify the default open option is returned
		assertEquals( ZarrDefaultOpenSettings.DEFAULT_OPEN_OPTION, settings.getChosenOpenOption(),
				"Default open option should be IMAGEJ_CUSTOM_RES" );
	}

	@Test
	void testGetChosenOpenOptionReturnsSetOption()
	{
		// Instantiate ZarrDefaultOpenSettings
		ZarrDefaultOpenSettings settings = new ZarrDefaultOpenSettings();

		// Set a new open option
		settings.setChosenOpenOption( ZarrOpenOptions.BDV_MULTI_RESOLUTION );

		// Verify the set option is returned
		assertEquals( ZarrOpenOptions.BDV_MULTI_RESOLUTION, settings.getChosenOpenOption(),
				"Chosen open option should be BDV_MULTI_RESOLUTION after being set explicitly" );
	}

	@Test
	void testSavePreferences()
	{
		try (Context context = new Context())
		{
			PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			// Load settings from preferences for the first time and verify default values
			ZarrDefaultOpenSettings settings = ZarrDefaultOpenSettings.loadSettingsFromPreferences( prefService );
			assertEquals( ZarrDefaultOpenSettings.DEFAULT_OPEN_OPTION, settings.getChosenOpenOption() );
			assertEquals( ZarrDefaultOpenSettings.DEFAULT_MAX_WIDTH, settings.getCustomWidth() );

			// Set custom values and save them to preferences
			settings.setChosenOpenOption( ZarrOpenOptions.IMAGEJ_CUSTOM_RESOLUTION );
			settings.setCustomWidth( 500 );
			settings.saveSettingsToPreferences( prefService );

			// Load settings from preferences again and verify custom values
			ZarrDefaultOpenSettings settings2 = ZarrDefaultOpenSettings.loadSettingsFromPreferences( prefService );
			assertEquals( ZarrOpenOptions.IMAGEJ_CUSTOM_RESOLUTION, settings2.getChosenOpenOption() );
			assertEquals( 500, settings2.getCustomWidth() );
		}
	}
}
