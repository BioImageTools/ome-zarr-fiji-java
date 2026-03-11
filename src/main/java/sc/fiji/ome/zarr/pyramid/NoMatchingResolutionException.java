package sc.fiji.ome.zarr.pyramid;

public class NoMatchingResolutionException extends RuntimeException
{

	public NoMatchingResolutionException( final int preferredWidth, final int minWidth )
	{
		super( "No resolution level with width <= " + preferredWidth + " found. Smallest available resolution has a width of " + minWidth
				+ "." );
	}
}
