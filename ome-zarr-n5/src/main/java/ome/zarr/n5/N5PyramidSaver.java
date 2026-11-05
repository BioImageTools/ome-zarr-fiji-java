package ome.zarr.n5;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.exceptions.AlreadyOccupiedException;
import ome.zarr.imglib2.exceptions.NotAMultiscaleImageException;
import ome.zarr.imglib2.exceptions.StoreAccessException;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.write.OmeZarrWritingOptions;
import ome.zarr.imglib2.write.PyramidSaver;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * {@link PyramidSaver} writing OME-Zarr v0.5 (Zarr v3) through N5.
 * <p>
 * Preliminary: compression, sharding and fill value from {@link OmeZarrWritingOptions}
 * are not honored yet (chunks are written raw).
 * <p>
 * One instance targets one multiscales group at a time: {@link #writeRegion} has no
 * group parameter, so it writes into the group of the most recent
 * {@link #createMultiscales} or {@link #openMultiscales} call. That's the idea put
 * forward in the implemented {@code PyramidSaver} interface.
 */
public class N5PyramidSaver< T extends RealType< T > & NativeType< T > > implements PyramidSaver< T >
{
	private static final String OME_ZARR_VERSION = "0.5";

	/** Axis names in the row order of {@link PyramidContents#transform(int)}. */
	private static final List< String > SPATIAL_AXES = Arrays.asList( AxisCalibration.X, AxisCalibration.Y, AxisCalibration.Z );

	private final URI uri;

	/** Opened by the first {@link #createMultiscales} or {@link #openMultiscales}, so that nothing touches the store before. */
	private N5Writer n5w;

	/** Per-level dataset paths and attributes of the bound multiscales group; set by {@link #createMultiscales} or {@link #openMultiscales}. */
	private String[] levelPaths;

	private DatasetAttributes[] levelAttributes;

	public N5PyramidSaver( final URI uri )
	{
		this.uri = uri;
	}

	/**
	 * Reads the level paths from the group's OME-Zarr v0.5 {@code ome/multiscales}
	 * metadata (the first multiscale, as the readers do) and the chunk layout from each
	 * level's array. Only Zarr v3 / OME-Zarr v0.5 groups are understood, i.e. what
	 * {@link #createMultiscales} writes.
	 * <p>
	 * Opening the writer creates an empty store root if there is none at the URI yet;
	 * the call then fails with {@link NotAMultiscaleImageException} anyway.
	 */
	@Override
	public void openMultiscales( final String groupPath, final T type )
	{
		final String group = normalizeGroupPath( groupPath );
		writer();
		final String[] paths;
		final DatasetAttributes[] attributes;
		try
		{
			final JsonArray multiscales = n5w.exists( group )
					? n5w.getAttribute( group, "ome/multiscales", JsonArray.class )
					: null;
			if ( multiscales == null || multiscales.size() == 0 )
				throw new NotAMultiscaleImageException( uri + "/" + group );
			final JsonArray datasets = multiscales.get( 0 ).getAsJsonObject().getAsJsonArray( "datasets" );
			if ( datasets == null || datasets.size() == 0 )
				throw new NotAMultiscaleImageException( uri + "/" + group );

			paths = new String[ datasets.size() ];
			attributes = new DatasetAttributes[ datasets.size() ];
			for ( int level = 0; level < paths.length; level++ )
			{
				final String scalePath = datasets.get( level ).getAsJsonObject().get( "path" ).getAsString();
				paths[ level ] = group.isEmpty() ? scalePath : group + "/" + scalePath;
				attributes[ level ] = n5w.getDatasetAttributes( paths[ level ] );
				if ( attributes[ level ] == null )
					throw new NotAMultiscaleImageException( uri + "/" + paths[ level ] );
			}
		}
		catch ( final N5Exception e )
		{
			throw new StoreAccessException( uri + "/" + group, e );
		}
		catch ( final IllegalStateException | ClassCastException | NullPointerException e )
		{
			// malformed multiscales JSON: not an object, or a dataset without "path"
			throw new NotAMultiscaleImageException( uri + "/" + group, e );
		}

		final DataType requested = N5Utils.dataType( type );
		for ( int level = 0; level < paths.length; level++ )
			if ( attributes[ level ].getDataType() != requested )
				throw new IllegalArgumentException( "Level " + level + " of " + uri + "/" + group + " stores "
						+ attributes[ level ].getDataType() + ", not the requested " + requested + "." );

		levelPaths = paths;
		levelAttributes = attributes;
	}

	/**
	 * Creates the group {@code groupPath} with one array per resolution level, named
	 * {@code 0}, {@code 1}, … as OME-Zarr expects, and writes the OME-Zarr v0.5
	 * {@code multiscales} metadata (axes, per-level scales and translations) onto the group.
	 * A level's translation is written only when it is non-zero.
	 * Chunk shapes come from {@code opts}, falling back to
	 * {@link OmeZarrWritingOptions#defaultOptionsFor(PyramidContents)} for levels it
	 * leaves unset. The store is created if it does not exist yet; other groups
	 * already in it are left alone.
	 *
	 * @throws AlreadyOccupiedException if {@code groupPath} already holds OME metadata or any child node
	 */
	@Override
	public void createMultiscales( final String groupPath,
			final PyramidContents< T > pc,
			final OmeZarrWritingOptions opts )
	{
		final String group = normalizeGroupPath( groupPath );
		writer();
		if ( isOccupied( group ) )
			throw new AlreadyOccupiedException( groupPath );

		final int nLevels = pc.numResolutionLevels();
		final int[][] chunkShapes = opts.getChunkShapePerLevel();
		final int[][] defaultChunkShapes = OmeZarrWritingOptions.defaultOptionsFor( pc ).getChunkShapePerLevel();

		final String[] scalePaths = new String[ nLevels ];
		final String[] paths = new String[ nLevels ];
		final DatasetAttributes[] attributes = new DatasetAttributes[ nLevels ];
		final double[][] scales = new double[ nLevels ][];
		final double[][] translations = new double[ nLevels ][];
		try
		{
			n5w.createGroup( group );
			for ( int level = 0; level < nLevels; level++ )
			{
				scalePaths[ level ] = String.valueOf( level );
				paths[ level ] = group.isEmpty() ? scalePaths[ level ] : group + "/" + scalePaths[ level ];
				final int[] chunkShape = chunkShapes != null && chunkShapes[ level ] != null
						? chunkShapes[ level ]
						: defaultChunkShapes[ level ];
				attributes[ level ] = n5w.createDataset( paths[ level ],
						pc.asImg( level ).dimensionsAsLongArray(),
						chunkShape,
						N5Utils.dataType( pc.type() ),
						new RawCompression() );
				scales[ level ] = pc.axes( level ).stream().mapToDouble( a -> a.scale ).toArray();
				translations[ level ] = translation( pc, level );
			}

			// F-order (x first) as in PyramidContents; the parser reverses to Zarr's C-order
			final List< AxisCalibration > axes0 = pc.axes( 0 );
			final Axis[] axes = axes0.stream()
					.map( a -> new Axis( axisType( a.name ), a.name, a.unit.isEmpty() ? null : a.unit ) )
					.toArray( Axis[]::new );
			final OmeNgffMetadata metadata = OmeNgffMetadata.buildForWriting( axes.length,
					pc.name() != null ? pc.name() : "", OME_ZARR_VERSION, axes, scalePaths, scales, translations );
			new OmeNgffMetadataParser( true ).writeMetadata( metadata, n5w, group );
		}
		catch ( final Exception e ) // writeMetadata() declares a bare Exception
		{
			throw new StoreAccessException( uri + "/" + group, e );
		}

		levelPaths = paths;
		levelAttributes = attributes;
	}

	/**
	 * Writes {@code region} at its own coordinates into level {@code level}; chunks
	 * only partially covered by it are merged with what is already stored.
	 *
	 * @throws IllegalStateException if neither {@link #createMultiscales} nor {@link #openMultiscales} has been called
	 * @throws IllegalArgumentException if {@code level} does not exist or {@code region}
	 *         reaches outside that level's array (N5 would silently grow the array)
	 */
	@Override
	public void writeRegion( final RandomAccessibleInterval< T > region,
			final int level )
	{
		if ( levelPaths == null )
			throw new IllegalStateException( "createMultiscales() or openMultiscales() must be called before writeRegion()." );
		if ( level < 0 || level >= levelPaths.length )
			throw new IllegalArgumentException( "No resolution level " + level + ", there are " + levelPaths.length + "." );
		final Interval levelInterval = new FinalInterval( levelAttributes[ level ].getDimensions() );
		if ( region.numDimensions() != levelInterval.numDimensions() || !Intervals.contains( levelInterval, region ) )
			throw new IllegalArgumentException( "Region " + Intervals.toString( region )
					+ " lies outside level " + level + " " + Intervals.toString( levelInterval ) + "." );

		try
		{
			N5Utils.saveRegion( region, n5w, levelPaths[ level ], levelAttributes[ level ] );
		}
		catch ( final N5Exception e )
		{
			throw new StoreAccessException( uri + "/" + levelPaths[ level ], e );
		}
	}

	/**
	 * Per-axis translation of {@code level}, or {@code null} if it is zero everywhere.
	 * {@link PyramidContents} keeps the origin offset only in its spatial
	 * {@link PyramidContents#transform(int) transform} (rows x, y, z), so the
	 * {@code x}/{@code y}/{@code z} axes take it from there and every other axis gets 0.
	 */
	private static double[] translation( final PyramidContents< ? > pc, final int level )
	{
		final AffineTransform3D transform = pc.transform( level );
		final List< AxisCalibration > axes = pc.axes( level );
		final double[] translation = new double[ axes.size() ];
		boolean nonZero = false;
		for ( int d = 0; d < translation.length; d++ )
		{
			final int row = SPATIAL_AXES.indexOf( axes.get( d ).name );
			if ( row >= 0 )
				translation[ d ] = transform.get( row, 3 );
			nonZero |= translation[ d ] != 0;
		}
		return nonZero ? translation : null;
	}

	/**
	 * Opens (creating if needed) the Zarr v3 store at the construction-time URI, once.
	 *
	 * @throws StoreAccessException if the store cannot be opened or created
	 */
	private N5Writer writer()
	{
		if ( n5w == null )
		{
			try
			{
				n5w = new N5Factory().openWriter( StorageFormat.ZARR3, uri );
			}
			catch ( final N5Exception e )
			{
				throw new StoreAccessException( uri.toString(), e );
			}
		}
		return n5w;
	}

	/** A node is occupied when it carries OME metadata or has any children. */
	private boolean isOccupied( final String path )
	{
		try
		{
			if ( !n5w.exists( path ) )
				return false;
			return n5w.getAttribute( path, "ome", JsonElement.class ) != null
					|| n5w.list( path ).length > 0;
		}
		catch ( final N5Exception e )
		{
			throw new StoreAccessException( uri + "/" + path, e );
		}
	}

	/** N5 addresses the root as {@code ""}, the interface allows {@code "/"} and a leading slash. */
	private static String normalizeGroupPath( final String path )
	{
		if ( path == null )
			return "";
		String p = path;
		while ( p.startsWith( "/" ) )
			p = p.substring( 1 );
		while ( p.endsWith( "/" ) )
			p = p.substring( 0, p.length() - 1 );
		return p;
	}

	private static String axisType( final String name )
	{
		switch ( name )
		{
		case AxisCalibration.X:
		case AxisCalibration.Y:
		case AxisCalibration.Z:
			return Axis.SPACE;
		case AxisCalibration.C:
			return Axis.CHANNEL;
		case AxisCalibration.T:
			return Axis.TIME;
		default:
			return null; // OME-Zarr allows axes without a type
		}
	}

	// ======================================================================
	// ======================================================================
	// ======================================================================
	// ======================================================================

	public static < T extends NativeType< T > > void writeFullImmediately(
			final RandomAccessibleInterval< T > img,
			final String containerPath,
			final String dsetSubPath )
	{
		final long[] dimensions = img.dimensionsAsLongArray();
		final int[] blockSize = new int[ dimensions.length ];
		for ( int i = 0; i < blockSize.length; i++ )
			blockSize[ i ] = Math.min( 128, ( int ) dimensions[ i ] );

		final N5Writer n5w = N5Factory.createWriter( containerPath );
		final DatasetAttributes attributes = n5w.createDataset( dsetSubPath, dimensions, blockSize,
				N5Utils.dataType( img.firstElement() ), new RawCompression() );

		N5Utils.saveRegion( img, n5w, dsetSubPath, attributes );

		// the region of the image covered by each block
		//final CellGrid grid = new CellGrid( dimensions, blockSize );
	}
}
