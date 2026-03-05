package sc.fiji.ome.zarr.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

import sc.fiji.ome.zarr.settings.ZarrDragAndDropOpenSettings;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;

/**
 * Unit tests for the {@link ZarrDragAndDropOpenSettingsUI#run()} method.
 * This method is responsible for saving user preferences for Zarr dataset opening behavior.
 */
class ZarrDragAndDropOpenSettingsUITest
{

	@Test
	void testRunUISavesChosenOpenOption() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException,
			NoSuchMethodException, InvocationTargetException
	{
		try (Context context = new Context())
		{
			ZarrDragAndDropOpenSettingsUI ui = new ZarrDragAndDropOpenSettingsUI();
			PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			final int customWidth = 500;

			Field prefServiceField = ZarrDragAndDropOpenSettingsUI.class.getDeclaredField( "prefService" );
			prefServiceField.setAccessible( true );
			prefServiceField.set( ui, prefService );

			Method initMethod = ZarrDragAndDropOpenSettingsUI.class.getDeclaredMethod( "init" );
			initMethod.setAccessible( true ); // bypasses private visibility
			initMethod.invoke( ui );

			Field defaultZarrOpenBehaviorField = ZarrDragAndDropOpenSettingsUI.class.getDeclaredField( "defaultZarrOpenBehavior" );
			defaultZarrOpenBehaviorField.setAccessible( true );
			defaultZarrOpenBehaviorField.set( ui, ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION.getDescription() );

			Field preferredWidthField = ZarrDragAndDropOpenSettingsUI.class.getDeclaredField( "preferredWidth" );
			preferredWidthField.setAccessible( true );
			preferredWidthField.set( ui, customWidth );

			ui.run();

			ZarrDragAndDropOpenSettings settings = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );

			assertEquals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION, settings.getOpenBehavior() );
			assertEquals( customWidth, settings.getPreferredMaxWidth() );

		}
	}
}
