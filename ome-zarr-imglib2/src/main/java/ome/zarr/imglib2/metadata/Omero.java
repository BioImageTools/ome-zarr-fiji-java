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
package ome.zarr.imglib2.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;

/**
 * The class is used to represent omero metadata. Immutable.
 */
@SuppressWarnings( "all" )
public final class Omero
{
	private final int id;

	private final String name;

	private final Rdefs rdefs;

	private final List< Channel > channels;

	public Omero( final int id, final String name, final Rdefs rdefs, final List< Channel > channels )
	{
		this.id = id;
		this.name = name;
		this.rdefs = rdefs;
		this.channels = channels == null ? null : new ArrayList<>( channels );
	}

	public int id()
	{
		return id;
	}

	public String name()
	{
		return name;
	}

	public Rdefs rdefs()
	{
		return rdefs;
	}

	/** Unmodifiable, or {@code null} if the metadata lists no channels. */
	public List< Channel > channels()
	{
		return channels == null ? null : Collections.unmodifiableList( channels );
	}

	public static final class Rdefs
	{
		private final int defaultT;

		private final int defaultZ;

		private final String model;

		public Rdefs( final int defaultT, final int defaultZ, final String model )
		{
			this.defaultT = defaultT;
			this.defaultZ = defaultZ;
			this.model = model;
		}

		public int defaultT()
		{
			return defaultT;
		}

		public int defaultZ()
		{
			return defaultZ;
		}

		public String model()
		{
			return model;
		}
	}

	public static final class Channel
	{
		private final boolean active;

		private final double coefficient;

		private final String color;

		private final String family;

		private final boolean inverted;

		private final String label;

		private final Window window;

		public Channel( final boolean active, final double coefficient, final String color, final String family,
				final boolean inverted, final String label, final Window window )
		{
			this.active = active;
			this.coefficient = coefficient;
			this.color = color;
			this.family = family;
			this.inverted = inverted;
			this.label = label;
			this.window = window;
		}

		public boolean active()
		{
			return active;
		}

		public double coefficient()
		{
			return coefficient;
		}

		public String color()
		{
			return color;
		}

		public String family()
		{
			return family;
		}

		public boolean inverted()
		{
			return inverted;
		}

		public String label()
		{
			return label;
		}

		public Window window()
		{
			return window;
		}

		public static final class Window
		{
			private final double start;

			private final double end;

			private final double min;

			private final double max;

			public Window( final double start, final double end, final double min, final double max )
			{
				this.start = start;
				this.end = end;
				this.min = min;
				this.max = max;
			}

			public double start()
			{
				return start;
			}

			public double end()
			{
				return end;
			}

			public double min()
			{
				return min;
			}

			public double max()
			{
				return max;
			}
		}
	}

	@Override
	public String toString()
	{
		return new Gson().toJson( this );
	}
}
