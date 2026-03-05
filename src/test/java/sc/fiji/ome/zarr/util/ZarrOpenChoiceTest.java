package sc.fiji.ome.zarr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

/**
 * Unit tests for the {@link ZarrOpenChoice#getByName(String)} method.
 * This method retrieves the corresponding {@link ZarrOpenChoice} enum value
 * by its name or throws a {@link NoSuchElementException} if not found.
 */
class ZarrOpenChoiceTest
{

	@Test
	void testGetByNameWithValidEnumName()
	{
		// Test that fetching a valid enum name returns the corresponding enum instance
		ZarrOpenChoice result = ZarrOpenChoice.getByName( "IMAGEJ_HIGHEST_RESOLUTION" );
		assertNotNull( result );
		assertEquals( ZarrOpenChoice.IMAGEJ_HIGHEST_RESOLUTION, result );
	}

	@Test
	void testGetByDescriptionWithValidDescription()
	{
		// Test that fetching a valid description returns the corresponding enum instance
		ZarrOpenChoice result = ZarrOpenChoice.getByDescription( "Open highest possible resolution in ImageJ" );
		assertNotNull( result );
		assertEquals( ZarrOpenChoice.IMAGEJ_HIGHEST_RESOLUTION, result );
	}

	@Test
	void testGetByDescriptionWithNonExistentDescription()
	{
		// Test a description that does not exist, expect a null result
		String invalidDescription = "Non-existent description";
		ZarrOpenChoice result = ZarrOpenChoice.getByDescription( invalidDescription );
		assertNull( result );
	}

	@Test
	void testGetByDescriptionWithNullDescription()
	{
		// Test providing a null description, expect a null result
		ZarrOpenChoice result = ZarrOpenChoice.getByDescription( null );
		assertNull( result );
	}

	@Test
	void testGetByNameWithNonExistentEnumName()
	{
		// Test an invalid enum name, expect a NoSuchElementException
		String invalidEnumName = "INVALID_OPTION";
		assertThrows( NoSuchElementException.class, () -> ZarrOpenChoice.getByName( invalidEnumName ) );
	}

	@Test
	void testGetByNameWithNullName()
	{
		// Test passing null to the method, expect a NoSuchElementException
		assertThrows( NoSuchElementException.class, () -> ZarrOpenChoice.getByName( null ) );
	}
}
