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
package sc.fiji.ome.zarr.pyramid.metadata;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The class is used to represent omero metadata.
 */
@SuppressWarnings( "all" )
public class Omero
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	// Top-level Omero class
	public int id;

	public String version;

	public Rdefs rdefs;

	public List< Channel > channels;

	public static class Rdefs
	{
		public int defaultT;

		public int defaultZ;

		public String model;
	}

	public static class Channel
	{
		public boolean active;

		public double coefficient;

		public String color;

		public String family;

		public boolean inverted;

		public String label;

		public Window window;

		public static class Window
		{
			public double start;

			public double end;

			public double min;

			public double max;
		}
	}

	@Override
	public String toString()
	{
		return new Gson().toJson( this );
	}

	public static String[] buildChannelLabels( final String fallbackName, final Omero omero, final int numChannels )
	{
		final boolean omeroValid = omero != null && omero.channels != null && omero.channels.size() == numChannels;
		if ( omeroValid )
			logger.trace( "Creating with OMERO metadata: {}", omero );
		else
			logger.trace( "Creating without OMERO metadata (not consistent or not available)" );

		final String[] labels = new String[ numChannels ];
		for ( int i = 0; i < numChannels; i++ )
			labels[ i ] = omeroValid ? omero.channels.get( i ).label : fallbackName;
		return labels;
	}
}