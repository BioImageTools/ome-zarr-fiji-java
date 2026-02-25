package sc.fiji.ome.zarr.ui;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.fiji.ome.zarr.util.ZarrDefaultOpenSettings;
import sc.fiji.ome.zarr.util.ZarrOpenOptions;

/**
 * A FIJI/ImageJ command to select how to open a Zarr dataset.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Zarr Default Open Options", initializer = "init" )
public class ZarrDefaultOpenSettingUI extends DynamicCommand
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@SuppressWarnings( "all" )
	@Parameter
	private PrefService prefService;

	@SuppressWarnings( "all" )
	@Parameter( label = "Default Zarr Open Option", description = "Chose what will happen if you drag and drop a zarr image folder into Fiji", initializer = "initZarrOpenOptionsChoices" )
	private String defaultZarrOpenOption;

	@SuppressWarnings( "all" )
	@Parameter( label = "Default Width for custom resolution", description = "For the option custom resolution a desired maximum width can be chosen" )
	private int customWidth;

	private ZarrDefaultOpenSettings settings;

	@Override
	public void run()
	{
		settings.setChosenOpenOption( ZarrOpenOptions.getByDescription( defaultZarrOpenOption ) );
		settings.setCustomWidth( customWidth );
		logger.debug( "Now saving Zarr Default Open Options to preferences. Option: {}, customWidth: {}", settings.getChosenOpenOption(),
				customWidth );
		settings.saveSettingsToPreferences( prefService );
	}

	@SuppressWarnings( "unused" )
	private void init()
	{
		settings = ZarrDefaultOpenSettings.loadSettingsFromPreferences( prefService );
		defaultZarrOpenOption = settings.getChosenOpenOption().getDescription();
		customWidth = settings.getCustomWidth();
	}

	@SuppressWarnings( "unused" )
	private void initZarrOpenOptionsChoices()
	{
		getInfo().getMutableInput( "defaultZarrOpenOption", String.class ).setChoices( enumNamesAsList( ZarrOpenOptions.values() ) );
	}

	static List< String > enumNamesAsList( final ZarrOpenOptions[] values )
	{
		return Arrays.stream( values ).map( ZarrOpenOptions::getDescription ).collect( Collectors.toList() );
	}
}
