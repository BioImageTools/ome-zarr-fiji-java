/*-
 * #%L
 * OME-Zarr reader based on zarr-java
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
package ome.zarr.zarrjava;

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
