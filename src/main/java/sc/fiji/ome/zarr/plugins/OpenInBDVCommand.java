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
package sc.fiji.ome.zarr.plugins;

import net.imagej.Dataset;
import net.imglib2.util.Cast;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.util.BdvFocusService;
import sc.fiji.ome.zarr.util.BdvUtils;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Current OME-Zarr Image in BigDataViewer" )
public class OpenInBDVCommand implements Command
{
	@Parameter
	LogService logService;

	@Parameter( required = false )
	private BdvFocusService bdvFocusService;

	@Parameter
	public Dataset dataset;

	@Override
	public void run()
	{
		final Dataset resolved = bdvFocusService != null ? bdvFocusService.resolveDataset( dataset ) : dataset;
		if ( !( resolved instanceof PyramidalDataset ) )
		{
			logService.error( "Cannot open in BDV: no OME-Zarr dataset is currently active." );
			return;
		}
		final PyramidalDataset< ? > pyramidalDataset = Cast.unchecked( resolved );
		final PyramidalDataset< ? > bdvDataset = pyramidalDataset.getPyramidal5DImageData().asPyramidalDataset();
		BdvUtils.showBdvAndRegisterDataset( bdvDataset, bdvFocusService );
	}
}
