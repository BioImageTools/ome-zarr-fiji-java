package sc.fiji.ome.zarr.examples;

import net.imglib2.RandomAccess;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;
import org.janelia.saalfeldlab.n5.zarr.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import com.google.gson.*;

import java.io.IOException;
import java.util.*;

/**
 * BigStitcher/BigDataViewer-style OME-Zarr reader using n5-zarr.
 * This follows the pattern used by bigdataviewer-omezarr and mobie-io projects.
 */
public class BigStitcherStyleOMEZarrReader {

	/**
	 * Reads OME-Zarr metadata following the OME-NGFF v0.4 specification.
	 * This is how BigDataViewer/MoBIE parse OME-Zarr metadata.
	 */
	public static class OMEZarrMetadata {
		public List<Multiscale> multiscales;
		public JsonObject omero;

		public static class Multiscale {
			public String version;
			public String name;
			public List<Axis> axes;
			public List<Dataset> datasets;
			public String type; // Optional
			public JsonObject metadata; // Optional additional metadata

			public static class Axis {
				public String name;
				public String type;
				public String unit;
			}

			public static class Dataset {
				public String path;
				public List<CoordinateTransformation> coordinateTransformations;

				public static class CoordinateTransformation {
					public String type;
					public double[] scale;
					public double[] translation;
				}
			}
		}
	}

	/**
	 * Opens an OME-Zarr dataset lazily, following BigStitcher's approach.
	 * <br>
	 * @param zarrPath Path to .ome.zarr (local or S3 URL like "s3://bucket/data.ome.zarr")
	 * @return OMEZarrContainer with metadata and lazy image access
	 * @throws IOException if there is an error reading the metadata or opening the dataset
	 */
	public static OMEZarrContainer openOMEZarr( String zarrPath ) throws IOException
	{
		N5Reader n5;

		// Create appropriate N5 reader
		if ( zarrPath.startsWith( "s3://" ) )
		{
			String[] parts = zarrPath.substring( 5 ).split( "/", 2 );
			String bucketName = parts[ 0 ];
			String keyPrefix = parts.length > 1 ? parts[ 1 ] : "";

			n5 = new N5AmazonS3Reader(
					com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
					bucketName,
					keyPrefix
			);
		}
		else
		{
			n5 = new N5ZarrReader( zarrPath );
		}

		// Parse OME-Zarr metadata from .zattrs
		OMEZarrMetadata metadata = parseOMEZarrMetadata( n5 );

		return new OMEZarrContainer( n5, metadata, zarrPath );
	}


	/**
	 * Parses OME-NGFF metadata from the root .zattrs file.
	 * This follows the pattern used by bigdataviewer-omezarr.
	 */
	private static OMEZarrMetadata parseOMEZarrMetadata(N5Reader n5) throws IOException {
		OMEZarrMetadata metadata = new OMEZarrMetadata();

		// Read the root attributes (equivalent to .zattrs)
		Map<String, Class<?>> attributes = n5.listAttributes("/");

		// Parse multiscales array
		if (attributes.containsKey("multiscales")) {
			JsonElement multiscalesJson = n5.getAttribute("/", "multiscales", JsonElement.class);
			if (multiscalesJson != null && multiscalesJson.isJsonArray()) {
				Gson gson = new Gson();
				metadata.multiscales = new ArrayList<>();
				JsonArray multiscalesArray = multiscalesJson.getAsJsonArray();

				for (JsonElement elem : multiscalesArray) {
					OMEZarrMetadata.Multiscale ms = gson.fromJson(
						elem,
						OMEZarrMetadata.Multiscale.class
					);
					metadata.multiscales.add(ms);
				}
			}
		}

		// Parse OMERO metadata if present
		if (attributes.containsKey("omero")) {
			metadata.omero = n5.getAttribute("/", "omero", JsonObject.class);
		}

		return metadata;
	}


	/**
	 * Container class holding N5 reader, metadata, and helper methods.
	 * Similar to what BigDataViewer uses internally.
	 */
	public static class OMEZarrContainer {
		private final N5Reader n5;
		private final OMEZarrMetadata metadata;
		private final String path;

		public OMEZarrContainer(N5Reader n5, OMEZarrMetadata metadata, String path) {
			this.n5 = n5;
			this.metadata = metadata;
			this.path = path;
		}

