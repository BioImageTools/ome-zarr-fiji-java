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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.io.location.FileLocation;
import org.scijava.prefs.PrefService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import sc.fiji.ome.zarr.ui.DnDActionChooser;
import sc.fiji.ome.zarr.settings.ZarrDragAndDropOpenSettings;
import sc.fiji.ome.zarr.util.ZarrOpenActions;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class DnDHandlerPluginTest
{
	@Test
	void testOpen() throws URISyntaxException, IOException
	{

		try (Context context = new Context())
		{
			PrefService prefService = context.getService( PrefService.class );
			ZarrDragAndDropOpenSettings settings = new ZarrDragAndDropOpenSettings();
			Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/" );
			FileLocation fileLocation = new FileLocation( path.toUri() );
			ZarrOpenActions actionsMock = mock( ZarrOpenActions.class );
			DnDActionChooser actionChooserMock = mock( DnDActionChooser.class );
			DnDHandlerPlugin dnDHandlerPlugin = new DnDHandlerPlugin()
			{
				@Override
				protected ZarrOpenActions createZarrOpenActions( final Path path, final Context context,
						final ZarrDragAndDropOpenSettings settings )
				{
					return actionsMock;
				}

				@Override
				protected DnDActionChooser createDnDActionChooser( final Context context, final ZarrOpenActions actions )
				{
					return actionChooserMock;
				}
			};
			dnDHandlerPlugin.setContext( context );

			settings.setCurrentChoice( ZarrOpenBehavior.BDV_MULTI_RESOLUTION );
			settings.saveSettingsToPreferences( prefService );
			dnDHandlerPlugin.open( fileLocation );
			verify( actionsMock ).openBDVWithImage();

			settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION );
			settings.saveSettingsToPreferences( prefService );
			dnDHandlerPlugin.open( fileLocation );
			verify( actionsMock, times( 1 ) ).openIJWithImage();

			settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION );
			settings.saveSettingsToPreferences( prefService );
			dnDHandlerPlugin.open( fileLocation );
			verify( actionsMock, times( 2 ) ).openIJWithImage();

			settings.setCurrentChoice( ZarrOpenBehavior.SHOW_SELECTION_DIALOG );
			settings.saveSettingsToPreferences( prefService );
			dnDHandlerPlugin.open( fileLocation );
			verify( actionChooserMock ).showDialog();
		}
	}
}
