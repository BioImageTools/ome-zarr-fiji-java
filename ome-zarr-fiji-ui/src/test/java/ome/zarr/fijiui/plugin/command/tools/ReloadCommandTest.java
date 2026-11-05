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
package ome.zarr.fijiui.plugin.command.tools;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import net.imagej.Dataset;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.RealType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.convert.ConvertService;
import org.scijava.ui.UIService;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ImageProcessor;
import ome.zarr.ZarrTestUtils;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fiji.read.OmeZarr;
import ome.zarr.fijiui.open.options.OmeZarrBackend;

/**
 * Use case 3 of issue #156 end to end: an OME-Zarr is shown in BigDataViewer and in ImageJ, one chunk
 * file is removed and then put back, and after each step {@link ReloadCommand} has to make both
 * viewers show what is on disk. Runs for a 2D image and for a 5D one shown as composite, once per backend.
 * <p>
 * Reloading twice is the point: the first reload of a single-plane image used to turn its virtual
 * stack into a plain copy, which the second reload then skipped.
 */
class ReloadCommandTest
{
	private static final Duration BDV_TIMEOUT = Duration.ofSeconds( 10 );

	private static final String LOGO = "ome/zarr/testdata/bdv_testing/logo_spots.ome.zarr";

	/** t, c, z, y, x = 4, 3, 16, 64, 64. A chunk holds all z planes of one channel at one time point. */
	private static final String IMAGE_5D = "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr";

	private static final Case[] CASES = {
			// the chunk covers the top left quarter of the only plane
			new Case( "2D", LOGO, "s0/c/0/0", 0, 0, 0, -1, 0, 0, 256, 256 ),
			// the chunk of channel 2 at time point 3 covers all of plane 5
			new Case( "5D as composite", IMAGE_5D, "0/c/2/1/0/0/0", 1, 4, 2, IJ.COMPOSITE, 0, 0, 64, 64 ) };

	@TempDir
	Path tempDir;

	private Context context;

	private ImagePlus imagePlus;

	@BeforeEach
	void setUp()
	{
		assumeFalse( GraphicsEnvironment.isHeadless(), "Skipped in headless environment" );
	}

	@AfterEach
	void tearDown()
	{
		if ( imagePlus != null )
		{
			imagePlus.changes = false;
			imagePlus.close();
		}
		closeWindows();
		if ( context != null )
			context.dispose();
	}

	static Stream< Arguments > cases()
	{
		final List< Arguments > cases = new ArrayList<>();
		for ( final OmeZarrBackend backend : OmeZarrBackend.values() )
			for ( final Case testCase : CASES )
				cases.add( Arguments.of( testCase, backend ) );
		return cases.stream();
	}

	@ParameterizedTest
	@MethodSource( "cases" )
	void reloadShowsRemovedAndRestoredChunk( final Case testCase, final OmeZarrBackend backend ) throws Exception
	{
		final Path dataset = testCase.copyTo( tempDir.resolve( "image.ome.zarr" ) );
		context = new Context();
		context.service( UIService.class ).showUI();
		final OmeZarr omeZarr = new OmeZarr( dataset.toUri(), context, backend.createBackend() );
		omeZarr.showInBdv();
		imagePlus = imagePlusOf( omeZarr.showInImageJ( 0 ) );
		testCase.moveTo( imagePlus );
		final PyramidalBdv< ? > bdv = pyramidalBdv();

		final double[] original = testCase.imageJPlane( imagePlus );
		final double[] withHole = testCase.withHole( original );
		assertArrayEquals( original, bdvPlane( bdv, testCase ), "BigDataViewer after opening" );

		final Path chunk = dataset.resolve( testCase.chunk );
		final Path removed = tempDir.resolve( "removed-chunk" );
		Files.move( chunk, removed );
		reload();
		assertArrayEquals( withHole, testCase.imageJPlane( imagePlus ), "ImageJ after removing the chunk" );
		assertArrayEquals( withHole, bdvPlane( bdv, testCase ), "BigDataViewer after removing the chunk" );

		Files.move( removed, chunk );
		reload();
		assertArrayEquals( original, testCase.imageJPlane( imagePlus ), "ImageJ after restoring the chunk" );
		assertArrayEquals( original, bdvPlane( bdv, testCase ), "BigDataViewer after restoring the chunk" );
	}

	private void reload() throws Exception
	{
		context.service( CommandService.class ).run( ReloadCommand.class, true ).get();
	}

	/** The ImageJ image showing {@code dataset}. imagej-legacy registers its window before {@code showInImageJ} returns. */
	private ImagePlus imagePlusOf( final PyramidalDataset dataset )
	{
		assertNotNull( dataset );
		final ConvertService convertService = context.service( ConvertService.class );
		for ( final int id : WindowManager.getIDList() )
		{
			final ImagePlus candidate = WindowManager.getImage( id );
			if ( convertService.convert( candidate, Dataset.class ) == dataset )
				return candidate;
		}
		return fail( "No ImageJ window shows the dataset" );
	}

