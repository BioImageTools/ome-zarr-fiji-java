/*-
 * #%L
 * OME-Zarr reader based on imglib2
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
package ome.zarr.imglib2.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.iterator.LocalizingIntervalIterator;
import net.imglib2.util.Intervals;

/**
 * Loads the cells of a cell image that intersect an interval, in parallel, and waits until all
 * are loaded.
 * <p>
 * Useful before code that reads a region one cell after another from a slow loader, such as
 * remote storage. The cells are loaded in parallel on the given executor, before such operations, which then can read them from the cache quickly.
 * Thus, the slow part (loading the cells) is done in parallel, and the follow-up code may run at full speed.
 * <p>
 * Progress is reported in the order the cells finish, so it advances steadily even if one cell
 * is slow.
 */
public final class CellPrefetcher
{
	private CellPrefetcher()
	{}

	/** Told about each loaded cell. */
	@FunctionalInterface
	public interface ProgressListener
	{
		/**
		 * Called on the waiting thread after each loaded cell.
		 *
		 * @param loaded the number of cells loaded so far
		 * @param total the number of cells to load
		 */
		void cellLoaded( int loaded, int total );
	}

	/**
	 * Loads every cell of {@code img} that intersects {@code interval} on {@code executor} and
	 * waits until all are loaded.
	 *
	 * @throws InterruptedException if interrupted while waiting. The loads not yet done are
	 *   canceled.
	 * @throws RuntimeException what loading a cell threw
	 */
	public static void prefetch( final AbstractCellImg< ?, ?, ?, ? > img, final Interval interval,
			final ExecutorService executor, final ProgressListener progress ) throws InterruptedException
	{
		final Interval region = Intervals.intersect( img, interval );
		if ( Intervals.isEmpty( region ) )
			return;
		final FinalInterval cells = cellsOf( img.getCellGrid(), region );
		final int total = Math.toIntExact( Intervals.numElements( cells ) );
		final CompletionService< Void > completionService = new ExecutorCompletionService<>( executor );
		final List< Future< Void > > futures = new ArrayList<>( total );
		try
		{
			final LocalizingIntervalIterator position = new LocalizingIntervalIterator( cells );
			while ( position.hasNext() )
			{
				position.fwd();
				final long[] cell = new long[ cells.numDimensions() ];
				position.localize( cell );
				futures.add( completionService.submit( () -> {
					img.getCells().randomAccess().setPositionAndGet( cell );
					return null;
				} ) );
			}
			// Waits on the completion service, not on the futures in list order: a slow early cell
			// would otherwise hold back reporting the progress of all cells after it, even those that already finished loading.
			int loaded = 0;
			while ( loaded < total )
			{
				completionService.take().get(); // waits for whichever cell finishes next
				loaded++;
				progress.cellLoaded( loaded, total );
			}
		}
		catch ( final ExecutionException e )
		{
			throw failure( e.getCause() );
		}
		finally
		{
			futures.forEach( future -> future.cancel( true ) );
		}
	}

	/** The cell-grid positions of the cells that intersect {@code region}. */
	private static FinalInterval cellsOf( final CellGrid grid, final Interval region )
	{
		final long[] min = new long[ region.numDimensions() ];
		final long[] max = new long[ region.numDimensions() ];
		grid.getCellPosition( region.minAsLongArray(), min );
		grid.getCellPosition( region.maxAsLongArray(), max );
		return new FinalInterval( min, max );
	}

	private static RuntimeException failure( final Throwable cause )
	{
		if ( cause instanceof RuntimeException )
			return ( RuntimeException ) cause;
		if ( cause instanceof Error )
			throw ( Error ) cause;
		return new IllegalStateException( cause );
	}
}
