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
package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.backend.n5.N5PyramidBackend;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

/**
 * Tests how {@link PyramidalDataset} names itself: a multi-resolution image
 * gets a " (R)" suffix (at any resolution level), while a single-resolution
 * image does not.
 */
class PyramidalDatasetNamingTest
{
	private static final String MULTI_LEVEL_RESOURCE = "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr";

	private static final String SINGLE_LEVEL_RESOURCE =
			"sc/fiji/ome/zarr/util/single_resolution_testing/single_resolution_dataset_v5.ome.zarr";

	@Test
	void multiResolutionImageNameEndsWithR() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( MULTI_LEVEL_RESOURCE );
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents = new N5PyramidBackend().load( path.toUri() );
			assertTrue( new PyramidalDataset( context, contents, 0 ).getName().endsWith( " (R)" ) );
			assertTrue( new PyramidalDataset( context, contents, 1 ).getName().endsWith( " (R)" ) );
		}
	}

	@Test
	void singleResolutionImageNameHasNoR() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( SINGLE_LEVEL_RESOURCE );
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents = new N5PyramidBackend().load( path.toUri() );
			final String name = new PyramidalDataset( context, contents, 0 ).getName();
			assertFalse( name.contains( "(R)" ), "expected no '(R)' in name but was: " + name );
		}
	}
}
