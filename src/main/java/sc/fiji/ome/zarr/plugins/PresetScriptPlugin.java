package sc.fiji.ome.zarr.plugins;

import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;
import org.scijava.widget.FileWidget;
import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>NGFF OME-Zarr>Preset DragAndDrop User Script",
		name = "DnDUserScript", headless = true)
public class PresetScriptPlugin implements Command {

	@Parameter(label = "Tooltip text:")
	String line;

	@Parameter(label = "Script to be executed:", style = FileWidget.OPEN_STYLE)
	File scriptPath;

	@Parameter
	LogService log;

	@Override
	public void run() {
		log.info("Thanks, memorizing it now...");
	}
}
