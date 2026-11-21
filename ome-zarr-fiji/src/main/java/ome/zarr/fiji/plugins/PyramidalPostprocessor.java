/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ome.zarr.fiji.plugins;

import org.scijava.Priority;
import org.scijava.display.DisplayPostprocessor;
import org.scijava.display.DisplayService;
import org.scijava.module.Module;
import org.scijava.module.ModuleItem;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.read.FirstPlanePrefetcher;

/**
 * Runs {@link FirstPlanePrefetcher} on each {@link PyramidalDataset} output that the
 * {@link DisplayPostprocessor}, which runs right after, is going to show. The
 * conditions are the same as there, so an output a script captures is not loaded.
 */
@Plugin( type = PostprocessorPlugin.class, priority = Priority.VERY_LOW + 1 )
public class PyramidalPostprocessor extends AbstractPostprocessorPlugin
{

	@Parameter( required = false )
	private DisplayService displayService;

	@Override
	public void process( final Module module )
	{
		if ( displayService == null )
			return;
		for ( final ModuleItem< ? > output : module.getInfo().outputs() )
		{
			final Object value = output.getValue( module );
			if ( value instanceof PyramidalDataset && !module.isOutputResolved( output.getName() )
					&& displayService.getDisplays( value ).isEmpty() )
				FirstPlanePrefetcher.run( ( PyramidalDataset ) value );
		}
	}
}
