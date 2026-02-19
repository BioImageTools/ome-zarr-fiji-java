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

import bdv.cache.SharedQueue;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.EuclideanSpace;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5Viewer;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadataParser;
import org.jetbrains.annotations.NotNull;
import org.scijava.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An OME-Zarr backed pyramidal 5D image
 * that can be visualised in ImageJ in various ways.
 *
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

	/**
	 * Basically a list of individual images<T,V>, each of which
	 * showing the same content but at different spatial resolution.
	 * This is where the 'pyramids' are held.
	 *
	 * Note that none of the individual images knows its pixel resolution,
	 * or any other metadata.
	 */
	private final MultiscaleImage< T, V > multiscaleImage;

	/**
	 * Only a shortcut as this information is otherwise also available
	 * in the {@link DefaultPyramidal5DImageData#multiscaleImage}.
	 */
	private int numResolutions;


	/** The fourth dimension size... */
	private int numTimepoints = 1;

	/** The fifth dimension size... */
	private int numChannels = 1;

	/** TODO: Should be 4 or 5, no??? */
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
	 * @param multiscaleImage The array containing the image all data.
	 * @throws Error any error
	 */
	public DefaultPyramidal5DImageData(
			final Context context,
			final String name,
			final MultiscaleImage< T, V > multiscaleImage ) throws Error
	{
		this.context = context;
		this.name = name;
		this.multiscaleImage = multiscaleImage;

		numResolutions = multiscaleImage.numResolutions();
		dimensions = multiscaleImage.dimensions();
		numDimensions = dimensions.length;

		imgPlus = new ImgPlus<>(multiscaleImage.getImg(0));
		imgPlus.setName(getName());
		updateImgPlusAxes();

		final DatasetService datasetService = context.getService(DatasetService.class);
		ijDataset = datasetService.create(imgPlus);
		ijDataset.setName(imgPlus.getName());
		ijDataset.setRGBMerged(false);
	}



	public static void main(String[] args) {
		//final String path = "/home/ulman/Documents/talks/CEITEC/2025_11_ZarrSymposium_Zurich/data/MitoEM_fixedRes.zarr/MitoEM_fixedRes";
		final String path = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0";
		final N5Reader n5reader = new N5Factory().openReader(path);
		System.out.println("got reader: "+n5reader);
		N5Metadata m = N5DatasetDiscoverer.discover(n5reader).getMetadata();
		System.out.println("got metadata: "+m);

		/*
		if (m instanceof OmeNgffV05Metadata) {
			OmeNgffV05Metadata mv05 = (OmeNgffV05Metadata)m;
			System.out.println("name: "+mv05.getName());
			for (OmeNgffMultiScaleMetadata ms : mv05.multiscales) {
				System.out.println("----------------");
				System.out.println("name: "+ms.name+"     type: "+ms.type+"     version: "+ms.version);
				System.out.println(ms.coordinateTransformations);
			}
		}
		*/

		//TODO fetch v0.5 NGFF Metadata -> ask John
		//there was supposed to be some class for it
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

				// Initialize N5Reader
				final Path inputPath = Paths.get( multiscaleImage.getMultiscalePath() );
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
		// FIXME (JOHN) implement SpimData creation
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
	{
	}

	/**
	 * Create/update calibrated axes for ImgPlus {@link DefaultPyramidal5DImageData#imgPlus}.
	 * <br>
	 * This only needs to consider
	 * the highest resolution dataset and metadata.
	 */
	private void updateImgPlusAxes()
	{
		final Multiscales multiscales = multiscaleImage.getMultiscales();

		// The axes, which are valid for all resolutions.
		final List< Multiscales.Axis > axes = multiscales.getAxes();

		// The scale factors of the resolution level 0
		// final double[] scaleFactors = multiscales.getScales().get( 0 ).scaleFactors;

		// The global transformations that
		// should be applied to all resolutions.
		final Multiscales.CoordinateTransformations[] globalCoordinateTransformations = multiscales.getCoordinateTransformations();

		// The transformations that should
		// only be applied to the highest resolution,
		// which is the one we are concerned with here.
		final Multiscales.CoordinateTransformations[] coordinateTransformations = multiscales.getDatasets()[ 0 ].coordinateTransformations;

		// Concatenate all scaling transformations
		final double[] scaleFactors = new double[ numDimensions ];
		Arrays.fill( scaleFactors, 1.0 );
		if ( globalCoordinateTransformations != null )
			for ( Multiscales.CoordinateTransformations transformation : globalCoordinateTransformations )
				for ( int d = 0; d < numDimensions; d++ )
					scaleFactors[ d ] *= transformation.scale[ d ];

		if ( coordinateTransformations != null )
			for ( Multiscales.CoordinateTransformations transformation : coordinateTransformations )
				for ( int d = 0; d < numDimensions; d++ )
				{
					if ( transformation.scale == null )
						continue;
					scaleFactors[ d ] *= transformation.scale[ d ];
				}

		reverseArray( scaleFactors );

		// Create the imgAxes
		final ArrayList< CalibratedAxis > imgAxes = new ArrayList<>();

		// X
		final int xAxisIndex = multiscales.getSpatialAxisIndex( Multiscales.Axis.X_AXIS_NAME );
		if ( xAxisIndex >= 0 )
			imgAxes.add( createAxis( xAxisIndex, Axes.X, axes, scaleFactors ) );

		// Y
		final int yAxisIndex = multiscales.getSpatialAxisIndex( Multiscales.Axis.Y_AXIS_NAME );
		if ( yAxisIndex >= 0 )
			imgAxes.add( createAxis( yAxisIndex, Axes.Y, axes, scaleFactors ) );

		// Z
		final int zAxisIndex = multiscales.getSpatialAxisIndex( Multiscales.Axis.Z_AXIS_NAME );
		if ( zAxisIndex >= 0 )
			imgAxes.add( createAxis( zAxisIndex, Axes.Z, axes, scaleFactors ) );

		// C
		final int cAxisIndex = multiscales.getChannelAxisIndex();
		if ( cAxisIndex >= 0 )
		{
			imgAxes.add( createAxis( cAxisIndex, Axes.CHANNEL, axes, scaleFactors ) );
			numChannels = (int) dimensions[ cAxisIndex ];
		}

		// T
		final int tAxisIndex = multiscales.getTimepointAxisIndex();
		if ( tAxisIndex >= 0 )
		{
			imgAxes.add( createAxis( tAxisIndex, Axes.TIME, axes, scaleFactors ) );
			numTimepoints = ( int ) dimensions[ tAxisIndex ];
		}

		// Set all axes
		for ( int i = 0; i < imgAxes.size(); ++i )
			imgPlus.setAxis( imgAxes.get( i ), i );
	}

	private void reverseArray( final double[] arr )
	{

		for ( int i = 0; i < arr.length / 2; i++ )
		{
			double temp = arr[ i ];
			arr[ i ] = arr[ arr.length - 1 - i ];
			arr[ arr.length - 1 - i ] = temp;
		}

	}

	@NotNull
	private DefaultLinearAxis createAxis( int axisIndex, AxisType axisType, List< Multiscales.Axis > axes, double[] scale )
	{
		return new DefaultLinearAxis(
				axisType,
				axes.get( axisIndex ).unit,
				scale[ axisIndex ] );
	}

	@Override
	public int numDimensions()
	{
		return multiscaleImage.numDimensions();
	}

	/**
	 * Get the number of levels in the resolution pyramid.
	 */
	public int numResolutions()
	{
		return numResolutions;
	}

	/**
	 * Get the number timepoints.
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
		return multiscaleImage.getType();
	}

	@Override
	public String getName()
	{
		return name;
	}
}
