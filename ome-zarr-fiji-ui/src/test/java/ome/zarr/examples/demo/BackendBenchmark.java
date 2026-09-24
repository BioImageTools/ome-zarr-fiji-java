/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.examples.demo;

import bdv.viewer.SourceAndConverter;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import dev.zarr.zarrjava.ZarrException;
import dev.zarr.zarrjava.core.Array;
import dev.zarr.zarrjava.experimental.ome.MultiscaleImage;
import dev.zarr.zarrjava.store.FilesystemStore;
import dev.zarr.zarrjava.store.StoreHandle;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Intervals;

import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser;
import org.scijava.Context;
import org.slf4j.LoggerFactory;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;

import ome.zarr.imglib2.PyramidContents;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.n5.N5PyramidBackend;
import ome.zarr.zarrjava.ZarrJavaPyramidBackend;
import ome.zarr.ZarrTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Times the same OME-Zarr datasets through both {@code PyramidBackend}
 * implementations and, as {@code -pure} rows, through the reader libraries
 * directly, so a difference can be pinned on a backend or on the library under
 * it. One row per dataset, operation and backend, with the voxels each row read
 * and the min, median and max of the measured rounds.
 *
 * <pre>
 * mvn -pl ome-zarr-fiji-ui -am -DskipTests test-compile
 * MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED" \
 * mvn -q -pl ome-zarr-fiji-ui -Dexec.classpathScope=test \
 *     -Dexec.mainClass=ome.zarr.examples.demo.BackendBenchmark exec:java
 * </pre>
 *
 * {@code -pl} because at the reactor root the goal runs for every module and
 * only this one has both backends on its test classpath; {@code MAVEN_OPTS}
 * because {@code exec:java} runs in the Maven JVM, which the root pom's
 * {@code zarr.test.addOpens} profiles do not reach — they configure surefire.
 * <p>
 * <b>Treat the numbers as indicative only</b>, and deliberately not documented
 * outside this class: the harness has not been studied closely enough to claim
 * the figures mean what they appear to. Known to distort them:
 * <ul>
 *   <li>the bundled datasets total ~1 MB and stay in the page cache, so nothing
 *       here measures storage or network;</li>
 *   <li>{@code read-pure} on zarr-java is one bulk {@code Array.read}, while
 *       every other read row walks voxels through an imglib2 cursor, and that
 *       traversal dominates them;</li>
 *   <li>no heap is pinned, so a {@code max} far above {@code min} is usually GC
 *       or imglib2 dropping softly-held cells, not the backend.</li>
 * </ul>
 */
public class BackendBenchmark
{
	/**
	 * Untimed rounds run before any measurement, to let the JIT compile the
	 * reader paths. Deliberately generous: the point of a warmup round is that
	 * it is cheap compared to being wrong.
	 */
	private static final int WARMUP_ROUNDS = 10;

	private static final int MEASURE_ROUNDS = 15;

	private static final List< String > DATASETS = Arrays.asList(
			"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr",
			"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr",
			"ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr",
			"ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr"
	);

	public static void main( final String[] args )
	{
		// Keep the real stderr before disableAllLogs() swallows it, so a failure
		// reports itself instead of vanishing. Without the explicit exit the JVM
		// would also hang on the failure path: the SciJava Context and BDV's
		// SharedQueue keep non-daemon threads alive.
		final PrintStream realErr = System.err;
		try
		{
			run();
			System.exit( 0 );
		}
		catch ( final Throwable t )
		{
			t.printStackTrace( realErr );
			System.exit( 1 );
		}
	}

	private static void run() throws Exception
	{
		final List< Path > datasets = resolveDatasets();
		disableAllLogs();

		// Warm up over every dataset before timing any of them. The JIT compiles
		// per JVM, not per dataset, so warming inside the measurement loop leaves
		// the first dataset measuring class loading and compilation rather than
		// the reader — which made it look ~5x slower per voxel than an identical
		// dataset later in the list.
		for ( final Path dataset : datasets )
			warmup( dataset.toString() );

		System.out.println( "Backend benchmark (times in ms over " + MEASURE_ROUNDS + " measured rounds, "
				+ WARMUP_ROUNDS + " warmup rounds per dataset)" );
		System.out.println( "min is the truest speed (noise only ever adds time); a max far above min means the "
				+ "measurement is unstable." );
		System.out.println();
		System.out.printf( Locale.ROOT, "%-24s %-10s %-10s %10s %9s %9s %9s%n",
				"Dataset", "Operation", "Backend", "voxels", "min", "median", "max" );
		System.out.println( divider( 87 ) );

		for ( final Path datasetPath : datasets )
		{
			final String dataset = datasetPath.toString();
			final String name = datasetPath.getFileName().toString();
			final OpenedReadContexts opened = openReadContexts( dataset );

			final long wrappedVoxels = countVoxels( opened.n5WrappedLevel0 );
			final long pureN5Voxels = Intervals.numElements( opened.pureN5Level0 );
			final long pureZjVoxels = countVoxels( opened.pureZjLevel0 );

			row( name, "open", "N5", NO_VOXELS, measure( () -> benchN5Open( dataset ) ) );
			row( name, "open", "zarr-java", NO_VOXELS, measure( () -> benchZarrJavaOpen( dataset ) ) );
			row( name, "open-pure", "N5", NO_VOXELS, measure( () -> benchPureN5Open( dataset ) ) );
			row( name, "open-pure", "zarr-java", NO_VOXELS, measure( () -> benchPureZarrJavaOpen( dataset ) ) );
			row( name, "read", "N5", wrappedVoxels, measure( () -> readVolumes( opened.n5WrappedLevel0 ) ) );
			row( name, "read", "zarr-java", countVoxels( opened.zjWrappedLevel0 ),
					measure( () -> readVolumes( opened.zjWrappedLevel0 ) ) );
			row( name, "read-pure", "N5", pureN5Voxels,
					measure( () -> readWholeImage( opened.pureN5Level0 ) ) );
			row( name, "read-pure", "zarr-java", pureZjVoxels,
					measure( () -> readWholePureZarr( opened.pureZjLevel0 ) ) );

			opened.close();
		}
	}

