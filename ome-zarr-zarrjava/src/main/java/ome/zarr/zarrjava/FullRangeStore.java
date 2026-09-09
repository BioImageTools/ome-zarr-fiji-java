/*-
 * #%L
 * OME-Zarr reader based on zarr-java
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
package ome.zarr.zarrjava;

import java.io.InputStream;
import java.nio.ByteBuffer;

import dev.zarr.zarrjava.store.Store;
import dev.zarr.zarrjava.store.StoreHandle;

/**
 * {@link Store} decorator that retries an unanswered open-ended range request
 * as an explicit {@code [start, size-1]} one.
 * <p>
 * Workaround for zarr-java
 * <a href="https://github.com/zarr-developers/zarr-java/issues/100">#100</a>:
 * {@code HttpStore.getInputStream} formats {@code bytes=%d-%d} unconditionally,
 * so the open-ended read that {@code ReadOnlyZipStore} uses to build its entry
 * index asks for {@code bytes=0--1} and gets {@code null} – leaving a zipped
 * archive silently empty over HTTP. Delete this once a fixed zarr-java is
 * pinned.
 * <p>
 * {@link #resolve} binds handles to this store so the retry also covers the
 * handle the ZIP store reads from.
 */
final class FullRangeStore implements Store
{
	private final Store delegate;

	FullRangeStore( final Store delegate )
	{
		this.delegate = delegate;
	}

	@Override
	public InputStream getInputStream( final String[] keys, final long start, final long end )
	{
		final InputStream in = delegate.getInputStream( keys, start, end );
		if ( in != null || end >= 0 )
			return in;
		// Only a delegate that cannot answer an open-ended range gets the explicit
		// one: FilesystemStore does answer it, and its end offset is not HTTP's
		// inclusive Range, so rewriting its request would truncate the archive.
		final long size = delegate.getSize( keys );
		if ( size <= 0 )
			return null; // size unknown, nothing better to try
		return delegate.getInputStream( keys, start, size - 1 );
	}

	@Override
	public StoreHandle resolve( final String... keys )
	{
		return new StoreHandle( this, keys );
	}

	@Override
	public boolean exists( final String[] keys )
	{
		return delegate.exists( keys );
	}

	@Override
	public ByteBuffer get( final String[] keys )
	{
		return delegate.get( keys );
	}

	@Override
	public ByteBuffer get( final String[] keys, final long start )
	{
		return delegate.get( keys, start );
	}

	@Override
	public ByteBuffer get( final String[] keys, final long start, final long end )
	{
		return delegate.get( keys, start, end );
	}

	@Override
	public void set( final String[] keys, final ByteBuffer bytes )
	{
		delegate.set( keys, bytes );
	}

	@Override
	public void delete( final String[] keys )
	{
		delegate.delete( keys );
	}

	@Override
	public long getSize( final String[] keys )
	{
		return delegate.getSize( keys );
	}

	@Override
	public String toString()
	{
		return delegate.toString();
	}
}
