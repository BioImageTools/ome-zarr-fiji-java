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

import java.util.ArrayList;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import sc.fiji.ome.zarr.pyramid.metadata.Multiscale;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;
import sc.fiji.ome.zarr.pyramid.metadata.ResolutionLevel;

abstract class AbstractMetadataAdapter implements MetadataAdapter
{
	protected final N5Reader reader;

	protected final N5TreeNode node;

	protected AbstractMetadataAdapter( final N5Reader reader, final N5TreeNode node )
	{
		this.reader = reader;
		this.node = node;
	}

	protected Multiscale buildMultiscale( final String name, final NgffSingleScaleAxesMetadata[] children )
	{
		final List< ResolutionLevel > levels = new ArrayList<>();
		int index = 0;
		for ( NgffSingleScaleAxesMetadata single : children )
		{
			levels.add( new ResolutionLevel( single.getPath(), index++, single.getAttributes(), single.getAxes(), null, null,
					single.getScale() ) );
		}
		return new Multiscale( name, levels, children[ 0 ].getAttributes().getDataType() );
	}

	@Override
	public Omero initOmeroMetadata()
	{
		final JsonElement base = reader.getAttribute( node.getPath(), getOmeroKey(), JsonElement.class );
		return new Gson().fromJson( base, Omero.class );
	}

	protected abstract String getOmeroKey();
}