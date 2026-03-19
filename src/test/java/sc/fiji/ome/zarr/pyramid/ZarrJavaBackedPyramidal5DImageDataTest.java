package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Cast;

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

class ZarrJavaBackedPyramidal5DImageDataTest
{

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v4_example"
		// "sc/fiji/ome/zarr/util/2d_testing/ome_zarr_v5_example" // NB: OME v05 not supported yet
		);
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsPyramidalDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
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
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			Dataset ijDataset = data.asDataset();
			assertNotNull( ijDataset );
			ImgPlus< ? > imgPlus = ijDataset.getImgPlus();
			assertNotNull( imgPlus );
			assertEquals( 1000, imgPlus.dimension( 0 ) );
			assertEquals( 1000, imgPlus.dimension( 1 ) );
			assertEquals( ZarrTestUtils.IMAGE_NAME, imgPlus.getName() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAsSources( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			assertNotNull( data.asSources() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			assertNotNull( data );
			assertEquals( 2, data.numDimensions() ); // NB: two spatial dimensions
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			assertEquals( 1, data.numTimepoints() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumChannels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			assertEquals( 1, data.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumResolutionLevels( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
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
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			VoxelDimensions voxelDimensions = data.voxelDimensions();
			assertNotNull( voxelDimensions );
			assertEquals( "", voxelDimensions.unit() );
			assertArrayEquals( new double[] { 1, 1, 1 }, voxelDimensions.dimensionsAsDoubleArray() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testPreferredMaxWidth( final String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context, 2000 );
			assertEquals( 1000, data.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 1000, data.asDataset().getImgPlus().dimension( 1 ) );
			data = load( resource, context, 900 );
			assertEquals( 500, data.asDataset().getImgPlus().dimension( 0 ) );
			assertEquals( 500, data.asDataset().getImgPlus().dimension( 1 ) );
			assertThrows( NoMatchingResolutionException.class, () -> load( resource, context, 400 ) );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testGetName( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
			assertEquals( ZarrTestUtils.IMAGE_NAME, data.getName() );
		}
	}

	@Test
	void testGetPyramidLevels() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			String resource = "sc/fiji/ome/zarr/util/pyramid_testing/pyramid_v4.zarr";
			ZarrJavaBackedPyramidal5DImageData< ? > data = load( resource, context );
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

	private ZarrJavaBackedPyramidal5DImageData< ? > load( final String resource, final Context context ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new ZarrJavaBackedPyramidal5DImageData<>( context, path.toString() );
	}

	private ZarrJavaBackedPyramidal5DImageData< ? > load(
			final String resource,
			final Context context,
			final Integer preferredWidth ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new ZarrJavaBackedPyramidal5DImageData<>( context, path.toString(), preferredWidth );
	}
}
