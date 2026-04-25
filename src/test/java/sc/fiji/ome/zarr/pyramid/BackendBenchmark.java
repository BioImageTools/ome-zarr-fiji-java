package sc.fiji.ome.zarr.pyramid;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import dev.zarr.zarrjava.ZarrException;
import dev.zarr.zarrjava.core.Array;
import dev.zarr.zarrjava.experimental.ome.MultiscaleImage;
import dev.zarr.zarrjava.store.FilesystemStore;
import dev.zarr.zarrjava.store.StoreHandle;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import org.scijava.Context;
import org.slf4j.LoggerFactory;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05Metadata;
import sc.fiji.ome.zarr.pyramid.backend.ZarrJavaPyramidBackend;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Simple backend benchmark without extra dependencies.
 *
 * Run with:
 * mvn -q -DskipTests -Dexec.classpathScope=test -Dexec.mainClass=sc.fiji.ome.zarr.pyramid.BackendBenchmark exec:java
 */
public class BackendBenchmark
{
	private static final int WARMUP_ROUNDS = 2;
	private static final int MEASURE_ROUNDS = 5;

	private static final List< String > DATASETS = Arrays.asList(
            "/home/hannes/Documents/projects/scm/data/3.66.9-6.141020_15-41-29.00.ome.zarr/0",
            "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example",
			"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example",
			"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr",
			"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr"
	);

	public static void main( final String[] args ) throws Exception
	{
		disableAllLogs();
		System.out.println( "Backend benchmark (times in ms, mean over " + MEASURE_ROUNDS + " rounds)" );
		System.out.println( "Warmup rounds: " + WARMUP_ROUNDS );
		System.out.println();
		System.out.printf( Locale.ROOT, "%-48s %10s %10s %12s %12s %10s %10s %12s %12s%n",
				"Dataset", "N5 open", "ZJ open", "PureN5 open", "PureZJ open", "N5 read", "ZJ read", "PureN5 read",
				"PureZJ read" );
		System.out.println( divider( 146 ) );

		for ( final String resource : DATASETS )
		{
			final Path datasetPath = resourcePath( resource );
			final String dataset = datasetPath.toString();
			final OpenedReadContexts opened = openReadContexts( dataset );
			warmup( dataset );

			final double n5Open = measure( () -> benchN5Open( dataset ) );
			final double zjOpen = measure( () -> benchZarrJavaOpen( dataset ) );
			final double pureN5Open = measure( () -> benchPureN5Open( dataset ) );
			final double pureZjOpen = measure( () -> benchPureZarrJavaOpen( dataset ) );
			final double n5OpenRead = measure( () -> readWholeImage( opened.n5WrappedLevel0 ) );
			final double zjOpenRead = measure( () -> readWholeImage( opened.zjWrappedLevel0 ) );
			final double pureN5Read = measure( () -> readWholeImage( opened.pureN5Level0 ) );
			final double pureZjRead = measure( () -> readWholePureZarr( opened.pureZjLevel0 ) );

			System.out.printf( Locale.ROOT, "%-48s %10.2f %10.2f %12.2f %12.2f %10.2f %10.2f %12.2f %12.2f%n",
					shortName( resource ), n5Open, zjOpen, pureN5Open, pureZjOpen, n5OpenRead, zjOpenRead, pureN5Read, pureZjRead );
			opened.close();
		}
		System.exit( 0 );
	}

	private static void warmup( final String dataset ) throws Exception
	{
		final OpenedReadContexts opened = openReadContexts( dataset );
		for ( int i = 0; i < WARMUP_ROUNDS; i++ )
		{
			benchN5Open( dataset );
			benchZarrJavaOpen( dataset );
			benchPureN5Open( dataset );
			benchPureZarrJavaOpen( dataset );
			readWholeImage( opened.n5WrappedLevel0 );
			readWholeImage( opened.zjWrappedLevel0 );
			readWholeImage( opened.pureN5Level0 );
			readWholePureZarr( opened.pureZjLevel0 );
		}
		opened.close();
	}

