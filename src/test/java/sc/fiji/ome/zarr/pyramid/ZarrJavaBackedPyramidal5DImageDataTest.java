package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Cast;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.stream.Stream;

import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.backend.zarrjava.ZarrJavaPyramidBackend;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class ZarrJavaBackedPyramidal5DImageDataTest
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

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsPyramidalDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageDataImpl< ?, ? > data = load( resource, context );
			PyramidalDataset< ? > pyramidalDataset = data.asPyramidalDataset();
			assertNotNull( pyramidalDataset );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			Dataset ijDataset = pyramidal5DImageData.asDataset();
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
			assertEquals( ZarrTestUtils.IMAGE_NAME, imgPlus.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsSources( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			assertNotNull( pyramidal5DImageData.asSources() );
			Source< ? > channel0 = pyramidal5DImageData.asSources().get( 0 ).getSpimSource();
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
				assertEquals( 3, pyramidal5DImageData.asSources().size() ); // 3 channels
				assertEquals( "lynEGFP", pyramidal5DImageData.asSources().get( 0 ).getSpimSource().getName() );
				assertEquals( "NLStdTomato", pyramidal5DImageData.asSources().get( 1 ).getSpimSource().getName() );
				assertEquals( 1, pyramidal5DImageData.getOmeroProperties().rdefs.defaultT );
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
					assertEquals( 3, pyramidal5DImageData.asSources().size() ); // 3 channels
				if ( resource.contains( "xyzt" ) )
					assertEquals( 1, pyramidal5DImageData.asSources().size() ); // 1 channel
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
					assertEquals( 3, pyramidal5DImageData.asSources().size() ); // 3 channels
				if ( resource.contains( "xyt" ) || resource.contains( "xyz" ) )
					assertEquals( 1, pyramidal5DImageData.asSources().size() ); // 1 channel
			}
			if ( resource.contains( "2d_testing" ) )
			{
				assertEquals( 1, pyramidal5DImageData.asSources().size() ); // 1 channel

				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				assertArrayEquals( new long[] { 64, 64, 1 }, dimensions );
				assertEquals( 1, pyramidal5DImageData.asSources().size() ); // 1 channel
				assertEquals( ZarrTestUtils.IMAGE_NAME, pyramidal5DImageData.asSources().get( 0 ).getSpimSource().getName() );
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageDataImpl< ?, ? > dataset = load( resource, context );
			assertNotNull( dataset );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 5, dataset.numDimensions() ); // NB: xyzct
			if ( resource.contains( "4d_testing" ) )
				assertEquals( 4, dataset.numDimensions() ); // NB: xyct, xyzc, xyzt
			if ( resource.contains( "3d_testing" ) )
				assertEquals( 3, dataset.numDimensions() ); // NB: xyc, xyt, xyz
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 2, dataset.numDimensions() ); // NB: xy

		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageDataImpl< ?, ? > data = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 4, data.numTimepoints() );
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, data.numTimepoints() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumChannels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 3, pyramidal5DImageData.numChannels() );
			if ( resource.contains( "4d_testing" ) )
			{
				if ( resource.contains( "xyct" ) || resource.contains( "xyzc" ) )
					assertEquals( 3, pyramidal5DImageData.numChannels() );
				if ( resource.contains( "xyzt" ) )
					assertEquals( 1, pyramidal5DImageData.numChannels() );
			}
			if ( resource.contains( "3d_testing" ) )
			{
				if ( resource.contains( "xyc" ) )
					assertEquals( 3, pyramidal5DImageData.numChannels() );
				if ( resource.contains( "xyz" ) || resource.contains( "xyt" ) )
					assertEquals( 1, pyramidal5DImageData.numChannels() );
			}
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, pyramidal5DImageData.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageDataImpl< ?, ? > data = load( resource, context );
			assertEquals( 2, data.numResolutionLevels() );
			assertEquals( 2, data.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testVoxelDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageDataImpl< ?, ? > data = load( resource, context );
			VoxelDimensions voxelDimensions = data.voxelDimensions();
			assertNotNull( voxelDimensions );
			assertEquals( "", voxelDimensions.unit() );
			assertArrayEquals( new double[] { 1, 1, 1 }, voxelDimensions.dimensionsAsDoubleArray() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testGetType( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			Object type = pyramidal5DImageData.getType();
			Assertions.assertInstanceOf( UnsignedByteType.class, type );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testGetName( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageDataImpl< ?, ? > data = load( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, data.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "pyramids" )
	void testGetPyramidLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			Source< ? > spimSource = pyramidal5DImageData.asSources().get( 0 ).getSpimSource();

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

			assertEquals( 3, pyramidal5DImageData.numResolutionLevels() );
			assertEquals( 3, spimSource.getNumMipmapLevels() );
			assertEquals( 180, value0.get() ); // NB: compare values in src/test/resources/sc/fiji/ome/zarr/util/pyramid_testing/create_pyramid.py
			assertEquals( 100, value1.get() );
			assertEquals( 20, value2.get() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testPreferredMaxWidth( final String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			boolean is3D = resource.contains( "5d_testing" ) ||
					( ( resource.contains( "4d_testing" ) && resource.contains( "xyzc" ) )
							|| ( resource.contains( "4d_testing" ) && resource.contains( "xyzt" ) ) )
					|| ( resource.contains( "3d_testing" ) && resource.contains( "xyz" ) );
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context, 100 ); // greater than the highest resolution
			assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
			if ( is3D )
				assertEquals( 16, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
			pyramidal5DImageData = load( resource, context, 64 ); // equals the highest resolution
			assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
			if ( is3D )
				assertEquals( 16, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
			pyramidal5DImageData = load( resource, context, 50 ); // less than the highest resolution, but greater than the lowest resolution
			assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
			if ( is3D )
				assertEquals( 8, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
			pyramidal5DImageData = load( resource, context, 32 ); // equals the lowest resolution
			assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
			if ( is3D )
				assertEquals( 8, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
			// less than the lowest resolution
			assertThrows( NoMatchingResolutionException.class, () -> load( resource, context, 30 ) );
			pyramidal5DImageData = load( resource, context, null ); // null preferred width results in the highest resolution
			assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
			if ( is3D )
				assertEquals( 16, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
		}
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private Pyramidal5DImageDataImpl< ?, ? > load( final String resource, final Context context ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new Pyramidal5DImageDataImpl( context, new ZarrJavaPyramidBackend( path.toString() ) );
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private Pyramidal5DImageDataImpl< ?, ? > load(
			final String resource,
			final Context context,
			final Integer preferredWidth ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new Pyramidal5DImageDataImpl( context, new ZarrJavaPyramidBackend( path.toString(), preferredWidth ) );
	}
}
