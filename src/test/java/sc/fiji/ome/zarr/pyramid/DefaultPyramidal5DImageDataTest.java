package sc.fiji.ome.zarr.pyramid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.type.numeric.integer.LongType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.stream.Stream;

import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.util.ZarrTestUtils;

class DefaultPyramidal5DImageDataTest
{
	private static final String IMAGE_NAME = "test";

	static Stream< String > omeZarrExamples()
	{
		return Stream.of(
				"sc/fiji/ome/zarr/util/ome_zarr_v4_example"
		// "sc/fiji/ome/zarr/util/ome_zarr_v5_example" // NB: OME v05 not supported yet
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
			assertEquals( 1000, imgPlus.dimension( 0 ) );
			assertEquals( 1000, imgPlus.dimension( 1 ) );
			assertEquals( IMAGE_NAME, imgPlus.getName() );
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
	void testAsSpimData( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			assertNull( pyramidal5DImageData.asSpimData() ); // NB: not yet implemented
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumDimensions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			DefaultPyramidal5DImageData< ?, ? > dataset = load( resource, context );
			dataset.numDimensions();
			assertNotNull( dataset );
			assertEquals( 2, dataset.numDimensions() ); // NB: two spatial dimensions
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumTimepoints( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
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
			assertEquals( 1, pyramidal5DImageData.numChannels() );
		}
	}

	@ParameterizedTest
	@MethodSource( "omeZarrExamples" )
	void testNumResolutions( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			Pyramidal5DImageData< ? > pyramidal5DImageData = load( resource, context );
			assertEquals( 2, pyramidal5DImageData.numResolutions() );
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
			assertEquals( IMAGE_NAME, pyramidal5DImageData.getName() );
		}
	}

	private DefaultPyramidal5DImageData< ?, ? > load( final String resource, final Context context ) throws URISyntaxException
	{
		Path path = ZarrTestUtils.resourcePath( resource );
		MultiscaleImage< ?, ? > multiscaleImage = new MultiscaleImage<>( path.toString() );
		return new DefaultPyramidal5DImageData<>( context, IMAGE_NAME, multiscaleImage );
	}
}
