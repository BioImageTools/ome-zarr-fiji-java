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
package ome.zarr.fijiui.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ome.zarr.ZarrTestUtils;

/**
 * Tests the folder-acceptance decision of the {@code File > Import > OME-Zarr...}
 * command. The actual opening is covered by {@code ZarrReader}'s tests; here
 * only {@link OpenOmeZarrCommand#validate} and the error wiring of
 * {@link OpenOmeZarrCommand#open} are exercised, so no window is ever shown.
 */
class OpenOmeZarrCommandTest
{
	private final List< String > errors = new ArrayList<>();

	private final Consumer< String > errorHandler = errors::add;

	/**
	 * A v0.4 (Zarr v2, {@code .zgroup}) and a v0.5 (Zarr v3, {@code zarr.json})
	 * dataset root, i.e. both metadata layouts a user can pick in the chooser.
	 */
	@ParameterizedTest
	@ValueSource( strings = {
			"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr",
			"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr" } )
	void zarrFolderIsAccepted( final String resource ) throws URISyntaxException
	{
		final File folder = ZarrTestUtils.resourcePath( resource ).toFile();
		assertNull( OpenOmeZarrCommand.validate( folder ) );
	}

	/**
	 * A resolution level inside a dataset is itself a Zarr node and is accepted,
	 * matching drag-and-drop, which lets a level be dropped and walks up to its
	 * multiscales group.
	 */
	@Test
	void resolutionLevelFolderIsAccepted() throws URISyntaxException
	{
		final File level = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr/0" ).toFile();
		assertNull( OpenOmeZarrCommand.validate( level ) );
	}

	/** The parent of the dataset roots holds no Zarr metadata of its own. */
	@Test
	void nonZarrFolderIsRejected() throws URISyntaxException
	{
		final File folder = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing" ).toFile();
		final String error = OpenOmeZarrCommand.validate( folder );
		assertNotNull( error );
		assertTrue( error.contains( "OME-Zarr" ), error );
		assertTrue( error.contains( folder.toString() ), error );
	}

	@Test
	void missingFolderIsRejected()
	{
		assertNotNull( OpenOmeZarrCommand.validate( new File( "/no/such/folder.ome.zarr" ) ) );
	}

	@Test
	void nullFolderIsRejected()
	{
		assertNotNull( OpenOmeZarrCommand.validate( null ) );
	}

	/**
	 * A rejected folder reports through the error handler and never reaches the
	 * opening pipeline – hence a {@code null} context is safe here.
	 */
	@Test
	void rejectedFolderReportsErrorAndDoesNotOpen() throws URISyntaxException
	{
		final File folder = ZarrTestUtils.resourcePath( "ome/zarr/testdata/2d_testing" ).toFile();
		assertFalse( OpenOmeZarrCommand.open( folder, null, errorHandler ) );
		assertEquals( 1, errors.size() );
		assertTrue( errors.get( 0 ).contains( "OME-Zarr" ) );
	}

	/** A missing error handler must not turn a rejection into an exception. */
	@Test
	void rejectionWithoutErrorHandlerDoesNotThrow()
	{
		assertFalse( OpenOmeZarrCommand.open( null, null, null ) );
	}
}
