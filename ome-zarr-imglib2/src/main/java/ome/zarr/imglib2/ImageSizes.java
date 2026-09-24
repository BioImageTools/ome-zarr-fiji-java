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
package ome.zarr.imglib2;

import java.util.Locale;

import net.imglib2.type.numeric.RealType;

/**
 * Pixel counts and uncompressed data sizes for images. Pass {@code 1} for absent axes.
 */
public final class ImageSizes
{
	private static final String[] BYTE_UNITS = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };

	private static final String[] PIXEL_UNITS = { "px", "kpx", "Mpx", "Gpx", "Tpx", "Ppx", "Epx" };

	private ImageSizes()
	{
		// prevent instantiation
	}

	/**
	 * Number of pixels (voxels) of an image with the given extents.
	 */
	public static long numPixels( final long x, final long y, final long z, final long c, final long t )
	{
		return x * y * z * c * t;
	}

	/**
	 * Uncompressed size in bytes of an image with the given extents and pixel
	 * type, i.e. {@link #numPixels} times {@link RealType#getBitsPerPixel()},
	 * rounded up to whole bytes.
	 */
	public static long uncompressedBytes( final long x, final long y, final long z, final long c, final long t,
			final RealType< ? > type )
	{
		return ( numPixels( x, y, z, c, t ) * type.getBitsPerPixel() + 7 ) / 8;
	}

	/**
	 * Formats a byte count in 1024-based units labeled KB, MB, GB, …, e.g.
	 * {@code "64.0 MB"} for 64 × 1024² bytes. This matches the size ImageJ shows
	 * in an image window's subtitle, which also divides by 1024 and says "MB".
	 */
	public static String formatBytes( final long bytes )
	{
		return formatWithPrefix( bytes, 1024, BYTE_UNITS );
	}

	/**
	 * Formats a pixel count with decimal (SI) prefixes, e.g. {@code "12.6 Mpx"}
	 * for 12.6 megapixels. Pixel counts are always decimal, as for camera
	 * megapixels.
	 */
	public static String formatPixels( final long pixels )
	{
		return formatWithPrefix( pixels, 1000, PIXEL_UNITS );
	}

	/**
	 * Scales {@code count} by {@code base} until it is below {@code base} and
	 * prints it with one decimal. Values below {@code base} are printed as whole
	 * numbers. The loop stops just <em>below</em> {@code base} ({@code - 0.05}),
	 * so that e.g., 1023.96 KB is printed as "1.0 MB", not as "1024.0 KB" after
	 * rounding. {@link Locale#ROOT} keeps the decimal point independent of the
	 * user's locale.
	 */
	private static String formatWithPrefix( final long count, final int base, final String[] units )
	{
		if ( count < base )
			return count + " " + units[ 0 ];
		double value = ( double ) count / base;
		int unit = 1;
		while ( value >= base - 0.05 && unit < units.length - 1 )
		{
			value /= base;
			unit++;
		}
		return String.format( Locale.ROOT, "%.1f %s", value, units[ unit ] );
	}
}
