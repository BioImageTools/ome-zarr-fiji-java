/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.fijiui.open.options;

import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.n5.N5PyramidBackend;
import ome.zarr.zarrjava.ZarrJavaPyramidBackend;

/**
 * The library to be used to read (write) OME-Zarr datasets.
 */
public enum OmeZarrBackend
{
	/**
	 * Backend supported via the N5 library (supports Zarr v2 and v3 through n5-zarr).
	 */
	N5( "N5" ),

	/**
	 * Backend supported via the zarr-java library (supports Zarr v2 and v3).
	 */
	ZARR_JAVA( "zarr-java" );

	private final String description;

	OmeZarrBackend( final String description )
	{
		this.description = description;
	}

	public static OmeZarrBackend getByName( final String name )
	{
		for ( final OmeZarrBackend option : values() )
			if ( option.name().equals( name ) )
				return option;
		throw new NoSuchElementException( name );
	}

	public static OmeZarrBackend getByDescription( final String description )
	{
		for ( final OmeZarrBackend option : values() )
			if ( option.description.equals( description ) )
				return option;
		return null;
	}

	public String getDescription()
	{
		return description;
	}

	/**
	 * Whether this backend's reader library is installed. N5 always is, as Fiji
	 * ships it. zarr-java needs both {@code ome-zarr-zarrjava} and {@code zarr-java},
	 * which come with the OME-Zarr update site and may be missing elsewhere.
	 *
	 * @return {@code true} if {@link #createBackend(String)} can construct this backend
	 */
	public boolean isAvailable()
	{
		return this != ZARR_JAVA || ZarrJavaProbe.AVAILABLE;
	}

	/**
	 * This backend if it {@link #isAvailable() is available}, otherwise {@link #N5}.
	 *
	 * @return the backend that {@link #createBackend(String)} actually constructs
	 */
	OmeZarrBackend effective()
	{
		return isAvailable() ? this : N5;
	}

	/**
	 * Creates a fresh backend for the backend library this constant represents,
	 * reading {@code s3:} URIs with the AWS SDK default profile.
	 *
	 * @return a new backend instance, never {@code null}
	 */
	public PyramidBackend createBackend()
	{
		return createBackend( null );
	}

	/**
	 * Creates a fresh backend for the backend library this constant represents,
	 * or for {@link #N5} if that library is not installed.
	 *
	 * @param awsProfile the AWS profile for {@code s3:} URIs, or {@code null} for
	 *   the AWS SDK default
	 * @return a new backend instance, never {@code null}
	 */
	public PyramidBackend createBackend( final String awsProfile )
	{
		switch ( effective() )
		{
		case ZARR_JAVA:
			return new ZarrJavaPyramidBackend( awsProfile );
		case N5:
		default:
			return new N5PyramidBackend( awsProfile );
		}
	}

	/**
	 * Probes once, on first use, whether zarr-java can be loaded. A holder class
	 * rather than a field of the enum, so the probe runs (and logs) only when asked.
	 */
	private static final class ZarrJavaProbe
	{
		private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

		static final boolean AVAILABLE = probe();

		private static boolean probe()
		{
			try
			{
				final ClassLoader loader = OmeZarrBackend.class.getClassLoader();
				Class.forName( "ome.zarr.zarrjava.ZarrJavaPyramidBackend", false, loader );
				Class.forName( "dev.zarr.zarrjava.core.Array", false, loader );
				return true;
			}
			catch ( ClassNotFoundException | LinkageError e )
			{
				logger.info( "zarr-java is not installed ({}), reading OME-Zarr with N5 instead. "
						+ "Enable the OME-Zarr update site to use zarr-java.", e.getMessage() );
				return false;
			}
		}
	}
}
