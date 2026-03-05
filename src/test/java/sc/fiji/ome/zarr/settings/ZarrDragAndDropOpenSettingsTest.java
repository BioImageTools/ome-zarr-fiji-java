package sc.fiji.ome.zarr.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

/**
 * Unit tests for the {@link ZarrDragAndDropOpenSettings#getOpenBehavior()} method.
 * This method retrieves the current chosen open option for Zarr datasets.
 */
class ZarrDragAndDropOpenSettingsTest
{

	@Test
	void testGetOpenBehaviorSet()
	{
		// Instantiate ZarrDefaultOpenSettings without custom values
		ZarrDragAndDropOpenSettings settings = new ZarrDragAndDropOpenSettings();

		// Verify the default open option is returned
		assertEquals( ZarrDragAndDropOpenSettings.DEFAULT_OPEN_BEHAVIOR, settings.getOpenBehavior(),
				"Default open option should be IMAGEJ_CUSTOM_RES" );
	}

	@Test
	void testGetOpenBehavior()
	{
		// Instantiate ZarrDefaultOpenSettings
		ZarrDragAndDropOpenSettings settings = new ZarrDragAndDropOpenSettings();

		// Set a new open option
		settings.setCurrentChoice( ZarrOpenBehavior.BDV_MULTI_RESOLUTION );

		// Verify the set option is returned
		assertEquals( ZarrOpenBehavior.BDV_MULTI_RESOLUTION, settings.getOpenBehavior(),
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
			ZarrDragAndDropOpenSettings settings = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );
			assertEquals( ZarrDragAndDropOpenSettings.DEFAULT_OPEN_BEHAVIOR, settings.getOpenBehavior() );
			assertEquals( ZarrDragAndDropOpenSettings.DEFAULT_PREFERRED_WIDTH, settings.getPreferredMaxWidth() );

			// Set custom values and save them to preferences
			settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION );
			settings.setPreferredMaxWidth( 500 );
			settings.saveSettingsToPreferences( prefService );

			// Load settings from preferences again and verify custom values
			ZarrDragAndDropOpenSettings settings2 = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );
			assertEquals( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION, settings2.getOpenBehavior() );
			assertEquals( 500, settings2.getPreferredMaxWidth() );
		}
	}
}
