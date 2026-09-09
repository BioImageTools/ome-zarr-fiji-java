package ome.zarr.fiji.plugins;

import ome.zarr.fiji.Pyramidal;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.script.ScriptService;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;

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
