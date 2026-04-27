package sc.fiji.ome.zarr.plugins;

import org.scijava.Context;
import org.scijava.desktop.links.LinkHandler;
import org.scijava.desktop.links.AbstractLinkHandler;
import org.scijava.desktop.links.Links;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.ome.zarr.util.ZarrOpenActions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Plugin( type = LinkHandler.class )
public class LinkHandlerPlugin extends AbstractLinkHandler
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HANDLER_NAME = "ZarrHandlerPlugin";

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
			logger.info( "open file URI: " + uri );
			String path = uri.getQuery().split( "=" )[ 1 ];
			logger.info( "open file path: " + path );
			new ZarrOpenActions( Path.of( path ), context ).openIJWithImage();
		}
		else if ( op.equals( "url" ) )
		{
			logger.info( "open url URI: " + uri );
			String path = uri.getQuery().split( "=" )[ 1 ];
			logger.info( "open remote path: " + path );
		}
		else
		{
			logger.info( "Sorry, don't know how to open this URI: " + uri );
		}
	}

	@Override
	public List< String > getSchemes()
	{
		// makes sure that the following schemes are registered with the OS
		return Arrays.asList( APP_NAME );
	}
}
