package sc.fiji.ome.zarr.pyramid;

public class NotAMultiscaleImageException extends RuntimeException
{

	public NotAMultiscaleImageException( final String path )
	{
		super( "The dataset at path '" + path + "' is not a valid OME-Zarr multiscale image." );
	}

	public NotAMultiscaleImageException( final String path, final Throwable cause )
	{
		super( "The dataset at path '" + path + "' is not a valid OME-Zarr multiscale image. Cause: " + cause.getMessage(), cause );
	}
}
