package sc.fiji.ome.zarr.plugins;

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
 * Unit tests for the {@link DragAndDropBehaviorSettings#run()} method.
 * This method is responsible for saving user preferences for Zarr dataset opening behavior.
 */
class DragAndDropBehaviorSettingsTest
{

	@Test
	void testRunUISavesChosenOpenOption() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException,
			NoSuchMethodException, InvocationTargetException
	{
		try (Context context = new Context())
		{
			DragAndDropBehaviorSettings ui = new DragAndDropBehaviorSettings();
			PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			final int customWidth = 500;

			Field prefServiceField = DragAndDropBehaviorSettings.class.getDeclaredField( "prefService" );
			prefServiceField.setAccessible( true );
			prefServiceField.set( ui, prefService );

			Method initMethod = DragAndDropBehaviorSettings.class.getDeclaredMethod( "init" );
			initMethod.setAccessible( true ); // bypasses private visibility
			initMethod.invoke( ui );

			Field defaultZarrOpenBehaviorField = DragAndDropBehaviorSettings.class.getDeclaredField( "defaultZarrOpenBehavior" );
			defaultZarrOpenBehaviorField.setAccessible( true );
			defaultZarrOpenBehaviorField.set( ui, ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION.getDescription() );

			Field preferredWidthField = DragAndDropBehaviorSettings.class.getDeclaredField( "preferredWidth" );
			preferredWidthField.setAccessible( true );
			preferredWidthField.set( ui, customWidth );

			ui.run();

			ZarrDragAndDropOpenSettings settings = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );

			assertEquals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION, settings.getOpenBehavior() );
			assertEquals( customWidth, settings.getPreferredMaxWidth() );

		}
	}
}
