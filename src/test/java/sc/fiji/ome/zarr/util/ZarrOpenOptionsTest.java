package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

/**
 * Unit tests for the {@link ZarrOpenOptions#getByName(String)} method.
 * This method retrieves the corresponding {@link ZarrOpenOptions} enum value
 * by its name or throws a {@link NoSuchElementException} if not found.
 */
class ZarrOpenOptionsTest
{

	@Test
	void testGetByNameWithValidEnumName()
	{
		// Test that fetching a valid enum name returns the corresponding enum instance
		ZarrOpenOptions result = ZarrOpenOptions.getByName( "IMAGEJ_HIGHEST_RESOLUTION" );
		assertNotNull( result );
		assertEquals( ZarrOpenOptions.IMAGEJ_HIGHEST_RESOLUTION, result );
	}

	@Test
	void testGetByDescriptionWithValidDescription()
	{
		// Test that fetching a valid description returns the corresponding enum instance
		ZarrOpenOptions result = ZarrOpenOptions.getByDescription( "Open highest possible resolution in ImageJ" );
		assertNotNull( result );
		assertEquals( ZarrOpenOptions.IMAGEJ_HIGHEST_RESOLUTION, result );
	}

	@Test
	void testGetByDescriptionWithNonExistentDescription()
	{
		// Test a description that does not exist, expect a null result
		String invalidDescription = "Non-existent description";
		ZarrOpenOptions result = ZarrOpenOptions.getByDescription( invalidDescription );
		assertNull( result );
	}

	@Test
	void testGetByDescriptionWithNullDescription()
	{
		// Test providing a null description, expect a null result
		ZarrOpenOptions result = ZarrOpenOptions.getByDescription( null );
		assertNull( result );
	}

	@Test
	void testGetByNameWithNonExistentEnumName()
	{
		// Test an invalid enum name, expect a NoSuchElementException
		String invalidEnumName = "INVALID_OPTION";
		assertThrows( NoSuchElementException.class, () -> ZarrOpenOptions.getByName( invalidEnumName ) );
	}

	@Test
	void testGetByNameWithNullName()
	{
		// Test passing null to the method, expect a NoSuchElementException
		assertThrows( NoSuchElementException.class, () -> ZarrOpenOptions.getByName( null ) );
	}
}
