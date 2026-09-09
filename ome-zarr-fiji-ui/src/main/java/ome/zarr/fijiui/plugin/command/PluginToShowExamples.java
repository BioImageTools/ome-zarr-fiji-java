package ome.zarr.fijiui.plugin.command;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Examples" )
public class PluginToShowExamples implements Command
{
	@Parameter( type = ItemIO.OUTPUT )
	String url_with_examples;

	@Override
	public void run()
	{
		url_with_examples = "Please, open this URL:\n https://www.fi.muni.cz/~xulman/files/Fiji-OME-Zarr-macros/";
	}
}
