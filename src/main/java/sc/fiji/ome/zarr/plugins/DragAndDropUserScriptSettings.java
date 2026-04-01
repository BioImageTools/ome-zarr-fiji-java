package sc.fiji.ome.zarr.plugins;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import java.io.File;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Drag & Drop User Script Settings", name = "OMEZarrDnDUserScript", headless = true )
public class DragAndDropUserScriptSettings implements Command
{

	@Parameter( label = "Tooltip text:" )
	String scriptTitle;

	@Parameter( label = "Script to be executed:", style = FileWidget.OPEN_STYLE )
	File scriptPath;

	@Parameter
	LogService log;

	@Override
	public void run()
	{
		log.info( "Thanks, memorizing the path: " + scriptPath );
	}
}
