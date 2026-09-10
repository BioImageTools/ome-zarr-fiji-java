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
package ome.zarr.imglib2.exceptions;

/**
 * Thrown when a zipped OME-Zarr archive ({@code .ozx}) is opened with a backend
 * that cannot read one.
 * <p>
 * This is a property of the selected reader, not of the data, so it is raised
 * before the store is touched. Catch it ahead of {@link StoreAccessException}
 * and point the user at the backend setting.
 */
public class ZipArchiveUnsupportedException extends StoreAccessException
{

	private final String backendName;

	/**
	 * @param path location of the archive
	 * @param backendName display name of the backend that cannot read it
	 */
	public ZipArchiveUnsupportedException( final String path, final String backendName )
	{
		super( path + " – the " + backendName + " backend cannot read zipped OME-Zarr archives", null );
		this.backendName = backendName;
	}

	/** Display name of the backend that cannot read the archive. */
	public String getBackendName()
	{
		return backendName;
	}
}
