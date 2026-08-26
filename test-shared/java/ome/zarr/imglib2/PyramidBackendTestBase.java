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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.util.stream.Stream;

import ome.zarr.ZarrTestUtils;
import ome.zarr.imglib2.exceptions.SingleArrayAxesUnknownException;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;

/**
 * Shared parameterized tests for the backend-agnostic {@link PyramidContents}
 * produced by each {@link PyramidBackend} implementation, run by a concrete
 * class that implements this interface and supplies {@link #read(String, Context)}.
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

	/**
	 * Resolution level of a multiscale image whose multiscales metadata sits two
	 * levels up, so the array's immediate parent group carries none.
	 */
	String NESTED_LEVEL_V4 = "ome/zarr/testdata/single_resolution_testing/nested_multiscale_v4.ome.zarr/sub/0";

	/** The Zarr v3 counterpart of {@link #NESTED_LEVEL_V4}. */
	String NESTED_LEVEL_V5 = "ome/zarr/testdata/single_resolution_testing/nested_multiscale_v5.ome.zarr/sub/0";

	/** Multiscale images whose dataset paths point into a subgroup ({@code sub/0}). */
	static Stream< String > nestedMultiscales()
	{
		return Stream.of(
				"ome/zarr/testdata/single_resolution_testing/nested_multiscale_v4.ome.zarr",
				"ome/zarr/testdata/single_resolution_testing/nested_multiscale_v5.ome.zarr"
		);
	}

	/**
	 * The lower resolution level ({@code 1}) of the 5d datasets, whose immediate
	 * parent group carries the multiscales metadata. A level (not level 0) so that
	 * the per-level metadata lookup has to pick the right dataset entry.
	 */
	static Stream< String > levelsOfMultiscaleParent()
	{
		return Stream.of(
				"ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr/1",
				"ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr/1"
		);
	}

	PyramidContents< ? > read( String resource, Context context )
			throws URISyntaxException;

	/**
	 * A Zarr v3 array without a multiscales parent reads as an uncalibrated
	 * one-level pyramid: the axis names come from its own
	 * {@code dimension_names}, and nothing supplies scale, unit or OMERO metadata.
	 */
	@Test
	default void testSingleArrayWithoutParentMultiscale() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( NESTED_LEVEL_V5, context );
			assertNotNull( contents );
			assertEquals( 1, contents.numResolutionLevels() );
			assertEquals( 2, contents.numDimensions() );
			assertEquals( "0", contents.name, "Without a multiscales name, the array node name is used" );
			assertNull( contents.omero );
			assertTrue( contents.hasPlaceholderCalibration,
					"An array read from its own axis names has no real scale or unit, and must say so" );

			// dimension_names are y, x; the imglib2 image is in F-order, so x comes first.
			assertEquals( 0, contents.axisIndex( AxisCalibration.X ) );
			assertEquals( 1, contents.axisIndex( AxisCalibration.Y ) );
			for ( final AxisCalibration axis : contents.axesPerLevel[ 0 ] )
			{
				assertEquals( 1.0, axis.scale, "An array without a parent multiscale reads uncalibrated" );
				assertEquals( "", axis.unit );
			}

			Img< ? > img = contents.asImg();
			assertEquals( 16, img.dimension( 0 ) );
			assertEquals( 16, img.dimension( 1 ) );
			// NB: the ramp written by create_nested_multiscale.py is value(y, x) == y * 16 + x
			assertEquals( 3 * 16 + 4, valueAt( contents, 0, 4, 3 ) );
		}
	}

	/**
	 * A Zarr v2 array without a multiscales parent cannot be interpreted at all:
	 * it has no {@code dimension_names} of its own (Zarr v2 has no such field) and
	 * no parent multiscales metadata to take axes from.
	 */
	@Test
	default void testSingleArrayWithoutAxesIsRejected()
	{
		try (Context context = new Context())
		{
			assertThrows( SingleArrayAxesUnknownException.class, () -> this.read( NESTED_LEVEL_V4, context ) );
		}
	}

	/**
	 * The intermediate group of a nested multiscale image is not readable either: it
	 * carries no multiscales metadata of its own, and although its parent does, that
	 * parent lists {@code sub/0} and {@code sub/1} - not {@code sub} itself - so it
	 * cannot be interpreted as a resolution level.
	 */
	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#nestedMultiscales" )
	default void testGroupNotListedByParentMultiscaleIsRejected( String resource )
	{
		try (Context context = new Context())
		{
			assertThrows( SingleArrayAxesUnknownException.class, () -> this.read( resource + "/sub", context ) );
		}
	}

	/**
	 * A resolution level whose immediate parent is the multiscales group reads as a
	 * one-level pyramid that takes everything the array itself does not know from
	 * that parent: the image name, the axis names, that level's scale and transform,
	 * and the group's OMERO metadata.
	 */
	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#levelsOfMultiscaleParent" )
	default void testSingleLevelFromParentMultiscale( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( resource, context );
			assertNotNull( contents );
			assertEquals( 1, contents.numResolutionLevels(), "A single level is a one-level pyramid" );
			assertEquals( 5, contents.numDimensions() );
			assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name, "The name comes from the parent multiscales entry" );

			// Level 1 of the 5d dataset: zarr [t, c, z, y, x] = [4, 3, 8, 32, 32],
			// reversed into imglib2 F-order.
			Img< ? > img = contents.asImg();
			assertArrayEquals( new long[] { 32, 32, 8, 3, 4 }, img.dimensionsAsLongArray() );

			// Level 1 is downsampled by 2 in x, y and z only, so the scales of that
			// dataset entry - not level 0's all-ones - must be the ones used.
			assertScaleAtAxis( contents, AxisCalibration.X, 2.0 );
			assertScaleAtAxis( contents, AxisCalibration.Y, 2.0 );
			assertScaleAtAxis( contents, AxisCalibration.Z, 2.0 );
			assertScaleAtAxis( contents, AxisCalibration.C, 1.0 );
			assertScaleAtAxis( contents, AxisCalibration.T, 1.0 );

			AffineTransform3D transform = contents.transforms[ 0 ];
			assertArrayEquals( new double[] { 2.0, 2.0, 2.0 },
					new double[] { transform.get( 0, 0 ), transform.get( 1, 1 ), transform.get( 2, 2 ) },
					"The transform is the one of the level that was read, not of level 0" );

			assertFalse( contents.hasPlaceholderCalibration,
					"The scale and unit come from the parent multiscales group, so they are real" );

			Omero omero = contents.omero;
			assertNotNull( omero, "OMERO metadata is inherited from the parent group" );
			assertEquals( 3, omero.channels.size() );
			assertEquals( "lynEGFP", omero.channels.get( 0 ).label );
			assertEquals( "00FF00", omero.channels.get( 0 ).color );
		}
	}

	/** Asserts the calibration scale of the named axis at the highest resolution level. */
	static void assertScaleAtAxis( final PyramidContents< ? > contents, final String axisName, final double expectedScale )
	{
		final int index = contents.axisIndex( axisName );
		assertNotEquals( -1, index, "Axis " + axisName + " is missing" );
		assertEquals( expectedScale, contents.axesPerLevel[ 0 ][ index ].scale, axisName + " scale" );
	}

	/**
	 * A multiscales group is read as a whole pyramid even when its dataset paths
	 * point into a subgroup rather than at direct children.
	 */
	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#nestedMultiscales" )
	default void testNestedMultiscaleDatasetPaths( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
			assertEquals( 2, contents.numResolutionLevels() );
			assertEquals( 16, contents.asImg( 0 ).dimension( 0 ) );
			assertEquals( 8, contents.asImg( 1 ).dimension( 0 ) );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
			assertEquals( 2, contents.numResolutionLevels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsImg( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( resource, context );

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
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testOmeroRdefs( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
			assertSelectedLevelDimensions( contents, 100, 64, 16, is3D ); // greater than the highest resolution
			assertSelectedLevelDimensions( contents, 64, 64, 16, is3D ); // equals the highest resolution
			assertSelectedLevelDimensions( contents, 50, 32, 8, is3D ); // between the lowest and highest resolution
			assertSelectedLevelDimensions( contents, 32, 32, 8, is3D ); // equals the lowest resolution
			// less than the lowest resolution: no level matches, the caller falls back
			// to the coarsest one
			assertSelectedLevelDimensions( contents, 30, 32, 8, is3D );
			assertEquals( PyramidContents.NO_MATCHING_LEVEL, contents.suggestResolutionLevel( 30 ),
					"Without a narrow enough level, no level must be suggested" );
			assertSelectedLevelDimensions( contents, null, 64, 16, is3D ); // null preferred width results in the highest resolution
		}
	}

	/**
	 * Suggests the resolution level for {@code preferredWidth} — falling back to the
	 * coarsest level when none matches, as the Fiji opener does — and asserts the
	 * x/y (and, when {@code is3D}, z) dimensions of
	 * {@link PyramidContents#asImg(int)} at that level. The imglib2 image is in
	 * F-order with x, y, z at indices 0, 1, 2.
	 */
	static void assertSelectedLevelDimensions( final PyramidContents< ? > contents,
			final Integer preferredWidth, final long expectedXY, final long expectedZ, final boolean is3D )
	{
		final int suggested = contents.suggestResolutionLevel( preferredWidth );
		final int level = suggested == PyramidContents.NO_MATCHING_LEVEL
				? contents.smallestResolutionLevel()
				: suggested;
		final Img< ? > img = contents.asImg( level );
		assertEquals( expectedXY, img.dimension( 0 ) );
		assertEquals( expectedXY, img.dimension( 1 ) );
		if ( is3D )
			assertEquals( expectedZ, img.dimension( 2 ) );
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testLargestAndSmallestImg( final String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents = this.read( resource, context );
			assertEquals( 0, contents.suggestResolutionLevel( null ),
					"Without a preferred width, the highest resolution must be suggested" );
			assertSame( contents.asImg( 0 ), contents.asLargestImg() );
			assertSame( contents.asImg( 0 ), contents.asImg() );
			assertEquals( contents.numResolutionLevels() - 1, contents.smallestResolutionLevel() );
			assertSame( contents.asImg( contents.numResolutionLevels() - 1 ), contents.asSmallestImg() );
			// the examples are 64 wide at level 0 and halve per level
			assertEquals( 64, contents.asLargestImg().dimension( 0 ) );
			assertTrue( contents.asSmallestImg().dimension( 0 ) <= contents.asLargestImg().dimension( 0 ),
					"The smallest image must not be wider than the largest one" );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testPhysicalSizeConsistentAcrossResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
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
			PyramidContents< ? > contents = this.read( resource, context );
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
		final UnsignedByteType value = assertInstanceOf( UnsignedByteType.class, randomAccess.get() );
		return value.get();
	}
}
