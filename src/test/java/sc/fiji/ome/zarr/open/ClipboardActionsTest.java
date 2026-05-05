package sc.fiji.ome.zarr.open;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.scijava.Context;

import sc.fiji.ome.zarr.util.ZarrTestUtils;

class ClipboardActionsTest
{
	private final List< String > errors = new ArrayList<>();

	private final Consumer< String > errorHandler = errors::add;

	@Test
	void nonZarrLocalPathReportsError() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing" );
			Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents( new StringSelection( path.toString() ), null );
			boolean result = ClipboardActions.pasteFromClipboard( context, errorHandler );
			assertFalse( result );
			assertEquals( 1, errors.size() );
			assertTrue( errors.get( 0 ).contains( "OME-Zarr" ) );
		}
	}
}
