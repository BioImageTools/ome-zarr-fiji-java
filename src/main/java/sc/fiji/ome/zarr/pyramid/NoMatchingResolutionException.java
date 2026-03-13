package sc.fiji.ome.zarr.pyramid;

public class NoMatchingResolutionException extends RuntimeException
{

	public NoMatchingResolutionException( final int preferredWidth, final int minWidth )
	{
		super( "No resolution level fitting the preferred width <= " + preferredWidth
				+ " pixels found.\nSmallest available resolution has a width of "
				+ minWidth + " pixels." );
	}
}
