package sc.fiji.ome.zarr.examples;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * BigStitcher/BigDataViewer-style OME-Zarr writer using n5-zarr.
 * This follows the pattern used by BigStitcher-Spark for creating OME-Zarr output.
 */
public class BigStitcherStyleOMEZarrWriter
{

	/**
	 * Writes a multi-scale OME-Zarr following OME-NGFF v0.4 specification.
	 * This is how BigStitcher-Spark creates OME-Zarr fusion output.
	 */
	public static class OMEZarrWriter
	{
		private final N5Writer n5;

		private final String zarrPath;

		private final int numScales;

		public OMEZarrWriter( String zarrPath, int numScales )
		{
			this.zarrPath = zarrPath;
			this.numScales = numScales;

			// Create N5 writer
			if ( zarrPath.startsWith( "s3://" ) )
			{
				String[] parts = zarrPath.substring( 5 ).split( "/", 2 );
				String bucketName = parts[ 0 ];
				String keyPrefix = parts.length > 1 ? parts[ 1 ] : "";

				this.n5 = new N5AmazonS3Writer(
						com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
						bucketName,
						keyPrefix
				);
			}
			else
			{
				this.n5 = new N5ZarrWriter( zarrPath, "/", false );
			}
		}

		/**
		 * Write a multiscale pyramid with proper OME-NGFF metadata. This follows BigStitcher-Spark's approach.<br>
		 * Writes a multiscale dataset to a Zarr-compatible format with OME-NGFF v0.4 metadata.
		 * The method saves multiple resolution levels, each corresponding to a downsampled
		 * version of the provided input, and writes the corresponding metadata necessary
		 * for OME-Zarr compliance.
		 * <br>
		 * @param scales An array of {@code RandomAccessibleInterval<T>} containing the resolution levels.
		 *               The first element represents the highest resolution, with progressively
		 *               downsampled scales in later elements.
		 * @param blockSizes A 2D array specifying the block sizes for each resolution level.
		 *                   Each element is an array of integers defining the block size along each dimension.
		 * @param voxelSizes A 2D array specifying the physical voxel sizes for each resolution level.
		 *                   Each element is an array of doubles corresponding to the size of each voxel
		 *                   along each dimension for a given resolution level.
		 * @param axisNames An array of strings defining the names of the dataset axes (e.g., "x", "y", "z").
		 * @param axisTypes An array of strings defining the types of the dataset axes (e.g., "space", "time").
		 * @param axisUnits An array of strings defining the units for the dataset axes (e.g., "micrometer").
		 * @param compression The compression method used to store blocks of data in the Zarr dataset.
		 * @param additionalMetadata A map containing additional metadata. This metadata may include
		 *                           custom properties or OMERO metadata to be included in the Zarr
		 *                           file structure.
		 * @throws IOException If an I/O error occurs during dataset or metadata writing.
		 */
		public < T extends RealType< T > & NativeType< T > > void writeMultiscale(
				RandomAccessibleInterval< T >[] scales,
				int[][] blockSizes,
				double[][] voxelSizes,
				String[] axisNames,
				String[] axisTypes,
				String[] axisUnits,
				Compression compression,
				Map< String, Object > additionalMetadata
		) throws IOException
		{

			// Write each resolution level
			for ( int level = 0; level < scales.length; level++ )
			{
				String datasetPath = String.valueOf( level );

				N5Utils.save(
						scales[ level ],
						n5,
						datasetPath,
						blockSizes[ level ],
						compression
				);

				System.out.println( "Written scale level " + level + ": " +
						Arrays.toString( scales[ level ].dimensionsAsLongArray() ) );
			}

			// Write OME-NGFF v0.4 metadata
			writeOMEMetadata(
					scales,
					voxelSizes,
					axisNames,
					axisTypes,
					axisUnits,
					additionalMetadata
			);
		}

