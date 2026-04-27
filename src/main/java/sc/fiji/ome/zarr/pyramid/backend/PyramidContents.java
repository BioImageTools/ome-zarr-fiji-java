/*-
 * #%L
 * OME-Zarr extras for Fiji
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
package sc.fiji.ome.zarr.pyramid.backend;

import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

/**
 * Immutable snapshot of everything a {@link PyramidBackend} produces when
 * opening an OME-Zarr multi-resolution image. The backend-agnostic pyramidal
 * image data class copies these fields into its own state.
 * <p>
 * Indices in {@code cachedCellImgs}, {@code volatileImgs} and {@code transforms}
 * are in resolution-level order (index 0 is highest resolution).
 * The imglib2 axis indices follow F-order (x, y, z, c, t), the order produced
 * after the backend has reversed any zarr C-order shapes.
 *
 * @param <T> pixel type
 * @param <V> volatile pixel type
 */
public final class PyramidContents<
		T extends NativeType< T > & RealType< T >,
		V extends Volatile< T > & NativeType< V > & RealType< V > >
{
	public final String name;

	public final int numResolutionLevels;

	public final int numChannels;

	public final int numTimepoints;

	public final int numDimensions;

	public final int selectedResolutionLevelIndex;

	public final T type;

	public final V volatileType;

	public final VoxelDimensions voxelDimensions;

	public final AffineTransform3D[] transforms;

	public final CachedCellImg< T, ? >[] cachedCellImgs;

	public final RandomAccessibleInterval< V >[] volatileImgs;

	public final ImgPlus< T > imgPlus;

	/** imglib2-order index of the channel axis, or -1 if absent. */
	public final int channelAxisIndex;

	/** True if the image has a z axis (in imglib2 / F order - X, Y, Z, C, T). */
	public final boolean zAxisPresent;

	/** True if the image has a time axis (in imglib2 / F order - X, Y, Z, C, T). */
	public final boolean timeAxisPresent;

	/** One label per channel; used as the source name in BigDataViewer. */
	public final String[] channelLabels;

	/** OMERO rendering metadata, or {@code null} if unavailable. */
	public final Omero omero;

	private PyramidContents( final Builder< T, V > b )
	{
		this.name = b.name;
		this.numResolutionLevels = b.numResolutionLevels;
		this.numChannels = b.numChannels;
		this.numTimepoints = b.numTimepoints;
		this.numDimensions = b.numDimensions;
		this.selectedResolutionLevelIndex = b.selectedResolutionLevelIndex;
		this.type = b.type;
		this.volatileType = b.volatileType;
		this.voxelDimensions = b.voxelDimensions;
		this.transforms = b.transforms;
		this.cachedCellImgs = b.cachedCellImgs;
		this.volatileImgs = b.volatileImgs;
		this.imgPlus = b.imgPlus;
		this.channelAxisIndex = b.channelAxisIndex;
		this.zAxisPresent = b.zAxisPresent;
		this.timeAxisPresent = b.timeAxisPresent;
		this.channelLabels = b.channelLabels;
		this.omero = b.omero;
	}

	public static <
			T extends NativeType< T > & RealType< T >,
			V extends Volatile< T > & NativeType< V > & RealType< V > > Builder< T, V > builder()
	{
		return new Builder<>();
	}

	public static final class Builder<
			T extends NativeType< T > & RealType< T >,
			V extends Volatile< T > & NativeType< V > & RealType< V > >
	{
		private String name;

		private int numResolutionLevels;

		private int numChannels;

		private int numTimepoints;

		private int numDimensions;

		private int selectedResolutionLevelIndex;

		private T type;

		private V volatileType;

		private VoxelDimensions voxelDimensions;

		private AffineTransform3D[] transforms;

		private CachedCellImg< T, ? >[] cachedCellImgs;

		private RandomAccessibleInterval< V >[] volatileImgs;

		private ImgPlus< T > imgPlus;

		private int channelAxisIndex = -1;

		private boolean zAxisPresent;

		private boolean timeAxisPresent;

		private String[] channelLabels;

		private Omero omero;

		public Builder< T, V > name( final String name )
		{
			this.name = name;
			return this;
		}

		public Builder< T, V > numResolutionLevels( final int n )
		{
			this.numResolutionLevels = n;
			return this;
		}

		public Builder< T, V > numChannels( final int n )
		{
			this.numChannels = n;
			return this;
		}

		public Builder< T, V > numTimepoints( final int n )
		{
			this.numTimepoints = n;
			return this;
		}

		public Builder< T, V > numDimensions( final int n )
		{
			this.numDimensions = n;
			return this;
		}

		public Builder< T, V > selectedResolutionLevelIndex( final int i )
		{
			this.selectedResolutionLevelIndex = i;
			return this;
		}

		public Builder< T, V > type( final T t )
		{
			this.type = t;
			return this;
		}

		public Builder< T, V > volatileType( final V v )
		{
			this.volatileType = v;
			return this;
		}

		public Builder< T, V > voxelDimensions( final VoxelDimensions v )
		{
			this.voxelDimensions = v;
			return this;
		}

		public Builder< T, V > transforms( final AffineTransform3D[] t )
		{
			this.transforms = t;
			return this;
		}

		public Builder< T, V > cachedCellImgs( final CachedCellImg< T, ? >[] i )
		{
			this.cachedCellImgs = i;
			return this;
		}

		public Builder< T, V > volatileImgs( final RandomAccessibleInterval< V >[] i )
		{
			this.volatileImgs = i;
			return this;
		}

		public Builder< T, V > imgPlus( final ImgPlus< T > i )
		{
			this.imgPlus = i;
			return this;
		}

		public Builder< T, V > channelAxisIndex( final int i )
		{
			this.channelAxisIndex = i;
			return this;
		}

		public Builder< T, V > zAxisPresent( final boolean b )
		{
			this.zAxisPresent = b;
			return this;
		}

		public Builder< T, V > timeAxisPresent( final boolean b )
		{
			this.timeAxisPresent = b;
			return this;
		}

		public Builder< T, V > channelLabels( final String[] l )
		{
			this.channelLabels = l;
			return this;
		}

		public Builder< T, V > omero( final Omero o )
		{
			this.omero = o;
			return this;
		}

		public PyramidContents< T, V > build()
		{
			return new PyramidContents<>( this );
		}
	}
}
