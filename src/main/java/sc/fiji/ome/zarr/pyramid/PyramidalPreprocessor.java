package sc.fiji.ome.zarr.pyramid;

import org.scijava.module.Module;
import org.scijava.module.process.AbstractSingleInputPreprocessor;
import org.scijava.module.process.PreprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.ome.zarr.util.BdvFocusService;

@Plugin(type = PreprocessorPlugin.class)
public class PyramidalPreprocessor extends AbstractSingleInputPreprocessor {

	@Parameter
	private BdvFocusService pyramidalService;

	@Override
	public void process(final Module module) {
		final String input = getSingleInput(module, Pyramidal.class);
		if (input != null) {
			module.setInput(input, pyramidalService.getActivePyramidal());
			module.resolveInput(input);
		}
	}
}
