/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.examples.demo;

import java.nio.file.Paths;

import net.imagej.ImageJ;

import ome.zarr.fijiui.plugin.OpenInBDVCommand;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.n5.N5PyramidBackend;

@SuppressWarnings( "all" )
public class PyramidalDatasetDemo
{
	public static void main( String[] args )
	{
		// final String multiscalePath = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0";
		final String multiscalePath = "/Users/hahmann/Data/idr0079_images.zarr/0"; // https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0

		// Show as imagePlus
		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();
		final PyramidContents< ? > contents =
				new N5PyramidBackend().load( Paths.get( multiscalePath ).toUri() );
		PyramidalDataset pyramidalDataset = new PyramidalDataset( imageJ.context(), contents, 0 );
		imageJ.ui().show( pyramidalDataset );

		// Also show the displayed image in BDV
		imageJ.command().run( OpenInBDVCommand.class, true );
	}
}