		/**
		 * Writes OME-NGFF v0.4 compliant metadata to root .zattrs. This is the key method for proper OME-Zarr compliance.<br>
		 * Writes OME-NGFF metadata to the Zarr dataset. This method generates metadata in compliance
		 * with the OME-Zarr specification (version 0.4) and writes it alongside the multiscale datasets.
		 * The metadata includes information about axes, resolutions, and transformations, and optionally
		 * adds OMERO metadata or other user-defined attributes.
		 * <br>
		 * @param scales An array of {@code RandomAccessibleInterval<T>} containing the resolution levels.
		 *               Each element represents a different resolution, where the first element corresponds
		 *               to the highest resolution and later elements are progressively downsampled.
		 * @param voxelSizes A 2D array specifying the physical sizes of voxels for each resolution level.
		 *                   Each element is an array of doubles corresponding to the size of each voxel
		 *                   along each dimension for a specific resolution.
		 * @param axisNames An array of strings defining the names of the dataset axes (e.g., "x", "y", "z").
		 *                  The order of the names corresponds to the order of dimensions in the provided datasets.
		 * @param axisTypes An array of strings specifying the type of each axis (e.g., "space", "time").
		 *                  These types follow the OME-Zarr standard for axis classification.
		 * @param axisUnits An array of strings specifying the units of measurement for each axis
		 *                  (e.g., "micrometer", "second"). This array may contain null values if units
		 *                  are not specified for certain axes.
		 * @param additionalMetadata A map containing optional additional metadata. This metadata may include
		 *                           custom properties or OMERO-specific metadata to be written alongside
		 *                           the dataset. If provided, the "omero" key should contain a {@code JsonObject}
		 *                           representing OMERO metadata.
		 */
		private < T extends RealType< T > & NativeType< T > > void writeOMEMetadata(
				RandomAccessibleInterval< T >[] scales,
				double[][] voxelSizes,
				String[] axisNames,
				String[] axisTypes,
				String[] axisUnits,
				Map< String, Object > additionalMetadata
		)
		{

			JsonObject rootAttrs = new JsonObject();

			// Create multiscales array (OME-NGFF v0.4)
			JsonArray multiscales = new JsonArray();
			JsonObject multiscale = new JsonObject();

			multiscale.addProperty( "version", "0.4" );
			multiscale.addProperty( "name",
					additionalMetadata != null && additionalMetadata.containsKey( "name" ) ? ( String ) additionalMetadata.get( "name" )
							: "default" );

			// Add axes
			JsonArray axes = new JsonArray();
			for ( int i = 0; i < axisNames.length; i++ )
			{
				JsonObject axis = new JsonObject();
				axis.addProperty( "name", axisNames[ i ] );
				axis.addProperty( "type", axisTypes[ i ] );
				if ( axisUnits != null && i < axisUnits.length && axisUnits[ i ] != null )
				{
					axis.addProperty( "unit", axisUnits[ i ] );
				}
				axes.add( axis );
			}
			multiscale.add( "axes", axes );

			// Add datasets (resolution pyramid levels)
			JsonArray datasets = new JsonArray();
			for ( int level = 0; level < scales.length; level++ )
			{
				JsonObject dataset = new JsonObject();
				dataset.addProperty( "path", String.valueOf( level ) );

				// Add coordinate transformations
				JsonArray transforms = new JsonArray();
				JsonObject scaleTransform = new JsonObject();
				scaleTransform.addProperty( "type", "scale" );

				JsonArray scaleArray = new JsonArray();
				for ( double voxel : voxelSizes[ level ] )
				{
					scaleArray.add( voxel );
				}
				scaleTransform.add( "scale", scaleArray );

				transforms.add( scaleTransform );
				dataset.add( "coordinateTransformations", transforms );

				datasets.add( dataset );
			}
			multiscale.add( "datasets", datasets );

			multiscales.add( multiscale );

			// Write multiscales to root
			n5.setAttribute( "/", "multiscales", multiscales );

			// Add OMERO metadata if provided
			if ( additionalMetadata != null && additionalMetadata.containsKey( "omero" ) )
			{
				JsonObject omero = ( JsonObject ) additionalMetadata.get( "omero" );
				n5.setAttribute( "/", "omero", omero );
			}
		}