	private static double measure( final ThrowingRunnable benchmark ) throws Exception
	{
		long totalNanos = 0L;
		for ( int i = 0; i < MEASURE_ROUNDS; i++ )
		{
			final long t0 = System.nanoTime();
			benchmark.run();
			totalNanos += ( System.nanoTime() - t0 );
		}
		return ( totalNanos / ( double ) MEASURE_ROUNDS ) / 1_000_000.0;
	}

	private static void benchN5Open( final String dataset )
	{
		try (Context context = new Context())
		{
			new DefaultPyramidal5DImageData<>( context, dataset );
		}
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static void benchZarrJavaOpen( final String dataset )
	{
		try (Context context = new Context())
		{
			new DefaultPyramidal5DImageData( context, new ZarrJavaPyramidBackend( dataset ) );
		}
	}

	private static void benchPureZarrJavaOpen( final String dataset ) throws IOException, ZarrException
	{
		final MultiscaleImage image = openMultiscaleImage( Paths.get( dataset ) );
		image.getMultiscaleNode( 0 );
	}

	private static void benchPureN5Open( final String dataset ) throws IOException
	{
		final N5OpenContext ctx = openN5Context( Paths.get( dataset ) );
		resolveN5Level0Path( ctx );
	}

	private static MultiscaleImage openMultiscaleImage( final Path inputPath ) throws IOException, ZarrException
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

	private static N5OpenContext openN5Context( final Path inputPath ) throws IOException
	{
		final Path rootPath = ZarrOnFileSystemUtils.findRootFolder( inputPath );
		if ( rootPath == null )
			throw new IOException( "No zarr root for " + inputPath );
		final String relativePath = String.join( "/", ZarrOnFileSystemUtils.relativePathElements( rootPath, inputPath ) );
		final N5Reader reader = new N5Factory().openReader( rootPath.toUri().toString() );
		final N5TreeNode node = new N5TreeNode( relativePath );
		final List< N5MetadataParser< ? > > parsers = Arrays.asList(
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadataParser(),
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser(),
				new org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.OmeNgffV05MetadataParser() );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, parsers );
		final N5Metadata metadata = node.getMetadata();
		if ( metadata == null )
			throw new IOException( "No NGFF metadata for " + inputPath );
		return new N5OpenContext( reader, metadata );
	}

