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
 * Thrown when the reader library a backend uses is on the classpath but too old
 * or incomplete for what the backend calls: a class it needs cannot be linked.
 * <p>
 * The cause is the {@link NoClassDefFoundError} that surfaced while reading.
 */
public class ReaderLibraryUnavailableException extends StoreAccessException
{
	private final String missingClass;

	public ReaderLibraryUnavailableException( final String path, final NoClassDefFoundError cause )
	{
		super( path + " – " + missingClassName( cause ) + " is not on the classpath", cause );
		this.missingClass = missingClassName( cause );
	}

	/** The class that could not be linked, in binary (dotted) notation. */
	public String getMissingClass()
	{
		return missingClass;
	}

	private static String missingClassName( final NoClassDefFoundError cause )
	{
		final String name = cause.getMessage();
		return name == null ? "a class of the reader library" : name.replace( '/', '.' );
	}
}