		/**
		 * Create a downsampled pyramid from a source image.
		 * This is similar to how BigStitcher-Spark creates resolution pyramids.
		 * <br>
		 * Creates a multiscale pyramid by downsampling the given source image over multiple levels.
		 * Each level in the pyramid is created by successively downsampling the previous level.
		 * The downsampling factors are meant to specify the scaling reductions applied at each level (currently unused).
		 * <br>
		 * @param source The highest resolution image represented as a {@code RandomAccessibleInterval<T>}.
		 *               This serves as the base for building the multiscale pyramid.
		 * @param numLevels The number of levels in the pyramid, including the original resolution.
		 * @param downsamplingFactors An array of integers specifying the downsampling factors for each dimension.
		 *                             These factors determine the scaling reductions applied (currently unused).
		 * @return An array of {@code RandomAccessibleInterval<T>} representing the multiscale pyramid.
		 *         The first element is the original image, and later elements are downsampled versions.
		 */
		public static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< T >[]
				createPyramid( RandomAccessibleInterval< T > source, int numLevels, int[] downsamplingFactors )
		{
			@SuppressWarnings( "unchecked" )
			RandomAccessibleInterval< T >[] pyramid = new RandomAccessibleInterval[ numLevels ];
			pyramid[ 0 ] = source;

			// Create downsampled levels
			RandomAccessibleInterval< T > current = source;
			for ( int level = 1; level < numLevels; level++ )
			{
				int factor = ( int ) Math.pow( 2, level );

				// Downsample by averaging (simple approach)
				// For production use, consider using imglib2-algorithm's Gauss + subsample
				long[] downsampledDims = new long[ current.numDimensions() ];
				for ( int d = 0; d < downsampledDims.length; d++ )
				{
					// Typically don't downsample the last two dimensions: time and channel
					if ( d >= ( downsampledDims.length - 2 ) )
					{
						downsampledDims[ d ] = current.dimension( d );
					}
					else
					{
						downsampledDims[ d ] = current.dimension( d ) / 2;
					}
				}
				System.out.println( "Level " + level + " pyramid size: " + downsampledDims );

				// Create downsampled image using simple subsampling
				// For better quality, use Gaussian blur before subsampling
				RandomAccessibleInterval< T > downsampled = subsample( current, downsampledDims );

				pyramid[ level ] = downsampled;
				current = downsampled;
			}

			return pyramid;
		}

		/**
		 * Simple subsampling helper (for demonstration).
		 * BigStitcher uses more sophisticated downsampling with Gaussian blur.
		 * <br>
		 * Subsamples a given source image to match the specified target dimensions.
		 * The subsampling is performed by taking every other pixel along the required dimensions.
		 * <br>
		 * @param <T> The type of the image, constrained to types extending {@code RealType}
		 *            and implementing {@code NativeType}.
		 * @param source The source image as a {@code RandomAccessibleInterval<T>} to be subsampled.
		 * @param targetDims A long array specifying the dimensions of the target subsampled image.
		 *                   Each element of the array represents the size of the corresponding dimension.
		 * @return A {@code RandomAccessibleInterval<T>} representing the subsampled image with the
		 *         specified target dimensions.
		 */
		private static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< T >
				subsample( RandomAccessibleInterval< T > source, long[] targetDims )
		{
			ArrayImgFactory< T > arrayImgFactory = new ArrayImgFactory<>();

			// Create target image -- DEPRECATED BUILDING OF NEW IMAGES
			net.imglib2.img.Img< T > target = arrayImgFactory.create(
					targetDims,
					source.randomAccess().get().createVariable()
			);

			// Simple subsampling by taking every other pixel
			Cursor< T > targetCursor = target.cursor();
			RandomAccess< T > sourceAccess = source.randomAccess();

			long[] sourcePos = new long[ source.numDimensions() ];
			while ( targetCursor.hasNext() )
			{
				targetCursor.fwd();
				targetCursor.localize( sourcePos );

				// Scale position to source coordinates
				for ( int d = 0; d < sourcePos.length - 2; d++ )
				{
					sourcePos[ d ] *= 2;
				}

				sourceAccess.setPosition( sourcePos );
				targetCursor.get().set( sourceAccess.get() );
			}

			return target;
		}

		public N5Writer getN5Writer()
		{
			return n5;
		}
	}

