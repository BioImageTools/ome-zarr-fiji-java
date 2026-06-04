package sc.fiji.ome.zarr.pyramid;


import org.scijava.Priority;
import org.scijava.module.Module;
import org.scijava.module.process.AbstractSingleInputPreprocessor;
import org.scijava.module.process.PreprocessorPlugin;
import org.scijava.plugin.Plugin;

@Plugin(type = PreprocessorPlugin.class, priority = Priority.VERY_HIGH)
public class PyramidalPreprocessor extends AbstractSingleInputPreprocessor {

	@Override
	public void process(final Module module) {
		final String input = getSingleInput(module, Pyramidal.class);
		if (input != null) {
			module.setInput(input, new Pyramidal() {{
				System.out.println("PyramidalPreprocessor.instance initializer");
			}});
			module.resolveInput(input);

		}
	}
}
