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
package sc.fiji.ome.zarr;

import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.VoxelDimensions;
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
import org.jetbrains.annotations.NotNull;
import org.scijava.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An OME-Zarr backed pyramidal 5D image
 * that can be visualised in ImageJ in various ways.
 *
 * @param <T> Type of the pixels
 * @param <V> Volatile type of the pixels
 */
public class DefaultPyramidal5DImageData< T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > > implements EuclideanSpace, Pyramidal5DImageData< T >
{
	/**
	 * The scijava context. This is needed (only) for creating {@link #ijDataset}.
	 */
	private final Context context;

	private final String name;

	private final MultiscaleImage< T, V > multiscaleImage;

	private int numChannels = 1;

	private int numTimePoints = 1;

	private int numResolutions;

	private ImgPlus< T > imgPlus;

	private Dataset ijDataset;

	private List< SourceAndConverter< T > > sources;

	private SpimData spimData;

	//TODO:
	//private OMEZarrAxes omeZarrAxes;

	private int numDimensions;

	private long[] dimensions;

	/**
	 * Build a dataset from a single {@code PyramidalOMEZarrArray},
	 * which MUST only contains subset of the axes: X,Y,Z,C,T
	 *
	 * @param context The SciJava context for building the SciJava dataset
	 * @param multiscaleImage The array containing the image all data.
	 * @throws Error
	 */
	public DefaultPyramidal5DImageData(
			final Context context,
			final String name,
			final MultiscaleImage< T, V > multiscaleImage ) throws Error
	{
		this.context = context;
		this.name = name;
		this.multiscaleImage = multiscaleImage;

		numResolutions = multiscaleImage.numResolutions();;
		dimensions = multiscaleImage.dimensions();
		numDimensions = dimensions.length;
	}

	@Override
	public PyramidalDataset asPyramidalDataset()
	{
		return new PyramidalDataset( this );
	}

	@Override
	public Dataset asDataset()
	{
		imgPlus();

		synchronized ( imgPlus )
		{
			if ( ijDataset == null )
			{
				final DatasetService datasetService = context.getService( DatasetService.class );
				ijDataset = datasetService.create( imgPlus );
				ijDataset.setName( imgPlus.getName() );
				ijDataset.setRGBMerged( false );
			}
			return ijDataset;
		}
	}

	@Override
	public List< SourceAndConverter< T > > asSources()
	{
		// FIXME (JOHN) implement List< SourceAndConverter< T > > creation
		return sources;
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

	private synchronized ImgPlus< T > imgPlus()
	{
		if ( imgPlus != null ) return null;

		imgPlus = new ImgPlus<>( multiscaleImage.getImg( 0 ) );
		imgPlus.setName( getName() );
		updateImgAxes();
		return imgPlus;
	}

	/**
	 * Create/update calibrated axes for ImgPlus.
	 *
	 * This only needs to consider
	 * the highest resolution dataset and metadata.
	 */
	private void updateImgAxes()
	{
		final Multiscales multiscales = multiscaleImage.getMultiscales();

		// The axes, which are valid for all resolutions.
		final List< Multiscales.Axis > axes = multiscales.getAxes();

		// The global transformations that
		// should be applied to all resolutions.
		final Multiscales.CoordinateTransformations[] globalCoordinateTransformations = multiscales.getCoordinateTransformations();

		// The transformations that should
		// only be applied to the highest resolution,
		// which is the one we are concerned with here.
		final Multiscales.CoordinateTransformations[] coordinateTransformations = multiscales.getDatasets()[ 0 ].coordinateTransformations;

		// Concatenate all scaling transformations
		final double[] scales = new double[ numDimensions ];
		Arrays.fill( scales, 1.0 );
		if ( globalCoordinateTransformations != null )
			for ( Multiscales.CoordinateTransformations transformation : globalCoordinateTransformations )
				for ( int d = 0; d < numDimensions; d++ )
					scales[ d ] *= transformation.scale[ d ];

		if ( coordinateTransformations != null )
			for ( Multiscales.CoordinateTransformations transformation : coordinateTransformations )
				for ( int d = 0; d < numDimensions; d++ )
					scales[ d ] *= transformation.scale[ d ];

		// Add translations?


		// Create the imgAxes
		final ArrayList< CalibratedAxis > imgAxes = new ArrayList<>();

		// X
		final int xAxisIndex = multiscales.getSpatialAxisIndex( Multiscales.Axis.X_AXIS_NAME );
		if ( xAxisIndex >= 0 )
			imgAxes.add( createAxis( xAxisIndex, Axes.X, axes, scales ) );

		// Y
		final int yAxisIndex = multiscales.getSpatialAxisIndex( Multiscales.Axis.Y_AXIS_NAME );
		if ( yAxisIndex >= 0 )
			imgAxes.add( createAxis( yAxisIndex, Axes.Y, axes, scales ) );

		// Z
		final int zAxisIndex = multiscales.getSpatialAxisIndex( Multiscales.Axis.Z_AXIS_NAME );
		if ( zAxisIndex >= 0 )
			imgAxes.add( createAxis( zAxisIndex, Axes.Z, axes, scales ) );

		// C
		final int cAxisIndex = multiscales.getChannelAxisIndex();
		if ( cAxisIndex >= 0 )
		{
			imgAxes.add( createAxis( cAxisIndex, Axes.CHANNEL, axes, scales ) );
			numChannels = (int) dimensions[ cAxisIndex ];
		}

		// T
		final int tAxisIndex = multiscales.getTimePointAxisIndex();
		if ( tAxisIndex >= 0 )
		{
			imgAxes.add( createAxis( tAxisIndex, Axes.TIME, axes, scales ) );
			numTimePoints = (int) dimensions[ tAxisIndex ];
		}

		// Set all axes
		for ( int i = 0; i < imgAxes.size(); ++i )
			imgPlus.setAxis( imgAxes.get( i ), i );
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
	public int numTimePoints()
	{
		return numTimePoints;
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
