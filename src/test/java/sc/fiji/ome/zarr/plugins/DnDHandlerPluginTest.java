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
package sc.fiji.ome.zarr.plugins;

import static org.mockito.Mockito.times;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.scijava.Context;
import org.scijava.io.location.FileLocation;

import sc.fiji.ome.zarr.open.ZarrOpenActions;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class DnDHandlerPluginTest
{
	@Test
	void openDelegatesToZarrOpenActions() throws URISyntaxException, IOException
	{
		try (Context context = new Context())
		{
			final Path path = ZarrTestUtils.resourcePath( "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr/" );
			final FileLocation fileLocation = new FileLocation( path.toUri() );

			final DnDHandlerPlugin dnDHandlerPlugin = new DnDHandlerPlugin();
			dnDHandlerPlugin.setContext( context );

			try (MockedStatic< ZarrOpenActions > mocked = Mockito.mockStatic( ZarrOpenActions.class ))
			{
				dnDHandlerPlugin.open( fileLocation );
				mocked.verify( () -> ZarrOpenActions.openWithSettings( path.toUri(), context ), times( 1 ) );
			}
		}
	}
}
