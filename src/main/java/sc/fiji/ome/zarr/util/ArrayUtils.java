package sc.fiji.ome.zarr.util;

public class ArrayUtils
{
	private ArrayUtils()
	{
		// prevent instantiation
	}

	/**
	 * Creates a new array containing all elements of the input array in reversed order.
	 *
	 * @param <T> the type of elements in the array
	 * @param array the input array to be reversed
	 * @return a new array with elements in reversed order compared to the input array
	 */
	public static < T > T[] reversedCopy( T[] array )
	{
		T[] result = array.clone();

		for ( int i = 0, j = array.length - 1; i < array.length; i++, j-- )
		{
			result[ i ] = array[ j ];
		}

		return result;
	}
}
