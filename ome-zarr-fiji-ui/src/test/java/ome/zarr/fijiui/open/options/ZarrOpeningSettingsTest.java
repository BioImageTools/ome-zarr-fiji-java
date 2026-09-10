/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.fijiui.open.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

import ome.zarr.fijiui.open.openers.BdvMultiResolutionOpener;
import ome.zarr.fijiui.open.openers.ImageJPreferredResolutionOpener;

/**
 * Unit tests for {@link ZarrOpeningSettings#getOpenerName()}, which reports
 * which {@link ome.zarr.fiji.open.ZarrOpener} the user chose.
 */
class ZarrOpeningSettingsTest
{

	@Test
	void testGetOpenerNameIsUnsetByDefault()
	{
		ZarrOpeningSettings settings = new ZarrOpeningSettings();

		// Nothing configured: it is the opener service, not the settings, that
		// decides what an unconfigured user gets.
		assertNull( settings.getOpenerName(), "A fresh settings object should not name an opener" );
	}

	@Test
	void testGetOpenerName()
	{
		ZarrOpeningSettings settings = new ZarrOpeningSettings();

		settings.setOpenerName( BdvMultiResolutionOpener.NAME );

		assertEquals( BdvMultiResolutionOpener.NAME, settings.getOpenerName(),
				"Chosen opener should be the BDV one after being set explicitly" );
	}

	@Test
	void testSavePreferences()
	{
		try (Context context = new Context())
		{
			PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			// Load settings from preferences for the first time and verify default values
			ZarrOpeningSettings settings = ZarrOpeningSettings.loadSettingsFromPreferences( prefService );
			assertNull( settings.getOpenerName() );
			assertEquals( ZarrOpeningSettings.DEFAULT_PREFERRED_WIDTH, settings.getPreferredMaxWidth() );

			// Set custom values and save them to preferences
			settings.setOpenerName( ImageJPreferredResolutionOpener.NAME );
			settings.setPreferredMaxWidth( 500 );
			settings.saveSettingsToPreferences( prefService );

			// Load settings from preferences again and verify custom values
			ZarrOpeningSettings settings2 = ZarrOpeningSettings.loadSettingsFromPreferences( prefService );
			assertEquals( ImageJPreferredResolutionOpener.NAME, settings2.getOpenerName() );
			assertEquals( 500, settings2.getPreferredMaxWidth() );
		}
	}
}
