package sc.fiji.ome.zarr.pyramid;

import bdv.BigDataViewer;
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.viewer.SourceAndConverter;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.AbstractContextual;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

public class PyramidalBdv< T extends NativeType< T > & RealType< T > > extends AbstractContextual implements Pyramidal
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Pyramidal5DImageData< T > data;

	private final List< SourceAndConverter< T > > sources;

	public PyramidalBdv( final Pyramidal5DImageData< T > data )
	{
		this.data = data;
		sources = initSourceAndConverters( data.getPyramidContents() );
		setContext( data.context() );
	}

	@Override
	public Pyramidal5DImageData< ? > getPyramidal5DImageData()
	{
		return data;
	}

	/**
	 * @return a list of BigDataViewer sources, representing a 5D (XYZCT) multi-resolution image, one source for each channel of the dataset.
	 * 	 The sources provide nested volatile versions. The sources are
	 * 	 multi-resolution, reflecting the resolution pyramid of the OME-Zarr.
	 */
	public List< SourceAndConverter< T > > asSources()
	{
		return sources;
	}

	@Override
	public int numTimepoints()
	{
		return data.numTimepoints();
	}

	public String getName()
	{
		return data.getName();
	}

	@Override
	public Omero getOmeroProperties()
	{
		return data.getOmeroProperties();
	}

	@SuppressWarnings( "unchecked" )
	private static < T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > >
			List< SourceAndConverter< T > > initSourceAndConverters( final PyramidContents< T, V > contents )
	{
		final int nLevels = contents.numResolutionLevels;
		final int numChannels = contents.numChannels;
		final int channelAxisIndex = contents.channelAxisIndex;
		final boolean zAxisPresent = contents.zAxisPresent;
		final boolean timeAxisPresent = contents.timeAxisPresent;
		final T type = contents.type;
		final V volatileType = contents.volatileType;
		final AffineTransform3D[] mipmapTransforms = contents.transforms;
		final VoxelDimensions voxelDimensions = contents.voxelDimensions;

		final RandomAccessibleInterval< T >[][] levelToChannels = new RandomAccessibleInterval[ nLevels ][];
		Arrays.setAll( levelToChannels,
				level -> splitInputStackIntoSourceStacks( numChannels, channelAxisIndex, zAxisPresent, timeAxisPresent,
						contents.cachedCellImgs[ level ] ) );

		final RandomAccessibleInterval< V >[][] levelToVolatileChannels = new RandomAccessibleInterval[ nLevels ][];
		Arrays.setAll( levelToVolatileChannels,
				level -> splitInputStackIntoSourceStacks( numChannels, channelAxisIndex, zAxisPresent, timeAxisPresent,
						contents.volatileImgs[ level ] ) );

		final List< SourceAndConverter< T > > sources = new ArrayList<>( numChannels );
		for ( int channelNumber = 0; channelNumber < numChannels; channelNumber++ )
		{
			final int channel = channelNumber;
			final String channelLabel = contents.channelLabels[ channelNumber ];
			final RandomAccessibleInterval< T >[] mipmapImgs = new RandomAccessibleInterval[ nLevels ];
			Arrays.setAll( mipmapImgs, level -> levelToChannels[ level ][ channel ] );
			final RandomAccessibleInterval< V >[] mipmapVolatileImgs = new RandomAccessibleInterval[ nLevels ];
			Arrays.setAll( mipmapVolatileImgs, level -> levelToVolatileChannels[ level ][ channel ] );
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile = new RandomAccessibleIntervalMipmapSource4D<>(
					mipmapVolatileImgs, volatileType, mipmapTransforms, voxelDimensions, channelLabel, true );
			final RandomAccessibleIntervalMipmapSource4D< T > source4D = new RandomAccessibleIntervalMipmapSource4D<>(
					mipmapImgs, type, mipmapTransforms, voxelDimensions, channelLabel, true );
			final SourceAndConverter< T > sourceAndConverter = createSourceAndConverter( source4D, source4DVolatile );
			sources.add( sourceAndConverter );
			BigDataViewer.createConverterSetup( sourceAndConverter, channelNumber );
		}
		return sources;
	}

	private static < T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > >
			SourceAndConverter< T > createSourceAndConverter( final RandomAccessibleIntervalMipmapSource4D< T > source4D,
					final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile )
	{
		final Converter< V, ARGBType > converterVolatile = BigDataViewer.createConverterToARGB( source4DVolatile.getType() );
		final Converter< T, ARGBType > converter = BigDataViewer.createConverterToARGB( source4D.getType() );
		final SourceAndConverter< V > sourceAndConverterVolatile =
				BigDataViewer.wrapWithTransformedSource( new SourceAndConverter<>( source4DVolatile, converterVolatile ) );
		return new SourceAndConverter<>( source4D, converter, sourceAndConverterVolatile );
	}

	/**
	 * Splits a single multichannel image stack into one {@link RandomAccessibleInterval} per channel,
	 * and ensures every result has XYZ and T dimensions (adding singleton axes where absent).
	 */
	@SuppressWarnings( "unchecked" )
	private static < T > RandomAccessibleInterval< T >[] splitInputStackIntoSourceStacks( final int numChannels, final int channelAxisIndex,
			final boolean zAxisPresent, final boolean timeAxisPresent, final RandomAccessibleInterval< T > img )
	{

		final RandomAccessibleInterval< T >[] sourceStacks = new RandomAccessibleInterval[ numChannels ];

		// If there is a channel dimension, slice img along that dimension.
		if ( channelAxisIndex != -1 )
		{
			Arrays.setAll( sourceStacks, c -> Views.hyperSlice( img, channelAxisIndex, c ) );
		}
		else
		{
			sourceStacks[ 0 ] = img;
		}

		// If there is no Z dimension, augment the sourceStacks by a Z dimension.
		if ( !zAxisPresent )
			Arrays.setAll( sourceStacks, c -> Views.addDimension( sourceStacks[ c ], 0, 0 ) );

		// If there is no T dimension, augment the sourceStacks by a T dimension.
		if ( !timeAxisPresent )
			Arrays.setAll( sourceStacks, c -> Views.addDimension( sourceStacks[ c ], 0, 0 ) );

		// If at this point the dim order is XYTZ (because there was only a T axis, and we appended a Z axis after that), permute to XYZT
		if ( !zAxisPresent && timeAxisPresent )
			Arrays.setAll( sourceStacks, c -> Views.permute( sourceStacks[ c ], 2, 3 ) );

		return sourceStacks;
	}

	// -- Reference counting, similar to what AbstractData does --

	@SuppressWarnings( "unused" )
	@Parameter( required = false )
	private ObjectService objectService;

	private int refs = 0;

	public void incrementReferences()
	{
		refs++;
		if ( refs == 1 )
			register();
	}

	public void decrementReferences()
	{
		logger.debug( "decrementReferences" );
		if ( refs == 0 )
		{
			throw new IllegalStateException( "decrementing reference count when it is already 0" );
		}
		refs--;
		if ( refs == 0 )
			delete();
	}

	/**
	 * Called the first time the reference count is incremented.
	 */
	private void register()
	{
		if ( objectService != null )
			objectService.addObject( this );
	}

	/**
	 * Called when the reference count is decremented to zero.
	 */
	private void delete()
	{
		if ( objectService != null )
			objectService.removeObject( this );
	}
}
