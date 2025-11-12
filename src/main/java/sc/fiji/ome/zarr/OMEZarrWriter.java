package sc.fiji.ome.zarr;

import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer;
import org.janelia.saalfeldlab.n5.zarr.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import com.google.gson.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OMEZarrWriter {

	/**
	 * Writes an ImgLib2 image to OME-Zarr format with metadata.
	 * Supports both local filesystem and S3 destinations.
	 * 
	 * @param image The ImgLib2 image to write
	 * @param path Local path or S3 URL (e.g., "/path/to/output.zarr" or "s3://bucket/output.zarr")
	 * @param datasetPath Dataset path within Zarr (e.g., "0" for highest resolution)
	 * @param blockSize Chunk size for each dimension
	 * @param compression Compression settings (e.g., new GzipCompression() or new BloscCompression())
	 */
	public static <T extends RealType<T> & NativeType<T>>
	void write(
		RandomAccessibleInterval<T> image,
		String path,
		String datasetPath,
		int[] blockSize,
		Compression compression
	) throws IOException {

		N5Writer n5;

		// Create writer based on path type
		if (path.startsWith("s3://")) {
			String[] parts = path.substring(5).split("/", 2);
			String bucketName = parts[0];
			String keyPrefix = parts.length > 1 ? parts[1] : "";

			n5 = new N5AmazonS3Writer(
				com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
				bucketName,
				keyPrefix
			);
		} else {
			// Local filesystem
			n5 = new N5ZarrWriter(path);
		}

		// Write the image data
		// N5Utils.save handles the chunking and writing automatically
		N5Utils.save(
			image,
			n5,
			datasetPath,
			blockSize,
			compression
		);
	}


	/**
	 * Writes a complete OME-Zarr with full metadata including multiscale information.
	 * 
	 * @param image The ImgLib2 image to write
	 * @param metadata OME-Zarr metadata to include
	 * @param path Output path (local or S3)
	 * @param datasetPath Dataset path within Zarr
	 * @param blockSize Chunk size
	 * @param compression Compression to use
	 */
	public static <T extends RealType<T> & NativeType<T>>
	void writeWithMetadata(
		RandomAccessibleInterval<T> image,
		OMEZarrMetadata metadata,
		String path,
		String datasetPath,
		int[] blockSize,
		Compression compression
	) throws IOException {

		N5Writer n5;

		if (path.startsWith("s3://")) {
			String[] parts = path.substring(5).split("/", 2);
			String bucketName = parts[0];
			String keyPrefix = parts.length > 1 ? parts[1] : "";
			n5 = new N5AmazonS3Writer(
				com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
				bucketName,
				keyPrefix
			);
		} else {
			n5 = new N5ZarrWriter(path);
		}

		// Write the image
		N5Utils.save(image, n5, datasetPath, blockSize, compression);

		// Write OME-Zarr metadata
		writeOMEMetadata(n5, metadata, datasetPath, image.dimensionsAsLongArray());
	}


	/**
	 * Writes OME-Zarr compliant metadata to the root group.
	 */
	private static void writeOMEMetadata(
		N5Writer n5,
		OMEZarrMetadata metadata,
		String datasetPath,
		long[] dimensions
	) throws IOException {

		JsonObject rootAttrs = new JsonObject();

		// Add multiscales metadata (required for OME-Zarr)
		JsonArray multiscales = new JsonArray();
		JsonObject multiscale = new JsonObject();

		multiscale.addProperty("version", "0.4");
		multiscale.addProperty("name", metadata.name != null ? metadata.name : "image");

		// Add axes information
		JsonArray axes = new JsonArray();
		for (String axis : metadata.axes) {
			JsonObject axisObj = new JsonObject();
			axisObj.addProperty("name", axis);
			axisObj.addProperty("type", getAxisType(axis));
			if (metadata.units.containsKey(axis)) {
				axisObj.addProperty("unit", metadata.units.get(axis));
			}
			axes.add(axisObj);
		}
		multiscale.add("axes", axes);

		// Add datasets (resolution levels)
		JsonArray datasets = new JsonArray();
		JsonObject dataset = new JsonObject();
		dataset.addProperty("path", datasetPath);

		// Add coordinate transformations (scale)
		JsonArray transforms = new JsonArray();
		JsonObject scale = new JsonObject();
		scale.addProperty("type", "scale");
		JsonArray scaleArray = new JsonArray();
		for (double s : metadata.pixelSizes) {
			scaleArray.add(s);
		}
		scale.add("scale", scaleArray);
		transforms.add(scale);
		dataset.add("coordinateTransformations", transforms);

		datasets.add(dataset);
		multiscale.add("datasets", datasets);

		multiscales.add(multiscale);
		rootAttrs.add("multiscales", multiscales);

		// Add OMERO metadata if provided
		if (metadata.omero != null) {
			rootAttrs.add("omero", metadata.omero);
		}

		// Write attributes to root group
		n5.setAttribute("/", "multiscales", multiscales);
		if (metadata.omero != null) {
			n5.setAttribute("/", "omero", metadata.omero);
		}
	}


	private static String getAxisType(String axisName) {
		switch (axisName.toLowerCase()) {
			case "t": return "time";
			case "c": return "channel";
			case "z": return "space";
			case "y": return "space";
			case "x": return "space";
			default: return "space";
		}
	}


	/**
	 * Example usage demonstrating writing to both local and S3
	 */
	public static void main(String[] args) {
		try {
			// Create a sample image (in practice, this would be your actual data)
			long[] dimensions = {512, 512, 100, 3}; // X, Y, Z, C
			RandomAccessibleInterval<UnsignedShortType> image =
				Views.interval(
					net.imglib2.img.array.ArrayImgs.unsignedShorts(dimensions),
					new long[]{0, 0, 0, 0},
					new long[]{511, 511, 99, 2}
				);

			// Create metadata
			OMEZarrMetadata metadata = new OMEZarrMetadata();
			metadata.name = "Sample Image";
			metadata.axes = new String[]{"x", "y", "z", "c"};
			metadata.pixelSizes = new double[]{0.5, 0.5, 1.0, 1.0}; // µm
			metadata.units = new HashMap<>();
			metadata.units.put("x", "micrometer");
			metadata.units.put("y", "micrometer");
			metadata.units.put("z", "micrometer");

			// Optional: Add OMERO metadata for visualization
			JsonObject omero = new JsonObject();
			JsonArray channels = new JsonArray();
			for (int i = 0; i < 3; i++) {
				JsonObject channel = new JsonObject();
				channel.addProperty("label", "Channel " + i);
				channel.addProperty("color", i == 0 ? "FF0000" : i == 1 ? "00FF00" : "0000FF");
				channels.add(channel);
			}
			omero.add("channels", channels);
			metadata.omero = omero;

			// Example 1: Write to local filesystem
			String localPath = "/path/to/output.ome.zarr";
			int[] blockSize = {64, 64, 32, 1}; // Chunk size per dimension

			writeWithMetadata(
				image,
				metadata,
				localPath,
				"0", // Highest resolution level
				blockSize,
				new GzipCompression() // or new BloscCompression() for better performance
			);

			System.out.println("Written to local path: " + localPath);

			// Example 2: Write to S3
			String s3Path = "s3://my-bucket/output.ome.zarr";

			writeWithMetadata(
				image,
				metadata,
				s3Path,
				"0",
				blockSize,
				new GzipCompression()
			);

			System.out.println("Written to S3: " + s3Path);

			// Example 3: Write multiscale pyramid
			// You would typically create downsampled versions of your image
			for (int level = 0; level < 3; level++) {
				int scale = (int) Math.pow(2, level);
				// In practice, downsample your image here
				// RandomAccessibleInterval<UnsignedShortType> downsampled = downsample(image, scale);

				OMEZarrMetadata levelMetadata = metadata.copy();
				for (int i = 0; i < levelMetadata.pixelSizes.length; i++) {
					levelMetadata.pixelSizes[i] *= scale;
				}

				int[] levelBlockSize = blockSize.clone();
				for (int i = 0; i < levelBlockSize.length - 1; i++) {
					levelBlockSize[i] = Math.min(levelBlockSize[i] * scale, (int)dimensions[i]);
				}

				// writeWithMetadata(downsampled, levelMetadata, localPath, String.valueOf(level), levelBlockSize, new GzipCompression());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Container class for OME-Zarr metadata
	 */
	public static class OMEZarrMetadata {
		public String name;
		public String[] axes;
		public double[] pixelSizes;
		public Map<String, String> units;
		public JsonObject omero;

		public OMEZarrMetadata copy() {
			OMEZarrMetadata copy = new OMEZarrMetadata();
			copy.name = this.name;
			copy.axes = this.axes.clone();
			copy.pixelSizes = this.pixelSizes.clone();
			copy.units = new HashMap<>(this.units);
			copy.omero = this.omero != null ? this.omero.deepCopy() : null;
			return copy;
		}
	}
}
