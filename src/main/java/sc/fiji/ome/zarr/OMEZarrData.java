/*-
 * #%L
 * Expose the Imaris XT interface as an ImageJ2 service backed by ImgLib2.
 * %%
 * Copyright (C) 2019 - 2021 Bitplane AG
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
package sc.fiji.ome.zarr;

import bdv.util.volatiles.SharedQueue;
import org.scijava.Context;

import javax.annotation.Nullable;

public class OMEZarrData
{
	private final Context context;
	private final String omeZarrPath;
	private String[] multiscalePaths;
	private final SharedQueue queue;

	public OMEZarrData(
			final String omeZarrPath,
			final Context context, // Scijava context
			@Nullable final SharedQueue queue )
	{
		this.context = context;
		this.omeZarrPath = omeZarrPath;
		this.multiscalePaths = fetchMultiscalePaths( omeZarrPath );
		this.queue = queue;
	}

	private String[] fetchMultiscalePaths( String omeZarrPath )
	{
		// FIXME JOHN
		return new String[ 0 ];
	}

	/**
	 * Default for creating something simple,
	 * from the potentially complex {@code OMEZarrData},
	 * that we can display in ImageJ
	 *
	 * @return
	 */
	public Pyramidal5DImageData createDefaultImage()
	{
		// Create a Java object from the first
		// multiscale metadata in the container.
		final MultiscaleImage< ?, ? > multiscaleImage = new MultiscaleImage<>( multiscalePaths[ 0 ], queue );

		// Convert it to something that ImageJ
		// can understand.
		// Note: a {@code Dataset} is the
		// "primary image data structure in ImageJ".
		return new DefaultPyramidal5DImageData<>(
				context,
				multiscalePaths[ 0 ],
				multiscaleImage );
	}

	// TODO one could use this to create a GUI
	public String[] getMultiscalePaths()
	{
		return multiscalePaths;
	}
}
