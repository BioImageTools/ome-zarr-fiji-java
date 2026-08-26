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
package ome.zarr.fijiui.plugin;

import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.location.Location;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;

import ome.zarr.fijiui.open.ZarrOpenActions;
import ome.zarr.imglib2.ZarrUtils;

/**
 * SciJava {@link IOPlugin} that claims OME-Zarr locations and opens them via
 * {@link ZarrOpenActions#openWithSettings(URI, org.scijava.Context)}.
 * <p>
 * Besides drag-and-drop, this is also what makes {@code fiji://open/...} links
 * work. Fiji-Latest ships {@code fiji-links}, whose {@code OpenLinkHandler}
 * parses the link, resolves {@code ?p=} into a {@link Location} and then calls
 * {@code IOService.open(Location)} – which dispatches to whichever
 * {@code IOPlugin} claims that location, i.e. to this one.
 * </p>
 * <p>
 * Both local and remote locations are accepted, because {@code fiji-links}
 * resolves {@code fiji://open/file?p=} to a {@code FileLocation} but
 * {@code fiji://open/url?p=} to an {@code HTTPLocation} (or
 * {@code URLLocation}). Either way we only need {@link Location#getURI()}: it
 * yields the URI for anything that can be expressed as one, and {@code null}
 * otherwise (e.g., an in-memory {@code BytesLocation}), which we decline.</p>
 * <p>
 * NB: for remote locations {@link #supportsOpen} costs a few HTTP HEAD requests, see
 * {@link ZarrUtils#isZarr(URI)}.
 * </p>
 * <p>
 * {@code s3:} URIs are out of reach here: {@code fiji-links} cannot resolve an
 * {@code s3://} string to a {@link Location}, so no {@code s3:} URI ever
 * arrives here currently.
 * </p>
 */
@SuppressWarnings( "java:S110" ) // NB: deliberately extends AbstractIOPlugin, which is a long class hierarchy
@Plugin( type = IOPlugin.class, attrs = @Attr( name = "eager" ) )
public class OmeZarrIOPlugin extends AbstractIOPlugin< Object >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	//the "innocent" product of the (hypothetical) file reading... which Fiji will not display
	private static final Object FAKE_INPUT = new ArrayList<>( 0 );

	@Parameter
	private PrefService prefService;

	@Override
	public boolean supportsOpen( final Location source )
	{
		final URI uri = source.getURI();
		logger.debug( "OME-Zarr IO plugin: supportsOpen check, location type={}, uri={}", source.getClass().getSimpleName(), uri );
		return ZarrUtils.isZarr( uri );
	}

	@Override
	public Object open( final Location source ) throws IOException
	{
		logger.debug( "OME-Zarr IO plugin: open, location type={}", source.getClass().getSimpleName() );
		final URI inputUri = source.getURI();
		if ( inputUri == null )
			throw new IOException( "Cannot express as a URI, and therefore not open: " + source );

		logger.debug( "OME-Zarr IO plugin: opening {}", inputUri );

		ZarrOpenActions.openWithSettings( inputUri, context() );

		// Returning a non-null object tells SciJava's IO subsystem the drop was fully
		// handled. It then tries to display the result, finds it cannot, and silently
		// gives up — which is what we want, since openWithSettings has already done
		// the displaying (or put the action chooser on screen).
		return FAKE_INPUT;
	}

	@Override
	public Class< Object > getDataType()
	{
		return Object.class;
	}
}
