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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class ZarrOnFileSystemUtilsTest
{

	@Test
	void testFindImageRootFolder_startOnRootFolder() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			Path result = ZarrOnFileSystemUtils.findImageRootFolder( path );
			assertNull( result, "Expected null for root folder: " + example );
		}
	}

	@Test
	void testFindImageRootFolder_startOnLevelOneFolder() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr/0"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			Path result = ZarrOnFileSystemUtils.findImageRootFolder( path );
			assertEquals( path, result, "Expected image root folder for: " + example );
		}
	}

	@Test
	void testFindImageRootFolder_startOnLeaveFolder() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0/0"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			Path result = ZarrOnFileSystemUtils.findImageRootFolder( path );
			assertNull( result, "Expected null for root folder: " + example );
		}
	}

	@Test
	void testIsZarrFolder_validZarrFolders() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr/0"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			assertTrue( ZarrOnFileSystemUtils.isZarrFolder( path ) );
		}
	}

	@Test
	void testIsZarrFolder_invalidZarrFolders() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0/0",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/0/0/0"
		};

		for ( String example : examples )
		{
			Path path = ZarrTestUtils.resourcePath( example );
			assertFalse( ZarrOnFileSystemUtils.isZarrFolder( path ) );
		}
	}

	@Test
	void testRelativePathDifferences_noElements()
	{
		Path pathA = Paths.get( "/some/path/to/folder" );

		List< String > result = ZarrOnFileSystemUtils.relativePathElements( pathA, pathA );

		assertTrue( result.isEmpty(), "Expected no differences when paths are the same." );
	}

	@Test
	void testRelativePathElements_simpleDifference()
	{
		Path shorterPath = Paths.get( "/root/folder" );
		Path longerPath = Paths.get( "/root/folder/subfolder" );

		List< String > result = ZarrOnFileSystemUtils.relativePathElements( shorterPath, longerPath );

		assertEquals( 1, result.size(), "Expected exactly 1 folder difference." );
		assertEquals( "subfolder", result.get( 0 ), "Expected the difference to be 'subfolder'." );
	}

	@Test
	void testRelativePathDifferences_multipleElements()
	{
		Path shorterPath = Paths.get( "/usr" );
		Path longerPath = Paths.get( "/usr/local/bin/java" );

		List< String > result = ZarrOnFileSystemUtils.relativePathElements( shorterPath, longerPath );

		assertEquals( 3, result.size(), "Expected 3 folder differences." );
		assertEquals( "local", result.get( 0 ), "First difference should be 'local'." );
		assertEquals( "bin", result.get( 1 ), "Second difference should be 'bin'." );
		assertEquals( "java", result.get( 2 ), "Third difference should be 'java'." );
	}

	@Test
	void testRelativePathDifferences_shorterPathIsParentRoot()
	{
		Path shorterPath = Paths.get( "C:/" );
		Path longerPath = Paths.get( "C:/Users/John/Documents/Projects/Code" );

		List< String > result = ZarrOnFileSystemUtils.relativePathElements( shorterPath, longerPath );

		assertEquals( 5, result.size(), "Expected 5 folder differences." );
		assertEquals( "Users", result.get( 0 ), "First difference should be 'Users'." );
		assertEquals( "John", result.get( 1 ), "Second difference should be 'John'." );
		assertEquals( "Documents", result.get( 2 ), "Third difference should be 'Documents'." );
		assertEquals( "Projects", result.get( 3 ), "Fourth difference should be 'Projects'." );
		assertEquals( "Code", result.get( 4 ), "Fifth difference should be 'Code'." );
	}

	@Test
	void testRelativePathElements_relativePaths()
	{
		Path shorterPath = Paths.get( "a" );
		Path longerPath = Paths.get( "a/b/c" );

		List< String > result = ZarrOnFileSystemUtils.relativePathElements( shorterPath, longerPath );

		assertEquals( 2, result.size(), "Expected 2 folder differences." );
		assertEquals( "b", result.get( 0 ), "First difference should be 'b'." );
		assertEquals( "c", result.get( 1 ), "Second difference should be 'c'." );
	}

	@Test
	void testRelativePathElements_differentRelativePaths()
	{
		Path shorterPath = Paths.get( ".." );
		Path longerPath = Paths.get( "../longer" );

		List< String > result = ZarrOnFileSystemUtils.relativePathElements( shorterPath, longerPath );

		assertEquals( 1, result.size(), "Expected 1 folder difference." );
		assertEquals( "longer", result.get( 0 ), "Expected the difference to be 'longer'." );
	}

	@Test
	void testRelativePathElements_pathsDoNotAlign()
	{
		Path shorterPath = Paths.get( "/differentRoot/folderB" );
		Path longerPath = Paths.get( "/root/folderA" );

		assertThrows(
				IllegalArgumentException.class, () -> {
					ZarrOnFileSystemUtils.relativePathElements( shorterPath, longerPath );
				}, "Expected IllegalArgumentException because paths do not align."
		);
	}
}
