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
package ome.zarr.fijiui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;

import net.imglib2.type.numeric.integer.UnsignedByteType;

import org.junit.jupiter.api.Test;

import ome.zarr.ZarrTestUtils;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.zarrjava.ZarrJavaPyramidBackend;

class PyramidTextsTest
{
	@Test
	void describesEveryAxisOfEachLevel() throws URISyntaxException
	{
		final PyramidContents< UnsignedByteType > contents = read( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr" );
		assertEquals( "<html><b>1</b>&nbsp; (64×64×16, c=3, t=4, uint8, slice 4.1 kpx ~ 4.0 KB, total 768.0 KB)</html>",
				PyramidTexts.describeLevel( contents, 0 ) );
		assertEquals( "<html><b>2</b>&nbsp; (32×32×8, c=3, t=4, uint8, slice 1.0 kpx ~ 1.0 KB, total 96.0 KB)</html>",
				PyramidTexts.describeLevel( contents, 1 ) );
	}

	@Test
	void leavesOutAbsentAxes() throws URISyntaxException
	{
		final PyramidContents< UnsignedByteType > contents = read( "ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr" );
		assertEquals( "<html><b>1</b>&nbsp; (64×64, uint8, slice 4.1 kpx ~ 4.0 KB, total 4.0 KB)</html>",
				PyramidTexts.describeLevel( contents, 0 ) );
	}

	@Test
	void describesSegmentationLevelsByExtentAndPixelSize() throws URISyntaxException
	{
		final PyramidContents< UnsignedByteType > contents = read( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr" );
		assertEquals( "<html>3 channels, 4 time points, uint8<br>Extent and pixel size in x×y×z order</html>",
				PyramidTexts.describeImage( contents ) );
		assertEquals( "<html><b>1</b>&nbsp; (64×64×16, pixel 1×1×1)</html>",
				PyramidTexts.describeSegmentationLevel( contents, 0 ) );
		assertEquals( "<html><b>2</b>&nbsp; (32×32×8, pixel 2×2×2)</html>",
				PyramidTexts.describeSegmentationLevel( contents, 1 ) );
	}

	@Test
	void leavesOutAbsentAxesForSegmentation() throws URISyntaxException
	{
		final PyramidContents< UnsignedByteType > contents = read( "ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr" );
		assertEquals( "<html>uint8<br>Extent and pixel size in x×y order</html>",
				PyramidTexts.describeImage( contents ) );
		assertEquals( "<html><b>2</b>&nbsp; (32×32, pixel 2×2)</html>",
				PyramidTexts.describeSegmentationLevel( contents, 1 ) );
	}

	private static PyramidContents< UnsignedByteType > read( final String resource ) throws URISyntaxException
	{
		return new ZarrJavaPyramidBackend().read( ZarrTestUtils.resourcePath( resource ).toUri() );
	}
}
