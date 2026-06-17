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
package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Cast;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Stream;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.fiji.PyramidalBdv;
import sc.fiji.ome.zarr.pyramid.fiji.PyramidalDataset;
import sc.fiji.ome.zarr.util.BdvUtils;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

/**
 * Shared parameterized tests for the {@link PyramidContents} produced by each
 * {@link sc.fiji.ome.zarr.pyramid.backend.PyramidBackend} implementation, run
 * by a concrete class that implements this interface and supplies
 * {@link #load(String, Context)}.
 */
public interface PyramidBackendTestBase
{

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyc/3d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyt/3d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyct/4d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzc/4d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzt/4d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/2d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyc/3d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyt/3d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/3d_testing/xyz/3d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyct/4d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzc/4d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/4d_testing/xyzt/4d_dataset_v5.ome.zarr",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr"
		);
	}

	static Stream< String > pyramids()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/pyramid_testing/pyramid_v4.zarr",
				"sc/fiji/ome/zarr/util/pyramid_testing/pyramid_v5.zarr"
		);
	}

	PyramidContents< ? > load( String resource, Context context )
			throws URISyntaxException;

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsPyramidalDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			PyramidalDataset pyramidalDataset = new PyramidalDataset( context, contents, 0 );
			assertNotNull( pyramidalDataset );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			Dataset ijDataset = new PyramidalDataset( context, contents, 0 );
			assertNotNull( ijDataset );
			ImgPlus< ? > imgPlus = ijDataset.getImgPlus();
			assertNotNull( imgPlus );
			boolean is3D = resource.contains( "5d_testing" )
					|| ( resource.contains( "4d_testing" ) && resource.contains( "xyz" ) )
					|| ( resource.contains( "3d_testing" ) && resource.contains( "xyz" ) );
			assertEquals( 64, imgPlus.dimension( 0 ) );
			assertEquals( 64, imgPlus.dimension( 1 ) );
			if ( is3D )
				assertEquals( 16, imgPlus.dimension( 2 ) );
			assertEquals( ZarrTestUtils.IMAGE_NAME + " (R)", imgPlus.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
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
				assertEquals( 1, contents.omero.rdefs.defaultT );
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
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertNotNull( contents );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 5, contents.numDimensions ); // NB: xyzct
			if ( resource.contains( "4d_testing" ) )
				assertEquals( 4, contents.numDimensions ); // NB: xyct, xyzc, xyzt
			if ( resource.contains( "3d_testing" ) )
				assertEquals( 3, contents.numDimensions ); // NB: xyc, xyt, xyz
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 2, contents.numDimensions ); // NB: xy

		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 4, contents.numTimepoints );
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) || resource.contains( "xyzt" ) )
					assertEquals( 4, contents.numTimepoints );
				if ( resource.contains( "xyzc" ) )
					assertEquals( 1, contents.numTimepoints );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyt" ) )
					assertEquals( 4, contents.numTimepoints );
				if ( resource.contains( "xyz" ) || resource.contains( "xyc" ) )
					assertEquals( 1, contents.numTimepoints );
			}
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, contents.numTimepoints );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumChannels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 3, contents.numChannels );
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) || resource.contains( "xyzc" ) )
					assertEquals( 3, contents.numChannels );
				if ( resource.contains( "xyzt" ) )
					assertEquals( 1, contents.numChannels );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyc" ) )
					assertEquals( 3, contents.numChannels );
				if ( resource.contains( "xyz" ) || resource.contains( "xyt" ) )
					assertEquals( 1, contents.numChannels );
			}
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, contents.numChannels );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testNumResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertEquals( 2, contents.numResolutionLevels );
			PyramidalBdv< ? > pyramidalBdv = new PyramidalBdv<>( context, contents );
			assertEquals( 2, pyramidalBdv.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
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
	@MethodSource( { "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" } )
	default void testPhysicalSizeConsistentAcrossResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			ImgPlus< ? > level0Img = new PyramidalDataset( context, contents, 0 ).getImgPlus();
			double[] level0Extents = physicalExtents( level0Img );

			for ( int level = 1; level < contents.numResolutionLevels; level++ )
			{
				ImgPlus< ? > levelImg = new PyramidalDataset( context, contents, level ).getImgPlus();
				assertArrayEquals( level0Extents, physicalExtents( levelImg ), 1e-9,
						"Physical extents at level " + level + " must match level 0" );
			}

			final int xDim = level0Img.dimensionIndex( Axes.X );
			final int yDim = level0Img.dimensionIndex( Axes.Y );
			final int zDim = level0Img.dimensionIndex( Axes.Z );

			assertEquals( 64.0, level0Extents[ xDim ], 1e-9 );
			assertEquals( 64.0, level0Extents[ yDim ], 1e-9 );
			if ( zDim >= 0 )
				assertEquals( 16.0, level0Extents[ zDim ], 1e-9 );
		}
	}

	static double[] physicalExtents( final ImgPlus< ? > img )
	{
		final double[] extents = new double[ img.numDimensions() ];
		for ( int d = 0; d < img.numDimensions(); d++ )
			extents[ d ] = img.axis( d ).calibratedValue( img.dimension( d ) );
		return extents;
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testGetType( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			Object type = contents.type;
			Assertions.assertInstanceOf( UnsignedByteType.class, type );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testGetName( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = load( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, contents.name );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#pyramids" )
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
			UnsignedByteType value0 = Cast.unchecked( randomAccessLevel0.get() ); // NB: compare uint8 type in src/test/resources/sc/fiji/ome/zarr/util/pyramid_testing/create_pyramid.py

			RandomAccessibleInterval< ? > resolutionLevel1 = spimSource.getSource( 0, 1 );
			RandomAccess< ? > randomAccessLevel1 = resolutionLevel1.randomAccess();
			randomAccessLevel1.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value1 = Cast.unchecked( randomAccessLevel1.get() );

			RandomAccessibleInterval< ? > resolutionLevel2 = spimSource.getSource( 0, 2 );
			RandomAccess< ? > randomAccessLevel2 = resolutionLevel2.randomAccess();
			randomAccessLevel2.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value2 = Cast.unchecked( randomAccessLevel2.get() );

			assertEquals( 3, contents.numResolutionLevels );
			assertEquals( 3, spimSource.getNumMipmapLevels() );
			assertEquals( 180, value0.get() ); // NB: compare values in src/test/resources/sc/fiji/ome/zarr/util/pyramid_testing/create_pyramid.py
			assertEquals( 100, value1.get() );
			assertEquals( 20, value2.get() );
		}
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
	default void testPreferredMaxWidth( final String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			boolean is3D = resource.contains( "5d_testing" ) ||
					( ( resource.contains( "4d_testing" ) && resource.contains( "xyzc" ) )
							|| ( resource.contains( "4d_testing" ) && resource.contains( "xyzt" ) ) )
					|| ( resource.contains( "3d_testing" ) && resource.contains( "xyz" ) );
			PyramidContents< ? > contents = load( resource, context );
			assertSelectedLevelDimensions( context, contents, 100, 64, 16, is3D ); // greater than the highest resolution
			assertSelectedLevelDimensions( context, contents, 64, 64, 16, is3D ); // equals the highest resolution
			assertSelectedLevelDimensions( context, contents, 50, 32, 8, is3D ); // between the lowest and highest resolution
			assertSelectedLevelDimensions( context, contents, 32, 32, 8, is3D ); // equals the lowest resolution
			// less than the lowest resolution
			assertThrows( NoMatchingResolutionException.class, () -> contents.selectResolutionLevel( 30 ) );
			assertSelectedLevelDimensions( context, contents, null, 64, 16, is3D ); // null preferred width results in the highest resolution
		}
	}

	/**
	 * Builds a {@link PyramidalDataset} at the resolution level selected for
	 * {@code preferredWidth} and asserts its x/y (and, when {@code is3D}, z)
	 * dimensions.
	 */
	static void assertSelectedLevelDimensions( final Context context, final PyramidContents< ? > contents,
			final Integer preferredWidth, final long expectedXY, final long expectedZ, final boolean is3D )
	{
		final int level = contents.selectResolutionLevel( preferredWidth );
		final ImgPlus< ? > imgPlus = new PyramidalDataset( context, contents, level ).getImgPlus();
		assertEquals( expectedXY, imgPlus.dimension( 0 ) );
		assertEquals( expectedXY, imgPlus.dimension( 1 ) );
		if ( is3D )
			assertEquals( expectedZ, imgPlus.dimension( 2 ) );
	}

	@ParameterizedTest
	@MethodSource( "sc.fiji.ome.zarr.pyramid.PyramidBackendTestBase#omeZarrExamples" )
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
