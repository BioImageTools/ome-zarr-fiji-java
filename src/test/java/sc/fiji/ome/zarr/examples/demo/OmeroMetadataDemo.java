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
package sc.fiji.ome.zarr.examples.demo;

import java.net.URI;

import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.backend.n5.N5PyramidBackend;
import sc.fiji.ome.zarr.pyramid.backend.zarrjava.ZarrJavaPyramidBackend;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

/**
 * Demonstrates loading a remote OME-Zarr dataset via the {@link N5PyramidBackend}
 * and {@link ZarrJavaPyramidBackend} backends and printing the OMERO metadata.
 * Dataset: IDR idr0033A BR00109990_C2
 * URI: <a href="https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0">https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0</a>
 * Run with: mvn exec:java -Dexec.mainClass=sc.fiji.ome.zarr.examples.demo.OmeroMetadataDemo
 */
public class OmeroMetadataDemo
{
	private static final String ZARR_URI = "https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0";

	public static void main( final String[] args ) throws Exception
	{
		final URI uri = new URI( ZARR_URI );

		System.out.println( "Opening OME-Zarr: " + uri );
		System.out.println();

		// --- open with N5 backend ---
		System.out.println( "=== N5 backend ===" );
		printInfo( new N5PyramidBackend().load( uri ) );

		// --- open with zarr-java backend ---
		System.out.println( "=== zarr-java backend ===" );
		printInfo( new ZarrJavaPyramidBackend().load( uri ) );
	}

	private static void printInfo( final PyramidContents< ? > contents )
	{
		final Omero omero = contents.omero;
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
