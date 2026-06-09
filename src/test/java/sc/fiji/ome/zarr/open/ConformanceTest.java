package sc.fiji.ome.zarr.open;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;
import org.scijava.display.DisplayService;
import sc.fiji.ome.zarr.open.options.ZarrOpenBehavior;
import sc.fiji.ome.zarr.open.options.ZarrOpeningSettings;
import sc.fiji.ome.zarr.open.options.ZarrReaderBackend;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConformanceTest
{
	static Stream< ZarrReaderBackend > readerBackends()
	{
		return Stream.of( ZarrReaderBackend.N5, ZarrReaderBackend.ZARR_JAVA );
	}

	static Stream< TestDataset > readerTestDatasets()
	{
		//TODO: get the .csv file from the resources
		final String csvFile = "/home/ulman/devel/Zurich_hack/ome-zarr-fiji-java__BioImageTools/testing/testbed_datasets.csv";

		List< TestDataset > datasets = new ArrayList<>( 200 );
		try (BufferedReader br = new BufferedReader( new FileReader( csvFile ) ))
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
			final URI testDatasetURI = new URI( testDataset.getZarrURL() );
			//TODO: add some logger on the screen

			final ZarrOpeningSettings settings = new ZarrOpeningSettings();
			settings.setCurrentChoice( ZarrOpenBehavior.IMAGEJ_HIGHEST_RESOLUTION );
			settings.setReaderBackend( backend );

			final AtomicReference< String > capturedError = new AtomicReference<>();
			final Consumer< String > errorHandler = capturedError::set;

			//open URL, read the dataset, and retrieve it as an opened Dataset
			assertDoesNotThrow( () -> new ZarrOpenActions( testDatasetURI, context, settings, errorHandler ).openIJWithImage(),
					"Failed while opening the dataset " + testDataset.getStudy() + " (study columns) with IJ." );

			DatasetService datasetService = context.getService( DatasetService.class );
			assertEquals( 1, datasetService.getDatasets().size() );

			Dataset d = datasetService.getDatasets().get( 0 );
			assertInstanceOf( PyramidalDataset.class, d );

			PyramidalDataset< ? > pyramidalDataset = ( PyramidalDataset< ? > ) d;

			//the conformance testing:
			assertEquals( pyramidalDataset.getWidth(), testDataset.getSizeX() );
			assertEquals( pyramidalDataset.getHeight(), testDataset.getSizeY() );
			assertEquals( pyramidalDataset.getDepth(), testDataset.getSizeZ() );
			//TODO: add more!

			//clean up: wait for the IJ window to clam down, get a reference on it, and close it
			SwingUtilities.invokeAndWait( () -> {} );
			DisplayService displayService = context.getService( DisplayService.class );
			assertNotNull( displayService.getActiveDisplay() );
			displayService.getActiveDisplay().close();
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
	}
}
