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
package sc.fiji.ome.zarr.pyramid.metadata.adapter;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05Metadata;

import sc.fiji.ome.zarr.pyramid.exceptions.NotAMultiscaleImageException;

/**
 * Picks the appropriate {@link MetadataAdapter} for a given parsed
 * OME-NGFF {@link N5Metadata} (v03, v04, or v05).
 */
public class MetadataAdapterFactory
{
	private MetadataAdapterFactory()
	{}

	public static MetadataAdapter getAdapter( final N5Metadata metadata, final N5Reader reader, final N5TreeNode node )
	{
		if ( metadata instanceof OmeNgffV05Metadata )
			return new V05MetadataAdapter( reader, node );
		if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata )
			return new V04MetadataAdapter( reader, node );
		if ( metadata instanceof OmeNgffMetadata )
			return new V03MetadataAdapter( reader, node );
		throw new NotAMultiscaleImageException(
				"Unsupported multiscale metadata type: " + metadata.getClass() );
	}
}