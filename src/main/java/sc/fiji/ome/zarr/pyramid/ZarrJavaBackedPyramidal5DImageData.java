package sc.fiji.ome.zarr.pyramid;

import dev.zarr.zarrjava.ZarrException;
import dev.zarr.zarrjava.core.Array;
import dev.zarr.zarrjava.ome.MultiscaleImage;
import dev.zarr.zarrjava.ome.metadata.Axis;
import dev.zarr.zarrjava.ome.metadata.CoordinateTransformation;
import dev.zarr.zarrjava.ome.metadata.MultiscalesEntry;
import dev.zarr.zarrjava.store.FilesystemStore;
import dev.zarr.zarrjava.store.StoreHandle;

import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.EuclideanSpace;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.scijava.Context;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Cast;

/**
 * An OME-Zarr backed pyramidal 5D image using zarr-java as backend (supports Zarr v2 and v3).
 * <p>
 * 5D refers to: x, y, z, t, channels.
 * <p>
 * Use this as an alternative to {@link DefaultPyramidal5DImageData} when Zarr v3 support is
 * required. {@link ZarrOpenActions} will attempt this backend first and fall back to the
 * N5-based {@link DefaultPyramidal5DImageData} if zarr-java cannot open the image.
 *
 * @param <T> Type of the pixels
 */
