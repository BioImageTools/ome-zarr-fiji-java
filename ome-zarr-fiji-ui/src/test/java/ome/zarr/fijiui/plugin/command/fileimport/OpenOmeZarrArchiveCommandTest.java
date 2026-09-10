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
package ome.zarr.fijiui.plugin.command.fileimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ome.zarr.ZarrTestUtils;

/**
 * Tests the archive-acceptance decision of the
 * {@code File > Import > OME-Zarr Archive (.ozx)...} command. The actual opening
 * is covered by {@code ZarrReader}'s tests; here only
 * {@link OpenOmeZarrArchiveCommand#validate} and the error wiring of
 * {@link OpenOmeZarrArchiveCommand#open} are exercised, so no window is ever
 * shown.
 */
class OpenOmeZarrArchiveCommandTest
{
	private final List< String > errors = new ArrayList<>();

	private final Consumer< String > errorHandler = errors::add;

	/**
	 * An archive is judged by the file itself, without reading the ZIP index, so
	 * the backend reports it later if the contents are not OME-Zarr.
	 */
	@Test
	void ozxArchiveIsAccepted( @TempDir final Path tempDir ) throws IOException
	{
		final File archive = Files.createFile( tempDir.resolve( "image.ozx" ) ).toFile();
		assertNull( OpenOmeZarrArchiveCommand.validate( archive ) );
	}

	@Test
	void missingArchiveIsRejected( @TempDir final Path tempDir )
	{
		assertNotNull( OpenOmeZarrArchiveCommand.validate( tempDir.resolve( "image.ozx" ).toFile() ) );
	}

	/** A plain file that is not an archive is not openable. */
	@Test
	void nonZarrFileIsRejected( @TempDir final Path tempDir ) throws IOException
	{
		final File file = Files.createFile( tempDir.resolve( "image.tif" ) ).toFile();
		assertNotNull( OpenOmeZarrArchiveCommand.validate( file ) );
	}

	/** A folder named like an archive is not one; only a real file is. */
	@Test
	void folderWithArchiveNameIsRejected( @TempDir final Path tempDir ) throws IOException
	{
		final File folder = Files.createDirectory( tempDir.resolve( "image.ozx" ) ).toFile();
		assertNotNull( OpenOmeZarrArchiveCommand.validate( folder ) );
	}

	@Test
	void nullArchiveIsRejected()
	{
		assertNotNull( OpenOmeZarrArchiveCommand.validate( null ) );
	}

	/**
	 * A rejected archive reports through the error handler and never reaches the
	 * opening pipeline – hence a {@code null} context is safe here.
	 */
	@Test
	void rejectedArchiveReportsErrorAndDoesNotOpen() throws URISyntaxException
	{
		final File folder = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing" ).toFile();
		assertFalse( OpenOmeZarrArchiveCommand.open( folder, null, errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "archive" ), errors.get( 0 ) );
	}

	/** A missing error handler must not turn a rejection into an exception. */
	@Test
	void rejectionWithoutErrorHandlerDoesNotThrow()
	{
		assertFalse( OpenOmeZarrArchiveCommand.open( null, null, null ) );
	}
}
