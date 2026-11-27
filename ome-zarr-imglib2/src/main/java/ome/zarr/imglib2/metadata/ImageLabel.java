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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * The {@code image-label} metadata of an OME-Zarr label image: display colors,
 * per-label properties and the image the labels belong to. Immutable.
 * <p>
 * Every part is optional in the specification, so every accessor may return
 * {@code null}. Being a label image is decided by where the image sits (under
 * a {@code labels/} group), not by the presence of this metadata.
 */
@SuppressWarnings( "all" )
public final class ImageLabel
{
	private final List< Color > colors;

	private final List< Property > properties;

	private final Source source;

	private final String version;

	public ImageLabel( final List< Color > colors, final List< Property > properties, final Source source, final String version )
	{
		this.colors = colors == null ? null : new ArrayList<>( colors );
		this.properties = properties == null ? null : new ArrayList<>( properties );
		this.source = source;
		this.version = version;
	}

	/** Unmodifiable, or {@code null} if the metadata lists no colors. */
	public List< Color > colors()
	{
		return colors == null ? null : Collections.unmodifiableList( colors );
	}

	/** Unmodifiable, or {@code null} if the metadata lists no properties. */
	public List< Property > properties()
	{
		return properties == null ? null : Collections.unmodifiableList( properties );
	}

	public Source source()
	{
		return source;
	}

	/**
	 * The {@code image-label} schema version, or {@code null}. Since OME-Zarr 0.5
	 * the version is normally only stated once for the whole image.
	 */
	public String version()
	{
		return version;
	}

	/** Display color of one label value. */
	public static final class Color
	{
		@SerializedName( "label-value" )
		private final int labelValue;

		private final int[] rgba;

		public Color( final int labelValue, final int[] rgba )
		{
			this.labelValue = labelValue;
			this.rgba = rgba == null ? null : rgba.clone();
		}

		public int labelValue()
		{
			return labelValue;
		}

		/** Red, green, blue and alpha in {@code [0, 255]}, as a copy, or {@code null} if no color is given. */
		public int[] rgba()
		{
			return rgba == null ? null : rgba.clone();
		}
	}

	/**
	 * Free-form metadata of one label value, e.g. {@code omero:roiId}.
	 * <p>
	 * Values are what the JSON holds: {@code String}, {@code Boolean},
	 * {@code List}, {@code Map} or a number. Numbers at the top level are
	 * normalized to {@code Long} if integral and {@code Double} otherwise, so
	 * they do not depend on the JSON library the reader backend uses.
	 */
	public static final class Property
	{
		private final int labelValue;

		private final Map< String, Object > values;

		public Property( final int labelValue, final Map< String, Object > values )
		{
			this.labelValue = labelValue;
			this.values = new LinkedHashMap<>();
			if ( values != null )
				values.forEach( ( key, value ) -> this.values.put( key, normalize( value ) ) );
		}

		public int labelValue()
		{
			return labelValue;
		}

		/** Unmodifiable, without the {@code label-value} key. */
		public Map< String, Object > values()
		{
			return Collections.unmodifiableMap( values );
		}

		private static Object normalize( final Object value )
		{
			if ( value instanceof Byte || value instanceof Short || value instanceof Integer )
				return ( ( Number ) value ).longValue();
			if ( value instanceof BigInteger && ( ( BigInteger ) value ).bitLength() < 64 )
				return ( ( BigInteger ) value ).longValue();
			if ( value instanceof Float || value instanceof BigDecimal )
				return ( ( Number ) value ).doubleValue();
			return value;
		}
	}

	/** The image the labels belong to. */
	public static final class Source
	{
		private final String image;

		public Source( final String image )
		{
			this.image = image;
		}

		/**
		 * Path of the image group, relative to the label image, or {@code null}
		 * if not given. The specification's default is {@code "../../"}.
		 */
		public String image()
		{
			return image;
		}
	}

	@Override
	public String toString()
	{
		return new Gson().toJson( this );
	}
}