	private static String resolveN5Level0Path( final N5OpenContext ctx ) throws IOException
	{
		final N5Metadata metadata = ctx.metadata;
		if ( metadata instanceof OmeNgffV05Metadata )
		{
			final OmeNgffV05Metadata v05 = ( OmeNgffV05Metadata ) metadata;
			return v05.multiscales[ 0 ].getChildrenMetadata()[ 0 ].getPath();
		}
		if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata )
		{
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata v04 =
					(org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata) metadata;
			return v04.multiscales[ 0 ].getChildrenMetadata()[ 0 ].getPath();
		}
		if ( metadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata )
		{
			final org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata v03 =
					(org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v03.OmeNgffMetadata) metadata;
			final N5SingleScaleMetadata[] children = v03.getMultiscales()[ 0 ].getChildrenMetadata();
			return children[ 0 ].getPath();
		}
		throw new IOException( "Unsupported metadata class: " + metadata.getClass().getName() );
	}

	private static OpenedReadContexts openReadContexts( final String dataset ) throws Exception
	{
		final Context n5Context = new Context();
		final DefaultPyramidal5DImageData< ?, ? > n5Wrapped = new DefaultPyramidal5DImageData<>( n5Context, dataset );
		final RandomAccessibleInterval< ? > n5WrappedLevel0 = n5Wrapped.asSources().get( 0 ).getSpimSource().getSource( 0, 0 );

		final Context zjContext = new Context();
		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final DefaultPyramidal5DImageData< ?, ? > zjWrapped = new DefaultPyramidal5DImageData( zjContext, new ZarrJavaPyramidBackend( dataset ) );
		final RandomAccessibleInterval< ? > zjWrappedLevel0 = zjWrapped.asSources().get( 0 ).getSpimSource().getSource( 0, 0 );

		final N5OpenContext n5Pure = openN5Context( Paths.get( dataset ) );
		final String level0Path = resolveN5Level0Path( n5Pure );
		final RandomAccessibleInterval< ? > pureN5Level0 = N5Utils.open( n5Pure.reader, level0Path );
		final MultiscaleImage pureZjImage = openMultiscaleImage( Paths.get( dataset ) );
		final Array pureZjLevel0 = pureZjImage.openScaleLevel( 0 );

		return new OpenedReadContexts(
				n5WrappedLevel0,
				zjWrappedLevel0,
				pureN5Level0,
				pureZjLevel0,
				n5Context,
				zjContext,
				n5Pure.reader );
	}

	private static void readWholeImage( final RandomAccessibleInterval< ? > img )
	{
		final Cursor< ? > cursor = net.imglib2.view.Views.flatIterable( img ).cursor();
		while ( cursor.hasNext() )
			cursor.next();
	}

	private static void readWholePureZarr( final Array array ) throws IOException, ZarrException
	{
		final long[] origin = new long[ array.metadata().shape.length ];
		final long[] shape = array.metadata().shape.clone();
		array.read( origin, shape );
	}

	private static String shortName( final String resource )
	{
		final int idx = resource.lastIndexOf( '/' );
		return idx >= 0 ? resource.substring( idx + 1 ) : resource;
	}

	private static String divider( final int n )
	{
		final StringBuilder sb = new StringBuilder( n );
		for ( int i = 0; i < n; i++ )
			sb.append( '-' );
		return sb.toString();
	}

	private static Path resourcePath( final String resource ) throws URISyntaxException
	{
		final Path path = Paths.get( resource );
		if ( path.isAbsolute() )
			return path;
		return ZarrTestUtils.resourcePath( resource );
	}

	private static void disableAllLogs()
	{
		// Mute System.err to suppress noisy plugin/framework startup logs.
		System.setErr( new PrintStream( new ByteArrayOutputStream() ) );

		// Force Logback root logger OFF.
		final LoggerContext context = ( LoggerContext ) LoggerFactory.getILoggerFactory();
		context.getLogger( org.slf4j.Logger.ROOT_LOGGER_NAME ).setLevel( Level.OFF );

		final LogManager logManager = LogManager.getLogManager();
		final Logger rootLogger = logManager.getLogger( "" );
		if ( rootLogger != null )
		{
			rootLogger.setLevel( java.util.logging.Level.OFF );
			for ( final Handler handler : rootLogger.getHandlers() )
				handler.setLevel( java.util.logging.Level.OFF );
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable
	{
		void run() throws Exception;
	}

	private static final class N5OpenContext
	{
		private final N5Reader reader;
		private final N5Metadata metadata;

		private N5OpenContext( final N5Reader reader, final N5Metadata metadata )
		{
			this.reader = reader;
			this.metadata = metadata;
		}
	}

	private static final class OpenedReadContexts implements AutoCloseable
	{
		private final RandomAccessibleInterval< ? > n5WrappedLevel0;
		private final RandomAccessibleInterval< ? > zjWrappedLevel0;
		private final RandomAccessibleInterval< ? > pureN5Level0;
		private final Array pureZjLevel0;
		private final Context n5Context;
		private final Context zjContext;
		private final N5Reader pureN5Reader;

		private OpenedReadContexts(
				final RandomAccessibleInterval< ? > n5WrappedLevel0,
				final RandomAccessibleInterval< ? > zjWrappedLevel0,
				final RandomAccessibleInterval< ? > pureN5Level0,
				final Array pureZjLevel0,
				final Context n5Context,
				final Context zjContext,
				final N5Reader pureN5Reader )
		{
			this.n5WrappedLevel0 = n5WrappedLevel0;
			this.zjWrappedLevel0 = zjWrappedLevel0;
			this.pureN5Level0 = pureN5Level0;
			this.pureZjLevel0 = pureZjLevel0;
			this.n5Context = n5Context;
			this.zjContext = zjContext;
			this.pureN5Reader = pureN5Reader;
		}

		@Override
		public void close()
		{
            pureN5Reader.close();
            try
			{
				n5Context.dispose();
			}
			finally
			{
				zjContext.dispose();
			}
		}
	}
}
