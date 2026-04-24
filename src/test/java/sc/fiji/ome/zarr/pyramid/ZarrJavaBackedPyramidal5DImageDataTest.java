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
import net.imglib2.type.numeric.integer.LongType;
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
import sc.fiji.ome.zarr.pyramid.backend.ZarrJavaPyramidBackend;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class ZarrJavaBackedPyramidal5DImageDataTest
{

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr",
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example",
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
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
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
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			Dataset ijDataset = data.asDataset();
			assertNotNull( ijDataset );
			ImgPlus< ? > imgPlus = ijDataset.getImgPlus();
			assertNotNull( imgPlus );
			if ( resource.contains( "5d_testing" ) )
			{
				assertEquals( 64, imgPlus.dimension( 0 ) );
				assertEquals( 64, imgPlus.dimension( 1 ) );
				assertEquals( 16, imgPlus.dimension( 2 ) );
			}
			if ( resource.contains( "2d_testing" ) )
			{
				assertEquals( 1000, imgPlus.dimension( 0 ) );
				assertEquals( 1000, imgPlus.dimension( 1 ) );
			}
			assertEquals( ZarrTestUtils.IMAGE_NAME, imgPlus.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsSources( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			assertNotNull( data.asSources() );
			if ( resource.contains( "5d_testing" ) )
			{
				Source< ? > channel0 = data.asSources().get( 0 ).getSpimSource();
				VoxelDimensions voxelDimensions = channel0.getVoxelDimensions();
				assertEquals( 2, channel0.getNumMipmapLevels() ); // 2 resolution levels
				assertInstanceOf( UnsignedByteType.class, channel0.getType() );
				assertNotNull( voxelDimensions );
				assertNotNull( channel0.getSource( 0, 0 ) ); // timepoint 0, resolution level 0
				assertNotNull( channel0.getSource( 0, 1 ) ); // timepoint 0, resolution level 1
				assertNotNull( channel0.getSource( 1, 0 ) ); // timepoint 1, resolution level 0
				assertNotNull( channel0.getSource( 1, 1 ) ); // timepoint 1, resolution level 1
				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				assertArrayEquals( new long[] { 64, 64, 16 }, dimensions );
				assertEquals( 2, data.asSources().size() ); // 2 channels
			}
			if ( resource.contains( "2d_testing" ) )
			{
				assertEquals( 1, data.asSources().size() ); // 1 channel
				Source< ? > channel0 = data.asSources().get( 0 ).getSpimSource();
				VoxelDimensions voxelDimensions = channel0.getVoxelDimensions();
				assertEquals( 2, channel0.getNumMipmapLevels() ); // 2 resolution levels
				assertInstanceOf( LongType.class, channel0.getType() );
				assertNotNull( voxelDimensions );
				assertNotNull( channel0.getSource( 0, 0 ) ); // timepoint 0, resolution level 0
				assertNotNull( channel0.getSource( 0, 1 ) ); // timepoint 0, resolution level 1
				long[] dimensions = channel0.getSource( 0, 0 ).dimensionsAsLongArray();
				assertArrayEquals( new long[] { 1000, 1000, 1 }, dimensions );
				assertEquals( 1, data.asSources().size() ); // 1 channel
			}
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			assertNotNull( data );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 5, data.numDimensions() ); // NB: xyzct
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 2, data.numDimensions() ); // NB: xy
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 2, data.numTimepoints() );
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
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 2, data.numChannels() );
			if ( resource.contains( "2d_testing" ) )
				assertEquals( 1, data.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
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
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
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
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			Object type = data.getType();
			if ( resource.contains( "5d_testing" ) )
				Assertions.assertInstanceOf( UnsignedByteType.class, type );
			if ( resource.contains( "2d_testing" ) )
				Assertions.assertInstanceOf( LongType.class, type );
		}
	}



	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testGetName( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, data.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "pyramids" )
	void testGetPyramidLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > data = load( resource, context );
			Source< ? > spimSource = data.asSources().get( 0 ).getSpimSource();

			RandomAccessibleInterval< ? > resolutionLevel0 = spimSource.getSource( 0, 0 );
			RandomAccess< ? > randomAccessLevel0 = resolutionLevel0.randomAccess();
			randomAccessLevel0.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value0 = Cast.unchecked( randomAccessLevel0.get() );

			RandomAccessibleInterval< ? > resolutionLevel1 = spimSource.getSource( 0, 1 );
			RandomAccess< ? > randomAccessLevel1 = resolutionLevel1.randomAccess();
			randomAccessLevel1.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value1 = Cast.unchecked( randomAccessLevel1.get() );

			RandomAccessibleInterval< ? > resolutionLevel2 = spimSource.getSource( 0, 2 );
			RandomAccess< ? > randomAccessLevel2 = resolutionLevel2.randomAccess();
			randomAccessLevel2.setPosition( new long[] { 10, 10, 10 } );
			UnsignedByteType value2 = Cast.unchecked( randomAccessLevel2.get() );

			assertEquals( 3, data.numResolutionLevels() );
			assertEquals( 3, spimSource.getNumMipmapLevels() );
			assertEquals( 180, value0.get() );
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
            if ( resource.contains( "2d_testing" ) )
            {
                DefaultPyramidal5DImageData< ?, ? > data = load( resource, context, 2000 ); // greater than the highest resolution
                assertEquals( 1000, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 1000, data.asDataset().getImgPlus().dimension( 1 ) );
                data = load( resource, context, 1000 ); // equals the highest resolution
                assertEquals( 1000, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 1000, data.asDataset().getImgPlus().dimension( 1 ) );
                data = load( resource, context, 900 ); // less than the highest resolution, but greater than the lowest resolution
                assertEquals( 500, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 500, data.asDataset().getImgPlus().dimension( 1 ) );
                data = load( resource, context, 500 ); // equals the lowest resolution
                assertEquals( 500, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 500, data.asDataset().getImgPlus().dimension( 1 ) );
                // less than the lowest resolution
                assertThrows( NoMatchingResolutionException.class, () -> load( resource, context, 400 ) );
            }
            if ( resource.contains( "5d_testing" ) )
            {
                DefaultPyramidal5DImageData< ?, ? > data = load( resource, context, 100 ); // greater than the highest resolution
                assertEquals( 64, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 64, data.asDataset().getImgPlus().dimension( 1 ) );
                assertEquals( 16, data.asDataset().getImgPlus().dimension( 2 ) );
                data = load( resource, context, 64 ); // equals the highest resolution
                assertEquals( 64, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 64, data.asDataset().getImgPlus().dimension( 1 ) );
                assertEquals( 16, data.asDataset().getImgPlus().dimension( 2 ) );
                data = load( resource, context, 50 ); // less than the highest resolution, but greater than the lowest resolution
                assertEquals( 32, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 32, data.asDataset().getImgPlus().dimension( 1 ) );
                assertEquals( 8, data.asDataset().getImgPlus().dimension( 2 ) );
                data = load( resource, context, 32 ); // equals the lowest resolution
                assertEquals( 32, data.asDataset().getImgPlus().dimension( 0 ) );
                assertEquals( 32, data.asDataset().getImgPlus().dimension( 1 ) );
                assertEquals( 8, data.asDataset().getImgPlus().dimension( 2 ) );
                // less than the lowest resolution
                assertThrows( NoMatchingResolutionException.class, () -> load( resource, context, 30 ) );
            }
        }
    }

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private DefaultPyramidal5DImageData< ?, ? > load( final String resource, final Context context ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new DefaultPyramidal5DImageData( context, new ZarrJavaPyramidBackend( path.toString() ) );
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private DefaultPyramidal5DImageData< ?, ? > load(
			final String resource,
			final Context context,
			final Integer preferredWidth ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new DefaultPyramidal5DImageData( context, new ZarrJavaPyramidBackend( path.toString(), preferredWidth ) );
	}
}