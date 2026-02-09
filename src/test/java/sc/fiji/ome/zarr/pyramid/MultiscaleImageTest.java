package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import sc.fiji.ome.zarr.util.ZarrTestUtils;

class MultiscaleImageTest
{
	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example",
				"sc/fiji/ome/zarr/util/ome_zarr_v5_example"
		);
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testNumDimensions( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		assertEquals( 2, img.numDimensions() );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testDimensions( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		long[] dimensions = img.dimensions();
		assertEquals( 1000, dimensions[ 0 ] );
		assertEquals( 1000, dimensions[ 1 ] );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testAxes( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		List< Multiscales.Axis > axes = img.getMultiscales().getAxes();
		assertEquals( 2, axes.size() );
		assertEquals( "y", axes.get( 0 ).name );
		assertEquals( "x", axes.get( 1 ).name );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testScales( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		List< Multiscales.Scale > scales = img.getMultiscales().getScales();
		assertEquals( 2, img.numResolutions() );
		assertEquals( 2, scales.size() );
		Multiscales.Scale scale0 = scales.get( 0 );
		assertEquals( 1d, scale0.scaleFactors[ 0 ] );
		assertEquals( 1d, scale0.scaleFactors[ 1 ] );
		assertEquals( 0d, scale0.offsets[ 0 ] );
		assertEquals( 0d, scale0.offsets[ 1 ] );
		Multiscales.Scale scale1 = scales.get( 1 );
		assertEquals( 2d, scale1.scaleFactors[ 0 ] );
		assertEquals( 2d, scale1.scaleFactors[ 1 ] );
		assertEquals( 0.5d, scale1.offsets[ 0 ] );
		assertEquals( 0.5d, scale1.offsets[ 1 ] );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testTimepoints( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testChannels( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	public void testImg( String resource ) throws URISyntaxException
	{
		MultiscaleImage< ?, ? > img = load( resource );
		long[] imgDimensions = img.getImg( 0 ).dimensionsAsLongArray();
		assertEquals( 1000, imgDimensions[ 0 ] );
		assertEquals( 1000, imgDimensions[ 1 ] );
		imgDimensions = img.getVolatileImg( 0 ).dimensionsAsLongArray();
		assertEquals( 1000, imgDimensions[ 0 ] );
		assertEquals( 1000, imgDimensions[ 1 ] );
	}

	private MultiscaleImage< ?, ? > load( String resource ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		return new MultiscaleImage<>( path.toString() );
	}
}