	/**
	 * Resolves every configured dataset up front and fails with the offending
	 * path when one is missing. {@link #resourcePath} passes absolute paths
	 * through unchecked, so without this an unreachable dataset only surfaces
	 * deep inside a backend.
	 */
	private static List< Path > resolveDatasets() throws URISyntaxException, IOException
	{
		final List< Path > paths = new ArrayList<>( DATASETS.size() );
		for ( final String resource : DATASETS )
		{
			final Path path = resourcePath( resource );
			if ( !Files.exists( path ) )
				throw new IOException( "Dataset not found: " + path + " (from '" + resource + "')" );
			paths.add( path );
		}
		return paths;
	}

	/** Marks a row whose operation reads no pixels, so no voxel count applies. */
	private static final long NO_VOXELS = -1L;

	private static void row( final String dataset, final String operation, final String backend,
			final long voxels, final Stats stats )
	{
		System.out.printf( Locale.ROOT, "%-24s %-10s %-10s %10s %9.2f %9.2f %9.2f%n",
				dataset, operation, backend, voxels == NO_VOXELS ? "-" : Long.toString( voxels ),
				stats.min, stats.median, stats.max );
	}

	private static long countVoxels( final Array array )
	{
		long voxels = 1;
		for ( final long dim : array.metadata().shape )
			voxels *= dim;
		return voxels;
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
			readVolumes( opened.n5WrappedLevel0 );
			readVolumes( opened.zjWrappedLevel0 );
			readWholeImage( opened.pureN5Level0 );
			readWholePureZarr( opened.pureZjLevel0 );
		}
		opened.close();
	}

	/**
	 * Times {@code benchmark} {@link #MEASURE_ROUNDS} times and reports the
	 * distribution rather than a mean. A mean is actively misleading for the read
	 * operations, where the first round loads and decompresses and the rest hit
	 * an already-populated cache: averaging 40ms with four times 1ms yields 8.8ms,
	 * a figure no round ever took. min/median/max keeps that split visible.
	 */
	private static Stats measure( final ThrowingRunnable benchmark ) throws Exception
	{
		final double[] millis = new double[ MEASURE_ROUNDS ];
		for ( int i = 0; i < MEASURE_ROUNDS; i++ )
		{
			final long t0 = System.nanoTime();
			benchmark.run();
			millis[ i ] = ( System.nanoTime() - t0 ) / 1_000_000.0;
		}
		Arrays.sort( millis );
		return new Stats( millis[ 0 ], millis[ millis.length / 2 ], millis[ millis.length - 1 ] );
	}

	private static final class Stats
	{
		private final double min;

		private final double median;

		private final double max;

		private Stats( final double min, final double median, final double max )
		{
			this.min = min;
			this.median = median;
			this.max = max;
		}
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static void benchN5Open( final String dataset )
	{
		new N5PyramidBackend().read( Paths.get( dataset ).toUri() );
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static void benchZarrJavaOpen( final String dataset )
	{
		new ZarrJavaPyramidBackend().read( Paths.get( dataset ).toUri() );
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
		final FilesystemStore store = new FilesystemStore( inputPath );
		StoreHandle handle = store.resolve();
		return MultiscaleImage.open( handle );
	}

	private static N5OpenContext openN5Context( final Path inputPath ) throws IOException
	{
		if ( inputPath == null )
			throw new IOException( "No zarr root for " + inputPath );
		final N5Reader reader = new N5Factory().openReader( inputPath.toUri().toString() );
		final N5TreeNode node = new N5TreeNode( "" );
		final List< N5MetadataParser< ? > > parsers = Collections.singletonList( new OmeNgffMetadataParser( reader ) );
		N5DatasetDiscoverer.parseMetadataShallow( reader, node, parsers, parsers );
		final N5Metadata metadata = node.getMetadata();
		if ( metadata == null )
			throw new IOException( "No NGFF metadata for " + inputPath );
		return new N5OpenContext( reader, metadata );
	}

	private static String resolveN5Level0Path( final N5OpenContext ctx ) throws IOException
	{
		final N5Metadata metadata = ctx.metadata;
		if ( metadata instanceof OmeNgffMetadata )
			return ( ( OmeNgffMetadata ) metadata ).multiscales[ 0 ].getChildrenMetadata()[ 0 ].getPath();
		throw new IOException( "Unsupported metadata class: " + metadata.getClass().getName() );
	}

	private static OpenedReadContexts openReadContexts( final String dataset ) throws Exception
	{
		final Context n5Context = new Context();
		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final PyramidContents< ? > n5Wrapped = new N5PyramidBackend().read( Paths.get( dataset ).toUri() );
		final List< RandomAccessibleInterval< ? > > n5WrappedLevel0 = allLevel0Volumes( n5Context, n5Wrapped );

		final Context zjContext = new Context();
		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final PyramidContents< ? > zjWrapped =
				new ZarrJavaPyramidBackend().read( Paths.get( dataset ).toUri() );
		final List< RandomAccessibleInterval< ? > > zjWrappedLevel0 = allLevel0Volumes( zjContext, zjWrapped );

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

	/**
	 * Every (channel, timepoint) volume of resolution level 0, as the BDV source
	 * stack exposes them.
	 * <p>
	 * Taking only {@code asSources().get( 0 ).getSpimSource().getSource( 0, 0 )}
	 * — channel 0 at timepoint 0 — would cover a twelfth of a 4t x 3c dataset,
	 * while the two {@code *-pure} paths read the array whole. That made
	 * {@code read-pure} look an order of magnitude slower when it was simply
	 * reading an order of magnitude more data. Iterating every channel and
	 * timepoint puts all four read paths over the same voxels without assuming
	 * anything about axis order: {@link PyramidContents#numChannels()} and
	 * {@link PyramidContents#numTimepoints()} both report 1 when the axis is
	 * absent, so a 2D dataset yields exactly one volume.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static List< RandomAccessibleInterval< ? > > allLevel0Volumes(
			final Context context, final PyramidContents contents )
	{
		final List< SourceAndConverter > sources = new PyramidalBdv( context, contents ).asSources();
		final int numTimepoints = contents.numTimepoints();
		final List< RandomAccessibleInterval< ? > > volumes =
				new ArrayList<>( sources.size() * numTimepoints );
		for ( final SourceAndConverter sac : sources )
			for ( int t = 0; t < numTimepoints; t++ )
				volumes.add( sac.getSpimSource().getSource( t, 0 ) );
		return volumes;
	}

	private static void readVolumes( final List< RandomAccessibleInterval< ? > > volumes )
	{
		for ( final RandomAccessibleInterval< ? > volume : volumes )
			readWholeImage( volume );
	}

	private static long countVoxels( final List< RandomAccessibleInterval< ? > > volumes )
	{
		long voxels = 0;
		for ( final RandomAccessibleInterval< ? > volume : volumes )
			voxels += Intervals.numElements( volume );
		return voxels;
	}

	private static void readWholeImage( final RandomAccessibleInterval< ? > img )
	{
		final Cursor< ? > cursor = net.imglib2.view.Views.flatIterable( img ).cursor();
		while ( cursor.hasNext() )
			cursor.next();
	}

	private static void readWholePureZarr( final Array array ) throws ZarrException
	{
		final long[] origin = new long[ array.metadata().shape.length ];
		final long[] shape = array.metadata().shape.clone();
		array.read( origin, shape );
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

		// Force Logback root logger OFF. The shared logback-test.xml also pins
		// ome.zarr to DEBUG, and an explicit level on a logger is not overridden
		// by the root's, so that one has to be silenced by name or its output
		// lands in the middle of the table.
		final LoggerContext context = ( LoggerContext ) LoggerFactory.getILoggerFactory();
		context.getLogger( org.slf4j.Logger.ROOT_LOGGER_NAME ).setLevel( Level.OFF );
		context.getLogger( "ome.zarr" ).setLevel( Level.OFF );

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
		private final List< RandomAccessibleInterval< ? > > n5WrappedLevel0;

		private final List< RandomAccessibleInterval< ? > > zjWrappedLevel0;

		private final RandomAccessibleInterval< ? > pureN5Level0;

		private final Array pureZjLevel0;

		private final Context n5Context;

		private final Context zjContext;

		private final N5Reader pureN5Reader;

		private OpenedReadContexts(
				final List< RandomAccessibleInterval< ? > > n5WrappedLevel0,
				final List< RandomAccessibleInterval< ? > > zjWrappedLevel0,
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