	/**
	 * Example usage demonstrating BigStitcher-style OME-Zarr writing
	 * <br>
	 * @param args ignored
	 */
	public static void main( String[] args )
	{
		try
		{
			// Create example 5D data (t, c, z, y, x) - typical OME-Zarr format
			//long[] dimensions = {1, 3, 50, 512, 512}; // 1 timepoint, 3 channels, 50 z-slices
			long[] dimensions = { 512, 512, 50, 3, 1 }; // "normal" order!!
			RandomAccessibleInterval< UnsignedShortType > sourceImage =
					net.imglib2.img.array.ArrayImgs.unsignedShorts( dimensions );

			// Fill with example data
			Cursor< UnsignedShortType > cursor = Views.flatIterable( sourceImage ).cursor();
			while ( cursor.hasNext() )
			{
				cursor.fwd();
				cursor.get().set( ( short ) ( Math.random() * 65535 ) );
			}

			// Example 1: Write to local filesystem
			String localPath = "/temp/output2.ome.zarr";
			OMEZarrWriter writer = new OMEZarrWriter( localPath, 3 ); // 3 resolution levels

			// Create multi-scale pyramid
			@SuppressWarnings( "unchecked" )
			RandomAccessibleInterval< UnsignedShortType >[] pyramid =
					OMEZarrWriter.createPyramid( sourceImage, 3, new int[] { 1, 2, 4 } );

			// Define block sizes for each level
			int[][] blockSizes = {
					{ 64, 64, 32, 1, 1 }, // Level 0: highest resolution
					{ 64, 64, 32, 1, 1 }, // Level 1: 2x downsampled
					{ 64, 64, 32, 1, 1 } // Level 2: 4x downsampled
			};

			// Define voxel sizes for each level (in micrometers)
			double[][] voxelSizes = {
					{ 0.5, 0.5, 1.0, 1.0, 1.0 }, // Level 0
					{ 1.0, 1.0, 1.0, 1.0, 1.0 }, // Level 1 (2x in x,y)
					{ 2.0, 2.0, 1.0, 1.0, 1.0 } // Level 2 (4x in x,y)
			};

			// Define axes following OME-NGFF convention
			String[] axisNames = { "t", "c", "z", "y", "x" };
			String[] axisTypes = { "time", "channel", "space", "space", "space" };
			String[] axisUnits = { null, null, "micrometer", "micrometer", "micrometer" };

			// Create OMERO metadata for visualization
			JsonObject omero = new JsonObject();
			JsonArray channels = new JsonArray();

			String[] channelNames = { "DAPI", "GFP", "RFP" };
			String[] channelColors = { "0000FF", "00FF00", "FF0000" };

			for ( int i = 0; i < 3; i++ )
			{
				JsonObject channel = new JsonObject();
				channel.addProperty( "label", channelNames[ i ] );
				channel.addProperty( "color", channelColors[ i ] );
				channel.addProperty( "active", true );

				JsonObject window = new JsonObject();
				window.addProperty( "start", 0 );
				window.addProperty( "end", 65535 );
				window.addProperty( "min", 0 );
				window.addProperty( "max", 65535 );
				channel.add( "window", window );

				channels.add( channel );
			}
			omero.add( "channels", channels );

			// Additional metadata
			Map< String, Object > metadata = new HashMap<>();
			metadata.put( "name", "ExampleDataset" );
			metadata.put( "omero", omero );

			// Write the multi-scale OME-Zarr
			writer.writeMultiscale(
					pyramid,
					blockSizes,
					voxelSizes,
					axisNames,
					axisTypes,
					axisUnits,
					new GzipCompression(), // or new BloscCompression()
					metadata
			);

			System.out.println( "Successfully written OME-Zarr to: " + localPath );

			/*
			// Example 2: Write to S3 (like BigStitcher-Spark does)
			String s3Path = "s3://my-bucket/output.ome.zarr";
			OMEZarrWriter s3Writer = new OMEZarrWriter(s3Path, 3);

			s3Writer.writeMultiscale(
				pyramid,
				blockSizes,
				voxelSizes,
				axisNames,
				axisTypes,
				axisUnits,
				new GzipCompression(),
				metadata
			);

			System.out.println("Successfully written OME-Zarr to S3: " + s3Path);

			// Example 3: Write with custom compression (Blosc for better performance)
			String bloscPath = "/path/to/output_blosc.ome.zarr";
			OMEZarrWriter bloscWriter = new OMEZarrWriter(bloscPath, 3);

			// Blosc typically provides better compression ratio and speed for microscopy data
			BloscCompression bloscCompression = new BloscCompression(
				"lz4",	  // compressor: lz4, zstd, zlib
				5,		  // compression level
				BloscCompression.SHUFFLE,  // shuffle mode
				0,		  // blocksize (0 = auto)
				4		   // number of threads
			);

			bloscWriter.writeMultiscale(
				pyramid,
				blockSizes,
				voxelSizes,
				axisNames,
				axisTypes,
				axisUnits,
				bloscCompression,
				metadata
			);

			System.out.println("Successfully written OME-Zarr with Blosc compression");

			// Example 4: Write single timepoint from time series
			// Useful for processing time-lapse data incrementally
			if (sourceImage.numDimensions() == 5 && sourceImage.dimension(0) > 1) {
				// Extract single timepoint
				RandomAccessibleInterval<UnsignedShortType> timepoint0 =
					Views.hyperSlice(sourceImage, 0, 0);

				// Create pyramid for this timepoint
				@SuppressWarnings("unchecked")
				RandomAccessibleInterval<UnsignedShortType>[] timepointPyramid =
					OMEZarrWriter.createPyramid(timepoint0, 3, new int[]{1, 2, 4});

				// Write to separate group
				String timeseriesPath = "/path/to/timeseries.ome.zarr";
				OMEZarrWriter tsWriter = new OMEZarrWriter(timeseriesPath + "/t0", 3);

				tsWriter.writeMultiscale(
					timepointPyramid,
					blockSizes,
					voxelSizes,
					axisNames,
					axisTypes,
					axisUnits,
					new GzipCompression(),
					metadata
				);
			}
			*/

		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to write a simple 3D volume as OME-Zarr (without multiscale).
	 * Useful for quick exports.
	 * <br>
	 * Writes a 3D volume to a Zarr store in a format compatible with OME-Zarr specifications.
	 * The method applies a BigStitcher-style layout, expanding the volume to 5 dimensions
	 * and customizing metadata such as voxel sizes, axis names, and compression settings.
	 *
	 * @param <T> The pixel type of the volume, which must extend both RealType and NativeType.
	 * @param volume The 3D input volume to be written, represented as a RandomAccessibleInterval.
	 * @param zarrPath The file path or URL where the Zarr store should be created.
	 * @param blockSize An array specifying the chunking size for the Z, Y, and X dimensions.
	 * @param voxelSize An array specifying the voxel size for the Z, Y, and X dimensions in micrometers.
	 * @param compression The compression scheme to be used for data storage.
	 * @throws IOException If there is an error during the writing process.
	 */
	public static < T extends RealType< T > & NativeType< T > > void writeSimple3D(
			RandomAccessibleInterval< T > volume,
			String zarrPath,
			int[] blockSize,
			double[] voxelSize,
			Compression compression
	) throws IOException
	{

		// Expand to 5D: (1, 1, z, y, x)
		RandomAccessibleInterval< T > expanded = Views.addDimension(
				Views.addDimension( volume, 0, 0 ), 0, 0 );

		OMEZarrWriter writer = new OMEZarrWriter( zarrPath, 1 );

		@SuppressWarnings( "unchecked" )
		RandomAccessibleInterval< T >[] scales = new RandomAccessibleInterval[] { expanded };

		int[][] blockSizes = {
				{ 1, 1, blockSize[ 0 ], blockSize[ 1 ], blockSize[ 2 ] }
		};

		double[][] voxelSizes = {
				{ 1.0, 1.0, voxelSize[ 0 ], voxelSize[ 1 ], voxelSize[ 2 ] }
		};

		String[] axisNames = { "t", "c", "z", "y", "x" };
		String[] axisTypes = { "time", "channel", "space", "space", "space" };
		String[] axisUnits = { null, null, "micrometer", "micrometer", "micrometer" };

		writer.writeMultiscale(
				scales,
				blockSizes,
				voxelSizes,
				axisNames,
				axisTypes,
				axisUnits,
				compression,
				Collections.singletonMap( "name", "volume" )
		);
	}
}
