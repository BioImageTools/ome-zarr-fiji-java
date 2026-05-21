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

import net.imagej.Dataset;

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;

import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

@Plugin( type = SciJavaService.class )
public class BdvFocusService extends AbstractService implements SciJavaService
{
	@Parameter
	private LogService logService;

	private PyramidalDataset< ? > activePyramidalDataset = null;

	@Override
	public void initialize()
	{
		logService.trace( "BdvFocusService initialized" );
		activePyramidalDataset = null;
	}

	/**
	 * Records {@code dataset} as the currently focused BDV dataset.
	 * Called by a {@code WindowFocusListener} whenever a BDV window gains focus,
	 * and immediately after a new BDV window is opened.
	 */
	public void notifyWindowFocused( final PyramidalDataset< ? > dataset )
	{
		activePyramidalDataset = dataset;
	}

	/**
	 * Clears the active dataset when its BDV window is closed.
	 * Has no effect if {@code dataset} is not the currently active one.
	 */
	public void notifyWindowClosed( final PyramidalDataset< ? > dataset )
	{
		if ( activePyramidalDataset == dataset )
			activePyramidalDataset = null;
	}

	/**
	 * Returns {@code dataset} if non-null, otherwise falls back to the active BDV dataset.
	 * Intended for use in command {@code initialize()} methods to resolve a {@code Dataset}
	 * parameter that was not filled by the standard IJ2 active-display mechanism.
	 */
	public Dataset resolveDataset( final Dataset dataset )
	{
		return dataset != null ? dataset : activePyramidalDataset;
	}
}