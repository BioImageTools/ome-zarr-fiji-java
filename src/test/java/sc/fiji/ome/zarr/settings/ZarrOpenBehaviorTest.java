package sc.fiji.ome.zarr.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

/**
 * Unit tests for the {@link ZarrOpenBehavior#getByName(String)} method.
 * This method retrieves the corresponding {@link ZarrOpenBehavior} enum value
 * by its name or throws a {@link NoSuchElementException} if not found.
 */
class ZarrOpenBehaviorTest
{

	@Test
	void testGetByNameWithValidEnumName()
	{
		// Test that fetching a valid enum name returns the corresponding enum instance
		ZarrOpenBehavior result = ZarrOpenBehavior.getByName( "IMAGEJ_HIGHEST_RESOLUTION" );
		assertNotNull( result );
		assertEquals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION, result );
	}

	@Test
	void testGetByDescriptionWithValidDescription()
	{
		// Test that fetching a valid description returns the corresponding enum instance
		ZarrOpenBehavior result = ZarrOpenBehavior.getByDescription( "Open the highest available single-resolution in ImageJ" );
		assertNotNull( result );
		assertEquals( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION, result );
	}

	@Test
	void testGetByDescriptionWithNonExistentDescription()
	{
		// Test a description that does not exist, expect a null result
		String invalidDescription = "Non-existent description";
		ZarrOpenBehavior result = ZarrOpenBehavior.getByDescription( invalidDescription );
		assertNull( result );
	}

	@Test
	void testGetByDescriptionWithNullDescription()
	{
		// Test providing a null description, expect a null result
		ZarrOpenBehavior result = ZarrOpenBehavior.getByDescription( null );
		assertNull( result );
	}

	@Test
	void testGetByNameWithNonExistentEnumName()
	{
		// Test an invalid enum name, expect a NoSuchElementException
		String invalidEnumName = "INVALID_OPTION";
		assertThrows( NoSuchElementException.class, () -> ZarrOpenBehavior.getByName( invalidEnumName ) );
	}

	@Test
	void testGetByNameWithNullName()
	{
		// Test passing null to the method, expect a NoSuchElementException
		assertThrows( NoSuchElementException.class, () -> ZarrOpenBehavior.getByName( null ) );
	}
}
