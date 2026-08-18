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
package ome.zarr.fijiui.plugin;

import org.scijava.Context;
import org.scijava.desktop.links.LinkHandler;
import org.scijava.desktop.links.AbstractLinkHandler;
import org.scijava.desktop.links.Links;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import ome.zarr.fijiui.open.ZarrOpenActions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Plugin( type = LinkHandler.class )
public class LinkHandlerPlugin extends AbstractLinkHandler
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String APP_NAME = "fiji";

	@Parameter
	private Context context;

	@Override
	public boolean supports( final URI uri )
	{
		return APP_NAME.equals( uri.getScheme() ) &&
				uri.getHost().equals( "open" ) &&
				uri.getQuery().contains( "zarr" );
	}

	@Override
	public void handle( URI uri )
	{
		String op = Links.operation( uri );
		if ( op.equals( "file" ) )
		{
			logger.debug( "open file URI: {}", uri );
			String path = uri.getQuery().split( "=" )[ 1 ];
			logger.debug( "open file path: {}", path );
			new ZarrOpenActions( Paths.get( path ).toUri(), context ).openIJWithImage();
		}
		else if ( op.equals( "url" ) )
		{
			logger.debug( "open url URI: {}", uri );
			String path = uri.getQuery().split( "=" )[ 1 ];
			logger.debug( "open remote path: {}", path );
		}
		else
		{
			logger.warn( "Sorry, don't know how to open this URI: {}", uri );
		}
	}

	@Override
	public List< String > getSchemes()
	{
		// makes sure that the following schemes are registered with the OS
		return Arrays.asList( APP_NAME );
	}
}
