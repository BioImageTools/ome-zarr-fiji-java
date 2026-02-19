package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import net.imglib2.RandomAccessibleInterval;

import sc.fiji.ome.zarr.util.ZarrTestUtils;

class MultiscaleImageTest
{
	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example"
		// "sc/fiji/ome/zarr/util/ome_zarr_v5_example" // NB: OME v05 not supported yet
		);
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumDimensions( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		assertNotNull( img );
		assertEquals( 2, img.numDimensions() );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testDimensions( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		long[] dimensions = img.dimensions();
		assertEquals( 1000, dimensions[ 0 ] );
		assertEquals( 1000, dimensions[ 1 ] );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testAxes( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		Multiscales multiscales = img.getMultiscales();
		assertNotNull( multiscales );
		List< Multiscales.Axis > axes = multiscales.getAxes();
		assertEquals( 2, axes.size() );
		assertEquals( "x", axes.get( 0 ).name ); // NB: reverse order compared to .zattrs as image data is in reverse order to
		assertEquals( "y", axes.get( 1 ).name );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	@Disabled( "Implementation of coordinate transformations yet incomplete" )
	void testCoordinateTransformations( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		Multiscales multiscales = img.getMultiscales();
		assertNotNull( multiscales );
		Multiscales.CoordinateTransformations[] coordinateTransformations = multiscales.getCoordinateTransformations();
		assertEquals( 2, img.numResolutions() );
		assertEquals( 2, coordinateTransformations.length );
		Multiscales.CoordinateTransformations coordinateTransformation0 = coordinateTransformations[ 0 ];
		assertEquals( 1d, coordinateTransformation0.scale[ 0 ] );
		assertEquals( 1d, coordinateTransformation0.scale[ 1 ] );
		assertEquals( 0d, coordinateTransformation0.translation[ 0 ] );
		assertEquals( 0d, coordinateTransformation0.translation[ 1 ] );
		Multiscales.CoordinateTransformations coordinateTransformation1 = coordinateTransformations[ 0 ];
		assertEquals( 2d, coordinateTransformation1.scale[ 0 ] );
		assertEquals( 2d, coordinateTransformation1.scale[ 1 ] );
		assertEquals( 0.5d, coordinateTransformation1.translation[ 0 ] );
		assertEquals( 0.5d, coordinateTransformation1.translation[ 1 ] );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumResolutions( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		assertEquals( 2, img.numResolutions() );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testImg( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		long[] imgDimensions = img.getImg( 0 ).dimensionsAsLongArray();
		assertEquals( 1000, imgDimensions[ 0 ] );
		assertEquals( 1000, imgDimensions[ 1 ] );
		imgDimensions = img.getVolatileImg( 0 ).dimensionsAsLongArray();
		assertEquals( 1000, imgDimensions[ 0 ] );
		assertEquals( 1000, imgDimensions[ 1 ] );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testVolatileImg( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		RandomAccessibleInterval< ? > randomAccessibleInterval = img.getVolatileImg( 0 );
		assertNotNull( randomAccessibleInterval );
		assertEquals( 1000, randomAccessibleInterval.dimension( 0 ) );
		assertEquals( 1000, randomAccessibleInterval.dimension( 1 ) );
	}

	private MultiscaleImage< ?, ? > load( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new MultiscaleImage<>( path.toString() );
	}
}
