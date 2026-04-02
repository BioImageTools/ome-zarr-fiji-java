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
