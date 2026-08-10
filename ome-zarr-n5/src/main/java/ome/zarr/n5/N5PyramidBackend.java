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
package ome.zarr.n5;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Exception;
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

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import software.amazon.awssdk.regions.Region;

import ome.zarr.imglib2.AbstractPyramidBackend;
import ome.zarr.imglib2.Affine3DUtils;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.ZarrUtils;
import ome.zarr.imglib2.exceptions.MultiImageDatasetException;
import ome.zarr.imglib2.exceptions.NotAMultiscaleImageException;
import ome.zarr.imglib2.exceptions.StoreAccessException;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;

/**
 * {@link PyramidBackend} that reads OME-Zarr images with the N5 universe
 * library. Supports OME-Zarr v0.3, v0.4, and v0.5 (N5 reads Zarr v2 and the
 * Zarr v3 variant used by v0.5).
 */
public class N5PyramidBackend extends AbstractPyramidBackend
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * Convenience entry point for reading an OME-Zarr image with the N5 backend
	 * without first constructing a backend instance. Equivalent to
	 * {@code new N5PyramidBackend().load( inputUri )}.
	 *
	 * @param <T> pixel type of the image being read
	 * @param inputUri location of the OME-Zarr root; either a {@code file:} URI
	 *   for local datasets or an {@code http(s):} URI for remote datasets
	 */
	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > open( final URI inputUri )
	{
		return new N5PyramidBackend().load( inputUri );
	}

	@Override
	protected < T extends NativeType< T > & RealType< T > > PyramidContents< T > loadMultiscale( final URI inputUri )
	{
		final N5Reader reader;
		final N5TreeNode treeNode = new N5TreeNode( "" );
		final OmeNgffMetadata metadata;
		try
		{
			reader = openReader( inputUri );
			metadata = readMetadata( reader, treeNode, inputUri );
		}
		catch ( N5Exception e )
		{
			// Store-level failure (e.g., S3 auth failure, missing bucket, network
			// error) before we could reach the dataset. Wrap in a backend-agnostic
			// exception.
			throw new StoreAccessException( inputUri.toString(), e );
		}
		final Multiscale multiscale = buildMultiscale( metadata, 0 );
		final Omero omero = readOmeroMetadata( reader, treeNode );

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

	private static N5Reader openReader( final URI uri )
	{
		final N5Factory factory = new N5Factory();
		// The region default only matters for s3:// URIs.
		if ( "s3".equalsIgnoreCase( uri.getScheme() ) )
			factory.s3Configuration( builder -> builder.region( Region.US_EAST_1 ) );
		return factory.openReader( uri.toString() );
	}

	private OmeNgffMetadata readMetadata( final N5Reader reader, final N5TreeNode node, final URI inputUri )
	{
		final OmeNgffMetadata metadata = readMetadataOrNull( reader, node );
		if ( metadata == null )
		{
			if ( isBioformats2rawLayout( reader ) )
				throw new MultiImageDatasetException( inputUri.toString() );
			throw new NotAMultiscaleImageException( inputUri.toString() );
		}
		return metadata;
	}

	/**
	 * Parses OME-NGFF multiscales metadata at {@code node}, or returns
	 * {@code null} when the node carries none (a bare array, or a group that is
	 * not a multiscales group). Unlike {@link #readMetadata}, this never throws
	 * for a missing multiscale, so callers walking to a parent can fall back
	 * cleanly.
	 */
	private OmeNgffMetadata readMetadataOrNull( final N5Reader reader, final N5TreeNode node )
	{
		final List< N5MetadataParser< ? > > parsers = Collections.singletonList( new OmeNgffMetadataParser( reader ) );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, parsers );
		final N5Metadata n5Metadata = node.getMetadata();
		return n5Metadata == null ? null : Cast.unchecked( n5Metadata );
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Parses the parent's OME-NGFF multiscales metadata and matches
	 * {@code arrayUri} against the dataset path of each resolution level.
	 */
	@Override
	protected < T extends NativeType< T > & RealType< T > > PyramidContents< T > tryLoadLevelFromParent(
			final URI parentUri, final URI arrayUri )
	{
		final OmeNgffMetadata parentMetadata;
		final Multiscale multiscale;
		final Omero omero;
		// the reader that backs the returned image is opened separately below and must stay open.
		try (N5Reader readerForParent = openReader( parentUri ))
		{
			final N5TreeNode node = new N5TreeNode( "" );
			parentMetadata = readMetadataOrNull( readerForParent, node );
			if ( parentMetadata == null )
				return null;
			multiscale = buildMultiscale( parentMetadata, 0 );
			omero = readOmeroMetadata( readerForParent, node );
		}
		catch ( RuntimeException e )
		{
			logger.debug( "Parent of {} is not a usable multiscales group: {}", arrayUri, e.getMessage() );
			return null;
		}
		final ResolutionLevel matched = levelForChild( multiscale, arrayUri );
		if ( matched == null )
		{
			logger.debug( "Parent multiscales at {} does not list child array {}", parentUri, arrayUri );
			return null;
		}
		final SpatialMetadataGroup< ? > spatialMetadata = Cast.unchecked( parentMetadata );
		final AffineTransform3D transform = spatialMetadata.spatialTransforms3d()[ matched.index ];
		final T type = N5Utils.type( multiscale.getDataType() );
		final CachedCellImg< T, ? > img = N5Utils.openVolatile( openReader( parentUri ), matched.datasetPath );
		return PyramidContents.singleLevel( multiscale.getName(), type, transform, img, createAxisCalibrations( matched ), omero );
	}

	private static ResolutionLevel levelForChild( final Multiscale multiscale, final URI arrayUri )
	{
		for ( final ResolutionLevel level : multiscale.getLevels() )
			if ( ZarrUtils.isChildPath( arrayUri, level.datasetPath ) )
				return level;
		return null;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Takes the axis names from the array's {@code dimension_names} attribute (see
	 * {@link #readDimensionNames}), which only a Zarr v3 array has; a Zarr v2 array
	 * therefore declines.
	 */
	@Override
	protected < T extends NativeType< T > & RealType< T > > PyramidContents< T > tryLoadArrayNodeOnly( final URI arrayUri )
	{
		final String[] names;
		final DataType dataType;
		// The reader that backs the returned image is opened separately below and must stay open.
		try (N5Reader metadataReader = openReader( arrayUri ))
		{
			names = readDimensionNames( metadataReader );
			final DatasetAttributes attributes = metadataReader.getDatasetAttributes( "" );
			if ( names.length == 0 || attributes == null )
				return null;
			dataType = attributes.getDataType();
		}
		catch ( RuntimeException e )
		{
			logger.debug( "Could not open {} as a plain array: {}", arrayUri, e.getMessage() );
			return null;
		}
		final T type = N5Utils.type( dataType );
		final AxisCalibration[] axes = AxisCalibration.fromZarrDimensionNames( names );
		final CachedCellImg< T, ? > img = N5Utils.openVolatile( openReader( arrayUri ), "" );
		return PyramidContents.singleLevel( ZarrUtils.lastSegment( arrayUri ), type, new AffineTransform3D(), img, axes, null );
	}

	/**
	 * Reads the Zarr v3 {@code dimension_names} attribute of the array node, or an
	 * empty array when absent (e.g. a Zarr v2 array, which has none).
	 * <p>
	 * NB: this relies on the N5 zarr reader surfacing {@code dimension_names} as a
	 * top-level attribute; it is a best-effort fallback only. The parent-group
	 * path above is the primary route and supplies axis names for both Zarr v2
	 * and v3, so an empty result here simply means an uncalibratable lone array.
	 */
	private static String[] readDimensionNames( final N5Reader reader )
	{
		try
		{
			final String[] names = reader.getAttribute( "", "dimension_names", String[].class );
			return names != null ? names : new String[ 0 ];
		}
		catch ( RuntimeException e )
		{
			return new String[ 0 ];
		}
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
			levels.add( new ResolutionLevel( children[ i ].getPath(), i, children[ i ].getAxes(), children[ i ].getScale() ) );
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

		private final Axis[] axes;

		private final double[] scales;

		private ResolutionLevel( final String datasetPath, final int index, final Axis[] axes, final double[] scales )
		{
			this.datasetPath = datasetPath;
			this.index = index;
			this.axes = axes;
			this.scales = scales;
		}
	}
}
