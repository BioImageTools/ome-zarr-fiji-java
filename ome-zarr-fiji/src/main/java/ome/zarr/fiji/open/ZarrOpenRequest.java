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
package ome.zarr.fiji.open;

import java.net.URI;
import java.util.function.Consumer;

import org.scijava.Context;

import ij.IJ;
import ome.zarr.fiji.read.ZarrReader;
import ome.zarr.imglib2.PyramidBackend;

/**
 * Everything a {@link ZarrOpener} needs to act on one OME-Zarr location: where
 * the dataset is, which SciJava context to work in, how to report a failure, and
 * the reader settings the user configured.
 * <p>
 * A request is created once per opening attempt and handed to exactly one
 * opener (or, when the user asked to be prompted, to the chooser dialog that
 * then picks one). An opener that brings its own reader needs only
 * {@link #uri()} and {@link #context()} and can ignore the rest; the openers
 * shipped here go through {@link #reader()}.
 * <p>
 * {@link #reader()} is built lazily and then cached, so an opener that never
 * reads a pixel never constructs a {@link ZarrReader}, and two openers sharing
 * one request share the read {@link ome.zarr.imglib2.PyramidContents}.
 */
public class ZarrOpenRequest
{
	private final URI uri;

	private final Context context;

	private final PyramidBackend backend;

	private final Integer preferredMaxWidth;

	private final Consumer< String > errorHandler;

	private ZarrReader reader;

	/**
	 * Request for {@code uri} at the highest resolution, reporting failures via
	 * {@code IJ::error}.
	 *
	 * @param uri the OME-Zarr location to open
	 * @param context the SciJava context used for display and services
	 * @param backend the backend used to read the dataset
	 */
	public ZarrOpenRequest( final URI uri, final Context context, final PyramidBackend backend )
	{
		this( uri, context, backend, null, IJ::error );
	}

	/**
	 * @param uri the OME-Zarr location to open
	 * @param context the SciJava context used for display and services
	 * @param backend the backend used to read the dataset
	 * @param preferredMaxWidth the width the user prefers not to exceed, or
	 *   {@code null} for the highest resolution
	 * @param errorHandler receives a user-facing message when opening fails
	 */
	public ZarrOpenRequest( final URI uri, final Context context, final PyramidBackend backend,
			final Integer preferredMaxWidth, final Consumer< String > errorHandler )
	{
		this.uri = uri;
		this.context = context;
		this.backend = backend;
		this.preferredMaxWidth = preferredMaxWidth;
		this.errorHandler = errorHandler;
	}

	/**
	 * @return the OME-Zarr location to open
	 */
	public URI uri()
	{
		return uri;
	}

	/**
	 * @return the SciJava context used for display and services
	 */
	public Context context()
	{
		return context;
	}

	/**
	 * @return the backend the user configured to read the dataset with
	 */
	public PyramidBackend backend()
	{
		return backend;
	}

	/**
	 * @return the width the user prefers not to exceed, or {@code null} for the
	 *   highest resolution
	 */
	public Integer preferredMaxWidth()
	{
		return preferredMaxWidth;
	}

	/**
	 * @return the sink for user-facing messages when opening fails
	 */
	public Consumer< String > errorHandler()
	{
		return errorHandler;
	}

	/**
	 * The reader for this request, built on first use and cached afterwards.
	 *
	 * @return a {@link ZarrReader} configured with this request's backend,
	 *   preferred width and error handler
	 */
	public synchronized ZarrReader reader()
	{
		if ( reader == null )
			reader = new ZarrReader( uri, context, backend, preferredMaxWidth, errorHandler );
		return reader;
	}

	@Override
	public String toString()
	{
		return "ZarrOpenRequest{uri=" + uri + ", backend=" + ( backend == null ? null : backend.getName() )
				+ ", preferredMaxWidth=" + preferredMaxWidth + "}";
	}
}
