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
}
