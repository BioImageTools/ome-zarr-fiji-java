package ome.zarr.imglib2.write;

import net.imglib2.img.Img;
import ome.zarr.imglib2.PyramidContents;

/**
 * Writing-time parameters for OME-Zarr output, to work with {@link PyramidSaver}s.
 * <p>
 * Covers only what {@link PyramidContents} cannot supply. The following are
 * already derivable from {@code PyramidContents} and are therefore absent here:
 * axis count, names, types and units (from {@code AxisCalibration}); array shape
 * and data type (from {@code CachedCellImg} and {@code PyramidContents.type});
 * scale and translation transforms (stored per level); number of resolution levels;
 * and OMERO display metadata.
 * <p>
 * Zarr format v3, chunk key separator {@code "/"}, and memory order {@code "F"}
 * are fixed. Single-file OME-Zarr is intentionally unsupported: archives cannot be
 * created progressively, which is the central theme of this package.
 * <p>
 * Instances are immutable. Setters return a new instance with one field changed,
 * enabling fluent chaining:
 * <pre>
 *   OmeZarrWritingOptions opts = OmeZarrWritingOptions.defaultOptionsFor( pc )
 *       .setChunkShape( new int[]{ 1, 1, 64, 64, 64 } )
 *       .setCompression( new OmeZarrWritingOptions.ZstdCompression( 3 ) );
 * </pre>
 */
public class OmeZarrWritingOptions
{
	/* stored to allow uniform-across-levels setters to expand a single shape */
	private final int nLevels;

	// --- Array storage ---
	private final Object fillValue;

	// --- Chunking (per level; auto-computed from array shape by default) ---
	private final int[][] chunkShapePerLevel;

	// --- Sharding (Zarr v3 only; null = no sharding) ---
	private final int[][] shardShapePerLevel;

	private final int[][] innerChunkShapePerLevel;

	private final String shardIndexLocation;

	/*
	 * Codec pipeline per level. Null entry means no compression for that level.
	 * In Zarr v3 byte order (endianness) is part of the codec pipeline ("bytes" codec);
	 * implementations should emit it ahead of any compression codec.
	 */
	private final Compression[] compressionPerLevel;

	// --- Optional OME-Zarr multiscales annotations (all default to null) ---
	private final String multiscaleName;

	private final String downscalingType;

	private final Object downscalingMetadata;

	private OmeZarrWritingOptions(
			final int nLevels,
			final Object fillValue,
			final int[][] chunkShapePerLevel,
			final int[][] shardShapePerLevel,
			final int[][] innerChunkShapePerLevel,
			final String shardIndexLocation,
			final Compression[] compressionPerLevel,
			final String multiscaleName,
			final String downscalingType,
			final Object downscalingMetadata )
	{
		this.nLevels = nLevels;
		this.fillValue = fillValue;
		this.chunkShapePerLevel = chunkShapePerLevel;
		this.shardShapePerLevel = shardShapePerLevel;
		this.innerChunkShapePerLevel = innerChunkShapePerLevel;
		this.shardIndexLocation = shardIndexLocation;
		this.compressionPerLevel = compressionPerLevel;
		this.multiscaleName = multiscaleName;
		this.downscalingType = downscalingType;
		this.downscalingMetadata = downscalingMetadata;
	}

	/**
	 * Creates options with defaults derived from the given {@link PyramidContents}.
	 * Chunk shapes are auto-computed from the dimensions at each resolution level
	 * (capped at 64 per axis).
	 */
	public static OmeZarrWritingOptions defaultOptionsFor( final PyramidContents< ? > pc )
	{
		return new OmeZarrWritingOptions(
				pc.numResolutionLevels(),
				0,
				defaultChunkShapes( pc ),
				null,
				null,
				"end",
				null,
				null,
				null,
				null );
	}

	// ---- Getters (defensive copies for array fields) ----------------------------

	public Object getFillValue()
	{
		return fillValue;
	}

	public int[][] getChunkShapePerLevel()
	{
		return deepCopy( chunkShapePerLevel );
	}

	public int[][] getShardShapePerLevel()
	{
		return deepCopy( shardShapePerLevel );
	}

	public int[][] getInnerChunkShapePerLevel()
	{
		return deepCopy( innerChunkShapePerLevel );
	}

	public String getShardIndexLocation()
	{
		return shardIndexLocation;
	}

	public Compression[] getCompressionPerLevel()
	{
		return compressionPerLevel == null ? null : compressionPerLevel.clone();
	}

	public String getMultiscaleName()
	{
		return multiscaleName;
	}

	public String getDownscalingType()
	{
		return downscalingType;
	}

	public Object getDownscalingMetadata()
	{
		return downscalingMetadata;
	}

	// ---- Chaining setters (each returns a new instance) -------------------------

	public OmeZarrWritingOptions setFillValue( final Object value )
	{
		return new OmeZarrWritingOptions( nLevels, value, chunkShapePerLevel,
				shardShapePerLevel, innerChunkShapePerLevel, shardIndexLocation,
				compressionPerLevel, multiscaleName, downscalingType, downscalingMetadata );
	}

	/** Applies the same chunk shape to every resolution level. */
	public OmeZarrWritingOptions setChunkShape( final int[] shape )
	{
		return setChunkShapePerLevel( uniformPerLevel( shape, nLevels ) );
	}

