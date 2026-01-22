package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class ZarrOnFileSystemUtilsTest
{

	private Path resourcePath( String resource ) throws URISyntaxException
	{
		URL url = getClass().getClassLoader().getResource( resource );
		assertNotNull( url, "Resource folder not found: " + resource );
		return Paths.get( url.toURI() );
	}

	@Test
	void testFindImageRootFolder_startOnRootFolder() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example"
		};

		for ( String example : examples )
		{
			Path path = resourcePath( example );
			Path result = ZarrOnFileSystemUtils.findImageRootFolder( path );
			assertNull( result, "Expected null for root folder: " + example );
		}
	}

	@Test
	void testFindImageRootFolder_startOnLevelOneFolder() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0"
		};

		for ( String example : examples )
		{
			Path path = resourcePath( example );
			Path result = ZarrOnFileSystemUtils.findImageRootFolder( path );
			assertEquals( path, result, "Expected image root folder for: " + example );
		}
	}

	@Test
	void testFindImageRootFolder_startOnLevelTwoFolder() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0/image",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image"
		};

		for ( String example : examples )
		{
			Path startPath = resourcePath( example );

			// Expected parent folder
			Path expectedPath = startPath.getParent();
			Path result = ZarrOnFileSystemUtils.findImageRootFolder( startPath );

			assertEquals( expectedPath, result, "Expected image root folder for: " + example );
		}
	}

	@Test
	void testIsZarrFolder_validZarrFolders() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0/image",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image"
		};

		for ( String example : examples )
		{
			Path path = resourcePath( example );
			assertTrue( ZarrOnFileSystemUtils.isZarrFolder( path ) );
		}
	}

	@Test
	void testIsZarrFolder_invalidZarrFolders() throws URISyntaxException
	{
		String[] examples = {
				"sc/fiji/ome/zarr/util",
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example/scale0/image/0",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example/scale0/image/c/0"
		};

		for ( String example : examples )
		{
			Path path = resourcePath( example );
			assertFalse( ZarrOnFileSystemUtils.isZarrFolder( path ) );
		}
	}

	@Test
	void testListPathDifferences_noDifferences()
	{
		Path pathA = Paths.get( "/some/path/to/folder" );
		Path pathB = Paths.get( "/some/path/to/folder" );

		List< String > result = ZarrOnFileSystemUtils.listPathDifferences( pathA, pathB );

		assertTrue( result.isEmpty(), "Expected no differences when paths are the same." );
	}

	@Test
	void testListPathDifferences_simpleDifference()
	{
		Path longerPath = Paths.get( "/root/folder/subfolder" );
		Path shorterPath = Paths.get( "/root/folder" );

		List< String > result = ZarrOnFileSystemUtils.listPathDifferences( longerPath, shorterPath );

		assertEquals( 1, result.size(), "Expected exactly 1 folder difference." );
		assertEquals( "subfolder", result.get( 0 ), "Expected the difference to be 'subfolder'." );
	}

	@Test
	void testListPathDifferences_multipleDifferences()
	{
		Path longerPath = Paths.get( "/usr/local/bin/java" );
		Path shorterPath = Paths.get( "/usr" );

		List< String > result = ZarrOnFileSystemUtils.listPathDifferences( longerPath, shorterPath );

		assertEquals( 3, result.size(), "Expected 3 folder differences." );
		assertEquals( "local", result.get( 0 ), "First difference should be 'local'." );
		assertEquals( "bin", result.get( 1 ), "Second difference should be 'bin'." );
		assertEquals( "java", result.get( 2 ), "Third difference should be 'java'." );
	}

	@Test
	void testListPathDifferences_shorterPathIsParentRoot()
	{
		Path longerPath = Paths.get( "C:/Users/John/Documents/Projects/Code" );
		Path shorterPath = Paths.get( "C:/" );

		List< String > result = ZarrOnFileSystemUtils.listPathDifferences( longerPath, shorterPath );

		assertEquals( 5, result.size(), "Expected 5 folder differences." );
		assertEquals( "Users", result.get( 0 ), "First difference should be 'Users'." );
		assertEquals( "John", result.get( 1 ), "Second difference should be 'John'." );
		assertEquals( "Documents", result.get( 2 ), "Third difference should be 'Documents'." );
		assertEquals( "Projects", result.get( 3 ), "Fourth difference should be 'Projects'." );
		assertEquals( "Code", result.get( 4 ), "Fifth difference should be 'Code'." );
	}

	@Test
	void testListPathDifferences_relativePaths()
	{
		Path longerPath = Paths.get( "a/b/c" );
		Path shorterPath = Paths.get( "a" );

		List< String > result = ZarrOnFileSystemUtils.listPathDifferences( longerPath, shorterPath );

		assertEquals( 2, result.size(), "Expected 2 folder differences." );
		assertEquals( "b", result.get( 0 ), "First difference should be 'b'." );
		assertEquals( "c", result.get( 1 ), "Second difference should be 'c'." );
	}

	@Test
	void testListPathDifferences_differentRelativePaths()
	{
		Path longerPath = Paths.get( "../longer" );
		Path shorterPath = Paths.get( ".." );

		List< String > result = ZarrOnFileSystemUtils.listPathDifferences( longerPath, shorterPath );

		assertEquals( 1, result.size(), "Expected 1 folder difference." );
		assertEquals( "longer", result.get( 0 ), "Expected the difference to be 'longer'." );
	}
}
