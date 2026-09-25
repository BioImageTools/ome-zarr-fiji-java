/*-
 * #%L
 * OME-Zarr reader based on N5
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ome.zarr.n5;

import java.lang.invoke.MethodHandles;
import java.net.URI;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.blosc.BloscCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser;
import org.janelia.saalfeldlab.n5.zarr.v3.ZarrV3DatasetAttributes;
import org.janelia.scicomp.n5.zstandard.ZstandardCompression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.exceptions.AlreadyOccupiedException;
import ome.zarr.imglib2.exceptions.StoreAccessException;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.write.OmeZarrWritingOptions;
import ome.zarr.imglib2.write.PyramidSaver;

/**
 * {@link PyramidSaver} implementation that writes OME-Zarr v0.5 (Zarr v3) to a
 * local or remote store using the N5-universe library.
 * <p>
 * Construct with a {@link URI} pointing to the container root, then call the
 * three-step protocol:
 * <ol>
 *   <li>{@link #initEmptyContainer()} — opens (or creates) the Zarr v3 store.</li>
 *   <li>{@link #initEmptyMultiscales} — writes OME-NGFF multiscales metadata and
 *       allocates empty datasets for each resolution level.</li>
 *   <li>{@link #writeRegion} — writes pixel data for one tile/region at one level.</li>
 * </ol>
 * <p>
 * Axis order: {@link PyramidContents} stores axes in imglib2 F-order (x=0, y=1, …).
 * {@link OmeNgffMetadataParser} with {@code reverse=true} (the default for Zarr)
 * reverses them to C-order when serialising the JSON, so no manual reversal is
 * required here.
 * <p>
 * Sharding ({@link OmeZarrWritingOptions#getShardShapePerLevel()}) is not yet
 * implemented; the shard shape field is silently ignored.
 */
