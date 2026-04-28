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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.convert.ConvertService;

import ij.ImagePlus;
import net.imagej.Dataset;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

/**
 * Demonstrates how to open an OME-Zarr dataset and obtain a legacy ImageJ1
 * {@link ImagePlus} handle from it. The intermediate IJ2 {@link Dataset} is
 * converted to an {@code ImagePlus} via SciJava's {@link ConvertService};
 * the converter itself is provided by {@code imagej-legacy} dependency on the
 * classpath.
 */
class Pyramidal5DImageDataAsImagePlusTest
{
	@Test
	void testOpenAsImagePlus() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr" );

		try (Context context = new Context())
		{
			final Pyramidal5DImageData< ? > image = new DefaultPyramidal5DImageData<>( context, path.toString() );
			final Dataset dataset = image.asDataset();
			final ImagePlus imagePlus = context.service( ConvertService.class ).convert( dataset, ImagePlus.class );

			assertNotNull( imagePlus );
			// order of dimensions for imagePlus: width, height, channels, slices, frames
			assertArrayEquals( new int[] { 64, 64, 3, 16, 4 }, imagePlus.getDimensions() );
			assertEquals( ZarrTestUtils.IMAGE_NAME, imagePlus.getTitle() );
		}
	}
}
