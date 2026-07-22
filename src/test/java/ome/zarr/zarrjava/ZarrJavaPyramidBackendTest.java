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
package ome.zarr.zarrjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import ome.zarr.imglib2.PyramidBackendTestBase;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.ZarrTestUtils;

class ZarrJavaPyramidBackendTest implements PyramidBackendTestBase
{
	@Override
	public PyramidContents< ? > load( final String resource, final Context context )
			throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new ZarrJavaPyramidBackend().load( path.toUri() );
	}

	@Test
	void testStaticOpen() throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr" );
		PyramidContents< ? > contents = ZarrJavaPyramidBackend.open( path.toUri() );
		assertNotNull( contents );
		assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
		assertEquals( 5, contents.numDimensions() );
		assertEquals( 2, contents.numResolutionLevels() );
	}
}
