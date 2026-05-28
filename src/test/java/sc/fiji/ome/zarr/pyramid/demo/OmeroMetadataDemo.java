package sc.fiji.ome.zarr.pyramid.demo;

import java.net.URI;

import org.scijava.Context;

import sc.fiji.ome.zarr.pyramid.Pyramidal5DImageData;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

/**
 * Demonstrates loading a remote OME-Zarr dataset via the static
 * {@link Pyramidal5DImageData} factory methods and printing the OMERO metadata.
 * Dataset: IDR idr0033A BR00109990_C2
 * URI: <a href="https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0">https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0</a>
 * Run with: mvn exec:java -Dexec.mainClass=sc.fiji.ome.zarr.pyramid.demo.OmeroMetadataDemo
 */
public class OmeroMetadataDemo
{
	private static final String ZARR_URI = "https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0";

	public static void main( final String[] args ) throws Exception
	{
		final URI uri = new URI( ZARR_URI );

		System.out.println( "Opening OME-Zarr: " + uri );
		System.out.println();

		try (Context context = new Context())
		{
			// --- open with N5 backend (default) ---
			System.out.println( "=== openWithN5 ===" );
			printInfo( Pyramidal5DImageData.open( context, uri, null ) );

			// --- open with zarr-java backend ---
			System.out.println( "=== openWithZarrJava ===" );
			printInfo( Pyramidal5DImageData.openWithZarrJava( context, uri, null ) );
		}
	}

	private static void printInfo( final Pyramidal5DImageData< ? > data )
	{
		final Omero omero = data.getOmeroProperties();
		if ( omero == null )
		{
			System.out.println( "OMERO metadata    : not available" );
		}
		else
		{
			System.out.println( "--- OMERO metadata ---" );
			System.out.println( "  id              : " + omero.id );
			System.out.println( "  name            : " + omero.name );

			if ( omero.rdefs != null )
			{
				System.out.println( "  rdefs.defaultT  : " + omero.rdefs.defaultT );
				System.out.println( "  rdefs.defaultZ  : " + omero.rdefs.defaultZ );
				System.out.println( "  rdefs.model     : " + omero.rdefs.model );
			}

			if ( omero.channels != null )
			{
				System.out.println( "  channels (" + omero.channels.size() + "):" );
				for ( int i = 0; i < omero.channels.size(); i++ )
				{
					final Omero.Channel ch = omero.channels.get( i );
					System.out.printf( "    [%d] label=%-20s active=%-5b color=%s%n",
							i, ch.label, ch.active, ch.color );
					if ( ch.window != null )
						System.out.printf( "        window: start=%.1f end=%.1f  min=%.1f max=%.1f%n",
								ch.window.start, ch.window.end, ch.window.min, ch.window.max );
				}
			}
		}
		System.out.println();
	}
}
