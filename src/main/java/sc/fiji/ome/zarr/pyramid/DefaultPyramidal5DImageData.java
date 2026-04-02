/*-
 * #%L
 * Expose the Imaris XT interface as an ImageJ2 service backed by ImgLib2.
 * %%
 * Copyright (C) 2019 - 2021 Bitplane AG
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
package sc.fiji.ome.zarr.pyramid;

import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.EuclideanSpace;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

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
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;
import sc.fiji.ome.zarr.util.Affine3DUtils;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;

/**
 * An OME-Zarr backed pyramidal 5D image
 * that can be visualized in ImageJ in various ways.
 * <br>
 * 5D refers to: x,y,z,t,channels (or simply the dimension in which all images are stacked)
 * The {@link EuclideanSpace} brings in only the `numDimensions()`.
 *
 * @param <T> Type of the pixels
 * @param <V> Volatile type of the pixels
 */
public class DefaultPyramidal5DImageData<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
		implements EuclideanSpace, Pyramidal5DImageData< T >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final Map< String, AxisType > AXIS_MAPPING;

	static
	{
		Map< String, AxisType > map = new HashMap<>();
		map.put( "x", Axes.X );
		map.put( "y", Axes.Y );
		map.put( "z", Axes.Z );
		map.put( "c", Axes.CHANNEL );
		map.put( "t", Axes.TIME );
		AXIS_MAPPING = Collections.unmodifiableMap( map );
	}

	/** The scijava context. This is needed (only) for creating {@link #ijDataset}. */
	private final Context context;

	/**
	 * Name of the dataset, likely the URI (or "basename" of it) when
	 * opening an existing image; or user-provided when creating a new one
	 * (note that one can create only in-memory new image and thus URI need
	 * not be available at the construction time).
	 */
	private final String name;

	/**
	 * The number of available resolutions.
	 */
	private final int numResolutionLevels;

	/** The fourth dimension size... */
	private final int numTimepoints;

	/** The fifth dimension size... */
	private final int numChannels;

	/** The total number of dimensions in the image. */
	private final int numDimensions;

	private final AffineTransform3D[] transforms;

	private final VoxelDimensions voxelDimensions;

	private final Omero omero;

	// this acts as cache not to create them again and again
	private final Dataset ijDataset;

	private final List< SourceAndConverter< T > > sourceAndConverters;

	private final T type;

	private final V volatileType;

	private final CachedCellImg< T, ? >[] cachedCellImgs;

	private final RandomAccessibleInterval< V >[] volatileImgs;

	private final String inputPathAsString;

	private final Path inputPath;

	private final Path rootPath;

	/**
	 * The relative path of the dataset within the root directory, represented as a string.
	 * E.g.
	 * <ul>
	 *     <li>input path: /Users/foo/Data/123.ome.zarr/dataset1/image1</li>
	 *     <li>root path: /Users/foo/Data/123.ome.zarr</li>
	 *     <li>relativePathAsString: dataset1/image1</li>
	 * </ul>
	 *
	 */
	private final String relativePathAsString;

	private final N5Reader reader;

	/**
	 * Build a dataset from the given OME-Zarr path. The path can be the root of the OME-Zarr dataset or a subfolder within it.
	 * <br>
	 * @param context The SciJava context for building the SciJava dataset
	 * @param inputPathAsString The path to the OME-Zarr dataset.
	 */
	public DefaultPyramidal5DImageData( final Context context, final String inputPathAsString )
	{
		this( context, inputPathAsString, null );
	}

	/**
	 * Build a dataset from the given OME-Zarr path. The path can be the root of the OME-Zarr dataset or a subfolder within it.
	 * <br>
	 * @param context The SciJava context for building the SciJava dataset
	 * @param inputPathAsString The path to the OME-Zarr dataset.
	 * @param preferredMaxWidth The preferred maximum width for the ij image to be loaded. If the highest resolution image is wider than this, a downsampled resolution is chosen.<br>
	 * 							This is useful for loading large images that may not fit in memory at full resolution.<br>
	 * 							If {@code null}, no downsampled version will be chosen (i.e. highest resolution). Only affects the imgPlus.
	 * @throws NoMatchingResolutionException If the given {@code preferredMaxWidth} is smaller than the width of the smallest resolution level.
	 */
	public DefaultPyramidal5DImageData( final Context context, final String inputPathAsString, final Integer preferredMaxWidth )
			throws NoMatchingResolutionException
	{
		this.context = context;
		this.inputPathAsString = inputPathAsString;
		this.inputPath = Paths.get( inputPathAsString );
		this.rootPath = resolveRootPath();
		this.relativePathAsString = resolveRelativePath();
		this.reader = createReader();
		final N5Metadata metadata = readMetadata();

		MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( metadata, reader, new N5TreeNode( relativePathAsString ) );
		final int multiscaleIndex = 0; // TODO: How to select multiscale index?
		final Multiscale multiscale = adapter.initMultiscale( metadata, multiscaleIndex );
		this.omero = adapter.initOmeroMetadata();
		final ResolutionLevel resolutionLevel = selectResolutionLevel( preferredMaxWidth, multiscale );
		final SpatialMetadataGroup< ? > spatialMetadata = Cast.unchecked( metadata );
		this.transforms = spatialMetadata.spatialTransforms3d();
		this.voxelDimensions = createVoxelDimensions( transforms[ 0 ], spatialMetadata.unit() ); // voxel dimensions — index 0 is chosen, i.e., the highest resolution
		this.type = N5Utils.type( multiscale.getDataType() );
		this.volatileType = Cast.unchecked( VolatileTypeMatcher.getVolatileTypeForType( type ) );
		this.name = multiscale.getName();
		this.numResolutionLevels = multiscale.numResolutionLevels();
		this.numDimensions = resolutionLevel.attributes.getDimensions().length;
		this.numTimepoints = getNumTimepointsFromResolutionLevel( resolutionLevel );
		this.numChannels = getNumChannelsFromResolutionLevel( resolutionLevel );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		cachedCellImgs = Cast.unchecked( new CachedCellImg[ numResolutionLevels ] );
		volatileImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		for ( ResolutionLevel level : multiscale.getLevels() )
		{
			cachedCellImgs[ level.index ] = N5Utils.openVolatile( reader, level.datasetPath );
			volatileImgs[ level.index ] = VolatileViews.wrapAsVolatile( cachedCellImgs[ level.index ], sharedQueue );
		}
		final ImgPlus< T > imgPlus = new ImgPlus<>( cachedCellImgs[ resolutionLevel.index ], name );
		configureImgPlusAxesFromResolutionLevel( imgPlus, resolutionLevel );

		this.ijDataset = new DefaultDataset( context, imgPlus );
		this.ijDataset.setName( name );
		this.ijDataset.setRGBMerged( false );

		sourceAndConverters = initSourceAndConverters( resolutionLevel );
	}

	private List< SourceAndConverter< T > > initSourceAndConverters( final ResolutionLevel resolutionLevel )
	{
		// only x,y axes have a fixed dimension index, all the other axes can be absent in the input tensor,
		// and thus their indices are "floating", and must be thus found here
		// NB: the input tensor is cachedCellImgs and its wrapper volatileImgs at a particular chosen resolution level
		final int zAxisIndex = findAxisIndex( resolutionLevel, Axes.Z );
		final int timeAxisIndex = findAxisIndex( resolutionLevel, Axes.TIME );
		final int channelAxisIndex = findAxisIndex( resolutionLevel, Axes.CHANNEL );

		final boolean zAxisPresent = zAxisIndex > 0;
		final boolean timeAxisPresent = timeAxisIndex > 0;

		final boolean isOmeroMetadataValid = omero != null && omero.channels != null && omero.channels.size() == numChannels;
		if ( isOmeroMetadataValid )
			logger.trace( "Creating with OMERO metadata: {}", omero );
		else
			logger.trace( "Creating without OMERO metadata (not consistent or not available)" );

		final List< SourceAndConverter< T > > sources = new ArrayList<>();
		for ( int channelNumber = 0; channelNumber < numChannels; channelNumber++ )
		{
			// for the Mipmap, RAIs must always be xyzt even if z and/or t is not present,
			// but first the particular channel is taken out, and then 4D is ensured:
			// NB: the input tensor is an OME-Zarr array, which is of the xy[z][t][c] order of dimensions,
			//     so really only a particular 'c' is extracted, and 'z' and 't' are added if they are missing
			RandomAccessibleInterval< V >[] channelsVolatile =
					ensureOrdered4dDimensions( extractChannel( volatileImgs, channelAxisIndex, channelNumber ), zAxisPresent, timeAxisPresent );
			RandomAccessibleInterval< T >[] channels =
					ensureOrdered4dDimensions( extractChannel( cachedCellImgs, channelAxisIndex, channelNumber ), zAxisPresent, timeAxisPresent );

			// wrap to create the mipmaps
			final String channelLabel = isOmeroMetadataValid ? omero.channels.get( channelNumber ).label : getName();
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile = new RandomAccessibleIntervalMipmapSource4D<>(
					channelsVolatile, volatileType, transforms, voxelDimensions, channelLabel, true );
			final RandomAccessibleIntervalMipmapSource4D< T > source4D =
					new RandomAccessibleIntervalMipmapSource4D<>( channels, type, transforms, voxelDimensions, channelLabel, true );

			// finally, provide the desired SAC for the current channel
			final SourceAndConverter< T > sourceAndConverter = createSourceAndConverter( source4D, source4DVolatile );
			sources.add( sourceAndConverter );
			BigDataViewer.createConverterSetup( sourceAndConverter, channelNumber );
		}
		return sources;
	}

	/**
	 * If the 'c' (channels) dimension is present in sourceImgs, extract it and reduce the dimensionality of the input.
	 * If it is absent, it is already "as if reduced", so return sourceImgs as is.
	 */
	private < R > RandomAccessibleInterval< R >[] extractChannel( final RandomAccessibleInterval< R >[] sourceImgs,
			final int channelAxisIndex, final int channelNumber )
	{
		// casting is here only because Java cannot do: new RAI<T>[ numberOfThem ]
		// while it can only do: RAI[ numberOfThem ]
		final RandomAccessibleInterval< R >[] resultImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			resultImgs[ level ] = channelAxisIndex < 0 // channel dimension does not exist
					? sourceImgs[ level ] // return as is
					: Views.hyperSlice( sourceImgs[ level ], channelAxisIndex, channelNumber ); // channel dimension exists, extract it
		}
		return resultImgs;
	}

	/**
	 * Make sure images of xyzt are returned when the sourceImgs are expected to be xy[z][t].
	 * The presence of the two axes is indicated.
	 */
	private < R > RandomAccessibleInterval< R >[] ensureOrdered4dDimensions( final RandomAccessibleInterval< R >[] sourceImgs,
			final boolean zAxisPresent, final boolean timeAxisPresent )
	{
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			RandomAccessibleInterval< R > img = sourceImgs[ level ];
			if ( zAxisPresent )
			{
				if ( !timeAxisPresent ) // case xyz
				{
					img = Views.addDimension( img, 0, 0 );
				}
				// NB: else (i.e., case xyzt) does not need to be covered, since it is already in the desired order
			}
			else
			{
				// zAxis is absent
				if ( timeAxisPresent ) // case xyt
				{
					//insert 1-long Z prior to T
					img = Views.addDimension( img, 0, 0 ); // now dims [0,1,2,3]
					img = Views.permute( img, 2, 3 );
				}
				else // case xy
				{
					//twice add 1-long dimensions for Z and T
					img = Views.addDimension( img, 0, 0 );
					img = Views.addDimension( img, 0, 0 );
				}
			}
			sourceImgs[ level ] = img;
		}
		return sourceImgs;
	}

	private SourceAndConverter< T > createSourceAndConverter( final RandomAccessibleIntervalMipmapSource4D< T > source4D,
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile )
	{
		final Converter< V, ARGBType > converterVolatile = BigDataViewer.createConverterToARGB( volatileType );
		final Converter< T, ARGBType > converter = BigDataViewer.createConverterToARGB( type );
		final SourceAndConverter< V > sourceAndConverterVolatile =
				BigDataViewer.wrapWithTransformedSource( new SourceAndConverter<>( source4DVolatile, converterVolatile ) );
		return new SourceAndConverter<>( source4D, converter, sourceAndConverterVolatile );
	}

	private VoxelDimensions createVoxelDimensions( final AffineTransform3D transform, final String unit )
	{
		if ( !Affine3DUtils.isScaling( transform, 0.01d ) )
			logger.warn( "The affine transform is not a strict scaling transform. This may cause problems with the image viewer." );

		double scaleX = transform.get( 0, 0 );
		double scaleY = transform.get( 1, 1 );
		double scaleZ = transform.get( 2, 2 );

		return new FinalVoxelDimensions( unit, scaleX, scaleY, scaleZ );
	}

	private ResolutionLevel selectResolutionLevel( final Integer preferredMaxWidth, final Multiscale multiscale )
			throws NoMatchingResolutionException
	{
		ResolutionLevel resolutionLevel = multiscale.getLevels().get( 0 ); // highest resolution according to OME-Zarr spec
		if ( preferredMaxWidth == null )
			return resolutionLevel;
		int width = 0;
		// iterate from the highest resolution to the lowest resolution
		for ( ResolutionLevel level : multiscale.getLevels() )
		{
			width = getAxisSize( level, Axes.X );
			if ( width <= preferredMaxWidth )
				return level;
		}
		throw new NoMatchingResolutionException( preferredMaxWidth, width );
	}

	// ---------------------------------------------------------------------
	// Metadata & Reader Utilities
	// ---------------------------------------------------------------------

	private Path resolveRootPath()
	{
		if ( inputPath == null )
			throw new IllegalArgumentException( "Input path is null" );
		Path path = ZarrOnFileSystemUtils.findRootFolder( inputPath );
		if ( path == null )
			throw new IllegalArgumentException( "Could not find root folder for non-OME-Zarr path: " + inputPath );
		return path;
	}

	private String resolveRelativePath()
	{
		if ( inputPath == null || rootPath == null )
			throw new IllegalStateException( "Input path or root path is null" );
		List< String > elements = ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath );
		return String.join( File.separator, elements );
	}

	private N5Reader createReader()
	{
		if ( rootPath == null )
			throw new IllegalStateException( "Invalid OME-Zarr path: " + inputPathAsString );
		return new N5Factory().openReader( rootPath.toUri().toString() );
	}

	private N5Metadata readMetadata()
	{
		if ( relativePathAsString == null )
			throw new NotAMultiscaleImageException( "Invalid OME-Zarr path: " + inputPathAsString );

		N5TreeNode node = new N5TreeNode( relativePathAsString );
		List< N5MetadataParser< ? > > parsers =
				Arrays.asList( new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadataParser(),
						new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser(),
						new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05MetadataParser() );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, new ArrayList<>( parsers ) );
		N5Metadata n5Metadata = node.getMetadata();
		if ( n5Metadata == null )
			throw new NotAMultiscaleImageException( inputPathAsString );
		return n5Metadata;
	}

	private static class Multiscale
	{

		private final String name;

		private final List< ResolutionLevel > resolutionLevels;

		private final DataType dataType;

		private Multiscale( final String name, final List< ResolutionLevel > resolutionLevels, final DataType dataType )
		{
			this.name = name;
			this.resolutionLevels = resolutionLevels;
			this.dataType = dataType;
		}

		public String getName()
		{
			return name;
		}

		public int numResolutionLevels()
		{
			return resolutionLevels.size();
		}

		public List< ResolutionLevel > getLevels()
		{
			return resolutionLevels;
		}

		public DataType getDataType()
		{
			return dataType;
		}
	}

	// ---------------------------------------------------------------------
	// Selected Scale Level Representation
	// ---------------------------------------------------------------------

	private static class ResolutionLevel
	{
		private final String datasetPath;

		private final int index; // the position of the resolution level within a multiscale object

		private final DatasetAttributes attributes;

		private final Axis[] axes; // for OME NGFF v04 metadata

		private final String[] axisNames; // for OME NGFF v03 metadata

		private final String[] units; // for OME NGFF v03 metadata

		private final double[] scales; // down sampling factor

		private ResolutionLevel(
				final String datasetPath, final int index, final DatasetAttributes attributes, final Axis[] axes, final String[] axisNames,
				final String[] units, final double[] scales )
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
	// Metadata Adapter Strategy
	// ---------------------------------------------------------------------

	private interface MetadataAdapter
	{
		Multiscale initMultiscale( final N5Metadata metadata, final int multiscaleIndex );

		Omero initOmeroMetadata();
	}

	private abstract static class AbstractMetadataAdapter implements MetadataAdapter
	{
		protected final N5Reader reader;

		protected final N5TreeNode node;

		public AbstractMetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			this.reader = reader;
			this.node = node;
		}

		protected Multiscale buildMultiscale( final String name,
				final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata[] children )
		{
			final List< ResolutionLevel > levels = new ArrayList<>();
			int index = 0;
			for ( org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata single : children )
			{
				levels.add( new ResolutionLevel( single.getPath(), index++, single.getAttributes(), single.getAxes(), null, null,
						single.getScale() ) );
			}
			return new Multiscale( name, levels, children[ 0 ].getAttributes().getDataType()
			);
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
			throw new NotAMultiscaleImageException(
					"Unsupported multiscale metadata type: " + metadata.getClass() );
		}
	}

	private static class V05MetadataAdapter extends AbstractMetadataAdapter
	{
		private V05MetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			super( reader, node );
		}

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05Metadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata multiscales =
					omeNgffMetadata.multiscales[ multiscaleIndex ]; // NB: v04 multi scale metadata ??
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
		private V04MetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			super( reader, node );
		}

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata multiscales = omeNgffMetadata.multiscales[ multiscaleIndex ];
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
		private V03MetadataAdapter( final N5Reader reader, final N5TreeNode node )
		{
			super( reader, node );
		}

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMultiScaleMetadata multiscales =
					omeNgffMetadata.getMultiscales()[ multiscaleIndex ];
			if ( multiscales.getChildrenMetadata() == null || multiscales.getChildrenMetadata().length == 0 )
				throw new NotAMultiscaleImageException( "Multiscale metadata does not contain any children metadata." );
			final List< ResolutionLevel > levels = new ArrayList<>();
			int index = 0;
			for ( N5SingleScaleMetadata single : multiscales.getChildrenMetadata() )
			{
				levels.add(
						new ResolutionLevel( single.getPath(), index++, single.getAttributes(), null, multiscales.axes, multiscales.units(),
						single.getPixelResolution() ) );
			}
			return new Multiscale( multiscales.name, levels, multiscales.getChildrenMetadata()[ 0 ].getAttributes().getDataType() );
		}

		@Override
		protected String getOmeroKey()
		{
			return "omero";
		}
	}

	// ---------------------------------------------------------------------
	// Axis Configuration
	// ---------------------------------------------------------------------

	private void configureImgPlusAxesFromResolutionLevel( final ImgPlus< T > img, final ResolutionLevel resolutionLevel )
	{
		if ( resolutionLevel.axes != null )
		{
			for ( int i = 0; i < resolutionLevel.axes.length; i++ )
			{
				Axis axis = resolutionLevel.axes[ i ];
				setImgPlusAxis( img, AXIS_MAPPING.get( axis.getName() ), axis.getUnit(), resolutionLevel.scales[ i ], i );
			}
		}
		else if ( resolutionLevel.axisNames != null )
		{
			for ( int i = 0; i < resolutionLevel.axisNames.length; i++ )
			{
				setImgPlusAxis( img, AXIS_MAPPING.get( resolutionLevel.axisNames[ i ] ), resolutionLevel.units[ i ],
						resolutionLevel.scales[ i ],
						i );
			}
		}
	}

	private void setImgPlusAxis( final ImgPlus< T > img, final AxisType type, final String unit, final double scale, final int index )
	{
		img.setAxis( new DefaultLinearAxis( type, unit, scale ), index );
	}

	private int getNumChannelsFromResolutionLevel( final ResolutionLevel resolutionLevel )
	{
		return getAxisSize( resolutionLevel, Axes.CHANNEL );
	}

	private int getNumTimepointsFromResolutionLevel( final ResolutionLevel resolutionLevel )
	{
		return getAxisSize( resolutionLevel, Axes.TIME );
	}

	private int getAxisSize( final ResolutionLevel resolutionLevel, final AxisType axisType )
	{
		final int axisIndex = findAxisIndex( resolutionLevel, axisType );

		return axisIndex >= 0 ? ( int ) resolutionLevel.attributes.getDimensions()[ axisIndex ] : 1;
	}

	private int findAxisIndex( final ResolutionLevel resolutionLevel, final AxisType axisType )
	{
		if ( resolutionLevel.axes != null )
		{
			for ( int i = 0; i < resolutionLevel.axes.length; i++ )
			{
				Axis axis = resolutionLevel.axes[ i ];
				if ( axisType.equals( AXIS_MAPPING.get( axis.getName() ) ) )
					return i;
			}
		}
		else if ( resolutionLevel.axisNames != null )
		{
			for ( int i = 0; i < resolutionLevel.axisNames.length; i++ )
			{
				if ( axisType.equals( AXIS_MAPPING.get( resolutionLevel.axisNames[ i ] ) ) )
					return i;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------------
	// Interface Implementations
	// ---------------------------------------------------------------------

	@Override
	public PyramidalDataset< T > asPyramidalDataset()
	{
		// TODO: cache the pyramidal dataset instead of recreating it every time?
		return new PyramidalDataset<>( this );
	}

	@Override
	public Dataset asDataset()
	{
		return ijDataset;
	}

	@Override
	public List< SourceAndConverter< T > > asSources()
	{
		return sourceAndConverters;
	}

	@Override
	public int numChannels()
	{
		return numChannels;
	}

	@Override
	public VoxelDimensions voxelDimensions()
	{
		return voxelDimensions;
	}

	@Override
	public int numDimensions()
	{
		return numDimensions;
	}

	/**
	 * Get the number of levels in the resolution pyramid.
	 */
	public int numResolutionLevels()
	{
		return numResolutionLevels;
	}

	/**
	 * Get the number of timepoints.
	 */
	@Override
	public int numTimepoints()
	{
		return numTimepoints;
	}

	/**
	 * Get an instance of the pixel type.
	 */
	@Override
	public T getType()
	{
		return type;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public Omero getOmeroProperties()
	{
		return omero;
	}
}
