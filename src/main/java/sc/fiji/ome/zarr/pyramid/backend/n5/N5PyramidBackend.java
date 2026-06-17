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
package sc.fiji.ome.zarr.pyramid.backend.n5;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import sc.fiji.ome.zarr.pyramid.exceptions.MultiImageDatasetException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.backend.PyramidBackend;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.metadata.AxisCalibration;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;
import sc.fiji.ome.zarr.pyramid.Affine3DUtils;

/**
 * {@link PyramidBackend} that reads OME-Zarr images with the N5 universe
 * library. Supports OME-Zarr v0.3, v0.4 and v0.5 (N5 reads Zarr v2 and the
 * Zarr v3 variant used by v0.5).
 */
public class N5PyramidBackend implements PyramidBackend
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Override
	public < T extends NativeType< T > & RealType< T > > PyramidContents< T > load( final URI inputUri )
	{
		final N5Reader reader = new N5Factory().openReader( inputUri.toString() );
		final N5TreeNode treeNode = new N5TreeNode( "" );
		final OmeNgffMetadata metadata = readMetadata( reader, treeNode, inputUri );
		final Multiscale multiscale = buildMultiscale( metadata, 0 );
		final Omero omero = readOmeroMetadata( reader, treeNode );
		final ResolutionLevel level0 = multiscale.getLevels().get( 0 );

		final SpatialMetadataGroup< ? > spatialMetadata = Cast.unchecked( metadata );
		final AffineTransform3D[] transforms = spatialMetadata.spatialTransforms3d();
		if ( !Affine3DUtils.isScaling( transforms[ 0 ], 0.01d ) )
			logger.warn( "The affine transform is not a strict scaling transform. This may cause problems with the image viewer." );
		final T type = N5Utils.type( multiscale.getDataType() );
		final String name = multiscale.getName();
		final int numResolutionLevels = multiscale.numResolutionLevels();

		final CachedCellImg< T, ? >[] cachedCellImgs = Cast.unchecked( new CachedCellImg[ numResolutionLevels ] );
		for ( final ResolutionLevel level : multiscale.getLevels() )
		{
			cachedCellImgs[ level.index ] = N5Utils.openVolatile( reader, level.datasetPath );
		}

		final AxisCalibration[][] axesPerLevel = new AxisCalibration[ numResolutionLevels ][];
		for ( final ResolutionLevel level : multiscale.getLevels() )
		{
			axesPerLevel[ level.index ] = createAxisCalibrations( level );
		}

		return PyramidContents.< T >builder()
				.name( name )
				.type( type )
				.transforms( transforms )
				.cachedCellImgs( cachedCellImgs )
				.axesPerLevel( axesPerLevel )
				.omero( omero )
				.build();
	}

	private OmeNgffMetadata readMetadata( final N5Reader reader, final N5TreeNode node, final URI inputUri )
	{
		final List< N5MetadataParser< ? > > parsers = Collections.singletonList( new OmeNgffMetadataParser( reader ) );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, parsers );
		final N5Metadata n5Metadata = node.getMetadata();
		if ( n5Metadata == null )
		{
			if ( isBioformats2rawLayout( reader ) )
				throw new MultiImageDatasetException( inputUri.toString() );
			throw new NotAMultiscaleImageException( inputUri.toString() );
		}
		return Cast.unchecked( n5Metadata );
	}

	private static boolean isBioformats2rawLayout( final N5Reader reader )
	{
		try
		{
			final JsonElement ome = reader.getAttribute( "", "ome", JsonElement.class );
			return ome != null && ome.isJsonObject() && ome.getAsJsonObject().has( "bioformats2raw.layout" );
		}
		catch ( final RuntimeException e )
		{
			logger.debug( "Could not read 'ome' attribute: {}", e.getMessage() );
			return false;
		}
	}

	private static Multiscale buildMultiscale( final OmeNgffMetadata metadata, final int multiscaleIndex )
	{
		final OmeNgffMultiScaleMetadata ms = metadata.multiscales[ multiscaleIndex ];
		final NgffSingleScaleAxesMetadata[] children = ms.getChildrenMetadata();
		if ( children == null || children.length == 0 || children[ 0 ] == null )
			throw new NotAMultiscaleImageException( "Multiscale metadata does not contain any children attributes." );
		final List< ResolutionLevel > levels = new ArrayList<>();
		for ( int i = 0; i < children.length; i++ )
			levels.add( new ResolutionLevel( children[ i ].getPath(), i, children[ i ].getAttributes(), children[ i ].getAxes(),
					children[ i ].getScale() ) );
		return new Multiscale( ms.name, levels, children[ 0 ].getAttributes().getDataType() );
	}

	private static Omero readOmeroMetadata( final N5Reader reader, final N5TreeNode node )
	{
		final JsonElement base = reader.getAttribute( node.getPath(), "", JsonElement.class );
		final String omeroKey = ( base != null && base.isJsonObject() && base.getAsJsonObject().has( "ome" ) )
				? "ome/omero" : "omero";
		return new Gson().fromJson( reader.getAttribute( node.getPath(), omeroKey, JsonElement.class ), Omero.class );
	}

	// ---------------------------------------------------------------------
	// Axis configuration
	// ---------------------------------------------------------------------

	private static AxisCalibration[] createAxisCalibrations( final ResolutionLevel level )
	{
		if ( level.axes == null )
			return new AxisCalibration[ 0 ];
		final AxisCalibration[] result = new AxisCalibration[ level.axes.length ];
		for ( int i = 0; i < level.axes.length; i++ )
			result[ i ] = new AxisCalibration( level.axes[ i ].getName(), level.axes[ i ].getUnit(), level.scales[ i ] );
		return result;
	}

	// ---------------------------------------------------------------------
	// Multiscale / level value types
	// ---------------------------------------------------------------------

	private static class Multiscale
	{
		private final String name;

		private final List< ResolutionLevel > resolutionLevels;

		private final DataType dataType;

		private Multiscale( final String name, final List< ResolutionLevel > levels, final DataType dataType )
		{
			this.name = name;
			this.resolutionLevels = levels;
			this.dataType = dataType;
		}

		String getName()
		{
			return name;
		}

		int numResolutionLevels()
		{
			return resolutionLevels.size();
		}

		List< ResolutionLevel > getLevels()
		{
			return resolutionLevels;
		}

		DataType getDataType()
		{
			return dataType;
		}
	}

	private static class ResolutionLevel
	{
		private final String datasetPath;

		private final int index;

		private final DatasetAttributes attributes;

		private final Axis[] axes;

		private final double[] scales;

		private ResolutionLevel( final String datasetPath, final int index, final DatasetAttributes attributes,
				final Axis[] axes, final double[] scales )
		{
			this.datasetPath = datasetPath;
			this.index = index;
			this.attributes = attributes;
			this.axes = axes;
			this.scales = scales;
		}
	}
}