public class ZarrJavaBackedPyramidal5DImageData<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
		implements EuclideanSpace, Pyramidal5DImageData< T >
{
	private static final Map< String, AxisType > AXIS_MAPPING;
    private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

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

	private final Context context;

	private final String name;

	private final int numResolutionLevels;

	private final int selectedResolutionLevelIndex;

	private int numTimepoints = 1;

	private int numChannels = 1;

	private final int numDimensions;

	private final long[] dimensions;

	private final ImgPlus< T > imgPlus;

	private final Dataset ijDataset;

	private List< SourceAndConverter< T > > sourceAndConverters;

	private final String inputPathAsString;

	private final MultiscaleImage multiscaleImage;

	private final T type;
	private final V volatileType;

	private final MultiscalesEntry entry;

	private final VoxelDimensions voxelDimensions;
	private final AffineTransform3D[] transforms;
	private final RandomAccessibleInterval< V >[] volatileImgs;
	private final RandomAccessibleInterval< T >[] cachedCellImgs;

	/**
	 * Build a dataset from the given OME-Zarr path.
	 *
	 * @param context           The SciJava context
	 * @param inputPathAsString Path to the OME-Zarr dataset (root or subfolder)
	 * @throws NotAMultiscaleImageException if zarr-java cannot open the path as a multiscale image
	 */
	public ZarrJavaBackedPyramidal5DImageData( final Context context, final String inputPathAsString )
	{
        this( context, inputPathAsString, null );
        logger.info( "Attempting to open OME-Zarr image with zarr-java backend: {}.", inputPathAsString );
	}

	public ZarrJavaBackedPyramidal5DImageData( final Context context, final String inputPathAsString, final Integer preferredMaxWidth )
	{
		this.context = context;
		this.inputPathAsString = inputPathAsString;

		final Path inputPath = Paths.get( inputPathAsString );

		try
		{
			this.multiscaleImage = openMultiscaleImage( inputPath );
		}
		catch ( ZarrException | IOException e )
		{
			throw new NotAMultiscaleImageException( inputPathAsString, e );
		}

		try
		{
			this.entry = multiscaleImage.getMultiscaleNode( 0 );
		}
		catch ( ZarrException e )
		{
			throw new NotAMultiscaleImageException( "No multiscale metadata at: " + inputPathAsString, e );
		}

		this.numResolutionLevels = countResolutionLevels();
		this.selectedResolutionLevelIndex = selectResolutionLevelIndex( preferredMaxWidth );

		final Array level0Array = openLevel( 0 );
		final Array selectedArray = openLevel( selectedResolutionLevelIndex );
		final ucar.ma2.DataType zarrDataType = level0Array.metadata().dataType().getMA2DataType();
		this.type = typeForZarrDataType( zarrDataType );
		this.volatileType = Cast.unchecked( VolatileTypeMatcher.getVolatileTypeForType( type ) );

		// zarr shape is C-order [t, c, z, y, x]; imglib2 uses F-order [x, y, z, c, t]
		final long[] zarrShape = selectedArray.metadata().shape;
		this.dimensions = reverseToLong( zarrShape );
		this.numDimensions = dimensions.length;

		this.numTimepoints = getDimSizeForAxis( zarrShape, "t" );
		this.numChannels = getDimSizeForAxis( zarrShape, "c" );

		final int[] imgChunkShape = reverseToInt( selectedArray.metadata().chunkShape() );
		final ReadOnlyCachedCellImgOptions options = ReadOnlyCachedCellImgOptions.options().cellDimensions( imgChunkShape );
		final CachedCellImg< T, ? > img = new ReadOnlyCachedCellImgFactory()
				.create( dimensions, type, new ZarrJavaCellLoader<>( selectedArray ), options );

		this.name = entry.name != null ? entry.name : inputPath.getFileName().toString();

		this.imgPlus = new ImgPlus<>( img, name );
		final double[] level0Scales = getLevel0Scales();
		configureImgPlusAxes( imgPlus, entry.axes, level0Scales );
		this.voxelDimensions = createVoxelDimensions( level0Scales, entry.axes );

		this.transforms = createTransforms( level0Scales );
		this.cachedCellImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		this.volatileImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			final Array arr = openLevel( level );
			final long[] imgShape = reverseToLong( arr.metadata().shape );
			final int[] imgChunk = reverseToInt( arr.metadata().chunkShape() );
			final ReadOnlyCachedCellImgOptions opts = ReadOnlyCachedCellImgOptions.options().cellDimensions( imgChunk );
			this.cachedCellImgs[ level ] = new ReadOnlyCachedCellImgFactory()
					.create( imgShape, type, new ZarrJavaCellLoader<>( arr ), opts );
			this.volatileImgs[ level ] = VolatileViews.wrapAsVolatile( this.cachedCellImgs[ level ], sharedQueue );
		}

		this.ijDataset = new DefaultDataset( context, imgPlus );
		this.ijDataset.setName( name );
		this.ijDataset.setRGBMerged( false );
	}

	// -------------------------------------------------------------------------
	// Store / path helpers
	// -------------------------------------------------------------------------

	private MultiscaleImage openMultiscaleImage( final Path inputPath ) throws IOException, ZarrException
	{
		final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( inputPath );
		final Path zarrRoot = rootPath != null ? rootPath : inputPath;
		final FilesystemStore store = new FilesystemStore( zarrRoot );

		StoreHandle handle = store.resolve();
		if ( rootPath != null && !rootPath.equals( inputPath ) )
		{
			for ( final String segment : ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath ) )
				handle = handle.resolve( segment );
		}
		return MultiscaleImage.open( handle );
	}

	// -------------------------------------------------------------------------
	// Metadata helpers
	// -------------------------------------------------------------------------

	private int countResolutionLevels()
	{
		try
		{
			return multiscaleImage.getScaleLevelCount();
		}
		catch ( ZarrException e )
		{
			return 1;
		}
	}


	private int selectResolutionLevelIndex( final Integer preferredMaxWidth )
	{
		if ( preferredMaxWidth == null )
			return 0;

		final int xAxis = zarrAxisIndex( "x" );
		if ( xAxis < 0 )
			return 0;

		int smallestWidth = Integer.MAX_VALUE;
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			final int width = ( int ) openLevel( level ).metadata().shape[ xAxis ];
			if ( width <= preferredMaxWidth )
				return level;
			smallestWidth = Math.min( smallestWidth, width );
		}

		throw new NoMatchingResolutionException( preferredMaxWidth, smallestWidth );
	}

	private Array openLevel( final int levelIndex )
	{
		try
		{
			return multiscaleImage.openScaleLevel( levelIndex );
		}
		catch ( IOException | ZarrException e )
		{
			throw new RuntimeException( "Cannot open resolution level " + levelIndex + " of: " + inputPathAsString, e );
		}
	}

	private int getDimSizeForAxis( final long[] zarrShape, final String axisName )
	{
		final List< Axis > axes = entry.axes;
		for ( int i = 0; i < axes.size(); i++ )
		{
			if ( axisName.equals( axes.get( i ).name ) )
				return ( int ) zarrShape[ i ];
		}
		return 1;
	}

	private int zarrAxisIndex( final String axisName )
	{
		final List< Axis > axes = entry.axes;
		for ( int i = 0; i < axes.size(); i++ )
		{
			if ( axisName.equals( axes.get( i ).name ) )
				return i;
		}
		return -1;
	}

	private double[] getLevel0Scales()
	{
		if ( entry.datasets == null || entry.datasets.isEmpty() )
			return defaultScales();
		final dev.zarr.zarrjava.ome.metadata.Dataset ds = entry.datasets.get( 0 );
		if ( ds.coordinateTransformations == null )
			return defaultScales();
		for ( final CoordinateTransformation ct : ds.coordinateTransformations )
		{
			if ( "scale".equals( ct.type ) && ct.scale != null )
			{
				final double[] scales = new double[ ct.scale.size() ];
				for ( int i = 0; i < scales.length; i++ )
					scales[ i ] = ct.scale.get( i );
				return scales;
			}
		}
		return defaultScales();
	}

	private double[] defaultScales()
	{
		final int n = entry.axes != null ? entry.axes.size() : numDimensions;
		final double[] s = new double[ n ];
		Arrays.fill( s, 1.0 );
		return s;
	}


	private VoxelDimensions createVoxelDimensions( final double[] level0Scales, final List< Axis > zarrAxes )
	{
		double xScale = 1.0;
		double yScale = 1.0;
		double zScale = 1.0;
		String unit = "";

		for ( int i = 0; i < zarrAxes.size(); i++ )
		{
			final Axis axis = zarrAxes.get( i );
			if ( "x".equals( axis.name ) )
			{
				xScale = level0Scales[ i ];
				unit = axis.unit == null ? "" : axis.unit;
			}
			else if ( "y".equals( axis.name ) )
			{
				yScale = level0Scales[ i ];
				unit = axis.unit == null ? "" : axis.unit;
			}
			else if ( "z".equals( axis.name ) )
			{
				zScale = level0Scales[ i ];
				unit = axis.unit == null ? "" : axis.unit;
			}
		}

		return new FinalVoxelDimensions( unit, xScale, yScale, zScale );
	}

	private AffineTransform3D[] createTransforms( final double[] level0Scales )
	{
		final double[][] mipmapScales = computeMipmapScales( level0Scales );
		final AffineTransform3D[] tr = new AffineTransform3D[ numResolutionLevels ];
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			final AffineTransform3D t = new AffineTransform3D();
			t.set( mipmapScales[ level ][ 0 ], 0, 0 );
			t.set( mipmapScales[ level ][ 1 ], 1, 1 );
			t.set( mipmapScales[ level ][ 2 ], 2, 2 );
			tr[ level ] = t;
		}
		return tr;
	}

	private double[][] computeMipmapScales( final double[] level0Scales )
	{
		final double[][] result = new double[ numResolutionLevels ][];
		final String[] spatialAxes = { "x", "y", "z" };
		final int[] spatialZarrIdx = new int[ 3 ];
		for ( int d = 0; d < 3; d++ )
			spatialZarrIdx[ d ] = zarrAxisIndex( spatialAxes[ d ] );

		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			result[ level ] = new double[ 3 ];
			if ( level == 0 || entry.datasets == null || entry.datasets.size() <= level )
			{
				Arrays.fill( result[ level ], 1.0 );
				continue;
			}
			final dev.zarr.zarrjava.ome.metadata.Dataset ds = entry.datasets.get( level );
			if ( ds.coordinateTransformations == null )
			{
				Arrays.fill( result[ level ], 1.0 );
				continue;
			}
			for ( final CoordinateTransformation ct : ds.coordinateTransformations )
			{
				if ( "scale".equals( ct.type ) && ct.scale != null )
				{
					for ( int d = 0; d < 3; d++ )
					{
						final int zi = spatialZarrIdx[ d ];
						if ( zi >= 0 && zi < ct.scale.size() && level0Scales[ zi ] != 0 )
							result[ level ][ d ] = ct.scale.get( zi ) / level0Scales[ zi ];
						else
							result[ level ][ d ] = 1.0;
					}
					break;
				}
			}
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Axis configuration
	// -------------------------------------------------------------------------

	private void configureImgPlusAxes( final ImgPlus< T > img, final List< Axis > zarrAxes, final double[] level0Scales )
	{
		if ( zarrAxes == null )
			return;
		final int n = zarrAxes.size();
		for ( int zarrDim = 0; zarrDim < n; zarrDim++ )
		{
			final int imgDim = n - 1 - zarrDim;
			final Axis axis = zarrAxes.get( zarrDim );
			final AxisType axisType = AXIS_MAPPING.getOrDefault( axis.name, Axes.unknown() );
			final String unit = axis.unit != null ? axis.unit : "";
			img.setAxis( new DefaultLinearAxis( axisType, unit, level0Scales[ zarrDim ] ), imgDim );
		}
	}

	// -------------------------------------------------------------------------
	// Type mapping
	// -------------------------------------------------------------------------

	@SuppressWarnings( "unchecked" )
	private static < T extends NativeType< T > & RealType< T > > T typeForZarrDataType( final ucar.ma2.DataType dt )
	{
		if ( dt == ucar.ma2.DataType.FLOAT ) return ( T ) new FloatType();
		if ( dt == ucar.ma2.DataType.DOUBLE ) return ( T ) new DoubleType();
		if ( dt == ucar.ma2.DataType.BYTE ) return ( T ) new ByteType();
		if ( dt == ucar.ma2.DataType.UBYTE ) return ( T ) new UnsignedByteType();
		if ( dt == ucar.ma2.DataType.SHORT ) return ( T ) new ShortType();
		if ( dt == ucar.ma2.DataType.USHORT ) return ( T ) new UnsignedShortType();
		if ( dt == ucar.ma2.DataType.INT ) return ( T ) new IntType();
		if ( dt == ucar.ma2.DataType.UINT ) return ( T ) new UnsignedIntType();
		if ( dt == ucar.ma2.DataType.LONG ) return ( T ) new LongType();
		if ( dt == ucar.ma2.DataType.ULONG ) return ( T ) new UnsignedLongType();
		throw new IllegalArgumentException( "Unsupported zarr data type: " + dt );
	}

	// -------------------------------------------------------------------------
	// Utility
	// -------------------------------------------------------------------------

	private static long[] reverseToLong( final long[] arr )
	{
		final long[] out = new long[ arr.length ];
		for ( int i = 0; i < arr.length; i++ )
			out[ i ] = arr[ arr.length - 1 - i ];
		return out;
	}

	private static int[] reverseToInt( final int[] arr )
	{
		final int[] out = new int[ arr.length ];
		for ( int i = 0; i < arr.length; i++ )
			out[ i ] = arr[ arr.length - 1 - i ];
		return out;
	}

	// -------------------------------------------------------------------------
	// Interface implementations
	// -------------------------------------------------------------------------

	@Override
	public PyramidalDataset< T > asPyramidalDataset()
	{
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
		if ( sourceAndConverters == null )
			sourceAndConverters = buildSources();
		return sourceAndConverters;
	}

	@SuppressWarnings( "unchecked" )
	private List< SourceAndConverter< T > > buildSources()
	{
		final int zarrChanIdx = zarrAxisIndex( "c" );
		final int imgChanDim = zarrChanIdx >= 0 ? ( numDimensions - 1 - zarrChanIdx ) : -1;
		final int nChannels = imgChanDim >= 0 ? ( int ) cachedCellImgs[ 0 ].dimension( imgChanDim ) : 1;
		this.numChannels = nChannels;

		final List< SourceAndConverter< T > > result = new ArrayList<>();

		for ( int c = 0; c < nChannels; c++ )
		{
			final RandomAccessibleInterval< T >[] channelLevels = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
			final RandomAccessibleInterval< V >[] channelVolatileLevels = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
			for ( int level = 0; level < numResolutionLevels; level++ )
			{
				final RandomAccessibleInterval< T > channel = imgChanDim >= 0
						? Views.hyperSlice( cachedCellImgs[ level ], imgChanDim, c )
						: cachedCellImgs[ level ];
				final RandomAccessibleInterval< V > channelVolatile = imgChanDim >= 0
						? Views.hyperSlice( volatileImgs[ level ], imgChanDim, c )
						: volatileImgs[ level ];
				channelLevels[ level ] = ensureMinDimensions( channel );
				channelVolatileLevels[ level ] = ensureMinDimensions( channelVolatile );
			}

			final String sourceName = nChannels > 1 ? name + " [c=" + c + "]" : name;
			final RandomAccessibleIntervalMipmapSource4D< T > source4D =
					new RandomAccessibleIntervalMipmapSource4D<>( channelLevels, type, transforms, voxelDimensions, sourceName, true );
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile =
					new RandomAccessibleIntervalMipmapSource4D<>( channelVolatileLevels, volatileType, transforms, voxelDimensions, sourceName, true );
			result.add( createSourceAndConverter( source4D, source4DVolatile ) );
		}
		return result;
	}

	private < R > RandomAccessibleInterval< R > ensureMinDimensions( RandomAccessibleInterval< R > img )
	{
		while ( img.numDimensions() < 4 )
			img = Views.addDimension( img, 0, 0 );
		return img;
	}

	private SourceAndConverter< T > createSourceAndConverter(
			final RandomAccessibleIntervalMipmapSource4D< T > source4D,
			final RandomAccessibleIntervalMipmapSource4D< V > source4DVolatile )
	{
		final Converter< V, ARGBType > converterVolatile = BigDataViewer.createConverterToARGB( volatileType );
		final Converter< T, ARGBType > converter = BigDataViewer.createConverterToARGB( type );
		final SourceAndConverter< V > sourceAndConverterVolatile =
				BigDataViewer.wrapWithTransformedSource( new SourceAndConverter<>( source4DVolatile, converterVolatile ) );
		return new SourceAndConverter<>( source4D, converter, sourceAndConverterVolatile );
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

	@Override
	public int numResolutionLevels()
	{
		return numResolutionLevels;
	}

	@Override
	public int numTimepoints()
	{
		return numTimepoints;
	}

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
