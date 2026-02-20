package sc.fiji.ome.zarr.pyramid;

public class NotASingleScaleImageException extends RuntimeException
{

	public NotASingleScaleImageException( final String path )
	{
		super( "The dataset at path '" + path + "' is not a valid OME-Zarr single scale image." );
	}

	public NotASingleScaleImageException( final String path, final Throwable cause )
	{
		super( "The dataset at path '" + path + "' is not a valid OME-Zarr single scale image. Cause: " + cause.getMessage(), cause );
	}
}
