package sc.fiji.ome.zarr.pyramid.backend.zarrjava;

import dev.zarr.zarrjava.core.Array;
import net.imglib2.Cursor;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * An imglib2 {@link CellLoader} backed by a zarr-java {@link Array}.
 *
 * <p>Zarr arrays use C-order (row-major, last axis fastest) while imglib2 uses
 * Fortran-order (column-major, first axis fastest). The two conventions share the
 * same flat-array layout when dimensions are reversed, so this loader simply
 * reverses the imglib2 cell offset and shape before calling
 * {@link Array#read(long[], long[])} and then copies the values element-wise via
 * an {@link ucar.ma2.IndexIterator}, which correctly interprets unsigned types.
 *
 * @param <T> the imglib2 pixel type
 */
public class ZarrJavaCellLoader< T extends NativeType< T > & RealType< T > > implements CellLoader< T >
{
	private final Array zarrArray;

	public ZarrJavaCellLoader( final Array zarrArray )
	{
		this.zarrArray = zarrArray;
	}

	@Override
	public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
	{
		final int n = cell.numDimensions();

		// imglib2 cell min and dims are in F-order [x, y, z, ...]
		final long[] imgMin = new long[ n ];
		cell.min( imgMin );
		final long[] imgDims = new long[ n ];
		cell.dimensions( imgDims );

		// Reverse to zarr C-order [..., z, y, x]
		final long[] zarrOffset = new long[ n ];
		final long[] zarrShape = new long[ n ];
		for ( int i = 0; i < n; i++ )
		{
			zarrOffset[ i ] = imgMin[ n - 1 - i ];
			zarrShape[ i ] = imgDims[ n - 1 - i ];
		}

		final ucar.ma2.Array data = zarrArray.read( zarrOffset, zarrShape );
		final ucar.ma2.IndexIterator it = data.getIndexIterator();
		final Cursor< T > cursor = cell.cursor();

		// ucar.ma2.IndexIterator.getDoubleNext() correctly handles unsigned types
		// (e.g. UBYTE returns [0, 255], not [-128, 127])
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( it.getDoubleNext() );
		}
	}
}
