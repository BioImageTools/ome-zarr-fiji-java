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
package ome.zarr.fiji;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Cast;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.util.List;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import ome.zarr.ZarrTestUtils;
import ome.zarr.fiji.util.BdvUtils;
import ome.zarr.imglib2.PyramidContents;

/**
 * Shared parameterized tests for the BigDataViewer Fiji wrapper
 * {@link PyramidalBdv} around a {@link PyramidContents}. A concrete class
 * supplies {@link #load(String, Context)} for a specific backend, so each
 * backend is exercised through the BDV wrapper.
 * <p>
 * These tests intentionally live in the {@code ome.zarr.fiji} test package rather
 * than alongside the backend-agnostic {@link ome.zarr.imglib2.PyramidBackendTestBase},
 * so that the {@code ome.zarr.imglib2} core and the backend packages stay free of any
 * dependency on the Fiji/BDV integration layer.
 */
public interface PyramidalBdvTestBase
{

	PyramidContents< ? > load( String resource, Context context )
			throws URISyntaxException;

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsSources( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			PyramidalBdv< ? > pyramidalBdv = new PyramidalBdv<>( context, contents );
			assertNotNull( pyramidalBdv.asSources() );
			Source< ? > channel0 = pyramidalBdv.asSources().get( 0 ).getSpimSource();
			VoxelDimensions voxelDimensions = channel0.getVoxelDimensions();
			assertEquals( 2, channel0.getNumMipmapLevels() ); // 2 resolution levels
			assertInstanceOf( UnsignedByteType.class, channel0.getType() );
			assertNotNull( voxelDimensions );
			assertNotNull( channel0.getSource( 0, 0 ) ); // timepoint 0, resolution level 0
			assertNotNull( channel0.getSource( 0, 1 ) ); // timepoint 0, resolution level 1
			if ( resource.contains( "5d_testing" ) )
			{
				assertNotNull( channel0.getSource( 1, 0 ) ); // timepoint 1, resolution level 0
				assertNotNull( channel0.getSource( 1, 1 ) ); // timepoint 1, resolution level 1
				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				assertArrayEquals( new long[] { 64, 64, 16 }, dimensions );
				assertEquals( 3, pyramidalBdv.asSources().size() ); // 3 channels
				assertEquals( "lynEGFP", pyramidalBdv.asSources().get( 0 ).getSpimSource().getName() );
				assertEquals( "NLStdTomato", pyramidalBdv.asSources().get( 1 ).getSpimSource().getName() );
			}
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) || resource.contains( "xyzt" ) )
				{
					assertNotNull( channel0.getSource( 1, 0 ) ); // timepoint 1, resolution level 0
					assertNotNull( channel0.getSource( 1, 1 ) ); // timepoint 1, resolution level 1
					assertNotNull( channel0.getSource( 2, 0 ) ); // timepoint 2, resolution level 0
					assertNotNull( channel0.getSource( 2, 1 ) ); // timepoint 2, resolution level 1
					assertNotNull( channel0.getSource( 3, 0 ) ); // timepoint 3, resolution level 0
					assertNotNull( channel0.getSource( 3, 1 ) ); // timepoint 3, resolution level 1
				}
				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				if ( resource.contains( "xyct" ) )
					assertArrayEquals( new long[] { 64, 64, 1 }, dimensions );
				if ( resource.contains( "xyzc" ) || resource.contains( "xyzt" ) )
					assertArrayEquals( new long[] { 64, 64, 16 }, dimensions );
				if ( resource.contains( "xyct" ) || resource.contains( "xyzc" ) )
					assertEquals( 3, pyramidalBdv.asSources().size() ); // 3 channels
				if ( resource.contains( "xyzt" ) )
					assertEquals( 1, pyramidalBdv.asSources().size() ); // 1 channel
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyt" ) )
				{
					assertNotNull( channel0.getSource( 1, 0 ) ); // timepoint 1, resolution level 0
					assertNotNull( channel0.getSource( 1, 1 ) ); // timepoint 1, resolution level 1
					assertNotNull( channel0.getSource( 2, 0 ) ); // timepoint 2, resolution level 0
					assertNotNull( channel0.getSource( 2, 1 ) ); // timepoint 2, resolution level 1
					assertNotNull( channel0.getSource( 3, 0 ) ); // timepoint 3, resolution level 0
					assertNotNull( channel0.getSource( 3, 1 ) ); // timepoint 3, resolution level 1
				}
				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				if ( resource.contains( "xyc" ) || resource.contains( "xyt" ) )
					assertArrayEquals( new long[] { 64, 64, 1 }, dimensions );
				if ( resource.contains( "xyz" ) )
					assertArrayEquals( new long[] { 64, 64, 16 }, dimensions );
				if ( resource.contains( "xyc" ) )
					assertEquals( 3, pyramidalBdv.asSources().size() ); // 3 channels
				if ( resource.contains( "xyt" ) || resource.contains( "xyz" ) )
					assertEquals( 1, pyramidalBdv.asSources().size() ); // 1 channel
			}
			if ( resource.contains( "2d_testing" ) )
			{
				assertEquals( 1, pyramidalBdv.asSources().size() ); // 1 channel

				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				assertArrayEquals( new long[] { 64, 64, 1 }, dimensions );
				assertEquals( 1, pyramidalBdv.asSources().size() ); // 1 channel
				assertEquals( ZarrTestUtils.IMAGE_NAME, pyramidalBdv.asSources().get( 0 ).getSpimSource().getName() );
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumMipmapLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			PyramidalBdv< ? > pyramidalBdv = new PyramidalBdv<>( context, contents );
			assertEquals( 2, pyramidalBdv.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testVoxelDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			VoxelDimensions voxelDimensions =
					new PyramidalBdv<>( context, contents ).asSources().get( 0 ).getSpimSource().getVoxelDimensions();
			assertNotNull( voxelDimensions );
			assertEquals( "", voxelDimensions.unit() );
			assertArrayEquals( new double[] { 1, 1, 1 }, voxelDimensions.dimensionsAsDoubleArray() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#pyramids" )
	default void testGetPyramidLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			PyramidalBdv< ? > pyramidalBdv = new PyramidalBdv<>( context, contents );
			Source< ? > spimSource = pyramidalBdv.asSources().get( 0 ).getSpimSource();

			RandomAccessibleInterval< ? > resolutionLevel0 = spimSource.getSource( 0, 0 );
			RandomAccess< ? > randomAccessLevel0 = resolutionLevel0.randomAccess();
			randomAccessLevel0.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value0 = Cast.unchecked( randomAccessLevel0.get() ); // NB: compare uint8 type in src/test/resources/ome/zarr/testdata/pyramid_testing/create_pyramid.py

			RandomAccessibleInterval< ? > resolutionLevel1 = spimSource.getSource( 0, 1 );
			RandomAccess< ? > randomAccessLevel1 = resolutionLevel1.randomAccess();
			randomAccessLevel1.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value1 = Cast.unchecked( randomAccessLevel1.get() );

			RandomAccessibleInterval< ? > resolutionLevel2 = spimSource.getSource( 0, 2 );
			RandomAccess< ? > randomAccessLevel2 = resolutionLevel2.randomAccess();
			randomAccessLevel2.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value2 = Cast.unchecked( randomAccessLevel2.get() );

			assertEquals( 3, contents.numResolutionLevels() );
			assertEquals( 3, spimSource.getNumMipmapLevels() );
			assertEquals( 180, value0.get() ); // NB: compare values in src/test/resources/ome/zarr/testdata/pyramid_testing/create_pyramid.py
			assertEquals( 100, value1.get() );
			assertEquals( 20, value2.get() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testConverterSetup( final String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			PyramidalBdv< ? > pyramidalBdv = new PyramidalBdv<>( context, contents );
			BdvHandle bdvHandle = BdvUtils.showBdvAndRegisterDataset( pyramidalBdv );
			List< ConverterSetup > converterSetups =
					bdvHandle.getConverterSetups().getConverterSetups( pyramidalBdv.asSources() );
			assertNotNull( converterSetups );
			if ( resource.contains( "2d_testing" ) ) // dataset without omero properties
			{
				assertEquals( 1, converterSetups.size() ); // 1 channel
				ConverterSetup converterSetup = converterSetups.get( 0 );
				assertEquals( 0, converterSetup.getDisplayRangeMin() );
				assertEquals( 255, converterSetup.getDisplayRangeMax() );
				assertEquals( "(r=255,g=255,b=255,a=255)", converterSetup.getColor().toString() );
			}
			if ( resource.contains( "5d_testing" ) ) // dataset with omero properties
			{
				assertEquals( 3, converterSetups.size() ); // 3 channels
				ConverterSetup converterSetup0 = converterSetups.get( 0 );
				assertEquals( 3, converterSetup0.getDisplayRangeMin() );
				assertEquals( 246, converterSetup0.getDisplayRangeMax() );
				assertEquals( "(r=0,g=255,b=0,a=255)", converterSetup0.getColor().toString() );
				ConverterSetup converterSetup1 = converterSetups.get( 1 );
				assertEquals( 6, converterSetup1.getDisplayRangeMin() );
				assertEquals( 133, converterSetup1.getDisplayRangeMax() );
				assertEquals( "(r=255,g=0,b=0,a=255)", converterSetup1.getColor().toString() );
			}
			bdvHandle.close();
		}
	}
}