		/**
		 * Get the number of resolution levels (scales) available.
		 * @return Number of scales
		 */
		public int getNumScales()
		{
			if ( metadata.multiscales == null || metadata.multiscales.isEmpty() )
			{
				return 0;
			}
			return metadata.multiscales.get( 0 ).datasets.size();
		}

		/**
		 * Get the dataset path for a specific resolution level.
		 * Typically "0" is highest resolution, "1" is downsampled by 2, etc.
		 * <br>
		 * @param scaleLevel Resolution level (scale) index
		 * @return Dataset path
		 */
		public String getScalePath( int scaleLevel )
		{
			if ( metadata.multiscales == null || metadata.multiscales.isEmpty() )
			{
				return String.valueOf( scaleLevel );
			}
			return metadata.multiscales.get( 0 ).datasets.get( scaleLevel ).path;
		}

		/**
		 * Get the voxel size (scale) for a specific resolution level.
		 * <br>
		 * @param scaleLevel Resolution level (scale) index
		 * @return Voxel size
		 */
		public double[] getVoxelSize( int scaleLevel )
		{
			if ( metadata.multiscales == null || metadata.multiscales.isEmpty() )
			{
				return null;
			}

			List< OMEZarrMetadata.Multiscale.Dataset.CoordinateTransformation > transforms =
					metadata.multiscales.get( 0 ).datasets.get( scaleLevel ).coordinateTransformations;

			if ( transforms == null || transforms.isEmpty() )
			{
				return null;
			}

			// Find the scale transformation
			for ( OMEZarrMetadata.Multiscale.Dataset.CoordinateTransformation transform : transforms )
			{
				if ( "scale".equals( transform.type ) )
				{
					return transform.scale;
				}
			}

			return null;
		}

		/**
		 * Get axis names (e.g., ["t", "c", "z", "y", "x"])
		 * @return Array of axis names, or null if there are no axes
		 */
		public String[] getAxisNames()
		{
			if ( metadata.multiscales == null || metadata.multiscales.isEmpty() )
			{
				return null;
			}

			List< OMEZarrMetadata.Multiscale.Axis > axes = metadata.multiscales.get( 0 ).axes;
			if ( axes == null )
			{
				return null;
			}

			return axes.stream().map( axis -> axis.name ).toArray( String[]::new );
		}

		/**
		 * Open a specific resolution level as a lazy ImgLib2 image.
		 * This is the key method - it returns a lazy RandomAccessibleInterval.
		 * <br>
		 * Note: While other methods seem to report in Python/numpy order [t,c,z,y,x],
		 * the order of axes in the returned image is "normal" --- that is, the opposite
		 * to what is listed in e.g. this.getAxisNames().
		 * <br>
		 * @param scaleLevel Resolution level (scale) index
		 * @return Lazy ImgLib2 image
		 */
		public < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< T > openScale( int scaleLevel )
		{
			String datasetPath = getScalePath( scaleLevel );
			return N5Utils.open( n5, datasetPath );
		}

		public < T extends RealType< T > & NativeType< T > > LazyCellImg< T, ? > openScaleLazy( int scaleLevel )
		{
			String datasetPath = getScalePath(scaleLevel);
			return N5Utils.open(n5, datasetPath);
		}

		/**
		 * Get all metadata for inspection
		 * @return OMEZarrMetadata object
		 */
		public OMEZarrMetadata getMetadata()
		{
			return metadata;
		}

		/**
		 * Get the underlying N5Reader for advanced operations
		 * @return N5Reader object
		 */
		public N5Reader getN5Reader()
		{
			return n5;
		}
	}

