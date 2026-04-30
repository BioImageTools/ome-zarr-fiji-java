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
package sc.fiji.ome.zarr.pyramid.backend.zarrjava;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.zarr.zarrjava.ZarrException;
import dev.zarr.zarrjava.core.Array;
import dev.zarr.zarrjava.experimental.ome.MultiscaleImage;
import dev.zarr.zarrjava.experimental.ome.metadata.Axis;
import dev.zarr.zarrjava.experimental.ome.metadata.MultiscalesEntry;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroChannel;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroMetadata;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroRdefs;
import dev.zarr.zarrjava.experimental.ome.metadata.OmeroWindow;
import dev.zarr.zarrjava.experimental.ome.metadata.transform.CoordinateTransformation;
import dev.zarr.zarrjava.experimental.ome.metadata.transform.ScaleCoordinateTransformation;
import dev.zarr.zarrjava.store.FilesystemStore;
import dev.zarr.zarrjava.store.HttpStore;
import dev.zarr.zarrjava.store.Store;
import dev.zarr.zarrjava.store.StoreHandle;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.realtransform.AffineTransform3D;
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
import net.imglib2.util.Cast;

import bdv.cache.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.exceptions.MultiImageDatasetException;
import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.exceptions.NotAMultiscaleImageException;
import sc.fiji.ome.zarr.pyramid.exceptions.PyramidLevelAccessException;
import sc.fiji.ome.zarr.pyramid.backend.PyramidBackend;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;

/**
 * {@link PyramidBackend} that reads OME-Zarr images with the zarr-java library.
 * Supports OME-NGFF v0.4 (Zarr v2) and v0.5 (Zarr v3).
 *
 * @param <T> pixel type
 * @param <V> volatile pixel type
 */
