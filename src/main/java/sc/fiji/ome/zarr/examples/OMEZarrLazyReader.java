package sc.fiji.ome.zarr.examples;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.zarr.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import net.imglib2.*;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

public class OMEZarrLazyReader {

	/**
	 * Reads an OME-Zarr dataset lazily into ImgLib2 structures.
	 * Supports both local filesystem and S3 URLs.
	 * <br>
	 * @param path Local path (e.g., "/path/to/data.zarr") or S3 URL (e.g., "s3://bucket/data.zarr")
	 * @param datasetPath Path within the Zarr (e.g., "0" for multiscale level 0, or "labels/cells")
	 * @return Lazy RandomAccessibleInterval backed by the OME-Zarr store
	 */
	public static <T extends RealType<T> & NativeType<T>>
			RandomAccessibleInterval< T > readLazy( String path, String datasetPath )
	{

		N5Reader n5;

		// Determine if it's S3 or local filesystem
		if (path.startsWith("s3://")) {
			// For S3, use N5AmazonS3Reader
			String[] parts = path.substring(5).split("/", 2);
			String bucketName = parts[0];
			String keyPrefix = parts.length > 1 ? parts[1] : "";

			// Create S3 reader with Zarr backend
			n5 = new N5AmazonS3Reader(
				com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
				bucketName,
				keyPrefix
			);
		} else {
			// Local filesystem - use ZarrKeyValueReader
			n5 = new N5ZarrReader(path);
		}

		final N5Reader n5reader = new N5Factory().openReader(path);
		RandomAccessibleInterval<T> lazyImg = N5Utils.open(n5reader, datasetPath);
		
		N5TreeNode rootNode = N5DatasetDiscoverer.discover(n5reader);
		if (rootNode.getDescendant(datasetPath).get().getMetadata() instanceof OMEZarrMetadata) {
			OMEZarrMetadata omeZarrMD = (OMEZarrMetadata) rootNode.getDescendant(datasetPath).get().getMetadata();

		}
		N5MultiScaleMetadata n5MultiScaleMetadata;

		N5Writer n5readerwriter = new N5Factory().openWriter(path);
		N5Utils.save(lazyImg, n5readerwriter, "firstImage0", new int[] {64,64,64}, new GzipCompression());


		return lazyImg;
	}


	/**
	 * Reads OME-Zarr metadata including multiscale information, axes, units, etc.
	 * <br>
	 * @param path Local path or S3 URL to the OME-Zarr container
	 * @return Metadata structure containing OME-Zarr specific information
	 */
	public static OMEZarrMetadata readMetadata( String path )
	{
		N5Reader n5;

		if (path.startsWith("s3://")) {
			String[] parts = path.substring(5).split("/", 2);
			String bucketName = parts[0];
			String keyPrefix = parts.length > 1 ? parts[1] : "";
			n5 = new N5AmazonS3Reader(
				com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
				bucketName,
				keyPrefix
			);
		} else {
			n5 = new N5ZarrReader(path);
		}

		OMEZarrMetadata metadata = new OMEZarrMetadata();

		// Read root attributes (.zattrs in Zarr)
		JsonElement attrs = n5.getAttribute("/", "attributes", JsonElement.class);
		if (attrs != null && attrs.isJsonObject()) {
			JsonObject attrsObj = attrs.getAsJsonObject();

			// Parse OME-Zarr multiscales metadata
			if (attrsObj.has("multiscales")) {
				metadata.multiscales = attrsObj.get("multiscales");
			}

			// Parse OME metadata if present
			if (attrsObj.has("omero")) {
				metadata.omero = attrsObj.get("omero").getAsJsonObject();
			}
		}

		// Read dataset-specific metadata
		DatasetAttributes datasetAttrs = n5.getDatasetAttributes("/0");
		if (datasetAttrs != null) {
			metadata.dimensions = datasetAttrs.getDimensions();
			metadata.blockSize = datasetAttrs.getBlockSize();
			metadata.dataType = datasetAttrs.getDataType();
		}

		return metadata;
	}

	/**
	 * Example usage demonstrating lazy reading from both local and S3 sources
	 * <br>
	 * @param args Ignored
	 */
	public static void main( String[] args )
	{
		// Example 1: Read from local filesystem
		String localPath = "/path/to/local/data.ome.zarr";
		RandomAccessibleInterval< UnsignedShortType > localImg =
				readLazy( localPath, "0" ); // "0" is typically the highest resolution

		System.out.println( "Local image dimensions: " +
				localImg.dimension( 0 ) + " x " +
				localImg.dimension( 1 ) + " x " +
				localImg.dimension( 2 ) );

		// Read metadata
		OMEZarrMetadata localMetadata = readMetadata( localPath );
		System.out.println( "Multiscales info: " + localMetadata.multiscales );

		// Example 2: Read from S3
		String s3Path = "s3://my-bucket/data.ome.zarr";
		RandomAccessibleInterval< UnsignedShortType > s3Img =
				readLazy( s3Path, "0" );

		System.out.println( "S3 image dimensions: " +
				s3Img.dimension( 0 ) + " x " +
				s3Img.dimension( 1 ) + " x " +
				s3Img.dimension( 2 ) );

		// Example 3: Access a specific region (still lazy - only reads needed blocks)
		IntervalView< UnsignedShortType > crop = Views.interval(
				localImg,
				new long[] { 100, 100, 0 },
				new long[] { 199, 199, 10 }
		);

		// Data is only actually read when you iterate over it
		Cursor< UnsignedShortType > cursor = Views.flatIterable( crop ).cursor();
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			// Process pixel values here
			// short value = cursor.get().get();
		}

		// Example 4: Read different resolution levels
		RandomAccessibleInterval< UnsignedShortType > level1 =
				readLazy( localPath, "1" ); // Lower resolution pyramid level
		RandomAccessibleInterval< UnsignedShortType > level2 =
				readLazy( localPath, "2" ); // Even lower resolution

	}


	/**
	 * Container class for OME-Zarr metadata
	 */
	public static class OMEZarrMetadata {
		public JsonElement multiscales;
		public JsonObject omero;
		public long[] dimensions;
		public int[] blockSize;
		public DataType dataType;
	}
}
