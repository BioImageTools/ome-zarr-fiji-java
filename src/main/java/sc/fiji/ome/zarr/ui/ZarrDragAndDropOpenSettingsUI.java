package sc.fiji.ome.zarr.ui;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.fiji.ome.zarr.settings.ZarrDragAndDropOpenSettings;
import sc.fiji.ome.zarr.settings.ZarrOpenBehavior;

/**
 * A FIJI/ImageJ command to select how to open a Zarr dataset.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Zarr Drag And Drop Open Settings", initializer = "init" )
public class ZarrDragAndDropOpenSettingsUI extends DynamicCommand
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final Integer WIDTH = 20;

	@SuppressWarnings( "all" )
	@Parameter
	private PrefService prefService;

	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String infoMessage = "<html>"
			+ "<body width=" + WIDTH + "cm align=left>"
			+ "Configure how OME-Zarr datasets are opened when dragging them into Fiji.<br>"
			+ "Choose a default behavior and optionally limit the preferred resolution."
			+ "</body>"
			+ "</html>";

	@SuppressWarnings( "all" )
	@Parameter( label = "Default zarr drag and drop open behavior", description = "Chose the behavior if you drag and drop a zarr image folder into Fiji", initializer = "initZarrOpenBehaviors" )
	private String defaultZarrOpenBehavior;

	@SuppressWarnings( "all" )
	@Parameter( label = "Preferred width for 'matching resolution' choice" )
	private int preferredWidth;

	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String preferredWidthInfo = "<html>"
			+ "<body width=" + WIDTH + "cm align=left>"
			+ "For the 'matching resolution' behavior, set a preferred maximum width.<br>"
			+ "Fiji will open the highest available resolution whose width is below this value.<br>"
			+ "If no such resolution exists, the image will not be opened."
			+ "</body>"
			+ "</html>";

	private ZarrDragAndDropOpenSettings settings;

	@Override
	public void run()
	{
		settings.setCurrentChoice( ZarrOpenBehavior.getByDescription( defaultZarrOpenBehavior ) );
		settings.setPreferredMaxWidth( preferredWidth );
		logger.debug( "Now saving Zarr Drag and Drop open settings to preferences. Behavior: {}, preferredWidth: {}",
				settings.getOpenBehavior(),
				preferredWidth );
		settings.saveSettingsToPreferences( prefService );
	}

	@SuppressWarnings( "unused" )
	private void init()
	{
		settings = ZarrDragAndDropOpenSettings.loadSettingsFromPreferences( prefService );
		defaultZarrOpenBehavior = settings.getOpenBehavior().getDescription();
		preferredWidth = settings.getPreferredMaxWidth();
	}

	@SuppressWarnings( "unused" )
	private void initZarrOpenBehaviors()
	{
		getInfo().getMutableInput( "defaultZarrOpenBehavior", String.class ).setChoices( enumNamesAsList( ZarrOpenBehavior.values() ) );
	}

	static List< String > enumNamesAsList( final ZarrOpenBehavior[] values )
	{
		return Arrays.stream( values ).map( ZarrOpenBehavior::getDescription ).collect( Collectors.toList() );
	}
}