	private PyramidalBdv< ? > pyramidalBdv()
	{
		for ( final Pyramidal pyramidal : context.service( PyramidalService.class ).getPyramidals() )
			if ( pyramidal instanceof PyramidalBdv )
				return ( PyramidalBdv< ? > ) pyramidal;
		return fail( "No BigDataViewer pyramidal is registered" );
	}

	/**
	 * The plane BigDataViewer shows for the case, read from the volatile source at full resolution,
	 * waiting until BigDataViewer has loaded every pixel. Stale cells are valid too, so they are read as such.
	 */
	private static double[] bdvPlane( final PyramidalBdv< ? > bdv, final Case testCase )
	{
		final RandomAccessibleInterval< ? > volatileImg =
				bdv.asSources().get( testCase.channel ).asVolatile().getSpimSource().getSource( testCase.t, 0 );
		return await( "BigDataViewer loading the plane" ).atMost( BDV_TIMEOUT )
				.until( () -> loadedPlane( volatileImg, testCase.z ), Objects::nonNull );
	}

	/** Plane {@code z} of {@code volatileImg}, or {@code null} while a pixel of it is still being loaded. */
	private static double[] loadedPlane( final RandomAccessibleInterval< ? > volatileImg, final int z )
	{
		final int width = ( int ) volatileImg.dimension( 0 );
		final int height = ( int ) volatileImg.dimension( 1 );
		final double[] plane = new double[ width * height ];
		final RandomAccess< ? > access = volatileImg.randomAccess();
		boolean allValid = true;
		for ( int y = 0; y < height; y++ )
			for ( int x = 0; x < width; x++ )
			{
				final Volatile< ? > value = ( Volatile< ? > ) access.setPositionAndGet( x, y, z );
				allValid &= value.isValid();
				plane[ y * width + x ] = ( ( RealType< ? > ) value.get() ).getRealDouble();
			}
		return allValid ? plane : null;
	}

	private static void closeWindows()
	{
		try
		{
			SwingUtilities.invokeAndWait( () -> {
				for ( final Window window : Window.getWindows() )
					window.dispose();
			} );
		}
		catch ( InterruptedException | InvocationTargetException e )
		{
			Thread.currentThread().interrupt();
		}
	}

	/** One way ImageJ shows an OME-Zarr, and the chunk to remove from it. */
	private static final class Case
	{
		private final String name;

		private final String resource;

		/** The chunk file, relative to the dataset. It covers the region below of the plane shown. */
		private final String chunk;

		private final int channel;

		private final int z;

		private final int t;

		/** {@link CompositeImage} display mode, or {@code -1} for a plain image. */
		private final int mode;

		private final int x0, y0, width, height;

		Case( final String name, final String resource, final String chunk, final int channel, final int z, final int t,
				final int mode, final int x0, final int y0, final int width, final int height )
		{
			this.name = name;
			this.resource = resource;
			this.chunk = chunk;
			this.channel = channel;
			this.z = z;
			this.t = t;
			this.mode = mode;
			this.x0 = x0;
			this.y0 = y0;
			this.width = width;
			this.height = height;
		}

		Path copyTo( final Path target ) throws Exception
		{
			final Path source = ZarrTestUtils.resourcePath( resource );
			try (Stream< Path > files = Files.walk( source ))
			{
				for ( final Path file : ( Iterable< Path > ) files::iterator )
				{
					final Path copy = target.resolve( source.relativize( file ).toString() );
					if ( Files.isDirectory( file ) )
						Files.createDirectories( copy );
					else
						Files.copy( file, copy );
				}
			}
			return target;
		}

		/** Shows this case's channel, plane and time point, in its display mode. */
		void moveTo( final ImagePlus imagePlus )
		{
			if ( mode != -1 )
				( ( CompositeImage ) imagePlus ).setMode( mode );
			imagePlus.setPosition( channel + 1, z + 1, t + 1 );
			imagePlus.updateAndDraw();
		}

		/** The pixels ImageJ shows of this case's channel. A composite keeps one processor per channel. */
		double[] imageJPlane( final ImagePlus imagePlus )
		{
			final ImageProcessor processor = mode == IJ.COMPOSITE
					? ( ( CompositeImage ) imagePlus ).getProcessor( channel + 1 )
					: imagePlus.getProcessor();
			final double[] plane = new double[ processor.getPixelCount() ];
			Arrays.setAll( plane, processor::getf );
			return plane;
		}

		/** {@code plane} with the region of the chunk set to zero, which must not be zero already. */
		double[] withHole( final double[] plane )
		{
			final int planeWidth = ( int ) Math.sqrt( plane.length ); // all cases are square
			final double[] withHole = plane.clone();
			boolean changed = false;
			for ( int y = y0; y < y0 + height; y++ )
				for ( int x = x0; x < x0 + width; x++ )
				{
					changed |= withHole[ y * planeWidth + x ] != 0;
					withHole[ y * planeWidth + x ] = 0;
				}
			assertTrue( changed, name + ": the chunk to remove holds only zeros, so the test would prove nothing" );
			return withHole;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
