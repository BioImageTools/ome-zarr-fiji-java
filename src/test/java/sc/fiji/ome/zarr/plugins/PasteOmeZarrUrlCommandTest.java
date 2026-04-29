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
package sc.fiji.ome.zarr.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import sc.fiji.ome.zarr.util.ZarrTestUtils;

class PasteOmeZarrUrlCommandTest
{
	private final List< String > errors = new ArrayList<>();

	private final Consumer< String > errorHandler = errors::add;

	@Test
	void emptyClipboardReportsError()
	{
		assertNull( PasteOmeZarrUrlCommand.parseClipboardUri( "", errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "clipboard" ) );
	}

	@Test
	void whitespaceOnlyClipboardReportsError()
	{
		assertNull( PasteOmeZarrUrlCommand.parseClipboardUri( "   \t\n", errorHandler ) );
		assertEquals( 1, errors.size() );
	}

	@Test
	void nullClipboardReportsError()
	{
		assertNull( PasteOmeZarrUrlCommand.parseClipboardUri( null, errorHandler ) );
		assertEquals( 1, errors.size() );
	}

	@Test
	void localZarrPathYieldsFileUri() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr" );
		final URI result = PasteOmeZarrUrlCommand.parseClipboardUri( path.toString(), errorHandler );
		assertNotNull( result );
		assertEquals( path.toUri(), result );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void localZarrFileUriIsAcceptedAsIs() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr" );
		final URI result = PasteOmeZarrUrlCommand.parseClipboardUri( path.toUri().toString(), errorHandler );
		assertNotNull( result );
		assertEquals( path.toUri(), result );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void leadingAndTrailingWhitespaceIsTrimmed() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr" );
		final URI result = PasteOmeZarrUrlCommand.parseClipboardUri( "  " + path + "  \n", errorHandler );
		assertNotNull( result );
	}

	@Test
	void nonZarrLocalPathReportsError() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing" );
		assertNull( PasteOmeZarrUrlCommand.parseClipboardUri( path.toString(), errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "OME-Zarr" ) );
	}

	@Test
	void unsupportedSchemeReportsError()
	{
		assertNull( PasteOmeZarrUrlCommand.parseClipboardUri( "ftp://example.com/foo.zarr", errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "ftp" ) );
	}
}