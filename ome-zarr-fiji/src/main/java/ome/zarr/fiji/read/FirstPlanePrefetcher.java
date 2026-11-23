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
package ome.zarr.fiji.read;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.scijava.app.StatusService;

import net.imagej.axis.Axes;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.img.cell.AbstractCellImg;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.imglib2.util.CellPrefetcher;

/**
 * Loads the chunks of the plane an ImageJ window shows first, in parallel and with
 * a progress bar that counts chunks.
 * <p>
 * Showing a dataset reads that plane three times: auto-contrast in parallel, then
 * the ImageJ2 projector and ImageJ 1 one chunk after another. Without this class
 * the download happens inside auto-contrast, whose "Computing min/max" bar counts
 * its tasks, not chunks. These all wait for the download, so the bar stays near
 * 0 % for most of it and then jumps, and contrast looks like the slow part.
 * Loaded beforehand, all three reads come from the cache.
 * <p>
 * Auto-contrast is skipped for a dataset with OMERO windows. The serial readers
 * would then download the plane on their own, several times slower.
 */
public final class FirstPlanePrefetcher
{
	private FirstPlanePrefetcher()
	{}

	/**
	 * Loads the first plane of every channel of {@code dataset}, reporting each
	 * chunk to the {@link StatusService}. Does nothing for an image that is not a
	 * cell image.
	 *
	 * @throws RuntimeException what loading a chunk threw
	 */
	public static void run( final PyramidalDataset dataset )
	{
		final Img< ? > img = dataset.getImgPlus().getImg();
		if ( !( img instanceof AbstractCellImg ) )
			return;
		final StatusService statusService = dataset.getContext().getService( StatusService.class );
		final String message = "Loading " + dataset.getName();
		// Not the ThreadService's executor: it starts a thread per task, i.e. one download per chunk.
		final ExecutorService executor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
		try
		{
			CellPrefetcher.prefetch( ( AbstractCellImg< ?, ?, ?, ? > ) img,
					firstPlane( img, dataset.dimensionIndex( Axes.CHANNEL ) ), executor,
					( loaded, total ) -> statusService.showStatus( loaded, total, message ) );
		}
		catch ( final InterruptedException e )
		{
			Thread.currentThread().interrupt();
		}
		finally
		{
			executor.shutdownNow();
			statusService.clearStatus();
		}
	}

	/** The first plane of every channel: all of X, Y and channel, the first position along every other axis. */
	private static FinalInterval firstPlane( final Img< ? > img, final int channelDimension )
	{
		final long[] min = img.minAsLongArray();
		final long[] max = img.minAsLongArray();
		for ( int d = 0; d < max.length; d++ )
			if ( d < 2 || d == channelDimension )
				max[ d ] = img.max( d );
		return new FinalInterval( min, max );
	}
}
