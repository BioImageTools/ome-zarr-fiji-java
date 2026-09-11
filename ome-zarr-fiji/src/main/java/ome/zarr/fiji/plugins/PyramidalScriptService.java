package ome.zarr.fiji.plugins;

import ome.zarr.fiji.Pyramidal;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.script.ScriptService;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;

/**
 * Registers {@link Pyramidal} as a script alias, so scripts can declare a parameter as
 * {@code #@ Pyramidal image} instead of spelling out the fully qualified class name.
 */
@Plugin( type = Service.class )
public class PyramidalScriptService extends AbstractService implements SciJavaService
{

	@Parameter
	private ScriptService scriptService;

	@Override
	public void initialize()
	{
		scriptService.addAlias( Pyramidal.class );
	}
}
