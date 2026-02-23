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
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
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
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata;
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
import mpicbg.spim.data.SpimData;
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
	/** The scijava context. This is needed (only) for creating {@link #ijDataset}. */
	private final Context context;

	/**
	 * Name of the dataset, likely the URI (or "basename" of it) when
	 * opening an existing image; or user-provided when creating a new one
	 * (note that one can create only in-memory new image and thus URI need
	 * not be available at the construction time).
	 */
	private final String name;

	private final String path;

	/**
	 * The number of available resolutions.
	 */
	private int numResolutions;

	/** The fourth dimension size... */
	private int numTimepoints = 1;

	/** The fifth dimension size... */
	private int numChannels = 1;

	/** The total number of dimensions in the image. */
	private int numDimensions;

	private long[] dimensions;

	//TODO: -- knows resolution along the dimensions
	//private OMEZarrAxes omeZarrAxes;

	//these act as caches not to create them again and again
	private final ImgPlus< T > imgPlus;

	private final Dataset ijDataset;

	private List< SourceAndConverter< T > > sourceAndConverters;

	private SpimData spimData;

	/**
	 * Build a dataset from a single {@code PyramidalOMEZarrArray},
	 * which MUST only contains subset of the axes: X,Y,Z,C,T
	 * <br>
	 * @param context The SciJava context for building the SciJava dataset
	 * @param path The path to the OME-Zarr dataset.
	 * @throws Error any error
	 */
	public DefaultPyramidal5DImageData(
			final Context context,
			final String path
	) throws Error
	{
		this.context = context;
		this.path = path;
		final String singleScaleName;

		// Initialize N5Reader
		final Path inputPath = Paths.get( this.path );
		final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( inputPath ); // TODO: maybe do not go back to root folder
		if ( rootPath == null )
		{
			boolean isZarrFolder = ZarrOnFileSystemUtils.isZarrFolder( inputPath );
			if ( !isZarrFolder )
				throw new NotAMultiscaleImageException( "The provided path '" + path + "' does not contain supported a multiscale image." );
			else
				throw new NotAMultiscaleImageException( "The provided path '" + path + "' is not a multiscale image." );
		}
		N5Reader reader = new N5Factory().openReader( rootPath.toUri().toString() );

		// Initialize N5TreeNode
		final List< String > relativePathElements = ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath );
		final String relativePath = String.join( File.separator, relativePathElements ); // TODO: go not back to root folder and use "" as relative path
		N5TreeNode n5TreeNode = new N5TreeNode( relativePath );

		// Create Parsers
		OmeNgffMetadataParser parserV03 = new OmeNgffMetadataParser();
		org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser parserV04 =
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser();
		List< N5MetadataParser< ? > > metadataParsers = Arrays.asList( parserV03, parserV04 );
		List< N5MetadataParser< ? > > groupParsers = new ArrayList<>( metadataParsers );

		// Parse Metadata
		N5DatasetDiscoverer.parseMetadataShallow( reader, n5TreeNode, metadataParsers, groupParsers );

		N5Metadata metadata = n5TreeNode.getMetadata();
		final String selectedDataset;
		final DatasetAttributes attributes;
		final int representativeResolutionLevel = 0; // TODO: highest resolution level or resolution level closest to 1000x1000?
		final int representativeMultiscaleIndex = 0; // TODO: if multiple multiscales are present, which one to choose?
		final Axis[] axes;
		final String[] axisNames;
		final String[] units;
		final double[] scales;
		// OME Zarr v04
		if ( ( metadata instanceof OmeNgffMetadata ) )
		{
			OmeNgffMetadata ngffMetadata = Cast.unchecked( metadata );
			OmeNgffMultiScaleMetadata multiscales = ngffMetadata.multiscales[ representativeMultiscaleIndex ];
			numResolutions = multiscales.datasets.length;
			name = multiscales.name;
			NgffSingleScaleAxesMetadata singleScaleMetadata = multiscales.getChildrenMetadata()[ representativeResolutionLevel ];
			selectedDataset = singleScaleMetadata.getPath();
			attributes = singleScaleMetadata.getAttributes();
			singleScaleName = singleScaleMetadata.getName();
			axes = singleScaleMetadata.getAxes();
			axisNames = null;
			units = null;
			scales = singleScaleMetadata.getScale();
		}
		// OME Zarr v03
		else if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata )
		{
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata ngffMetadata = Cast.unchecked( metadata );
			org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMultiScaleMetadata multiscales =
					ngffMetadata.getMultiscales()[ representativeMultiscaleIndex ];
			numResolutions = multiscales.datasets.length;
			name = multiscales.name;
			N5SingleScaleMetadata singleScaleMetadata = multiscales.getChildrenMetadata()[ representativeResolutionLevel ];
			selectedDataset = singleScaleMetadata.getPath();
			attributes = singleScaleMetadata.getAttributes();
			singleScaleName = singleScaleMetadata.getName();
			axes = null;
			axisNames = multiscales.axes;
			units = multiscales.units();
			scales = singleScaleMetadata.getPixelResolution();
		}
		// Unsupported
		else
		{
			throw new NotAMultiscaleImageException( "The provided path '" + path + "' does not contain supported a multiscale image." );
		}

		dimensions = attributes.getDimensions();
		numDimensions = dimensions.length;

		CachedCellImg< T, ? > cachedCellImg = N5Utils.openVolatile( reader, selectedDataset );
		imgPlus = new ImgPlus<>( cachedCellImg );
		imgPlus.setName( singleScaleName );

		Map< String, AxisType > mapping = new HashMap<>();
		mapping.put( "x", Axes.X );
		mapping.put( "y", Axes.Y );
		mapping.put( "z", Axes.Z );
		mapping.put( "c", Axes.CHANNEL );
		mapping.put( "t", Axes.TIME );

		if ( axes != null )
		{
			for ( int i = 0; i < axes.length; i++ )
			{
				Axis axis = axes[ i ];
				AxisType axisType = mapping.get( axis.getName() );
				String unit = axis.getUnit();
				double scale = scales[ i ];
				CalibratedAxis calibratedAxis = new DefaultLinearAxis(
						axisType,
						unit,
						scale );
				imgPlus.setAxis( calibratedAxis, i );
			}
		}
		else if ( axisNames != null && units != null )
		{
			for ( int i = 0; i < axisNames.length; i++ )
			{
				AxisType axisType = mapping.get( axisNames[ i ] );
				String unit = units[ i ];
				double scale = scales[ i ];
				CalibratedAxis calibratedAxis = new DefaultLinearAxis(
						axisType,
						unit,
						scale );
				imgPlus.setAxis( calibratedAxis, i );
			}
		}

		final DatasetService datasetService = context.getService( DatasetService.class );
		ijDataset = datasetService.create( imgPlus );
		ijDataset.setName( imgPlus.getName() );
		ijDataset.setRGBMerged( false );
	}

	@Override
	public PyramidalDataset< T > asPyramidalDataset()
	{
		return new PyramidalDataset< T >( this );
	}

	@Override
	public Dataset asDataset()
	{
		return ijDataset;
	}

	@Override
	public List< SourceAndConverter< T > > asSources()
	{
		if ( sourceAndConverters == null )
		{
			try
			{
				sourceAndConverters = new ArrayList<>();

				// TODO: avoid code duplication with constructor
				// Initialize N5Reader
				final Path inputPath = Paths.get( path );
				final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( inputPath );
				N5Reader reader = new N5Factory().openReader( rootPath.toUri().toString() );

				// Initialize N5TreeNode
				final List< String > relativePathElements = ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath );
				final String relativePath = String.join( File.separator, relativePathElements );
				N5TreeNode n5TreeNode = new N5TreeNode( relativePath );

				// Create Parsers
				OmeNgffMetadataParser parserV03 = new OmeNgffMetadataParser();
				org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser parserV04 =
						new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser();
				List< N5MetadataParser< ? > > metadataParsers = Arrays.asList( parserV03, parserV04 );
				List< N5MetadataParser< ? > > groupParsers = new ArrayList<>( metadataParsers );

				// Parse Metadata
				N5DatasetDiscoverer.parseMetadataShallow( reader, n5TreeNode, metadataParsers, groupParsers );

				// Create a list containing only the metadata of the dataset we want to visualize
				List< N5Metadata > selectedMetadata = Collections.singletonList( n5TreeNode.getMetadata() );

				// Initialize BDV Cache
				final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

				// Create BDV options
				BdvOptions bdvOptions = BdvOptions.options().frameTitle( getName() );
				this.numTimepoints = N5Viewer.buildN5Sources( reader, selectedMetadata, sharedQueue, new ArrayList<>(), sourceAndConverters,
						bdvOptions );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		}
		return sourceAndConverters;
	}

	@Override
	public SpimData asSpimData()
	{
		return spimData;
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

	/**
	 * The purpose is to create the internal {@link DefaultPyramidal5DImageData#imgPlus}
	 * just once.
	 */
	private synchronized void imgPlus()
	{}

	@Override
	public int numDimensions()
	{
		return numDimensions;
	}

	/**
	 * Get the number of levels in the resolution pyramid.
	 */
	public int numResolutions()
	{
		return numResolutions;
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
		if ( imgPlus == null )
			return null;
		return imgPlus.firstElement();
	}

	@Override
	public String getName()
	{
		return name;
	}
}
