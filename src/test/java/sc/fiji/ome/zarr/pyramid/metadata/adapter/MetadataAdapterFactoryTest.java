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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata;
import org.junit.jupiter.api.Test;

import sc.fiji.ome.zarr.pyramid.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.metadata.Multiscale;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class MetadataAdapterFactoryTest
{
	private static final String V04_FIXTURE = "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr";

	private static final String V05_FIXTURE = "sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr";

	@Test
	void v04MetadataYieldsV04Adapter() throws URISyntaxException
	{
		Loaded loaded = load( V04_FIXTURE );
		MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( loaded.metadata, loaded.reader, loaded.node );
		assertInstanceOf( V04MetadataAdapter.class, adapter );
	}

	@Test
	void v05MetadataYieldsV05Adapter() throws URISyntaxException
	{
		Loaded loaded = load( V05_FIXTURE );
		MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( loaded.metadata, loaded.reader, loaded.node );
		assertInstanceOf( V05MetadataAdapter.class, adapter );
	}

	@Test
	void v03MetadataYieldsV03Adapter()
	{
		// No v03 fixture exists; a mock is enough to exercise the instanceof dispatch
		org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata v03 = mock( OmeNgffMetadata.class );
		MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( v03, mock( N5Reader.class ), new N5TreeNode( "" ) );
		assertInstanceOf( V03MetadataAdapter.class, adapter );
	}

	@Test
	@SuppressWarnings( "resource" )
	void unknownMetadataThrows()
	{
		N5Metadata unknown = mock( N5Metadata.class );
		N5Reader reader = mock( N5Reader.class );
		N5TreeNode node = new N5TreeNode( "" );
		assertThrows( NotAMultiscaleImageException.class,
				() -> MetadataAdapterFactory.getAdapter( unknown, reader, node ) );
	}

	@Test
	void initMultiscaleProducesExpectedPyramid() throws URISyntaxException
	{
		Loaded loaded = load( V05_FIXTURE );
		MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( loaded.metadata, loaded.reader, loaded.node );
		Multiscale multiscale = adapter.initMultiscale( loaded.metadata, 0 );
		assertNotNull( multiscale );
		assertEquals( 2, multiscale.numResolutionLevels() );
		assertEquals( DataType.UINT8, multiscale.getDataType() );
		assertEquals( 0, multiscale.getLevels().get( 0 ).getIndex() );
		assertEquals( 1, multiscale.getLevels().get( 1 ).getIndex() );
	}

	private static Loaded load( final String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		N5Reader reader = new N5Factory().openReader( path.toUri().toString() );
		N5TreeNode node = new N5TreeNode( "" );
		List< N5MetadataParser< ? > > parsers = Arrays.asList(
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadataParser(),
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser(),
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05MetadataParser() );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, new ArrayList<>( parsers ) );
		N5Metadata metadata = node.getMetadata();
		assertNotNull( metadata, "Parser produced no metadata for " + resource );
		return new Loaded( reader, node, metadata );
	}

	private static final class Loaded
	{
		final N5Reader reader;

		final N5TreeNode node;

		final N5Metadata metadata;

		Loaded( final N5Reader reader, final N5TreeNode node, final N5Metadata metadata )
		{
			this.reader = reader;
			this.node = node;
			this.metadata = metadata;
		}
	}
}
