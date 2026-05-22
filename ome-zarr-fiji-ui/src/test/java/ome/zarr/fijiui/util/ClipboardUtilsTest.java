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
package ome.zarr.fijiui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ome.zarr.ZarrTestUtils;

class ClipboardUtilsTest
{
	private final List< String > errors = new ArrayList<>();

	private final Consumer< String > errorHandler = errors::add;

	static Stream< String > clipboardContents()
	{
		return Stream.of(
				"",
				"   \t\n",
				null
		);
	}

	@BeforeEach
	void clearErrors()
	{
		errors.clear();
	}

	@ParameterizedTest
	@MethodSource( "clipboardContents" )
	void clipboardReportsError( String clipboardContents )
	{
		assertNull( ClipboardUtils.stringToUri(clipboardContents, errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "clipboard" ) );
	}

	@Test
	void localZarrPathYieldsFileUri() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr" );
		final URI result = ClipboardUtils.stringToUri(path.toString(), errorHandler );
		assertNotNull( result );
		assertEquals( path.toUri(), result );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void localZarrFileUriIsAcceptedAsIs() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr" );
		final URI result = ClipboardUtils.stringToUri(path.toUri().toString(), errorHandler );
		assertNotNull( result );
		assertEquals( path.toUri(), result );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void leadingAndTrailingWhitespaceIsTrimmed() throws URISyntaxException
	{
		final Path path = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr" );
		final URI result = ClipboardUtils.stringToUri("  " + path + "  \n", errorHandler );
		assertNotNull( result );
	}

	@Test
	void s3UriIsAccepted()
	{
		final URI result = ClipboardUtils.stringToUri( "s3://my-bucket/path/to/dataset", errorHandler );
		assertNotNull( result );
		assertEquals( URI.create( "s3://my-bucket/path/to/dataset" ), result );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void unsupportedSchemeReportsError()
	{
		assertNull( ClipboardUtils.stringToUri("ftp://example.com/foo.zarr", errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "ftp" ) );
	}

	// --- readClipboard() and parseClipboardUri(Consumer) via system clipboard ---

	@Test
	void systemClipboardWithHttpsUrlReturnsUri()
	{
		setClipboard( "https://example.com/dataset" );
		final URI result = ClipboardUtils.readClipboardAsUri( errorHandler );
		assertNotNull( result );
		assertEquals( URI.create( "https://example.com/dataset" ), result );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void systemClipboardWithLocalPathReturnsFileUri()
	{
		setClipboard( "/tmp/some-zarr-dataset" );
		final URI result = ClipboardUtils.readClipboardAsUri( errorHandler );
		assertNotNull( result );
		assertEquals( "file", result.getScheme() );
		assertTrue( errors.isEmpty(), "Unexpected errors: " + errors );
	}

	@Test
	void emptySystemClipboardReportsError()
	{
		setClipboard( "" );
		assertNull( ClipboardUtils.readClipboardAsUri( errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "clipboard" ) );
	}

	@Test
	void nonTextSystemClipboardReportsError()
	{
		// A clipboard owner that advertises no flavors – simulates image or
		// binary content with no string representation.
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new Transferable()
				{
					@Override
					public DataFlavor[] getTransferDataFlavors()
					{
						return new DataFlavor[ 0 ];
					}

					@Override
					public boolean isDataFlavorSupported( DataFlavor f )
					{
						return false;
					}

					@Override
					public @NonNull Object getTransferData( DataFlavor f )
					{
						throw new UnsupportedOperationException();
					}
				}, null );
		assertNull( ClipboardUtils.readClipboardAsUri( errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "clipboard" ) );
	}

	// --- InvalidPathException branch in stringToUri() ---

	@Test
	void invalidPathReportsError()
	{
		// A null byte is rejected by Paths.get() on all platforms with an
		// InvalidPathException; the string is also not a valid URI so
		// tryParseUri() returns null first, reaching the Paths.get() call.
		assertNull( ClipboardUtils.stringToUri("invalid\0path", errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "Could not interpret" ) );
	}

	private static void setClipboard( final String text )
	{
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents( new StringSelection( text ), null );
	}
}
