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
package ome.zarr.imglib2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Cast;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.util.stream.Stream;

import ome.zarr.ZarrTestUtils;
import ome.zarr.imglib2.exceptions.NoMatchingResolutionException;
import ome.zarr.imglib2.metadata.AxisCalibration;

/**
 * Shared parameterized tests for the backend-agnostic {@link PyramidContents}
 * produced by each {@link PyramidBackend} implementation, run by a concrete
 * class that implements this interface and supplies {@link #load(String, Context)}.
 * <p>
 * This base is deliberately free of any Fiji/BDV dependency: it exercises only
 * the {@code ome.zarr.imglib2} core API. Tests for the Fiji wrappers
 * ({@code PyramidalDataset}, {@code PyramidalBdv}) live in the {@code ome.zarr.fiji}
 * test package instead, so that the core and backend packages stay independent
 * of the Fiji integration layer.
 */
public interface PyramidBackendTestBase
{

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"ome/zarr/testdata/2d_testing/2d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr",
				"ome/zarr/testdata/2d_testing/2d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyc/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyt/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/3d_testing/xyz/3d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyct/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzc/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/4d_testing/xyzt/4d_dataset_v5.ome.zarr",
				"ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}

	static Stream< String > pyramids()
	{
		return Stream.of(
				"ome/zarr/testdata/pyramid_testing/pyramid_v4.zarr",
				"ome/zarr/testdata/pyramid_testing/pyramid_v5.zarr"
		);
	}

