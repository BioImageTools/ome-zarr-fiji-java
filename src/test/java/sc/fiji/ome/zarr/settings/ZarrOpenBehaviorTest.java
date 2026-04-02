/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
