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

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;

import org.junit.jupiter.api.Test;

/** {@link ImageSizes} on artificial 5d images (x, y, z, c, t) of different pixel types. */
class ImageSizesTest
{
	/** 64 x 32 x 4 x 3 x 2 */
	private static final long[] DIMS = { 64, 32, 4, 3, 2 };

	private static final long NUM_PIXELS = 49_152;

	@Test
	void testNumPixels()
	{
		assertEquals( NUM_PIXELS, numPixels( ArrayImgs.unsignedBytes( DIMS ) ) );
	}

	@Test
	void testUncompressedBytesPerPixelType()
	{
		assertEquals( NUM_PIXELS, uncompressedBytes( ArrayImgs.unsignedBytes( DIMS ) ) );
		assertEquals( NUM_PIXELS * 2, uncompressedBytes( ArrayImgs.unsignedShorts( DIMS ) ) );
		assertEquals( NUM_PIXELS * 4, uncompressedBytes( ArrayImgs.floats( DIMS ) ) );
		assertEquals( NUM_PIXELS * 8, uncompressedBytes( ArrayImgs.doubles( DIMS ) ) );
		assertEquals( NUM_PIXELS / 8, uncompressedBytes( ArrayImgs.bits( DIMS ) ) );
		// 3 x 3 = 9 bits round up to 2 bytes
		assertEquals( 2, uncompressedBytes( ArrayImgs.bits( 3, 3, 1, 1, 1 ) ) );
	}

	@Test
	void testFormatBytes()
	{
		assertEquals( "48.0 KB", ImageSizes.formatBytes( uncompressedBytes( ArrayImgs.unsignedBytes( DIMS ) ) ) );
		assertEquals( "384.0 KB", ImageSizes.formatBytes( uncompressedBytes( ArrayImgs.doubles( DIMS ) ) ) );
		assertEquals( "1023 B", ImageSizes.formatBytes( 1023 ) );
		assertEquals( "1.0 KB", ImageSizes.formatBytes( 1024 ) );
		assertEquals( "1.0 MB", ImageSizes.formatBytes( 1024 * 1024 - 1 ) );
		assertEquals( "8.0 EB", ImageSizes.formatBytes( Long.MAX_VALUE ) );
	}

	@Test
	void testFormatPixels()
	{
		assertEquals( "49.2 kpx", ImageSizes.formatPixels( NUM_PIXELS ) );
		assertEquals( "999 px", ImageSizes.formatPixels( 999 ) );
		assertEquals( "999.9 kpx", ImageSizes.formatPixels( 999_949 ) );
		assertEquals( "1.0 Mpx", ImageSizes.formatPixels( 999_950 ) );
		assertEquals( "12.6 Mpx", ImageSizes.formatPixels( 12_600_000 ) );
	}

	private static long numPixels( final Img< ? > img )
	{
		return ImageSizes.numPixels( img.dimension( 0 ), img.dimension( 1 ), img.dimension( 2 ), img.dimension( 3 ),
				img.dimension( 4 ) );
	}

	private static < T extends RealType< T > > long uncompressedBytes( final Img< T > img )
	{
		return ImageSizes.uncompressedBytes( img.dimension( 0 ), img.dimension( 1 ), img.dimension( 2 ), img.dimension( 3 ),
				img.dimension( 4 ), img.firstElement() );
	}
}
