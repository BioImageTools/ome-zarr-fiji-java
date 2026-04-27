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

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
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
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import bdv.cache.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.backend.PyramidBackend;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;
import sc.fiji.ome.zarr.util.Affine3DUtils;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;

/**
 * {@link PyramidBackend} that reads OME-Zarr images with the N5 universe
 * library. Supports OME-NGFF v0.3, v0.4 and v0.5 (N5 reads Zarr v2 and the
 * Zarr v3 variant used by v0.5).
 *
 * @param <T> pixel type
 * @param <V> volatile pixel type
 */
public class N5PyramidBackend<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
		implements PyramidBackend< T, V >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final Map< String, AxisType > AXIS_MAPPING;

	static
	{
		final Map< String, AxisType > map = new HashMap<>();
		map.put( "x", Axes.X );
		map.put( "y", Axes.Y );
		map.put( "z", Axes.Z );
		map.put( "c", Axes.CHANNEL );
		map.put( "t", Axes.TIME );
		AXIS_MAPPING = Collections.unmodifiableMap( map );
	}

	private final URI inputUri;

	private final Integer preferredMaxWidth;

	public N5PyramidBackend( final URI inputUri )
	{
		this( inputUri, null );
	}

	public N5PyramidBackend( final URI inputUri, final Integer preferredMaxWidth )
	{
		this.inputUri = inputUri;
		this.preferredMaxWidth = preferredMaxWidth;
	}

	@Override
	public PyramidContents< T, V > load()
	{
		final Path inputPath = Paths.get( inputUri );
		final Path rootPath = resolveRootPath( inputPath );
		final String relativePath = resolveRelativePath( rootPath, inputPath );
		final N5Reader reader = createReader( rootPath );
		final N5Metadata metadata = readMetadata( reader, relativePath );

		final MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( metadata, reader, new N5TreeNode( relativePath ) );
		final int multiscaleIndex = 0;
		final Multiscale multiscale = adapter.initMultiscale( metadata, multiscaleIndex );
		final Omero omero = adapter.initOmeroMetadata();
		final ResolutionLevel selectedLevel = selectResolutionLevel( preferredMaxWidth, multiscale );

		final SpatialMetadataGroup< ? > spatialMetadata = Cast.unchecked( metadata );
		final AffineTransform3D[] transforms = spatialMetadata.spatialTransforms3d();
		final VoxelDimensions voxelDimensions = createVoxelDimensions( transforms[ 0 ], spatialMetadata.unit() );
		final T type = N5Utils.type( multiscale.getDataType() );
		final V volatileType = Cast.unchecked( VolatileTypeMatcher.getVolatileTypeForType( type ) );
		final String name = multiscale.getName();
		final int numResolutionLevels = multiscale.numResolutionLevels();
		final int numDimensions = selectedLevel.attributes.getDimensions().length;
		final int numTimepoints = getAxisSize( selectedLevel, Axes.TIME );
		final int numChannels = getAxisSize( selectedLevel, Axes.CHANNEL );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		final CachedCellImg< T, ? >[] cachedCellImgs = Cast.unchecked( new CachedCellImg[ numResolutionLevels ] );
		final RandomAccessibleInterval< V >[] volatileImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		for ( final ResolutionLevel level : multiscale.getLevels() )
		{
			cachedCellImgs[ level.index ] = N5Utils.openVolatile( reader, level.datasetPath );
			volatileImgs[ level.index ] = VolatileViews.wrapAsVolatile( cachedCellImgs[ level.index ], sharedQueue );
		}

		final ImgPlus< T > imgPlus = new ImgPlus<>( cachedCellImgs[ selectedLevel.index ], name );
		configureImgPlusAxes( imgPlus, selectedLevel );

		final int channelAxisIndex = findAxisIndex( selectedLevel, Axes.CHANNEL );
		final int zAxisIndex = findAxisIndex( selectedLevel, Axes.Z );
		final int timeAxisIndex = findAxisIndex( selectedLevel, Axes.TIME );

		final String[] channelLabels = Omero.buildChannelLabels( name, omero, numChannels );

		return PyramidContents.< T, V >builder()
				.name( name )
				.numResolutionLevels( numResolutionLevels )
				.numChannels( numChannels )
				.numTimepoints( numTimepoints )
				.numDimensions( numDimensions )
				.selectedResolutionLevelIndex( selectedLevel.index )
				.type( type )
				.volatileType( volatileType )
				.voxelDimensions( voxelDimensions )
				.transforms( transforms )
				.cachedCellImgs( cachedCellImgs )
				.volatileImgs( volatileImgs )
				.imgPlus( imgPlus )
				.channelAxisIndex( channelAxisIndex )
				.zAxisPresent( zAxisIndex > 0 )
				.timeAxisPresent( timeAxisIndex > 0 )
				.channelLabels( channelLabels )
				.omero( omero )
				.build();
	}

	// ---------------------------------------------------------------------
	// Path / reader helpers
	// ---------------------------------------------------------------------

	private Path resolveRootPath( final Path inputPath )
	{
		if ( inputPath == null )
			throw new IllegalArgumentException( "Input path is null" );
		final Path path = ZarrOnFileSystemUtils.findRootFolder( inputPath );
		if ( path == null )
			throw new IllegalArgumentException( "Could not find root folder for non-OME-Zarr path: " + inputPath );
		return path;
	}

	private String resolveRelativePath( final Path rootPath, final Path inputPath )
	{
		final List< String > elements = ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath );
		return String.join( File.separator, elements );
	}

	private N5Reader createReader( final Path rootPath )
	{
		if ( rootPath == null )
			throw new IllegalStateException( "Invalid OME-Zarr URI: " + inputUri );
		return new N5Factory().openReader( rootPath.toUri().toString() );
	}

	private N5Metadata readMetadata( final N5Reader reader, final String relativePath )
	{
		if ( relativePath == null )
			throw new NotAMultiscaleImageException( "Invalid OME-Zarr URI: " + inputUri );

		final N5TreeNode node = new N5TreeNode( relativePath );
		final List< N5MetadataParser< ? > > parsers =
				Arrays.asList( new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadataParser(),
						new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser(),
						new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05MetadataParser() );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, new ArrayList<>( parsers ) );
		final N5Metadata n5Metadata = node.getMetadata();
		if ( n5Metadata == null )
			throw new NotAMultiscaleImageException( inputUri.toString() );
		return n5Metadata;
	}

	private VoxelDimensions createVoxelDimensions( final AffineTransform3D transform, final String unit )
	{
		if ( !Affine3DUtils.isScaling( transform, 0.01d ) )
			logger.warn( "The affine transform is not a strict scaling transform. This may cause problems with the image viewer." );

		final double scaleX = transform.get( 0, 0 );
		final double scaleY = transform.get( 1, 1 );
		final double scaleZ = transform.get( 2, 2 );
		return new FinalVoxelDimensions( unit, scaleX, scaleY, scaleZ );
	}

	private ResolutionLevel selectResolutionLevel( final Integer preferredMaxWidth, final Multiscale multiscale )
	{
		ResolutionLevel resolutionLevel = multiscale.getLevels().get( 0 );
		if ( preferredMaxWidth == null )
			return resolutionLevel;
		int width = 0;
		for ( final ResolutionLevel level : multiscale.getLevels() )
		{
			width = getAxisSize( level, Axes.X );
			if ( width <= preferredMaxWidth )
				return level;
		}
		throw new NoMatchingResolutionException( preferredMaxWidth, width );
	}

	// ---------------------------------------------------------------------
	// Axis configuration
	// ---------------------------------------------------------------------

	private void configureImgPlusAxes( final ImgPlus< T > img, final ResolutionLevel level )
	{
		if ( level.axes != null )
		{
			for ( int i = 0; i < level.axes.length; i++ )
			{
				final Axis axis = level.axes[ i ];
				img.setAxis( new DefaultLinearAxis( AXIS_MAPPING.get( axis.getName() ), axis.getUnit(), level.scales[ i ] ), i );
			}
		}
		else if ( level.axisNames != null )
		{
			for ( int i = 0; i < level.axisNames.length; i++ )
			{
				img.setAxis( new DefaultLinearAxis( AXIS_MAPPING.get( level.axisNames[ i ] ), level.units[ i ], level.scales[ i ] ), i );
			}
		}
	}

	private int getAxisSize( final ResolutionLevel level, final AxisType axisType )
	{
		final int axisIndex = findAxisIndex( level, axisType );
		return axisIndex >= 0 ? ( int ) level.attributes.getDimensions()[ axisIndex ] : 1;
	}

	private int findAxisIndex( final ResolutionLevel level, final AxisType axisType )
	{
		if ( level.axes != null )
		{
			for ( int i = 0; i < level.axes.length; i++ )
				if ( axisType.equals( AXIS_MAPPING.get( level.axes[ i ].getName() ) ) )
					return i;
		}
		else if ( level.axisNames != null )
		{
			for ( int i = 0; i < level.axisNames.length; i++ )
				if ( axisType.equals( AXIS_MAPPING.get( level.axisNames[ i ] ) ) )
					return i;
		}
		return -1;
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

		private final Axis[] axes; // for v04/v05 metadata

		private final String[] axisNames; // for v03 metadata

		private final String[] units; // for v03 metadata

		private final double[] scales;

		private ResolutionLevel( final String datasetPath, final int index, final DatasetAttributes attributes,
				final Axis[] axes, final String[] axisNames, final String[] units, final double[] scales )
		{
			this.datasetPath = datasetPath;
			this.index = index;
			this.attributes = attributes;
			this.axes = axes;
			this.axisNames = axisNames;
			this.units = units;
			this.scales = scales;
		}
	}

	// ---------------------------------------------------------------------
	// Metadata adapter strategy (per OME-NGFF version)
	// ---------------------------------------------------------------------

	private interface MetadataAdapter
	{
		Multiscale initMultiscale( N5Metadata metadata, int multiscaleIndex );

		Omero initOmeroMetadata();
	}

	private abstract static class AbstractMetadataAdapter implements MetadataAdapter
	{
		protected final N5Reader reader;

		protected final N5TreeNode node;

		AbstractMetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			this.reader = reader;
			this.node = node;
		}

		protected Multiscale buildMultiscale( final String name,
				final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata[] children )
		{
			final List< ResolutionLevel > levels = new ArrayList<>();
			int index = 0;
			for ( final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata single : children )
				levels.add( new ResolutionLevel( single.getPath(), index++, single.getAttributes(), single.getAxes(), null, null,
						single.getScale() ) );
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

	private static class MetadataAdapterFactory
	{
		static MetadataAdapter getAdapter( final N5Metadata metadata, final N5Reader reader, final N5TreeNode node )
		{
			if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05Metadata )
				return new V05MetadataAdapter( reader, node );
			if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata )
				return new V04MetadataAdapter( reader, node );
			if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata )
				return new V03MetadataAdapter( reader, node );
			throw new NotAMultiscaleImageException( "Unsupported multiscale metadata type: " + metadata.getClass() );
		}
	}

	private static class V05MetadataAdapter extends AbstractMetadataAdapter
	{
		V05MetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			super( reader, node );
		}

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05Metadata omeNgffMetadata =
					Cast.unchecked( n5Metadata );
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata multiscales =
					omeNgffMetadata.multiscales[ multiscaleIndex ];
			return buildMultiscale( multiscales.name, multiscales.getChildrenMetadata() );
		}

		@Override
		protected String getOmeroKey()
		{
			return "ome/omero";
		}
	}

	private static class V04MetadataAdapter extends AbstractMetadataAdapter
	{
		V04MetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			super( reader, node );
		}

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata multiscales =
					omeNgffMetadata.multiscales[ multiscaleIndex ];
			if ( multiscales.getChildrenMetadata().length == 0 || multiscales.getChildrenMetadata()[ 0 ] == null )
				throw new NotAMultiscaleImageException( "Multiscale metadata does not contain any children attributes." );
			return buildMultiscale( multiscales.name, multiscales.getChildrenMetadata() );
		}

		@Override
		protected String getOmeroKey()
		{
			return "omero";
		}
	}

	private static class V03MetadataAdapter extends AbstractMetadataAdapter
	{
		V03MetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			super( reader, node );
		}

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMultiScaleMetadata multiscales =
					omeNgffMetadata.getMultiscales()[ multiscaleIndex ];
			if ( multiscales.getChildrenMetadata() == null || multiscales.getChildrenMetadata().length == 0 )
				throw new NotAMultiscaleImageException( "Multiscale metadata does not contain any children metadata." );
			final List< ResolutionLevel > levels = new ArrayList<>();
			int index = 0;
			for ( final N5SingleScaleMetadata single : multiscales.getChildrenMetadata() )
				levels.add( new ResolutionLevel( single.getPath(), index++, single.getAttributes(), null, multiscales.axes,
						multiscales.units(), single.getPixelResolution() ) );
			return new Multiscale( multiscales.name, levels, multiscales.getChildrenMetadata()[ 0 ].getAttributes().getDataType() );
		}

		@Override
		protected String getOmeroKey()
		{
			return "omero";
		}
	}
}