	PyramidContents< ? > load( String resource, Context context )
			throws URISyntaxException;

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertNotNull( contents );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 5, contents.numDimensions() ); // NB: xyzct
			if ( resource.contains( "4d_testing" ) )
				assertEquals( 4, contents.numDimensions() ); // NB: xyct, xyzc, xyzt
			if ( resource.contains( "3d_testing" ) )
				assertEquals( 3, contents.numDimensions() ); // NB: xyc, xyt, xyz
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 2, contents.numDimensions() ); // NB: xy

		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 4, contents.numTimepoints() );
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) || resource.contains( "xyzt" ) )
					assertEquals( 4, contents.numTimepoints() );
				if ( resource.contains( "xyzc" ) )
					assertEquals( 1, contents.numTimepoints() );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyt" ) )
					assertEquals( 4, contents.numTimepoints() );
				if ( resource.contains( "xyz" ) || resource.contains( "xyc" ) )
					assertEquals( 1, contents.numTimepoints() );
			}
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, contents.numTimepoints() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumChannels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 3, contents.numChannels() );
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) || resource.contains( "xyzc" ) )
					assertEquals( 3, contents.numChannels() );
				if ( resource.contains( "xyzt" ) )
					assertEquals( 1, contents.numChannels() );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyc" ) )
					assertEquals( 3, contents.numChannels() );
				if ( resource.contains( "xyz" ) || resource.contains( "xyt" ) )
					assertEquals( 1, contents.numChannels() );
			}
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, contents.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertEquals( 2, contents.numResolutionLevels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsImg( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );

			Img< ? > fullResolution = contents.asImg();
			assertNotNull( fullResolution );
			assertSame( contents.asImg( 0 ), fullResolution );
			assertEquals( contents.numDimensions(), fullResolution.numDimensions() );

			int resolutionLevels = contents.numResolutionLevels();
			assertThrows( IndexOutOfBoundsException.class, () -> contents.asImg( -1 ) );
			assertThrows( IndexOutOfBoundsException.class, () -> contents.asImg( resolutionLevels ) );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testGetType( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			Object type = contents.type;
			assertInstanceOf( UnsignedByteType.class, type );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testGetName( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testOmeroRdefs( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			if ( resource.contains( "5d_testing" ) ) // dataset with omero properties
				assertEquals( 1, contents.omero.rdefs.defaultT );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testPreferredMaxWidth( final String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			boolean is3D = resource.contains( "5d_testing" ) ||
					( ( resource.contains( "4d_testing" ) && resource.contains( "xyzc" ) )
							|| ( resource.contains( "4d_testing" ) && resource.contains( "xyzt" ) ) )
					|| ( resource.contains( "3d_testing" ) && resource.contains( "xyz" ) );
			PyramidContents< ? > contents = load( resource, context );
			assertSelectedLevelDimensions( contents, 100, 64, 16, is3D ); // greater than the highest resolution
			assertSelectedLevelDimensions( contents, 64, 64, 16, is3D ); // equals the highest resolution
			assertSelectedLevelDimensions( contents, 50, 32, 8, is3D ); // between the lowest and highest resolution
			assertSelectedLevelDimensions( contents, 32, 32, 8, is3D ); // equals the lowest resolution
			// less than the lowest resolution
			assertThrows( NoMatchingResolutionException.class, () -> contents.selectResolutionLevel( 30 ) );
			assertSelectedLevelDimensions( contents, null, 64, 16, is3D ); // null preferred width results in the highest resolution
		}
	}

	/**
	 * Selects the resolution level for {@code preferredWidth} and asserts the x/y
	 * (and, when {@code is3D}, z) dimensions of {@link PyramidContents#asImg(int)}
	 * at that level. The imglib2 image is in F-order with x, y, z at indices 0, 1, 2.
	 */
	static void assertSelectedLevelDimensions( final PyramidContents< ? > contents,
			final Integer preferredWidth, final long expectedXY, final long expectedZ, final boolean is3D )
	{
		final int level = contents.selectResolutionLevel( preferredWidth );
		final Img< ? > img = contents.asImg( level );
		assertEquals( expectedXY, img.dimension( 0 ) );
		assertEquals( expectedXY, img.dimension( 1 ) );
		if ( is3D )
			assertEquals( expectedZ, img.dimension( 2 ) );
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testPhysicalSizeConsistentAcrossResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			double[] level0Extents = physicalExtents( contents, 0 );

			for ( int level = 1; level < contents.numResolutionLevels(); level++ )
				assertArrayEquals( level0Extents, physicalExtents( contents, level ), 1e-9,
						"Physical extents at level " + level + " must match level 0" );

			final int xDim = contents.axisIndex( AxisCalibration.X );
			final int yDim = contents.axisIndex( AxisCalibration.Y );
			final int zDim = contents.axisIndex( AxisCalibration.Z );

			assertEquals( 64.0, level0Extents[ xDim ], 1e-9 );
			assertEquals( 64.0, level0Extents[ yDim ], 1e-9 );
			if ( zDim >= 0 )
				assertEquals( 16.0, level0Extents[ zDim ], 1e-9 );
		}
	}

	/**
	 * Physical extent of each dimension at the given resolution level, computed
	 * from the core calibration ({@code scale * size}). Equivalent to a linear
	 * ImageJ axis' {@code calibratedValue(size)}, but without any Fiji dependency.
	 */
	static double[] physicalExtents( final PyramidContents< ? > contents, final int level )
	{
		final AxisCalibration[] axes = contents.axesPerLevel[ level ];
		final Img< ? > img = contents.asImg( level );
		final double[] extents = new double[ axes.length ];
		for ( int d = 0; d < axes.length; d++ )
			extents[ d ] = axes[ d ].scale * img.dimension( d );
		return extents;
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testVoxelCalibration( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			// Full-resolution spatial calibration: unit spacing, empty unit string
			// (the core counterpart of the BDV VoxelDimensions {1,1,1} / "" check).
			for ( final String axisName : new String[] { AxisCalibration.X, AxisCalibration.Y, AxisCalibration.Z } )
			{
				final int index = contents.axisIndex( axisName );
				if ( index < 0 )
					continue; // axis not present (e.g. no z)
				final AxisCalibration axis = contents.axesPerLevel[ 0 ][ index ];
				assertEquals( 1.0, axis.scale );
				assertEquals( "", axis.unit );
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#pyramids" )
	default void testGetPyramidLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertEquals( 3, contents.numResolutionLevels() );

			// NB: compare uint8 values in
			// src/test/resources/ome/zarr/testdata/pyramid_testing/create_pyramid.py
			assertEquals( 180, valueAt( contents, 0, 10, 10, 10 ) );
			assertEquals( 100, valueAt( contents, 1, 10, 10, 10 ) );
			assertEquals( 20, valueAt( contents, 2, 10, 10, 10 ) );
		}
	}

	static int valueAt( final PyramidContents< ? > contents, final int level, final long... position )
	{
		final RandomAccess< ? > randomAccess = contents.asImg( level ).randomAccess();
		randomAccess.setPosition( position );
		final UnsignedByteType value = Cast.unchecked( randomAccess.get() );
		return value.get();
	}
}