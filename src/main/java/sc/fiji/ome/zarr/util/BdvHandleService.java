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

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;

@Plugin( type = SciJavaService.class )
public class BdvHandleService extends AbstractService implements SciJavaService
{
	@Parameter
	private LogService logService;

	//TODO: could be a list of recentlyOpenedBdvs
	private BdvStackSource< ? > lastStartedBdv = null;

	// Called when service is initialized
	@Override
	public void initialize()
	{
		logService.trace( "BdvHandleService initialized" );
		lastStartedBdv = null;
	}

	/**
	 * @return True if the service is aware of an opened BDV window.
	 */
	public boolean isLastBdvStillAlive()
	{
		//the function intentionally looses the reference
		//on the BDV at the first occasion...

		//is there any BDV registered at all?
		if ( lastStartedBdv == null )
			return false;

		//can a reference to a functional BDV window be retrieved?
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

		//is this "functional" BDV still up on the screen?
		if ( panel.isValid() )
			return true;

		lastStartedBdv = null;
		return false;
	}

	/**
	 * @return A handle on an opened BDV window, if the service is aware of any.
	 *         Else null.
	 */
	public BdvStackSource< ? > returnBdvStackOrNull()
	{
		return isLastBdvStillAlive() ? lastStartedBdv : null;
	}

	/**
	 * Always opens a new BDV, and remembers a handle to it (so that
	 * it can be manipulated later again).
	 * <br>
	 * @param img Image to be displayed
	 * @param name Name of the image
	 */
	public void openNewBdv( RandomAccessibleInterval< ? > img, String name )
	{
		//TODO for more generic sources....
		lastStartedBdv = BdvFunctions.show( img, name );
	}

	/**
	 * Attempts to add another source into the most recently opened BDV.
	 * If the service is not aware of any BDV, it just opens a new one
	 * (and again remembers a handle to it).
	 * <br>
	 * @param img Image to be added
	 * @param name Name of the image
	 */
	public void addToLastOrInNewBdv( RandomAccessibleInterval< ? > img, String name )
	{
		//TODO for more generic sources....
		if ( isLastBdvStillAlive() )
		{
			BdvFunctions.show( img, name, BdvOptions.options().addTo( lastStartedBdv ) );
		}
		else
		{
			openNewBdv( img, name );
		}
	}
}
