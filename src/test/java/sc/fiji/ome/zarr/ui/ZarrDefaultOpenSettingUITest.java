package sc.fiji.ome.zarr.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

import sc.fiji.ome.zarr.util.ZarrDefaultOpenSettings;
import sc.fiji.ome.zarr.util.ZarrOpenChoice;

/**
 * Unit tests for the {@link ZarrDefaultOpenSettingUI#run()} method.
 * This method is responsible for saving user preferences for Zarr dataset opening behavior.
 */
class ZarrDefaultOpenSettingUITest
{

	@Test
	void testRunUISavesChosenOpenOption() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException,
			NoSuchMethodException, InvocationTargetException
	{
		try (Context context = new Context())
		{
			ZarrDefaultOpenSettingUI ui = new ZarrDefaultOpenSettingUI();
			PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			final int customWidth = 500;

			Field prefServiceField = ZarrDefaultOpenSettingUI.class.getDeclaredField( "prefService" );
			prefServiceField.setAccessible( true );
			prefServiceField.set( ui, prefService );

			Method initMethod = ZarrDefaultOpenSettingUI.class.getDeclaredMethod( "init" );
			initMethod.setAccessible( true ); // bypasses private visibility
			initMethod.invoke( ui );

			Field defaultZarrOpenOptionField = ZarrDefaultOpenSettingUI.class.getDeclaredField( "defaultZarrOpenOption" );
			defaultZarrOpenOptionField.setAccessible( true );
			defaultZarrOpenOptionField.set( ui, ZarrOpenChoice.IMAGEJ_HIGHEST_RESOLUTION.getDescription() );

			Field customWidthField = ZarrDefaultOpenSettingUI.class.getDeclaredField( "customWidth" );
			customWidthField.setAccessible( true );
			customWidthField.set( ui, customWidth );

			ui.run();

			ZarrDefaultOpenSettings settings = ZarrDefaultOpenSettings.loadSettingsFromPreferences( prefService );

			assertEquals( ZarrOpenChoice.IMAGEJ_HIGHEST_RESOLUTION, settings.getChosenOpenOption() );
			assertEquals( customWidth, settings.getPreferredMaxWidth() );

		}
	}
}
