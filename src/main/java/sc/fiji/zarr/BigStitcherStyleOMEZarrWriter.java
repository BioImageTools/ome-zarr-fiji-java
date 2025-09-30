package sc.fiji.zarr;

import net.imglib2.RandomAccess;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.blosc.BloscCompression;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer;
import org.janelia.saalfeldlab.n5.zarr.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import com.google.gson.*;

import java.io.IOException;
import java.util.*;

/**
 * BigStitcher/BigDataViewer-style OME-Zarr writer using n5-zarr.
 * This follows the pattern used by BigStitcher-Spark for creating OME-Zarr output.
 */
public class BigStitcherStyleOMEZarrWriter {
    
    /**
     * Writes a multi-scale OME-Zarr following OME-NGFF v0.4 specification.
     * This is how BigStitcher-Spark creates OME-Zarr fusion output.
     */
    public static class OMEZarrWriter {
        private final N5Writer n5;
        private final String zarrPath;
        private final int numScales;
        
        public OMEZarrWriter(String zarrPath, int numScales) throws IOException {
            this.zarrPath = zarrPath;
            this.numScales = numScales;
            
            // Create N5 writer
            if (zarrPath.startsWith("s3://")) {
                String[] parts = zarrPath.substring(5).split("/", 2);
                String bucketName = parts[0];
                String keyPrefix = parts.length > 1 ? parts[1] : "";
                
                this.n5 = new N5AmazonS3Writer(
                    com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient(),
                    bucketName,
                    keyPrefix
                );
            } else {
                this.n5 = new N5ZarrWriter(zarrPath, "/", false);
            }
        }
        
