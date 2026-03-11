package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Cast;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.stream.Stream;

import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class DefaultPyramidal5DImageDataTest
{

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v4.ome.zarr"
		// "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example" // NB: OME v05 not supported yet
		// "sc/fiji/ome/zarr/util/5d_testing/5d_dataset_v5.ome.zarr" // NB: OME v05 not supported yet
		);
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsPyramidalDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			PyramidalDataset< ? > pyramidalDataset = pyramidal5DImageData.asPyramidalDataset();
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
			if ( resource.contains( "5d_testing" ) )
			{
				assertEquals( 64, imgPlus.dimension( 0 ) );
				assertEquals( 64, imgPlus.dimension( 1 ) );
				assertEquals( 16, imgPlus.dimension( 2 ) );
			}
			if ( resource.contains( "ome_zarr_v" ) )
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
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			assertNotNull( pyramidal5DImageData.asSources() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > dataset = load( resource, context );
			assertNotNull( dataset );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 5, dataset.numDimensions() ); // NB: xyzct
			if ( resource.contains( "ome_zarr_v" ) )
				assertEquals( 2, dataset.numDimensions() ); // NB: xy

		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			if ( resource.contains( "5d_testing" ) )
				assertEquals( 2, pyramidal5DImageData.numTimepoints() );
			if ( resource.contains( "ome_zarr_v" ) )
				assertEquals( 1, pyramidal5DImageData.numTimepoints() );
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
				assertEquals( 2, pyramidal5DImageData.numChannels() );
			if ( resource.contains( "ome_zarr_v" ) )
				assertEquals( 1, pyramidal5DImageData.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			assertEquals( 2, pyramidal5DImageData.numResolutionLevels() );
			assertEquals( 2, pyramidal5DImageData.asSources().get( 0 ).getSpimSource().getNumMipmapLevels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testVoxelDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			VoxelDimensions voxelDimensions = pyramidal5DImageData.voxelDimensions();
			assertNull( voxelDimensions ); // NB: not yet implemented
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
			if ( resource.contains( "5d_testing" ) )
				Assertions.assertInstanceOf( UnsignedByteType.class, type );
			if ( resource.contains( "ome_zarr_v" ) )
				Assertions.assertInstanceOf( LongType.class, type );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testGetName( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, pyramidal5DImageData.getName() );
		}
	}

	@Test
	void testGetPyramidLevels() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			String resource = "sc/fiji/ome/zarr/util/pyramid_testing/pyramid_v4.zarr";
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
			if ( resource.contains( "2d_testing" ) )
			{
				Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context, 2000 ); // greater than the highest resolution
				assertEquals( 1000, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 1000, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				pyramidal5DImageData = load( resource, context, 1000 ); // equals the highest resolution
				assertEquals( 1000, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 1000, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				pyramidal5DImageData = load( resource, context, 900 ); // less than the highest resolution, but greater than the lowest resolution
				assertEquals( 500, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 500, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				pyramidal5DImageData = load( resource, context, 500 ); // equals the lowest resolution
				assertEquals( 500, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 500, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				// less than the lowest resolution
				assertThrows( NoMatchingResolutionException.class, () -> load( resource, context, 400 ) );
			}
			if ( resource.contains( "5d_testing" ) )
			{
				Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context, 100 ); // greater than the highest resolution
				assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				assertEquals( 16, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
				pyramidal5DImageData = load( resource, context, 64 ); // equals the highest resolution
				assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 64, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				assertEquals( 16, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
				pyramidal5DImageData = load( resource, context, 50 ); // less than the highest resolution, but greater than the lowest resolution
				assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				assertEquals( 8, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
				pyramidal5DImageData = load( resource, context, 32 ); // equals the lowest resolution
				assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 0 ) );
				assertEquals( 32, pyramidal5DImageData.asDataset().getImgPlus().dimension( 1 ) );
				assertEquals( 8, pyramidal5DImageData.asDataset().getImgPlus().dimension( 2 ) );
				// less than the lowest resolution
				assertThrows( NoMatchingResolutionException.class, () -> load( resource, context, 30 ) );
			}
		}
	}

	private DefaultPyramidal5DImageData< ?, ? > load( final String resource, final Context context ) throws URISyntaxException
	{
		return load( resource, context, null );
	}

	private DefaultPyramidal5DImageData< ?, ? > load( final String resource, final Context context, final Integer preferredWidth )
			throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new DefaultPyramidal5DImageData<>( context, path.toString(), preferredWidth );
	}
}