public class N5PyramidSaver< T extends NativeType< T > & RealType< T > >
		implements PyramidSaver< T >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String OME_ZARR_VERSION = "0.5";

	private final URI uri;

	/* null until initEmptyContainer() */
	private N5Writer writer;

	/* dataset paths (from container root) per level; null until initEmptyMultiscales() */
	private String[] levelPaths;

	public N5PyramidSaver( final URI uri )
	{
		this.uri = uri;
	}

	/**
	 * Opens (or creates) the Zarr v3 store at the URI supplied at construction.
	 *
	 * @throws AlreadyOccupiedException if the store already contains OME-Zarr
	 *                                  multiscales metadata at its root
	 * @throws StoreAccessException     if the store cannot be opened or created
	 */
	@Override
	public void initEmptyContainer()
	{
		if ( writer != null )
			return;
		try
		{
			writer = new N5Factory().openWriter( StorageFormat.ZARR3, uri );
		}
		catch ( N5Exception e )
		{
			throw new StoreAccessException( "Cannot open Zarr v3 store at " + uri, e );
		}
		if ( hasOmeZarrContent( writer, "" ) )
			throw new AlreadyOccupiedException( uri.toString() );
	}

	/**
	 * Writes OME-Zarr multiscales metadata and creates empty Zarr v3 datasets
	 * for each resolution level under {@code path}.
	 * <p>
	 * Dataset paths are assigned as {@code path/0}, {@code path/1}, … and stored
	 * internally so that subsequent {@link #writeRegion} calls can find them.
	 * Calling this method again for a different {@code path} replaces the stored
	 * paths; {@link #writeRegion} then writes to the new group.
	 *
	 * @throws AlreadyOccupiedException if {@code path} already has multiscales metadata
	 * @throws StoreAccessException     on any storage error
	 */
	@Override
	public void initEmptyMultiscales(
			final String path,
			final PyramidContents< T > pc,
			final OmeZarrWritingOptions opts )
	{
		if ( writer == null )
			initEmptyContainer();

		final String groupPath = normalizeGroupPath( path );
		if ( hasOmeZarrContent( writer, groupPath ) )
			throw new AlreadyOccupiedException( path );

		final int nLevels = pc.numResolutionLevels();
		levelPaths = new String[ nLevels ];

		final int[][] chunkShapes = opts.getChunkShapePerLevel();
		final OmeZarrWritingOptions.Compression[] compressions = opts.getCompressionPerLevel();

		final String[] scalePaths = new String[ nLevels ];

		try
		{
			writer.createGroup( groupPath );
		}
		catch ( N5Exception e )
		{
			throw new StoreAccessException( "Cannot create group at " + groupPath, e );
		}

		for ( int level = 0; level < nLevels; level++ )
		{
			scalePaths[ level ] = String.valueOf( level );
			levelPaths[ level ] = groupPath.isEmpty() ? scalePaths[ level ] : groupPath + "/" + scalePaths[ level ];

			final Img< T > img = pc.asImg( level );
			final long[] dims = new long[ img.numDimensions() ];
			img.dimensions( dims );

			final int[] chunkShape = ( chunkShapes != null && chunkShapes[ level ] != null )
					? chunkShapes[ level ]
					: defaultChunkShape( dims );

			final Compression n5Compression = ( compressions != null && compressions[ level ] != null )
					? toN5Compression( compressions[ level ] )
					: new RawCompression();

			final ZarrV3DatasetAttributes attrs = ZarrV3DatasetAttributes
					.builder( dims, N5Utils.dataType( pc.type ) )
					.blockSize( chunkShape )
					.compression( n5Compression )
					.fillValue( fillValueString( opts.getFillValue() ) )
					.build();

			try
			{
				writer.createDataset( levelPaths[ level ], attrs );
			}
			catch ( N5Exception e )
			{
				throw new StoreAccessException( "Cannot create dataset at " + levelPaths[ level ], e );
			}
		}

		/*
		 * Axes are in imglib2 F-order (from PyramidContents); OmeNgffMetadataParser(true)
		 * reverses both axes and coordinate-transform values to C-order on serialisation.
		 */
		final AxisCalibration[] axes0 = pc.axesPerLevel[ 0 ];
		final Axis[] axes = new Axis[ axes0.length ];
		for ( int d = 0; d < axes0.length; d++ )
			axes[ d ] = toN5Axis( axes0[ d ] );

		final double[][] scales = new double[ nLevels ][];
		for ( int level = 0; level < nLevels; level++ )
		{
			final AxisCalibration[] ac = pc.axesPerLevel[ level ];
			scales[ level ] = new double[ ac.length ];
			for ( int d = 0; d < ac.length; d++ )
				scales[ level ][ d ] = ac[ d ].scale;
		}

		final OmeNgffMetadata metadata = OmeNgffMetadata.buildForWriting(
				axes0.length,
				pc.name != null ? pc.name : "",
				OME_ZARR_VERSION,
				axes,
				scalePaths,
				scales,
				null );

		try
		{
			new OmeNgffMetadataParser( true ).writeMetadata( metadata, writer, groupPath );
		}
		catch ( Exception e )
		{
			throw new StoreAccessException( "Failed to write OME-Zarr metadata at " + groupPath, e );
		}

		logger.info( "N5PyramidSaver: wrote multiscales skeleton at '{}', {} levels.", groupPath, nLevels );
	}

	/**
	 * Writes the pixels of {@code region} into the dataset at the given
	 * resolution {@code level}. The region's interval coordinates determine
	 * placement within the full array.
	 *
	 * @throws IllegalStateException if {@link #initEmptyMultiscales} has not been called
	 * @throws StoreAccessException  on any storage error
	 */
	@Override
	public void writeRegion( final RandomAccessibleInterval< T > region, final int level )
	{
		if ( writer == null || levelPaths == null )
			throw new IllegalStateException( "initEmptyMultiscales() must be called before writeRegion()." );
		N5Utils.saveRegion( region, writer, levelPaths[ level ] );
	}

	// ---- private helpers --------------------------------------------------------

	private static boolean hasOmeZarrContent( final N5Writer n5, final String path )
	{
		try
		{
			if ( n5.getAttribute( path, "multiscales", JsonElement.class ) != null )
				return true;
		}
		catch ( Exception ignored ) {}
		try
		{
			if ( n5.getAttribute( path, "ome/multiscales", JsonElement.class ) != null )
				return true;
		}
		catch ( Exception ignored ) {}
		return false;
	}

	/* Strips a leading "/" so N5 path conventions are satisfied. */
	private static String normalizeGroupPath( final String path )
	{
		if ( path == null || path.equals( "/" ) )
			return "";
		return path.startsWith( "/" ) ? path.substring( 1 ) : path;
	}

	private static int[] defaultChunkShape( final long[] dims )
	{
		final int[] chunk = new int[ dims.length ];
		for ( int d = 0; d < dims.length; d++ )
			chunk[ d ] = ( int ) Math.min( dims[ d ], 64 );
		return chunk;
	}

	private static String fillValueString( final Object fillValue )
	{
		return fillValue == null ? "0" : String.valueOf( fillValue );
	}

	private static Axis toN5Axis( final AxisCalibration ac )
	{
		return new Axis( axisType( ac.name ), ac.name, ac.unit.isEmpty() ? null : ac.unit );
	}

	private static String axisType( final String name )
	{
		switch ( name )
		{
		case AxisCalibration.X: case AxisCalibration.Y: case AxisCalibration.Z:
			return Axis.SPACE;
		case AxisCalibration.C:
			return Axis.CHANNEL;
		case AxisCalibration.T:
			return Axis.TIME;
		default:
			return Axis.ARRAY;
		}
	}

	private static Compression toN5Compression( final OmeZarrWritingOptions.Compression compression )
	{
		if ( compression instanceof OmeZarrWritingOptions.GzipCompression )
		{
			return new GzipCompression( ( ( OmeZarrWritingOptions.GzipCompression ) compression ).level );
		}
		else if ( compression instanceof OmeZarrWritingOptions.ZstdCompression )
		{
			return new ZstandardCompression( ( ( OmeZarrWritingOptions.ZstdCompression ) compression ).level );
		}
		else if ( compression instanceof OmeZarrWritingOptions.BloscCompression )
		{
			final OmeZarrWritingOptions.BloscCompression b = ( OmeZarrWritingOptions.BloscCompression ) compression;
			return new BloscCompression( b.cname, b.clevel, shuffleInt( b.shuffle ), b.blocksize, 0 );
		}
		return new RawCompression();
	}

	private static int shuffleInt( final String shuffle )
	{
		if ( "shuffle".equals( shuffle ) )    return BloscCompression.SHUFFLE;
		if ( "bitshuffle".equals( shuffle ) ) return BloscCompression.BITSHUFFLE;
		return BloscCompression.NOSHUFFLE;
	}
}