	/**
	 * Example usage demonstrating BigStitcher-style OME-Zarr access
	 * @param args Ignored
	 */
	public static void main( String[] args )
	{
		try
		{
			// Example 1: Open local OME-Zarr
			//String localPath = "/home/ulman/data/Mette_moreEmbryos/embryo4_first_tp/dataset.ome.zarr/s0-t0.zarr";
			String localPath = "/temp/output2.ome.zarr";
			OMEZarrContainer container = openOMEZarr( localPath );

			System.out.println( "Number of resolution levels: " + container.getNumScales() );
			System.out.println( "Axis names: " + Arrays.toString( container.getAxisNames() ) );

			// Open highest resolution (level 0) lazily
			RandomAccessibleInterval< UnsignedShortType > level0 = container.openScale( 0 );
			System.out.println( "Level 0 dimensions: " +
					Arrays.toString( level0.dimensionsAsLongArray() ) );

			// Get voxel size
			double[] voxelSize = container.getVoxelSize( 0 );
			System.out.println( "Level 0 voxel size: " + Arrays.toString( voxelSize ) );

			// Open lower resolution levels (for multi-scale visualization like BigDataViewer)
			for ( int level = 0; level < container.getNumScales(); level++ )
			{
				RandomAccessibleInterval< UnsignedShortType > img = container.openScale( level );
				double[] scale = container.getVoxelSize( level );

				System.out.println( "Level " + level + ": " +
						Arrays.toString( img.dimensionsAsLongArray() ) +
						" @ scale " + Arrays.toString( scale ) );
			}

			/*
			// Example 2: Open from S3 (like BigStitcher-Spark does)
			String s3Path = "s3://my-bucket/data.ome.zarr";
			OMEZarrContainer s3Container = openOMEZarr(s3Path);

			// Open specific channel and timepoint by slicing
			// OME-Zarr is typically 5D: (t, c, z, y, x)
			RandomAccessibleInterval<UnsignedShortType> fullImage = s3Container.openScale(0);

			// For BigDataViewer-style access, you might want to extract
			// specific timepoint and channel
			// Assuming dimensions are (t, c, z, y, x)
			long[] dimensions = fullImage.dimensionsAsLongArray();
			System.out.println("Full 5D shape: " + Arrays.toString(dimensions));

			// Extract t=0, c=0 to get 3D volume (like BigDataViewer does)
			if (dimensions.length == 5) {
				RandomAccessibleInterval<UnsignedShortType> volume =
					net.imglib2.view.Views.hyperSlice(
						net.imglib2.view.Views.hyperSlice(fullImage, 0, 0), // t=0
						0, 0  // c=0
					);
				System.out.println("3D volume shape: " +
					Arrays.toString(volume.dimensionsAsLongArray()));
			}

			// Example 3: Access OMERO metadata for visualization settings
			if (container.getMetadata().omero != null) {
				JsonObject omero = container.getMetadata().omero;
				System.out.println("OMERO metadata: " + omero);

				// Parse channel colors, names, etc. for rendering
				if (omero.has("channels")) {
					JsonArray channels = omero.getAsJsonArray("channels");
					for (int i = 0; i < channels.size(); i++) {
						JsonObject channel = channels.get(i).getAsJsonObject();
						String label = channel.has("label") ?
							channel.get("label").getAsString() : "Channel " + i;
						String color = channel.has("color") ?
							channel.get("color").getAsString() : "FFFFFF";

						System.out.println("Channel " + i + ": " + label +
							" (color: " + color + ")");
					}
				}
			}
			*/

			// Example 4: Work with the data lazily
			// This is the key advantage - no data is loaded until accessed
			RandomAccessibleInterval< UnsignedShortType > lazyImage = container.openScale( 0 );

			// Only when you access pixels does data get loaded from disk/S3
			RandomAccess< UnsignedShortType > ra = lazyImage.randomAccess();
			ra.setPosition( new long[] { 256, 256, 50, 0, 0 } ); // "normal" order!!
			int pixelValue = ra.get().get();
			System.out.println( "Pixel value at position: " + pixelValue );

			System.out.println( "-----------" );
			for ( int x = 200; x < 220; ++x )
				System.out.println( lazyImage.getAt( x, 257, 50, 0, 0 ) );
			System.out.println( "===========" );
			for ( int x = 200; x < 220; ++x )
				System.out.println( lazyImage.getAt( x, 257, 50, 0, 0 ) );
			System.out.println( "+++++++++++" );

			ImageJFunctions.show( lazyImage );
			lazyImage = container.openScale( 1 );
			ImageJFunctions.show( lazyImage );
			lazyImage = container.openScale( 2 );
			ImageJFunctions.show( lazyImage );
			System.out.println( lazyImage );

		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}
}
