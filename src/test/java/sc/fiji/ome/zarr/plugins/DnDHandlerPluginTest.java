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
			Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example/" );
			FileLocation fileLocation = new FileLocation( path.toUri() );
			ZarrOpenActions actionsMock = mock( ZarrOpenActions.class );
			DnDActionChooser actionChooserMock = mock( DnDActionChooser.class );
			DnDHandlerPlugin dnDHandlerPlugin = new DnDHandlerPlugin()
			{
				@Override
				protected ZarrOpenActions createZarrOpenActions( final Path path, final Context context, final Integer preferredWidth )
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