public class ZarrJavaPyramidBackend<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
		implements PyramidBackend< T, V >
{
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

	public ZarrJavaPyramidBackend( final URI inputUri )
	{
		this( inputUri, null );
	}

	public ZarrJavaPyramidBackend( final URI inputUri, final Integer preferredMaxWidth )
	{
		this.inputUri = inputUri;
		this.preferredMaxWidth = preferredMaxWidth;
	}

	@Override
	public PyramidContents< T, V > load()
	{
		final MultiscaleImage multiscaleImage = openMultiscaleImage();
		final MultiscalesEntry entry = readMultiscalesEntry( multiscaleImage );

		final int numResolutionLevels = countResolutionLevels( multiscaleImage );
		final int selectedResolutionLevelIndex =
				selectResolutionLevelIndex( multiscaleImage, entry, numResolutionLevels, preferredMaxWidth );

		final Array level0Array = openLevel( multiscaleImage, 0 );
		final Array selectedArray = openLevel( multiscaleImage, selectedResolutionLevelIndex );
		final T type = typeForZarrDataType( level0Array.metadata().dataType().getMA2DataType() );
		final V volatileType = Cast.unchecked( VolatileTypeMatcher.getVolatileTypeForType( type ) );

		// zarr shape is C-order [t, c, z, y, x]; imglib2 uses F-order [x, y, z, c, t]
		final long[] zarrShape = selectedArray.metadata().shape;
		final long[] dimensions = reverseToLong( zarrShape );
		final int numDimensions = dimensions.length;

		final int numTimepoints = getDimSizeForAxis( entry.axes, zarrShape, "t" );
		final int numChannels = getDimSizeForAxis( entry.axes, zarrShape, "c" );

		final String name = entry.name != null ? entry.name : defaultName();
		final double[] level0Scales = getLevel0Scales( entry, numDimensions );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		final CachedCellImg< T, ? >[] cachedCellImgs = Cast.unchecked( new CachedCellImg[ numResolutionLevels ] );
		final RandomAccessibleInterval< V >[] volatileImgs = Cast.unchecked( new RandomAccessibleInterval[ numResolutionLevels ] );
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			final Array arr = openLevel( multiscaleImage, level );
			final long[] imgShape = reverseToLong( arr.metadata().shape );
			final int[] imgChunk = reverseToInt( arr.metadata().chunkShape() );
			final ReadOnlyCachedCellImgOptions opts = ReadOnlyCachedCellImgOptions.options().cellDimensions( imgChunk );
			cachedCellImgs[ level ] = new ReadOnlyCachedCellImgFactory()
					.create( imgShape, type, new ZarrJavaCellLoader<>( arr ), opts );
			volatileImgs[ level ] = VolatileViews.wrapAsVolatile( cachedCellImgs[ level ], sharedQueue );
		}

		final ImgPlus< T > imgPlus = new ImgPlus<>( cachedCellImgs[ selectedResolutionLevelIndex ], name );
		configureImgPlusAxes( imgPlus, entry.axes, level0Scales );

		final VoxelDimensions voxelDimensions = createVoxelDimensions( level0Scales, entry.axes );
		final AffineTransform3D[] transforms = createTransforms( entry, numResolutionLevels, level0Scales );

		final int channelAxisIndex = imglibAxisIndex( entry.axes, "c", numDimensions );
		final int zAxisIndex = imglibAxisIndex( entry.axes, "z", numDimensions );
		final int timeAxisIndex = imglibAxisIndex( entry.axes, "t", numDimensions );

		final Omero omero = convertOmero( multiscaleImage.getOmeroMetadata() );
		final String[] channelLabels = Omero.buildChannelLabels( name, omero, numChannels );

		return PyramidContents.< T, V >builder()
				.name( name )
				.numResolutionLevels( numResolutionLevels )
				.numChannels( numChannels )
				.numTimepoints( numTimepoints )
				.numDimensions( numDimensions )
				.selectedResolutionLevelIndex( selectedResolutionLevelIndex )
				.type( type )
				.volatileType( volatileType )
				.voxelDimensions( voxelDimensions )
				.transforms( transforms )
				.cachedCellImgs( cachedCellImgs )
				.volatileImgs( volatileImgs )
				.imgPlus( imgPlus )
				.channelAxisIndex( channelAxisIndex )
				.zAxisPresent( zAxisIndex >= 0 )
				.timeAxisPresent( timeAxisIndex >= 0 )
				.channelLabels( channelLabels )
				.omero( omero )
				.build();
	}

	private static Omero convertOmero( final OmeroMetadata source )
	{
		if ( source == null )
			return null;
		final Omero omero = new Omero();
		omero.id = source.id != null ? source.id : 0;
		omero.version = source.version;
		omero.rdefs = convertRdefs( source.rdefs );
		if ( source.channels != null )
		{
			final List< Omero.Channel > channels = new ArrayList<>( source.channels.size() );
			for ( final OmeroChannel channel : source.channels )
				channels.add( convertChannel( channel ) );
			omero.channels = channels;
		}
		return omero;
	}

	private static Omero.Rdefs convertRdefs( final OmeroRdefs source )
	{
		if ( source == null )
			return null;
		final Omero.Rdefs rdefs = new Omero.Rdefs();
		rdefs.defaultT = source.defaultT != null ? source.defaultT : 0;
		rdefs.defaultZ = source.defaultZ != null ? source.defaultZ : 0;
		rdefs.model = source.model;
		return rdefs;
	}

	private static Omero.Channel convertChannel( final OmeroChannel source )
	{
		if ( source == null )
			return null;
		final Omero.Channel channel = new Omero.Channel();
		channel.active = source.active != null && source.active;
		channel.coefficient = source.coefficient != null ? source.coefficient : 0.0;
		channel.color = source.color;
		channel.family = source.family;
		channel.inverted = source.inverted != null && source.inverted;
		channel.label = source.label;
		channel.window = convertWindow( source.window );
		return channel;
	}

	private static Omero.Channel.Window convertWindow( final OmeroWindow source )
	{
		if ( source == null )
			return null;
		final Omero.Channel.Window window = new Omero.Channel.Window();
		window.start = source.start != null ? source.start : 0.0;
		window.end = source.end != null ? source.end : 0.0;
		window.min = source.min != null ? source.min : 0.0;
		window.max = source.max != null ? source.max : 0.0;
		return window;
	}

	// ---------------------------------------------------------------------
	// Store / path helpers
	// ---------------------------------------------------------------------

	private MultiscaleImage openMultiscaleImage()
	{
		final String scheme = inputUri.getScheme();
		if ( scheme == null || "file".equalsIgnoreCase( scheme ) )
			return openMultiscaleImageFromFilesystem( Paths.get( inputUri ) );
		if ( "http".equalsIgnoreCase( scheme ) || "https".equalsIgnoreCase( scheme ) )
			return openMultiscaleImageOverHttp( inputUri );
		throw new IllegalArgumentException( "Unsupported URI scheme '" + scheme + "' for OME-Zarr location: " + inputUri );
	}

	private MultiscaleImage openMultiscaleImageFromFilesystem( final Path inputPath )
	{
		StoreHandle handle;
		try
		{
			final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( inputPath );
			final Path zarrRoot = rootPath != null ? rootPath : inputPath;
			final FilesystemStore store = new FilesystemStore( zarrRoot );

			handle = store.resolve();
			if ( rootPath != null && !rootPath.equals( inputPath ) )
			{
				for ( final String segment : ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath ) )
					handle = handle.resolve( segment );
			}
			if ( hasOmeChild( handle ) )
				throw new MultiImageDatasetException( inputUri.toString() );
			return MultiscaleImage.open( handle );
		}
		catch ( ZarrException | IOException e )
		{
			throw new NotAMultiscaleImageException( inputUri.toString(), e );
		}
	}

	/**
	 * Open the OME-Zarr referenced by an http(s) URI. The URI is assumed to point
	 * at the OME-Zarr root – we cannot walk up the URL hierarchy to discover it
	 * the way we do on a local filesystem.
	 */
	private MultiscaleImage openMultiscaleImageOverHttp( final URI httpUri )
	{
		StoreHandle handle;
		try
		{
			final Store store = new HttpStore( httpUri.toString() );
			handle = store.resolve();
			if ( hasOmeChild( handle ) )
				throw new MultiImageDatasetException( inputUri.toString() );
			return MultiscaleImage.open( handle );
		}
		catch ( ZarrException | IOException e )
		{
			throw new NotAMultiscaleImageException( inputUri.toString(), e );
		}
	}

	/**
	 * Detects an {@code OME/} child group at {@code handle} — the marker for
	 * a {@code bioformats2raw.layout} collection (and, more loosely, for any
	 * OME-Zarr container whose root carries OME-XML metadata rather than a
	 * single multiscale image). Case-insensitive because some implementations
	 * use {@code OME} and others {@code ome}. Returns {@code false} if
	 * {@link StoreHandle#listChildren()} fails (e.g. http stores that don't
	 * expose directory listings) so a transient listing error never promotes
	 * a generic failure into a misleading "multi-image" message.
	 */
	private static boolean hasOmeChild( final StoreHandle handle )
	{
		try (final Stream< String > children = handle.listChildren())
		{
			return children.anyMatch( "OME"::equalsIgnoreCase );
		}
		catch ( final RuntimeException e )
		{
			return false;
		}
	}

	/** Fallback dataset name when the multiscales entry has none. */
	private String defaultName()
	{
		if ( "file".equalsIgnoreCase( inputUri.getScheme() ) )
			return Paths.get( inputUri ).getFileName().toString();
		final String path = inputUri.getPath();
		if ( path == null || path.isEmpty() )
			return "";
		final String trimmed = path.endsWith( "/" ) ? path.substring( 0, path.length() - 1 ) : path;
		final int slash = trimmed.lastIndexOf( '/' );
		return slash >= 0 ? trimmed.substring( slash + 1 ) : trimmed;
	}

	private MultiscalesEntry readMultiscalesEntry( final MultiscaleImage multiscaleImage )
	{
		try
		{
			return multiscaleImage.getMultiscaleNode( 0 );
		}
		catch ( ZarrException | NullPointerException | IndexOutOfBoundsException e )
		{
			// NB: zarr-java declares only ZarrException on getMultiscaleNode, but in practice it leaks NullPointerException
			// when no multi scales entry is present
			// or an IndexOutOfBoundsException when the array is empty
			// surface those as a missing-metadata error rather than letting them
			// bubble up unhandled.
			throw new NotAMultiscaleImageException( "No multiscale metadata at: " + inputUri, e );
		}
	}

	// ---------------------------------------------------------------------
	// Resolution level helpers
	// ---------------------------------------------------------------------

	private static int countResolutionLevels( final MultiscaleImage multiscaleImage )
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

	private int selectResolutionLevelIndex( final MultiscaleImage multiscaleImage, final MultiscalesEntry entry,
			final int numResolutionLevels, final Integer preferredMaxWidth )
	{
		if ( preferredMaxWidth == null )
			return 0;

		final int xAxis = zarrAxisIndex( entry.axes, "x" );
		if ( xAxis < 0 )
			return 0;

		int smallestWidth = Integer.MAX_VALUE;
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			final int width = ( int ) openLevel( multiscaleImage, level ).metadata().shape[ xAxis ];
			if ( width <= preferredMaxWidth )
				return level;
			smallestWidth = Math.min( smallestWidth, width );
		}

		throw new NoMatchingResolutionException( preferredMaxWidth, smallestWidth );
	}

	private Array openLevel( final MultiscaleImage multiscaleImage, final int levelIndex )
	{
		try
		{
			return multiscaleImage.openScaleLevel( levelIndex );
		}
		catch ( IOException | ZarrException e )
		{
			throw new PyramidLevelAccessException( inputUri.toString(), levelIndex, e );
		}
	}

	// ---------------------------------------------------------------------
	// Axis / scale helpers
	// ---------------------------------------------------------------------

	private static int getDimSizeForAxis( final List< Axis > axes, final long[] zarrShape, final String axisName )
	{
		if ( axes == null )
			return 1;
		for ( int i = 0; i < axes.size(); i++ )
		{
			if ( axisName.equals( axes.get( i ).name ) )
				return ( int ) zarrShape[ i ];
		}
		return 1;
	}

	private static int zarrAxisIndex( final List< Axis > axes, final String axisName )
	{
		if ( axes == null )
			return -1;
		for ( int i = 0; i < axes.size(); i++ )
		{
			if ( axisName.equals( axes.get( i ).name ) )
				return i;
		}
		return -1;
	}

	private static int imglibAxisIndex( final List< Axis > axes, final String axisName, final int numDimensions )
	{
		final int zarrIndex = zarrAxisIndex( axes, axisName );
		return zarrIndex < 0 ? -1 : numDimensions - 1 - zarrIndex;
	}

	private static double[] getLevel0Scales( final MultiscalesEntry entry, final int numDimensions )
	{
		final double[] scales = findLevelScale( entry, 0 );
		if ( scales != null )
			return scales;
		final int n = entry.axes != null ? entry.axes.size() : numDimensions;
		final double[] fallback = new double[ n ];
		Arrays.fill( fallback, 1.0 );
		return fallback;
	}

	private static VoxelDimensions createVoxelDimensions( final double[] level0Scales, final List< Axis > zarrAxes )
	{
		if ( zarrAxes == null )
			return new FinalVoxelDimensions( "", 1.0, 1.0, 1.0 );
		final double xScale = scaleForNamedAxis( zarrAxes, level0Scales, "x" );
		final double yScale = scaleForNamedAxis( zarrAxes, level0Scales, "y" );
		final double zScale = scaleForNamedAxis( zarrAxes, level0Scales, "z" );
		return new FinalVoxelDimensions( spatialUnit( zarrAxes ), xScale, yScale, zScale );
	}

	private static double scaleForNamedAxis( final List< Axis > axes, final double[] scales, final String name )
	{
		final int idx = zarrAxisIndex( axes, name );
		return idx >= 0 ? scales[ idx ] : 1.0;
	}

	/**
	 * Returns the unit attached to the last x/y/z axis encountered. OME-NGFF
	 * spatial axes share a single unit in well-formed datasets, so this
	 * collapses to "the spatial unit"; the original loop happened to write
	 * it last-wins, and this preserves that behavior.
	 */
	private static String spatialUnit( final List< Axis > axes )
	{
		String unit = "";
		for ( final Axis axis : axes )
		{
			if ( "x".equals( axis.name ) || "y".equals( axis.name ) || "z".equals( axis.name ) )
				unit = axis.unit == null ? "" : axis.unit;
		}
		return unit;
	}

	private static AffineTransform3D[] createTransforms( final MultiscalesEntry entry,
			final int numResolutionLevels, final double[] level0Scales )
	{
		final int[] spatialZarrIdx = new int[] {
				zarrAxisIndex( entry.axes, "x" ),
				zarrAxisIndex( entry.axes, "y" ),
				zarrAxisIndex( entry.axes, "z" )
		};
		final AffineTransform3D[] tr = new AffineTransform3D[ numResolutionLevels ];
		for ( int level = 0; level < numResolutionLevels; level++ )
		{
			final double[] scales = computeLevelScale( entry, level, level0Scales, spatialZarrIdx );
			final AffineTransform3D t = new AffineTransform3D();
			t.set( scales[ 0 ], 0, 0 );
			t.set( scales[ 1 ], 1, 1 );
			t.set( scales[ 2 ], 2, 2 );
			tr[ level ] = t;
		}
		return tr;
	}

	private static double[] computeLevelScale( final MultiscalesEntry entry, final int level,
			final double[] level0Scales, final int[] spatialZarrIdx )
	{
		final double[] levelScale = findLevelScale( entry, level );
		if ( levelScale == null )
			return fallbackSpatialScale( level0Scales, spatialZarrIdx );

		final double[] scales = new double[ 3 ];
		for ( int d = 0; d < 3; d++ )
		{
			final int zi = spatialZarrIdx[ d ];
			if ( zi >= 0 && zi < levelScale.length )
				scales[ d ] = levelScale[ zi ];
			else
				scales[ d ] = fallbackScaleAtAxis( level0Scales, zi );
		}
		return scales;
	}

	/**
	 * Returns the resolved scale array of the first
	 * {@link ScaleCoordinateTransformation} at {@code level} whose
	 * {@code scale} field is non-null. Returns {@code null} when the level
	 * doesn't exist or has no usable scale transformation. Returning the
	 * array directly (instead of the library type with a nullable
	 * {@code scale} field) keeps null-tracking local to this method, so
	 * callers don't have to repeat the {@code scaleCt.scale != null} check.
	 * OME-NGFF datasets carry at most one scale transformation per level,
	 * so "first usable one" is observably equivalent to "first scale ct,
	 * null-check at the call site".
	 * <p>
	 * Sonar's {@code S1168} ("return an empty array instead of null") does
	 * not apply: callers branch on the absence of a scale transformation
	 * (and build a length-correct fallback in that branch); an empty array
	 * would silently take the "use it" path and produce a zero-extent
	 * dataset.
	 */
	@SuppressWarnings( "java:S1168" )
	private static double[] findLevelScale( final MultiscalesEntry entry, final int level )
	{
		if ( entry.datasets == null || entry.datasets.size() <= level )
			return null;
		final dev.zarr.zarrjava.experimental.ome.metadata.Dataset ds = entry.datasets.get( level );
		if ( ds.coordinateTransformations == null )
			return null;
		for ( final CoordinateTransformation ct : ds.coordinateTransformations )
		{
			if ( ct instanceof ScaleCoordinateTransformation )
			{
				final ScaleCoordinateTransformation scaleCt = ( ScaleCoordinateTransformation ) ct;
				if ( scaleCt.scale != null )
					return toDoubleArray( scaleCt.scale );
			}
		}
		return null;
	}

	private static double[] toDoubleArray( final List< Double > values )
	{
		final double[] out = new double[ values.size() ];
		for ( int i = 0; i < out.length; i++ )
			out[ i ] = values.get( i );
		return out;
	}

	private static double[] fallbackSpatialScale( final double[] level0Scales, final int[] spatialZarrIdx )
	{
		final double[] scales = new double[ 3 ];
		for ( int d = 0; d < 3; d++ )
			scales[ d ] = fallbackScaleAtAxis( level0Scales, spatialZarrIdx[ d ] );
		return scales;
	}

	private static double fallbackScaleAtAxis( final double[] level0Scales, final int zarrIndex )
	{
		return zarrIndex >= 0 && zarrIndex < level0Scales.length ? level0Scales[ zarrIndex ] : 1.0;
	}

	private static < T extends NativeType< T > & RealType< T > > void configureImgPlusAxes(
			final ImgPlus< T > img, final List< Axis > zarrAxes, final double[] level0Scales )
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

	// ---------------------------------------------------------------------
	// Type mapping / utility
	// ---------------------------------------------------------------------

	@SuppressWarnings( "unchecked" )
	private static < T extends NativeType< T > & RealType< T > > T typeForZarrDataType( final ucar.ma2.DataType dt )
	{
		if ( dt == ucar.ma2.DataType.FLOAT )
			return ( T ) new FloatType();
		if ( dt == ucar.ma2.DataType.DOUBLE )
			return ( T ) new DoubleType();
		if ( dt == ucar.ma2.DataType.BYTE )
			return ( T ) new ByteType();
		if ( dt == ucar.ma2.DataType.UBYTE )
			return ( T ) new UnsignedByteType();
		if ( dt == ucar.ma2.DataType.SHORT )
			return ( T ) new ShortType();
		if ( dt == ucar.ma2.DataType.USHORT )
			return ( T ) new UnsignedShortType();
		if ( dt == ucar.ma2.DataType.INT )
			return ( T ) new IntType();
		if ( dt == ucar.ma2.DataType.UINT )
			return ( T ) new UnsignedIntType();
		if ( dt == ucar.ma2.DataType.LONG )
			return ( T ) new LongType();
		if ( dt == ucar.ma2.DataType.ULONG )
			return ( T ) new UnsignedLongType();
		throw new IllegalArgumentException( "Unsupported zarr data type: " + dt );
	}

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
}
