package ome.zarr.fijiui.open;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;
import org.scijava.display.DisplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ome.zarr.fijiui.open.options.ZarrOpenBehavior;
import ome.zarr.fijiui.open.options.ZarrOpeningSettings;
import ome.zarr.fijiui.open.options.ZarrReaderBackend;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.ZarrTestUtils;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.github.freva.asciitable.AsciiTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConformanceTest
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	static Stream< ZarrReaderBackend > readerBackends()
	{
		return Stream.of( ZarrReaderBackend.N5, ZarrReaderBackend.ZARR_JAVA );
	}

	static Stream< TestDataset > readerTestDatasets()
	{
		//the .csv lives in the shared test resources (test-shared/resources),
		//so it is resolved from the test classpath rather than an absolute path
		final Path csvFile;
		try
		{
			csvFile = ZarrTestUtils.resourcePath( "ome/zarr/testdata/conformance_testing/testbed_datasets.csv" );
		}
		catch ( URISyntaxException e )
		{
			throw new RuntimeException( "Could not locate the conformance testbed .csv resource.", e );
		}

		List< TestDataset > datasets = new ArrayList<>( 200 );
		try (BufferedReader br = Files.newBufferedReader( csvFile ))
		{
			String line;
			boolean skipFirstLine = true;
			while ( ( line = br.readLine() ) != null )
			{
				String[] fields = line.split( ",", -1 ); // -1 preserves trailing empty fields
				if ( !skipFirstLine )
					datasets.add( new TestDataset( fields ) );
				else
					skipFirstLine = false;
			}
		}
		catch ( IOException e )
		{
			throw new RuntimeException( "Failed while reading the .csv file.", e );
		}
		return datasets.stream();
	}

	static Stream< Arguments > testCasesFeeder()
	{
		return readerTestDatasets()
				.filter( dataset -> dataset.getOmeNgffVersion() >= 0.4 )
				.limit( 2 )
				.flatMap( dataset -> readerBackends().map( backend -> Arguments.of( backend, dataset ) ) );
	}

	@Disabled( "Conformance testing is explicitly opted out from the automated testing. Please, run manually." )
	@ParameterizedTest
	@MethodSource( "testCasesFeeder" )
	void testOpenAndCheckDatasetParams( ZarrReaderBackend backend, TestDataset testDataset )
			throws URISyntaxException, InterruptedException, InvocationTargetException
	{
		try (Context context = new Context())
		{
			logger.debug( "Conformance testing dataset: study = {}\n  at URL: {}",
					testDataset.getStudy(), testDataset.getZarrURL() );

			final URI testDatasetURI = new URI( testDataset.getZarrURL() );

			final ZarrOpeningSettings settings = new ZarrOpeningSettings();
			settings.setReaderBackend( backend );
			//open the lowest available resolution as a standard IJ window
			//(or open with BDV; just be aware that in order to display anything,
			//both displays (IJ, BDV) will start fetching/downloading the pixels,
			//so avoid fetching the finest/largests resolution; while BDV would
			//probably start with some worse/smaller resolution, it may decide
			//later to fetch a better one, despite our use case doesn't need it,
			//thus IJ window has been chosen to use in this test)
			settings.setPreferredMaxWidth( 600 );
			settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_CUSTOM_RESOLUTION );

			//open URL, read the dataset, and retrieve it as an opened Dataset
			assertDoesNotThrow( () -> new ZarrOpenActions( testDatasetURI, context, settings ).openIJWithImage(),
					"Failed opening dataset '" + testDataset.getStudy() + "' with IJ." );

			DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 1, datasetService.getDatasets().size(),
					"After opening, no Dataset is available in Fiji." );

			Dataset d = datasetService.getDatasets().get( 0 );
			assertInstanceOf( PyramidalDataset.class, d,
					"After opening, incorrect Dataset is available in Fiji." );

			final PyramidalDataset pyramidalDataset = ( PyramidalDataset ) d;
			//the multi-resolution pyramid and its metadata; note that the opened
			//IJ window may show a coarser resolution level (see setPreferredMaxWidth
			//above), but the reference values in the .csv describe the full/base
			//resolution, so all size/scale/level checks below query the contents
			//directly at the base resolution level (index 0), not the IJ window
			final PyramidContents< ? > contents = pyramidalDataset.getPyramidContents();

			//base-resolution image; note that no pixels are actively loaded while
			//we hold now a variable of a (lazily loaded) Imglib2 img-like RAI
			final RandomAccessibleInterval< ? > rai = contents.asImg( 0 );

			//finally, the conformance testing:
			assertEquals( testDataset.getSizeX(), rai.dimension( contents.axisIndex( AxisCalibration.X ) ),
					"Mismatch in X-axis size." );
			assertEquals( testDataset.getSizeY(), rai.dimension( contents.axisIndex( AxisCalibration.Y ) ),
					"Mismatch in Y-axis size." );
			final long sizeZ = contents.hasAxis( AxisCalibration.Z )
					? rai.dimension( contents.axisIndex( AxisCalibration.Z ) ) : 1;
			assertEquals( testDataset.getSizeZ(), sizeZ, "Mismatch in Z-axis size." );
			if ( testDataset.getSizeC() != -1 )
				assertEquals( testDataset.getSizeC(), contents.numChannels(),
						"Mismatch in number of channels." );
			if ( testDataset.getSizeT() != -1 )
				assertEquals( testDataset.getSizeT(), contents.numTimepoints(),
						"Mismatch in number of time points." );

			StringBuilder axes = new StringBuilder();
			for ( int i = 0; i < pyramidalDataset.getImgPlus().numDimensions(); ++i )
				axes.append( pyramidalDataset.getImgPlus().axis( i ).type().toString().charAt( 0 ) );
			assertEquals( testDataset.getAxes(), axes.toString(), "Mismatch in understanding the order of axes." );

			//TODO: wells, fields

			final String refType = testDataset.getDataType();
			if ( refType != null && !refType.isEmpty() )
				checkPixelTypes( refType, pyramidalDataset );

			final double ACCURACY_DELTA = 0.00001f;
			if ( testDataset.getScaleX() != -1 )
				assertEquals( testDataset.getScaleX(), scaleAlongAxis( contents, AxisCalibration.X ),
						ACCURACY_DELTA, "Mismatch in extracted pixel resolution along X-axis." );
			if ( testDataset.getScaleY() != -1 )
				assertEquals( testDataset.getScaleY(), scaleAlongAxis( contents, AxisCalibration.Y ),
						ACCURACY_DELTA, "Mismatch in extracted pixel resolution along Y-axis." );
			if ( testDataset.getScaleZ() != -1 )
				assertEquals( testDataset.getScaleZ(), scaleAlongAxis( contents, AxisCalibration.Z ),
						ACCURACY_DELTA, "Mismatch in extracted pixel resolution along Z-axis." );

			if ( testDataset.getNumberOfResLevels() != -1 )
				assertEquals( testDataset.getNumberOfResLevels(), contents.numResolutionLevels(),
						"Mismatch in extracted number of resolution levels." );

			//clean up: wait for the IJ window to calm down, get a reference on it, and close it
			SwingUtilities.invokeAndWait( () -> {} );
			DisplayService displayService = context.getService( DisplayService.class );
			assertNotNull( displayService.getActiveDisplay(), "Internal error!" );
			displayService.getActiveDisplay().close();
		}
	}

	Optional< String > testOpenAndCheckDatasetParamsWrapper( ZarrReaderBackend backend, TestDataset testDataset )
	{
		try
		{
			testOpenAndCheckDatasetParams( backend, testDataset );
			return Optional.empty();
		}
		catch ( Throwable t )
		{
			return Optional.of( t.getClass().getSimpleName() + ": " + t.getMessage() );
		}
	}

	@Disabled( "Conformance testing is explicitly opted out from the automated testing. Please, run manually." )
	@Test
	void reportSuccessRateAndPrintTable()
	{
		final List< String[] > rows = new ArrayList<>();
		//
		final int[] stats = { 0, 0 };
		final int passedIdx = 0;
		final int totalIdx = 1;

		testCasesFeeder().forEach( arguments -> {
			ZarrReaderBackend backend = ( ZarrReaderBackend ) arguments.get()[ 0 ];
			TestDataset testDataset = ( TestDataset ) arguments.get()[ 1 ];

			final Optional< String > result = testOpenAndCheckDatasetParamsWrapper( backend, testDataset );
			rows.add( new String[] {
					testDataset.condensedDesription(),
					backend.toString(),
					result.isPresent() ? "FAIL" : "PASS",
					result.orElse( "" )
			} );
			if ( !result.isPresent() )
				stats[ passedIdx ]++;
			stats[ totalIdx ]++;
		} );

		int passed = stats[ passedIdx ];
		int total = stats[ totalIdx ];

		final String[] headers = { "Dataset", "Backend", "Result", "Reason" };
		System.out.println( AsciiTable.getTable( headers, rows.toArray( new String[ 0 ][] ) ) );
		System.out.printf( "%nPassed: %d / %d%n", passed, total );
	}

	/**
	 * Pixel spacing of the full/base resolution level along the axis with the
	 * given OME-Zarr name, or {@code -1} if that axis is not present.
	 */
	private static double scaleAlongAxis( final PyramidContents< ? > contents, final String axisName )
	{
		final int idx = contents.axisIndex( axisName );
		return idx < 0 ? -1.0 : contents.axesPerLevel[ 0 ][ idx ].scale;
	}

	private static void checkPixelTypes( final String refType, final PyramidalDataset pyramidalDataset )
	{
		final RealType< ? > imglibType = pyramidalDataset.getPyramidContents().type;

		if ( refType.startsWith( "float" ) )
		{
			if ( refType.contains( "32" ) )
			{
				assertInstanceOf( FloatType.class, imglibType );
			}
			else if ( refType.contains( "64" ) )
			{
				assertInstanceOf( DoubleType.class, imglibType );
			}
			else
			{
				logger.error( "Unrecognized reference float type '{}'. Internal error.", refType );
			}
		}
		else if ( refType.contains( "int" ) )
		{
			if ( refType.startsWith( "u" ) )
			{
				//unsigned integers
				if ( refType.endsWith( "8" ) )
				{
					assertInstanceOf( UnsignedByteType.class, imglibType );
				}
				else if ( refType.endsWith( "16" ) )
				{
					assertInstanceOf( UnsignedShortType.class, imglibType );
				}
				else if ( refType.endsWith( "32" ) )
				{
					assertInstanceOf( UnsignedIntType.class, imglibType );
				}
				else if ( refType.endsWith( "64" ) )
				{
					assertInstanceOf( UnsignedLongType.class, imglibType );
				}
				else
				{
					logger.error( "Unrecognized reference integer type '{}'. Internal error.", refType );
				}
			}
			else
			{
				//signed integers
				if ( refType.endsWith( "8" ) )
				{
					assertInstanceOf( ByteType.class, imglibType );
				}
				else if ( refType.endsWith( "16" ) )
				{
					assertInstanceOf( ShortType.class, imglibType );
				}
				else if ( refType.endsWith( "32" ) )
				{
					assertInstanceOf( IntType.class, imglibType );
				}
				else if ( refType.endsWith( "64" ) )
				{
					assertInstanceOf( LongType.class, imglibType );
				}
				else
				{
					logger.error( "Unrecognized reference integer type '{}'. Internal error.", refType );
				}
			}
		}
		else
		{
			logger.error( "Unrecognized reference type '{}'. Internal error.", refType );
		}
	}

	/**
	 * Internal record of one dataset for the conformance testing.
	 * It contains a reference (URL) to a dataset and its OME-Zarr version,
	 * and a couple of reference, basic parameters of that dataset such as
	 * number of resolution levels or size along the x-axis, etc. Typical
	 * usage is to open the dataset in a tested reader, and check reader's
	 * values against the reference ones from this class.
	 */
	static class TestDataset
	{
		// @formatter:off
		private final float omeNgffVersion;
		private final String zarrURL;

		private final String dataType;
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final int sizeC;
		private final int sizeT;
		private final String axes;

		private final float scaleX;
		private final float scaleY;
		private final float scaleZ;

		private final int numberOfResLevels;
		private final int wells;
		private final int fields;

		private final String license;
		private final String study;
		// @formatter:off

		public TestDataset( String... s )
		{
			this( s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8], s[9], s[10], s[11], s[12], s[13], s[14], s[15], s[16] );
		}

		public TestDataset( //the order as it is in the CSV file!!!
		                    String omeNgffVersion, String filePath, String sizeX,
		                    String sizeY, String sizeZ, String sizeC, String sizeT,
		                    String axes, String wells, String fields,
		                    String license, String study,
		                    String pixelType, String scaleX, String scaleY, String scaleZ,
		                    String numberOfResLevels )
		{
			// @formatter:off
			this.omeNgffVersion = Float.parseFloat( omeNgffVersion ); //let fail if the number is not parseable/available !
			this.zarrURL = filePath;

			this.dataType = pixelType;
			this.sizeX = getIntOrMinusOne( sizeX );
			this.sizeY = getIntOrMinusOne( sizeY );
			this.sizeZ = getIntOrMinusOne( sizeZ );
			this.sizeC = getIntOrMinusOne( sizeC );
			this.sizeT = getIntOrMinusOne( sizeT );
			this.axes = axes;

			this.scaleX = getFloatOrMinusOne( scaleX );
			this.scaleY = getFloatOrMinusOne( scaleY );
			this.scaleZ = getFloatOrMinusOne( scaleZ );

			this.numberOfResLevels = getIntOrMinusOne( numberOfResLevels );
			this.wells = getIntOrMinusOne( wells );
			this.fields = getIntOrMinusOne( fields );

			this.license = license;
			this.study = study;
			// @formatter:on
		}

		//internal string-to-number "cast", resilient to missing values
		private int getIntOrMinusOne( String floatString )
		{
			return floatString.isEmpty() ? -1 : ( int ) Float.parseFloat( floatString );
		}

		private float getFloatOrMinusOne( String floatString )
		{
			return floatString.isEmpty() ? -1.0f : ( float ) Float.parseFloat( floatString );
		}

		// @formatter:off
		// Getters, ...written in a very succinct form
		public float getOmeNgffVersion() { return omeNgffVersion; }
		public String getZarrURL() { return zarrURL; }

		public String getDataType() { return dataType; }
		public int getSizeX() { return sizeX; }
		public int getSizeY() { return sizeY; }
		public int getSizeZ() { return sizeZ; }
		public int getSizeC() { return sizeC; }
		public int getSizeT() { return sizeT; }
		public String getAxes() { return axes; }

		public float getScaleX() { return scaleX; }
		public float getScaleY() { return scaleY; }
		public float getScaleZ() { return scaleZ; }

		public int getNumberOfResLevels() { return numberOfResLevels; }
		public int getWells() { return wells; }
		public int getFields() { return fields; }

		public String getLicense() { return license; }
		public String getStudy() { return study; }
		// @formatter:on

		@Override
		public String toString()
		{
			return "TestDataset v" + omeNgffVersion + " : " + zarrURL
					+ "\n  size=(" + sizeX + " x " + sizeY + " x " + sizeZ + " x " + sizeC + " x " + sizeT + ") [XYZCT]"
					+ "\n  axes='" + axes + "'" + ", multiscales=" + numberOfResLevels + ", pxType=" + dataType
					+ "\n  wells=" + wells + ", fields=" + fields
					+ ", resolution=(" + scaleX + " x " + scaleY + " x " + scaleZ + ") [XYZ]";
		}

		public String shortedURL()
		{
			final URI uri = URI.create( zarrURL );
			String scheme = uri.getScheme() != null ? uri.getScheme() + "://" : "";
			String host = uri.getHost();
			if ( host != null )
			{
				if ( host.length() > 15 )
				{
					host = host.substring( 0, 5 ) + "....." + host.substring( host.length() - 5 );
				}
				host += "/";
			}
			else
				host = "";
			String[] path = uri.getPath().split( "/" );
			return scheme + host + "...../" + path[ path.length - 1 ];
		}

		public String condensedDesription()
		{
			return shortedURL() + " (" + study + ") v" + omeNgffVersion + ":"
					+ "\n  size=(" + sizeX + " x " + sizeY + " x " + sizeZ + " x " + sizeC + " x " + sizeT + ") [XYZCT]"
					+ ", axes='" + axes + "', pxType=" + dataType
					+ "\n  multiscales=" + numberOfResLevels + ", wells=" + wells + ", fields=" + fields
					+ ", resolution=(" + scaleX + " x " + scaleY + " x " + scaleZ + ") [XYZ]";
		}
	}
}