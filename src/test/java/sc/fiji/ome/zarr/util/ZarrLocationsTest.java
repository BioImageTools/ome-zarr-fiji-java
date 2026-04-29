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
package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ZarrLocationsTest
{
	@Test
	void detectsLocalZarrFolderViaFileUri() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr" );
		assertTrue( ZarrLocations.isZarr( path.toUri() ) );
	}

	@Test
	void rejectsNonZarrLocalFolder() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing" );
		assertFalse( ZarrLocations.isZarr( path.toUri() ) );
	}

	@Test
	void rejectsNullUri()
	{
		assertFalse( ZarrLocations.isZarr( null ) );
	}

	@Test
	void rejectsUnsupportedScheme()
	{
		assertFalse( ZarrLocations.isZarr( URI.create( "ftp://example.com/foo" ) ) );
	}

	@Test
	void rejectsMalformedFileUri()
	{
		// jar: URIs throw FileSystemNotFoundException from Paths.get(URI); the
		// dispatcher should catch that rather than propagate.
		assertFalse( ZarrLocations.isZarr( URI.create( "jar:file:/tmp/foo.jar!/bar" ) ) );
	}
}