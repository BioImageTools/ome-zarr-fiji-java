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
package sc.fiji.ome.zarr.util;

import net.imglib2.RandomAccessibleInterval;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;

/**
 * Manages a single {@link BdvStackSource} handle — remembers the most recently
 * opened BDV window and can add sources to it. Extracted from the original
 * {@code BdvHandleService} implementation for use in demos and manual tests.
 */
public class BdvHandleService
{
	private BdvStackSource< ? > lastStartedBdv = null;

	public boolean isLastBdvStillAlive()
	{
		if ( lastStartedBdv == null )
			return false;
		ViewerPanel panel;
		try
		{
			panel = lastStartedBdv.getBdvHandle().getViewerPanel();
		}
		catch ( Exception e )
		{
			lastStartedBdv = null;
			return false;
		}
		if ( panel.isValid() )
			return true;
		lastStartedBdv = null;
		return false;
	}

	public void openNewBdv( final RandomAccessibleInterval< ? > img, final String name )
	{
		lastStartedBdv = BdvFunctions.show( img, name );
	}

	public void addToLastOrInNewBdv( final RandomAccessibleInterval< ? > img, final String name )
	{
		if ( isLastBdvStillAlive() )
			BdvFunctions.show( img, name, BdvOptions.options().addTo( lastStartedBdv ) );
		else
			openNewBdv( img, name );
	}
}
