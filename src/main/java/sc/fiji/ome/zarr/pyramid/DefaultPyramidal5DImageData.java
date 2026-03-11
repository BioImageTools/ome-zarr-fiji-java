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
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5Viewer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.scijava.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.cache.SharedQueue;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
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

	private final long[] dimensions;

	//these act as caches not to create them again and again
	private final ImgPlus< T > imgPlus;

	private final Dataset ijDataset;

	private List< SourceAndConverter< T > > sourceAndConverters;

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

	private final N5Metadata metadata;

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
	 * @param preferredMaxWidth The preferred maximum width for the ij image to be loaded. If the highest resolution image is wider than this, a downsampled resolution is chosen. This is useful for loading large images that may not fit in memory at full resolution. If {@code null}, no downsampled version will be chosen. Only affects the imgPlus.
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
		this.metadata = readMetadata();

		MetadataAdapter adapter = MetadataAdapterFactory.getAdapter( metadata );
		final int multiscaleIndex = 0; // TODO: How to select multiscale index?
		Multiscale multiscale = adapter.initMultiscale( metadata, multiscaleIndex );
		this.name = multiscale.getName();
		this.numResolutionLevels = multiscale.numResolutionLevels();
		final ResolutionLevel resolutionLevel = selectResolutionLevel( preferredMaxWidth, multiscale );
		this.dimensions = resolutionLevel.attributes.getDimensions();
		this.numDimensions = dimensions.length;
		this.numTimepoints = getNumTimepointsFromResolutionLevel( resolutionLevel );
		this.numChannels = getNumChannelsFromResolutionLevel( resolutionLevel );

		CachedCellImg< T, ? > img = N5Utils.openVolatile( reader, resolutionLevel.datasetPath );
		this.imgPlus = new ImgPlus<>( img, name );
		configureImgPlusAxesFromResolutionLevel( imgPlus, resolutionLevel );

		this.ijDataset = new DefaultDataset( context, imgPlus );
		this.ijDataset.setName( name );
		this.ijDataset.setRGBMerged( false );
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
			throw new IllegalArgumentException( "Could not find root folder for non-Zarr path: " + inputPath );
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
						new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser() );
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

		private Multiscale( final String name, final List< ResolutionLevel > resolutionLevels )
		{
			this.name = name;
			this.resolutionLevels = resolutionLevels;
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
	}

	// ---------------------------------------------------------------------
	// Selected Scale Level Representation
	// ---------------------------------------------------------------------

	private static class ResolutionLevel
	{
		private final String datasetPath;

		private final DatasetAttributes attributes;

		private final Axis[] axes; // for OME NGFF v04 metadata

		private final String[] axisNames; // for OME NGFF v03 metadata

		private final String[] units; // for OME NGFF v03 metadata

		private final double[] scales; // down sampling factor

		private ResolutionLevel(
				final String datasetPath, final DatasetAttributes attributes, final Axis[] axes, final String[] axisNames,
				final String[] units, final double[] scales )
		{
			this.datasetPath = datasetPath;
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
	}

	private static class MetadataAdapterFactory
	{
		static MetadataAdapter getAdapter( final N5Metadata metadata )
		{
			if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata )
				return new V04MetadataAdapter();
			if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata )
				return new V03MetadataAdapter();
			throw new NotAMultiscaleImageException(
					"Unsupported multiscale metadata type: " + metadata.getClass() );
		}
	}

	private static class V04MetadataAdapter implements MetadataAdapter
	{

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			OmeNgffMultiScaleMetadata multiscales = omeNgffMetadata.multiscales[ multiscaleIndex ];
			List< ResolutionLevel > levels = new ArrayList<>();
			for ( NgffSingleScaleAxesMetadata single : multiscales.getChildrenMetadata() )
			{
				levels.add(
						new ResolutionLevel( single.getPath(), single.getAttributes(), single.getAxes(), null, null, single.getScale() ) );
			}
			return new Multiscale( multiscales.name, levels );
		}
	}

	private static class V03MetadataAdapter implements MetadataAdapter
	{

		@Override
		public Multiscale initMultiscale( final N5Metadata n5Metadata, final int multiscaleIndex )
		{
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata omeNgffMetadata = Cast.unchecked( n5Metadata );
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMultiScaleMetadata multiscales =
					omeNgffMetadata.getMultiscales()[ multiscaleIndex ];
			List< ResolutionLevel > levels = new ArrayList<>();
			for ( N5SingleScaleMetadata single : multiscales.getChildrenMetadata() )
			{
				levels.add( new ResolutionLevel( single.getPath(), single.getAttributes(), null, multiscales.axes, multiscales.units(),
						single.getPixelResolution() ) );
			}
			return new Multiscale( multiscales.name, levels );
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
		if ( resolutionLevel == null )
			return 1;

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
		if ( reader == null )
			throw new IllegalStateException( "Cannot create sources: no reader available for path: " + inputPathAsString );
		if ( metadata == null )
			throw new NotAMultiscaleImageException( "Cannot create sources: no metadata available for path: " + inputPathAsString );
		if ( sourceAndConverters == null )
		{
			try
			{
				sourceAndConverters = new ArrayList<>();
				SharedQueue queue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
				BdvOptions options = BdvOptions.options().frameTitle( name );
				N5Viewer.buildN5Sources( reader, Collections.singletonList( metadata ), queue, new ArrayList<>(), sourceAndConverters,
						options );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		}
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
		return null;
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
		return imgPlus.firstElement();
	}

	@Override
	public String getName()
	{
		return name;
	}
}
