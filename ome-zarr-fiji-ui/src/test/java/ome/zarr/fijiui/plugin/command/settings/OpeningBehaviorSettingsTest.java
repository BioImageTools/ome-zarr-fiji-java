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
package ome.zarr.fijiui.plugin.command.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

import ome.zarr.fijiui.open.openers.ImageJHighestResolutionOpener;
import ome.zarr.fiji.open.ZarrOpenerService;
import ome.zarr.fijiui.open.options.ZarrOpeningSettings;
import ome.zarr.fijiui.open.options.ZarrBackend;

/**
 * Unit tests for the {@link OpeningBehaviorSettings#run()} method.
 * This method is responsible for saving user preferences for Zarr dataset opening behavior.
 */
class OpeningBehaviorSettingsTest
{

	@Test
	void testRunUISavesChosenOpenOption() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException,
			NoSuchMethodException, InvocationTargetException
	{
		try (Context context = new Context())
		{
			OpeningBehaviorSettings ui = new OpeningBehaviorSettings();
			PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			final int customWidth = 500;

			setField( ui, "prefService", prefService );
			setField( ui, "openerService", context.getService( ZarrOpenerService.class ) );

			Method initMethod = OpeningBehaviorSettings.class.getDeclaredMethod( "init" );
			initMethod.setAccessible( true ); // bypasses private visibility
			initMethod.invoke( ui );

			setField( ui, "defaultOpener", labelOf( context, ImageJHighestResolutionOpener.NAME ) );
			setField( ui, "preferredWidth", customWidth );

			ui.run();

			ZarrOpeningSettings settings = ZarrOpeningSettings.loadSettingsFromPreferences( prefService );

			assertEquals( ImageJHighestResolutionOpener.NAME, settings.getOpenerName() );
			assertEquals( customWidth, settings.getPreferredMaxWidth() );
		}
	}

	@Test
	void testBackendDescriptionsReturnsAllDescriptionsInDeclarationOrder()
	{
		final List< String > expected =
				Arrays.stream( ZarrBackend.values() ).map( ZarrBackend::getDescription ).collect( Collectors.toList() );
		final List< String > actual = OpeningBehaviorSettings.backendDescriptions( ZarrBackend.values() );
		assertEquals( expected, actual );
		// Anchored explicit values: these are the strings the settings dialog
		// presents to the user, so a stealth rename should fail this test.
		assertEquals( Arrays.asList( "N5", "zarr-java" ), actual );
	}

	@Test
	void testBackendDescriptionsIsEmptyForEmptyInput()
	{
		assertEquals( Collections.emptyList(), OpeningBehaviorSettings.backendDescriptions( new ZarrBackend[ 0 ] ) );
	}

	/** The choice the dialog shows for the opener registered under {@code name}. */
	private static String labelOf( final Context context, final String name )
	{
		final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
		return ZarrOpenerService.labelOf( openerService.getOpenerInfo( name ) );
	}

	private static void setField( final OpeningBehaviorSettings ui, final String fieldName, final Object value )
			throws NoSuchFieldException, IllegalAccessException
	{
		final Field field = OpeningBehaviorSettings.class.getDeclaredField( fieldName );
		field.setAccessible( true );
		field.set( ui, value );
	}
}