	public OmeZarrWritingOptions setChunkShapePerLevel( final int[][] shapes )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, shapes,
				shardShapePerLevel, innerChunkShapePerLevel, shardIndexLocation,
				compressionPerLevel, multiscaleName, downscalingType, downscalingMetadata );
	}

	/** Applies the same shard shape to every resolution level. */
	public OmeZarrWritingOptions setShardShape( final int[] shape )
	{
		return setShardShapePerLevel( uniformPerLevel( shape, nLevels ) );
	}

	public OmeZarrWritingOptions setShardShapePerLevel( final int[][] shapes )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shapes, innerChunkShapePerLevel, shardIndexLocation,
				compressionPerLevel, multiscaleName, downscalingType, downscalingMetadata );
	}

	/** Applies the same inner chunk shape (inside shards) to every resolution level. */
	public OmeZarrWritingOptions setInnerChunkShape( final int[] shape )
	{
		return setInnerChunkShapePerLevel( uniformPerLevel( shape, nLevels ) );
	}

	public OmeZarrWritingOptions setInnerChunkShapePerLevel( final int[][] shapes )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shardShapePerLevel, shapes, shardIndexLocation,
				compressionPerLevel, multiscaleName, downscalingType, downscalingMetadata );
	}

	/** @param location {@code "start"} or {@code "end"} (default) */
	public OmeZarrWritingOptions setShardIndexLocation( final String location )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shardShapePerLevel, innerChunkShapePerLevel, location,
				compressionPerLevel, multiscaleName, downscalingType, downscalingMetadata );
	}

	/** Applies the same compression codec to every resolution level. */
	public OmeZarrWritingOptions setCompression( final Compression c )
	{
		return setCompressionPerLevel( uniformPerLevel( c, nLevels ) );
	}

	public OmeZarrWritingOptions setCompressionPerLevel( final Compression[] c )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shardShapePerLevel, innerChunkShapePerLevel, shardIndexLocation,
				c, multiscaleName, downscalingType, downscalingMetadata );
	}

	public OmeZarrWritingOptions setMultiscaleName( final String name )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shardShapePerLevel, innerChunkShapePerLevel, shardIndexLocation,
				compressionPerLevel, name, downscalingType, downscalingMetadata );
	}

	/** @param type e.g. {@code "gaussian"}; stored in {@code multiscales[].type} */
	public OmeZarrWritingOptions setDownscalingType( final String type )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shardShapePerLevel, innerChunkShapePerLevel, shardIndexLocation,
				compressionPerLevel, multiscaleName, type, downscalingMetadata );
	}

	/** @param metadata arbitrary object stored in {@code multiscales[].metadata} */
	public OmeZarrWritingOptions setDownscalingMetadata( final Object metadata )
	{
		return new OmeZarrWritingOptions( nLevels, fillValue, chunkShapePerLevel,
				shardShapePerLevel, innerChunkShapePerLevel, shardIndexLocation,
				compressionPerLevel, multiscaleName, downscalingType, metadata );
	}

	// ---- Compression marker interface and standard implementations ---------------

	/**
	 * Marker interface for chunk compression configuration. Backend implementations
	 * of {@link PyramidSaver} receive these and convert to their native codec types.
	 */
	public interface Compression
	{}

	public static final class GzipCompression implements Compression
	{
		/** Compression level 1 (fastest) to 9 (best ratio). */
		public final int level;

		public GzipCompression( final int level )
		{
			this.level = level;
		}
	}

	public static final class ZstdCompression implements Compression
	{
		/** Compression level: negative = faster, higher positive = better ratio. */
		public final int level;

		public ZstdCompression( final int level )
		{
			this.level = level;
		}
	}

	public static final class BloscCompression implements Compression
	{
		/** Codec name: {@code "lz4"}, {@code "zstd"}, {@code "zlib"}, {@code "blosclz"}, {@code "lz4hc"}, {@code "snappy"}. */
		public final String cname;

		/** Compression level 0 (no compression) to 9 (best ratio). */
		public final int clevel;

		/** Shuffle filter: {@code "noshuffle"}, {@code "shuffle"}, {@code "bitshuffle"}. */
		public final String shuffle;

		/** Internal block size in bytes; 0 = automatic. */
		public final int blocksize;

		public BloscCompression( final String cname, final int clevel,
				final String shuffle, final int blocksize )
		{
			this.cname = cname;
			this.clevel = clevel;
			this.shuffle = shuffle;
			this.blocksize = blocksize;
		}
	}

	/** CRC32C checksum with no compression. */
	public static final class Crc32cCompression implements Compression
	{}

	// ---- Private helpers --------------------------------------------------------

	private static int[][] defaultChunkShapes( final PyramidContents< ? > pc )
	{
		final int nLevels = pc.numResolutionLevels();
		final int[][] shapes = new int[ nLevels ][];
		for ( int level = 0; level < nLevels; level++ )
		{
			final Img< ? > img = pc.asImg( level );
			final int n = img.numDimensions();
			final int[] chunk = new int[ n ];
			for ( int d = 0; d < n; d++ )
				chunk[ d ] = ( int ) Math.min( img.dimension( d ), 64 );
			shapes[ level ] = chunk;
		}
		return shapes;
	}

	private static int[][] deepCopy( final int[][] src )
	{
		if ( src == null )
			return null;
		final int[][] copy = new int[ src.length ][];
		for ( int i = 0; i < src.length; i++ )
			copy[ i ] = src[ i ] == null ? null : src[ i ].clone();
		return copy;
	}

	private static int[][] uniformPerLevel( final int[] shape, final int nLevels )
	{
		final int[][] shapes = new int[ nLevels ][];
		for ( int i = 0; i < nLevels; i++ )
			shapes[ i ] = shape.clone();
		return shapes;
	}

	private static Compression[] uniformPerLevel( final Compression c, final int nLevels )
	{
		final Compression[] cs = new Compression[ nLevels ];
		for ( int i = 0; i < nLevels; i++ )
			cs[ i ] = c;
		return cs;
	}
}
