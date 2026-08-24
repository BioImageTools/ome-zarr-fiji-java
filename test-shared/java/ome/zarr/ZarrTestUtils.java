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
package ome.zarr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class ZarrTestUtils
{
	public static final String IMAGE_NAME = "image";

	public static Path resourcePath( final String resource ) throws URISyntaxException
	{
		URL url = ZarrTestUtils.class.getClassLoader().getResource( resource );
		assertNotNull( url, "Resource folder not found: " + resource );
		return Paths.get( url.toURI() );
	}

	/**
	 * The multiscale example datasets, covering 2d to 5d in every axis order, each
	 * in OME-Zarr v0.4 (Zarr v2) and v0.5 (Zarr v3). Suitable as a JUnit
	 * {@code @MethodSource}.
	 */
	public static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}
}
