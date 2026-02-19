/*-
 * #%L
 * Fiji viewer for MoBIE projects
 * %%
 * Copyright (C) 2018 - 2022 EMBL
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
package sc.fiji.ome.zarr.pyramid;

import net.imagej.Dataset;
import net.imglib2.util.Cast;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;

@Plugin( type = Command.class )
public class OpenInBDVCommand implements Command
{
	@Parameter
	LogService logService;

	@Parameter
	public Dataset dataset;

	@Override
	public void run()
	{
		if ( dataset instanceof PyramidalDataset )
		{
			logService.log( 0, "Opening " + dataset.getClass().toString() + " in BDV..." );
			PyramidalDataset< ? > pyramidalDataset = Cast.unchecked( dataset );
			BdvFunctions.show( pyramidalDataset.asSources(), pyramidalDataset.numTimepoints(),
					BdvOptions.options().frameTitle( pyramidalDataset.getName() ) );
		}
		else
		{
			logService.error( "Cannot display datasets of type " + dataset.getClass().toString() + " in BDV." );
		}
	}
}
