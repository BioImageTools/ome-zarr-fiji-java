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
package ome.zarr.fiji.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import ome.zarr.imglib2.metadata.Omero;

class BdvUtilsTest
{
	/** Omero channels are applied only when there are as many of them as there are sources. */
	@Test
	void testOmeroChannelCountMismatch()
	{
		final Omero omero = new Omero();
		omero.channels = Arrays.asList( new Omero.Channel(), new Omero.Channel(), new Omero.Channel() );
		final int numChannels = omero.channels.size();
		assertEquals( numChannels, BdvUtils.omeroChannels( omero, numChannels ).size(), "as many channels as sources" );
		assertTrue( BdvUtils.omeroChannels( omero, numChannels + 1 ).isEmpty(), "fewer channels than sources" );
		assertTrue( BdvUtils.omeroChannels( omero, numChannels - 1 ).isEmpty(), "more channels than sources" );
		// empty, never null, so callers test isEmpty() and never dereference null
		assertTrue( BdvUtils.omeroChannels( null, numChannels ).isEmpty(), "no omero metadata at all" );
	}
}
