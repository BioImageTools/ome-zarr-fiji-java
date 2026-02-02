package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZarrTestUtils
{
	static Path resourcePath( String resource ) throws URISyntaxException
	{
		URL url = ZarrTestUtils.class.getClassLoader().getResource( resource );
		assertNotNull( url, "Resource folder not found: " + resource );
		return Paths.get( url.toURI() );
	}
}