        /**
         * Write a multi-scale pyramid with proper OME-NGFF metadata.
         * This follows BigStitcher-Spark's approach.
         */
        public <T extends RealType<T> & NativeType<T>> void writeMultiscale(
            RandomAccessibleInterval<T>[] scales,
            int[][] blockSizes,
            double[][] voxelSizes,
            String[] axisNames,
            String[] axisTypes,
            String[] axisUnits,
            Compression compression,
            Map<String, Object> additionalMetadata
        ) throws IOException {
            
            // Write each resolution level
            for (int level = 0; level < scales.length; level++) {
                String datasetPath = String.valueOf(level);
                
                N5Utils.save(
                    scales[level],
                    n5,
                    datasetPath,
                    blockSizes[level],
                    compression
                );
                
                System.out.println("Written scale level " + level + ": " + 
                    Arrays.toString(scales[level].dimensionsAsLongArray()));
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
         * Writes OME-NGFF v0.4 compliant metadata to root .zattrs.
         * This is the key method for proper OME-Zarr compliance.
         */
        private <T extends RealType<T> & NativeType<T>> void writeOMEMetadata(
            RandomAccessibleInterval<T>[] scales,
            double[][] voxelSizes,
            String[] axisNames,
            String[] axisTypes,
            String[] axisUnits,
            Map<String, Object> additionalMetadata
        ) throws IOException {
            
            JsonObject rootAttrs = new JsonObject();
            
            // Create multiscales array (OME-NGFF v0.4)
            JsonArray multiscales = new JsonArray();
            JsonObject multiscale = new JsonObject();
            
            multiscale.addProperty("version", "0.4");
            multiscale.addProperty("name", 
                additionalMetadata != null && additionalMetadata.containsKey("name") ?
                    (String) additionalMetadata.get("name") : "default");
            
            // Add axes
            JsonArray axes = new JsonArray();
            for (int i = 0; i < axisNames.length; i++) {
                JsonObject axis = new JsonObject();
                axis.addProperty("name", axisNames[i]);
                axis.addProperty("type", axisTypes[i]);
                if (axisUnits != null && i < axisUnits.length && axisUnits[i] != null) {
                    axis.addProperty("unit", axisUnits[i]);
                }
                axes.add(axis);
            }
            multiscale.add("axes", axes);
            
            // Add datasets (resolution pyramid levels)
            JsonArray datasets = new JsonArray();
            for (int level = 0; level < scales.length; level++) {
                JsonObject dataset = new JsonObject();
                dataset.addProperty("path", String.valueOf(level));
                
                // Add coordinate transformations
                JsonArray transforms = new JsonArray();
                JsonObject scaleTransform = new JsonObject();
                scaleTransform.addProperty("type", "scale");
                
                JsonArray scaleArray = new JsonArray();
                for (double voxel : voxelSizes[level]) {
                    scaleArray.add(voxel);
                }
                scaleTransform.add("scale", scaleArray);
                
                transforms.add(scaleTransform);
                dataset.add("coordinateTransformations", transforms);
                
                datasets.add(dataset);
            }
            multiscale.add("datasets", datasets);
            
            multiscales.add(multiscale);
            
            // Write multiscales to root
            n5.setAttribute("/", "multiscales", multiscales);
            
            // Add OMERO metadata if provided
            if (additionalMetadata != null && additionalMetadata.containsKey("omero")) {
                JsonObject omero = (JsonObject) additionalMetadata.get("omero");
                n5.setAttribute("/", "omero", omero);
            }
        }
        
        /**
         * Create a downsampled pyramid from a source image.
         * This is similar to how BigStitcher-Spark creates resolution pyramids.
         */
        public static <T extends RealType<T> & NativeType<T>> 
        RandomAccessibleInterval<T>[] createPyramid(
            RandomAccessibleInterval<T> source,
            int numLevels,
            int[] downsamplingFactors
        ) {
            @SuppressWarnings("unchecked")
            RandomAccessibleInterval<T>[] pyramid = new RandomAccessibleInterval[numLevels];
            pyramid[0] = source;
            
            // Create downsampled levels
            RandomAccessibleInterval<T> current = source;
            for (int level = 1; level < numLevels; level++) {
                int factor = (int) Math.pow(2, level);
                
                // Downsample by averaging (simple approach)
                // For production use, consider using imglib2-algorithm's Gauss + subsample
                long[] downsampledDims = new long[current.numDimensions()];
                for (int d = 0; d < downsampledDims.length; d++) {
                    // Typically don't downsample the last two dimensions: time and channel
                    if (d >= (downsampledDims.length-2)) {
                        downsampledDims[d] = current.dimension(d);
                    } else {
                        downsampledDims[d] = current.dimension(d) / 2;
                    }
                }
                System.out.println("Level "+level+" pyramid size: "+downsampledDims);
                
                // Create downsampled image using simple subsampling
                // For better quality, use Gaussian blur before subsampling
                RandomAccessibleInterval<T> downsampled = 
                    subsample(current, downsampledDims);
                
                pyramid[level] = downsampled;
                current = downsampled;
            }
            
            return pyramid;
        }
        
        /**
         * Simple subsampling helper (for demonstration).
         * BigStitcher uses more sophisticated downsampling with Gaussian blur.
         */
        private static <T extends RealType<T> & NativeType<T>> 
        RandomAccessibleInterval<T> subsample(
            RandomAccessibleInterval<T> source,
            long[] targetDims
        ) {
            ArrayImgFactory<T> arrayImgFactory = new ArrayImgFactory<>();

            // Create target image -- DEPRECATED BUILDING OF NEW IMAGES
            net.imglib2.img.Img<T> target = arrayImgFactory.create(
                targetDims,
                source.randomAccess().get().createVariable()
            );
            
            // Simple subsampling by taking every other pixel
            Cursor<T> targetCursor = target.cursor();
            RandomAccess<T> sourceAccess = source.randomAccess();
            
            long[] sourcePos = new long[source.numDimensions()];
            while (targetCursor.hasNext()) {
                targetCursor.fwd();
                targetCursor.localize(sourcePos);
                
                // Scale position to source coordinates
                for (int d = 0; d < sourcePos.length-2; d++) {
                    sourcePos[d] *= 2;
                }
                
                sourceAccess.setPosition(sourcePos);
                targetCursor.get().set(sourceAccess.get());
            }
            
            return target;
        }
        
        public N5Writer getN5Writer() {
            return n5;
        }
    }
    
    /**
     * Example usage demonstrating BigStitcher-style OME-Zarr writing
     */
    public static void main(String[] args) {
        try {
            // Create example 5D data (t, c, z, y, x) - typical OME-Zarr format
            //long[] dimensions = {1, 3, 50, 512, 512}; // 1 timepoint, 3 channels, 50 z-slices
            long[] dimensions = {512,512,50, 3,1}; // "normal" order!!
            RandomAccessibleInterval<UnsignedShortType> sourceImage = 
                net.imglib2.img.array.ArrayImgs.unsignedShorts(dimensions);
            
            // Fill with example data
            Cursor<UnsignedShortType> cursor = Views.flatIterable(sourceImage).cursor();
            while (cursor.hasNext()) {
                cursor.fwd();
                cursor.get().set((short) (Math.random() * 65535));
            }
            
            // Example 1: Write to local filesystem
            String localPath = "/temp/output2.ome.zarr";
            OMEZarrWriter writer = new OMEZarrWriter(localPath, 3); // 3 resolution levels
            
            // Create multi-scale pyramid
            @SuppressWarnings("unchecked")
            RandomAccessibleInterval<UnsignedShortType>[] pyramid = 
                OMEZarrWriter.createPyramid(sourceImage, 3, new int[]{1, 2, 4});
            
            // Define block sizes for each level
            int[][] blockSizes = {
                {64, 64, 32, 1, 1},   // Level 0: highest resolution
                {64, 64, 32, 1, 1},   // Level 1: 2x downsampled
                {64, 64, 32, 1, 1}    // Level 2: 4x downsampled
            };
            
            // Define voxel sizes for each level (in micrometers)
            double[][] voxelSizes = {
                {0.5, 0.5, 1.0, 1.0, 1.0},     // Level 0
                {1.0, 1.0, 1.0, 1.0, 1.0},     // Level 1 (2x in x,y)
                {2.0, 2.0, 1.0, 1.0, 1.0}      // Level 2 (4x in x,y)
            };
            
            // Define axes following OME-NGFF convention
            String[] axisNames = {"t", "c", "z", "y", "x"};
            String[] axisTypes = {"time", "channel", "space", "space", "space"};
            String[] axisUnits = {null, null, "micrometer", "micrometer", "micrometer"};
            
            // Create OMERO metadata for visualization
            JsonObject omero = new JsonObject();
            JsonArray channels = new JsonArray();
            
            String[] channelNames = {"DAPI", "GFP", "RFP"};
            String[] channelColors = {"0000FF", "00FF00", "FF0000"};
            
            for (int i = 0; i < 3; i++) {
                JsonObject channel = new JsonObject();
                channel.addProperty("label", channelNames[i]);
                channel.addProperty("color", channelColors[i]);
                channel.addProperty("active", true);
                
                JsonObject window = new JsonObject();
                window.addProperty("start", 0);
                window.addProperty("end", 65535);
                window.addProperty("min", 0);
                window.addProperty("max", 65535);
                channel.add("window", window);
                
                channels.add(channel);
            }
            omero.add("channels", channels);
            
            // Additional metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("name", "ExampleDataset");
            metadata.put("omero", omero);
            
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
            
            System.out.println("Successfully written OME-Zarr to: " + localPath);

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
                "lz4",      // compressor: lz4, zstd, zlib
                5,          // compression level
                BloscCompression.SHUFFLE,  // shuffle mode
                0,          // blocksize (0 = auto)
                4           // number of threads
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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to write a simple 3D volume as OME-Zarr (without multi-scale).
     * Useful for quick exports.
     */
    public static <T extends RealType<T> & NativeType<T>> void writeSimple3D(
        RandomAccessibleInterval<T> volume,
        String zarrPath,
        int[] blockSize,
        double[] voxelSize,
        Compression compression
    ) throws IOException {
        
        // Expand to 5D: (1, 1, z, y, x)
        RandomAccessibleInterval<T> expanded = Views.addDimension(
            Views.addDimension(volume, 0, 0), 0, 0);
        
        OMEZarrWriter writer = new OMEZarrWriter(zarrPath, 1);
        
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<T>[] scales = new RandomAccessibleInterval[]{expanded};
        
        int[][] blockSizes = {
            {1, 1, blockSize[0], blockSize[1], blockSize[2]}
        };
        
        double[][] voxelSizes = {
            {1.0, 1.0, voxelSize[0], voxelSize[1], voxelSize[2]}
        };
        
        String[] axisNames = {"t", "c", "z", "y", "x"};
        String[] axisTypes = {"time", "channel", "space", "space", "space"};
        String[] axisUnits = {null, null, "micrometer", "micrometer", "micrometer"};
        
        writer.writeMultiscale(
            scales,
            blockSizes,
            voxelSizes,
            axisNames,
            axisTypes,
            axisUnits,
            compression,
            Collections.singletonMap("name", "volume")
        );
    }
}
