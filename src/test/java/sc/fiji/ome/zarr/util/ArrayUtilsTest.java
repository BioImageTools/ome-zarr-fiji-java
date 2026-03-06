package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArrayUtilsTest
{
	@Test
	void testReversedCopyWithStrings()
	{
		String[] input = { "apple", "banana", "cherry" };
		String[] expected = { "cherry", "banana", "apple" };
		assertArrayEquals( expected, ArrayUtils.reversedCopy( input ) );
	}

	@Test
	void testReversedCopyWithIntegers()
	{
		Integer[] input = { 1, 2, 3, 4, 5 };
		Integer[] expected = { 5, 4, 3, 2, 1 };
		assertArrayEquals( expected, ArrayUtils.reversedCopy( input ) );
	}

	@Test
	void testReversedCopyWithEmptyArray()
	{
		String[] input = {};
		String[] expected = {};
		assertArrayEquals( expected, ArrayUtils.reversedCopy( input ) );
	}

	@Test
	void testReversedCopyWithNullArray()
	{
		assertThrows( NullPointerException.class, () -> ArrayUtils.reversedCopy( null ) );
	}
}
