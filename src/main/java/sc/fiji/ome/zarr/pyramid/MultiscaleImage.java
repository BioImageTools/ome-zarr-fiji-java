/*-
 * #%L
 * Expose the Imaris XT interface as an ImageJ2 service backed by ImgLib2.
 * %%
 * Copyright (C) 2019 - 2021 Bitplane AG
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
package sc.fiji.ome.zarr.pyramid;

import static sc.fiji.ome.zarr.pyramid.Multiscales.MULTI_SCALE_KEY;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import bdv.cache.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;

public class MultiscaleImage<
		  T extends NativeType< T > & RealType< T >,
		  V extends Volatile< T > & NativeType< V > & RealType< V > >
{
	private final String multiscalePath;

	private final SharedQueue queue;

	private int numResolutions;

	private long[] dimensions;

	private T type;

	private V volatileType;

	private CachedCellImg< T, ? >[] imgs;

	private RandomAccessibleInterval< V >[] vimgs;

	private Multiscales multiscales;

	private int multiscaleArrayIndex = 0; // TODO (see comments within code)

	/**
	 * TODO
	 */
	public MultiscaleImage( final String multiscalePath )
	{
		this.multiscalePath = multiscalePath;
		this.queue = null;
	}

	private void init()
	{
		if ( imgs != null ) return;

		//TODO: re-use the code from N5Importer and fill local attributes from that


		try
		{
			// FIXME support S3
			final N5ZarrReader n5ZarrReader = new N5ZarrReader( multiscalePath );

			// Fetch metadata
			//
			Multiscales[] multiscalesArray = n5ZarrReader.getAttribute( "", MULTI_SCALE_KEY, Multiscales[].class );

			// In principle the call above would be sufficient.
			// However since we need to support different
			// versions of OME-Zarrr we need to "manually"
			// fix some fields.
			// Thus, we parse the same JSON again and fill in missing
			// information.
			// TODO: could we do this by means of a JsonDeserializer?

			// final JsonArray multiscalesJsonArray = n5ZarrReader.getAttributes( "" ).get( MULTI_SCALE_KEY ).getAsJsonArray();
			final JsonElement jsonElement = n5ZarrReader.getAttributes( "" );
			final JsonArray multiscalesJsonArray = jsonElement.getAsJsonObject().getAsJsonArray( MULTI_SCALE_KEY );

			for ( int i = 0; i < multiscalesArray.length; i++ )
			{
				multiscalesArray[ i ].applyVersionFixes( multiscalesJsonArray.get( i ).getAsJsonObject() );
				multiscalesArray[ i ].init();
			}

			// TODO
			//   From the spec:
			//   "If only one multiscale is provided, use it.
			//   Otherwise, the user can choose by name,
			//   using the first multiscale as a fallback"
			//   Right now, we always only use the first one.
			//   One option would be to add the {@code multiscaleArrayIndex}
			//   array index as a parameter to the constructor
			multiscales = multiscalesArray[ multiscaleArrayIndex ];

			// Here, datasets are single resolution N-D Images.
			// Each dataset represents one resolution layer.
			final Multiscales.Dataset[] datasets = multiscales.getDatasets();
			numResolutions = datasets.length;

			// Set the dimensions and data type
			// from the highest resolution dataset's
			// metadata.
			final DatasetAttributes attributes = n5ZarrReader.getDatasetAttributes( datasets[ 0 ].path );
			dimensions = attributes.getDimensions();
			initTypes( attributes.getDataType() );

			// Initialize the images for all resolutions.
			//
			// TODO only on demand
			imgs = new CachedCellImg[ numResolutions ];
			vimgs = new RandomAccessibleInterval[ numResolutions ];

			for ( int resolution = 0; resolution < numResolutions; ++resolution )
			{
				imgs[ resolution ] = N5Utils.openVolatile( n5ZarrReader, datasets[ resolution ].path );

				if ( queue == null )
					vimgs[ resolution ] = VolatileViews.wrapAsVolatile( imgs[ resolution ] );
				else
					vimgs[ resolution ] = VolatileViews.wrapAsVolatile( imgs[ resolution ], queue );
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}

	}

	private void initTypes( DataType dataType )
	{
		if ( type != null ) return;

		// TODO JOHN: Does the below code already exists
		//   somewhere in N5?
		switch ( dataType ) {
			case UINT8:
				type = Cast.unchecked( new UnsignedByteType() );
				break;
			case UINT16:
				type = Cast.unchecked( new UnsignedShortType() );
				break;
			case UINT32:
				type = Cast.unchecked( new UnsignedIntType() );
				break;
			case UINT64:
				type = Cast.unchecked( new UnsignedLongType() );
				break;
			case INT8:
				type = Cast.unchecked( new ByteType() );
				break;
			case INT16:
				type = Cast.unchecked( new ShortType() );
				break;
			case INT32:
				type = Cast.unchecked( new IntType() );
				break;
			case INT64:
				type = Cast.unchecked( new LongType() );
				break;
			case FLOAT32:
				type = Cast.unchecked( new FloatType() );
				break;
			case FLOAT64:
				type = Cast.unchecked( new DoubleType() );
				break;
		}

		volatileType = ( V ) VolatileTypeMatcher.getVolatileTypeForType( type );
	}

	public Multiscales getMultiscales()
	{
		return multiscales;
	}

	public long[] dimensions()
	{
		init();
		return dimensions;
	}

	public int numResolutions()
	{
		init();
		return numResolutions;
	}

	public CachedCellImg< T, ? > getImg( final int resolutionLevel )
	{
		init();
		return imgs[ resolutionLevel ];
	}

	public RandomAccessibleInterval< V > getVolatileImg( final int resolutionLevel )
	{
		init();
		return vimgs[ resolutionLevel ];
	}

	public T getType()
	{
		init();
		return type;
	}

	public V getVolatileType()
	{
		init();
		return volatileType;
	}

	public int numDimensions()
	{
		return dimensions.length;
	}
}
