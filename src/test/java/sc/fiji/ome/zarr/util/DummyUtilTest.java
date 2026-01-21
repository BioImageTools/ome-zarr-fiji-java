package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DummyUtilTest
{
	@Test
	void dummyTest()
	{
		assertEquals( "hello world", DummyUtil.dummyMethod(), "Dummy method should return 'hello world'" );
	}

	@Test
	void testHello()
	{
		assertEquals( "hello", DummyUtil.hello(), "hello method should return 'hello'" );
	}
}
