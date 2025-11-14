package sc.fiji.ome.zarr.ui;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.widget.FileWidget;
import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>NGFF OME-Zarr>Preset DragAndDrop User Script",
		name = "DndUserScript", headless = true)
public class PluginPresetDndScript implements Command {

	@Parameter(label = "Button first line text (max 10 chars):")
	String line1;

	@Parameter(label = "Button second line text (max 10 chars):")
	String line2;

	@Parameter(label = "Script to be executed:", style = FileWidget.OPEN_STYLE)
	File scriptPath;

	@Parameter
	LogService log;

	@Override
	public void run() {
		log.info("Thanks, memorizing it now...");
	}
}
