package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

/**
 * Unit tests for the {@link ZarrDragAndDropOpenSettings#getCurrentChoice()} method.
 * This method retrieves the current chosen open option for Zarr datasets.
 */
class ZarrDragAndDropOpenSettingsTest
{

	@Test
	void testGetCurrentChoiceSet()
	{
		// Instantiate ZarrDefaultOpenSettings without custom values
		ZarrDragAndDropOpenSettings settings = new ZarrDragAndDropOpenSettings();

		// Verify the default open option is returned
		assertEquals( ZarrDragAndDropOpenSettings.DEFAULT_OPEN_CHOICE, settings.getCurrentChoice(),
				"Default open option should be IMAGEJ_CUSTOM_RES" );
	}

	@Test
	void testGetCurrentChoice()
	{
		// Instantiate ZarrDefaultOpenSettings
		ZarrDragAndDropOpenSettings settings = new ZarrDragAndDropOpenSettings();

		// Set a new open option
		settings.setCurrentChoice( ZarrOpenChoice.BDV_MULTI_RESOLUTION );

		// Verify the set option is returned
		assertEquals( ZarrOpenChoice.BDV_MULTI_RESOLUTION, settings.getCurrentChoice(),
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
			assertEquals( ZarrDragAndDropOpenSettings.DEFAULT_OPEN_CHOICE, settings.getCurrentChoice() );
			assertEquals( ZarrDragAndDropOpenSettings.DEFAULT_PREFERRED_WIDTH, settings.getPreferredMaxWidth() );

			// Set custom values and save them to preferences
			settings.setCurrentChoice( ZarrOpenChoice.IMAGEJ_CUSTOM_RESOLUTION );
			settings.setPreferredMaxWidth( 500 );
			settings.saveSettingsToPreferences( prefService );

			// Load settings from preferences again and verify custom values
			ZarrDragAndDropOpenSettings settings2 = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );
			assertEquals( ZarrOpenChoice.IMAGEJ_CUSTOM_RESOLUTION, settings2.getCurrentChoice() );
			assertEquals( 500, settings2.getPreferredMaxWidth() );
		}
	}
}
