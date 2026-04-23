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
