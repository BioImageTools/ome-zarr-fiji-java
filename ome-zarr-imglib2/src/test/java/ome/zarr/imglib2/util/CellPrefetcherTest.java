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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.imglib2.FinalInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.type.numeric.integer.UnsignedByteType;

class CellPrefetcherTest
{
	/** 100 x 100 x 10 pixels in cells of 16 x 16 x 4, i.e. a grid of 7 x 7 x 3 cells. */
	private static final long[] DIMENSIONS = { 100, 100, 10 };

	private static final int[] CELL_DIMENSIONS = { 16, 16, 4 };

	private final ExecutorService executor = Executors.newFixedThreadPool( 4 );

	@AfterEach
	void shutDown()
	{
		executor.shutdownNow();
	}

	/**
	 * Prefetching the first plane loads exactly its 7 x 7 cells and reports each
	 * one, even for an interval that sticks out of the image.
	 * <p>
	 * Progress follows the order the cells finish. To show this, the test
	 * deliberately holds back the loading of the first cell until all other cells
	 * were reported. If progress waited for the cells in the order they were
	 * submitted, that would never happen, and the first cell fails after 10 s.
	 */
	@Test
	void prefetchLoadsTheCellsOfTheIntervalAndReportsThemAsTheyFinish() throws InterruptedException
	{
		final int numCells = 7 * 7;
		final CountDownLatch othersReported = new CountDownLatch( 1 );
		final Set< List< Long > > loaded = ConcurrentHashMap.newKeySet();
		final CachedCellImg< UnsignedByteType, ? > img = image( cell -> {
			final List< Long > position = gridPosition( cell.minAsLongArray() );
			if ( position.equals( Arrays.asList( 0L, 0L, 0L ) )
					&& !othersReported.await( 10, TimeUnit.SECONDS ) )
				throw new IllegalStateException( "progress waited for the first cell" );
			loaded.add( position );
		} );
		final List< Integer > reportedLoaded = new ArrayList<>();
		final List< Integer > reportedTotal = new ArrayList<>();

		CellPrefetcher.prefetch( img, new FinalInterval( new long[] { -50, -50, 0 }, new long[] { 149, 149, 0 } ), executor,
				( count, total ) -> {
					reportedLoaded.add( count );
					reportedTotal.add( total );
					if ( count == numCells - 1 )
						othersReported.countDown();
				} );

		final Set< List< Long > > firstPlane = new HashSet<>();
		for ( long x = 0; x < 7; x++ )
			for ( long y = 0; y < 7; y++ )
				firstPlane.add( Arrays.asList( x, y, 0L ) );
		assertEquals( firstPlane, loaded, "exactly the cells of the first plane" );
		final List< Integer > oneToNumCells = new ArrayList<>();
		for ( int i = 1; i <= numCells; i++ )
			oneToNumCells.add( i );
		assertEquals( oneToNumCells, reportedLoaded, "one report per cell, counting up" );
		assertEquals( Collections.nCopies( numCells, numCells ), reportedTotal, "every report gives the number of cells as total" );
	}

	/** An interval outside the image loads nothing and reports nothing. */
	@Test
	void prefetchOutsideTheImageDoesNothing() throws InterruptedException
	{
		final Set< List< Long > > loaded = ConcurrentHashMap.newKeySet();
		final CachedCellImg< UnsignedByteType, ? > img = image( cell -> loaded.add( gridPosition( cell.minAsLongArray() ) ) );
		final List< Integer > reports = new ArrayList<>();

		CellPrefetcher.prefetch( img, new FinalInterval( new long[] { 200, 200, 0 }, new long[] { 300, 300, 0 } ), executor,
				( count, total ) -> reports.add( count ) );

		assertTrue( loaded.isEmpty() );
		assertTrue( reports.isEmpty() );
	}

	/** What the loader throws reaches the caller of prefetch. */
	@Test
	void prefetchPassesOnWhatTheLoaderThrows()
	{
		final IllegalArgumentException failure = new IllegalArgumentException( "cannot load" );
		final CachedCellImg< UnsignedByteType, ? > img = image( cell -> {
			throw failure;
		} );

		final RuntimeException thrown = assertThrows( RuntimeException.class,
				() -> CellPrefetcher.prefetch( img, img, executor, ( count, total ) -> {} ) );

		assertSame( failure, rootCause( thrown ) );
	}

	private static CachedCellImg< UnsignedByteType, ? > image( final CellLoader< UnsignedByteType > loader )
	{
		return new ReadOnlyCachedCellImgFactory().create( DIMENSIONS, new UnsignedByteType(), loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions( CELL_DIMENSIONS ) );
	}

	private static List< Long > gridPosition( final long[] cellMin )
	{
		final List< Long > position = new ArrayList<>();
		for ( int d = 0; d < cellMin.length; d++ )
			position.add( cellMin[ d ] / CELL_DIMENSIONS[ d ] );
		return position;
	}

	/** The cache wraps what a loader throws, so the original is the innermost cause. */
	private static Throwable rootCause( Throwable throwable )
	{
		while ( throwable.getCause() != null )
			throwable = throwable.getCause();
		return throwable;
	}
}
