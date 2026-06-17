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

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import sc.fiji.ome.zarr.pyramid.exceptions.NoMatchingResolutionException;
import sc.fiji.ome.zarr.pyramid.metadata.AxisCalibration;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

/**
 * Immutable snapshot of everything a {@link PyramidBackend} produces when
 * opening an OME-Zarr multi-resolution image. The backend-agnostic pyramidal
 * image data class copies these fields into its own state.
 * <p>
 * Indices in {@code cachedCellImgs} and {@code transforms}
 * are in resolution-level order (index 0 is the highest resolution).
 * The imglib2 axis indices follow F-order (x, y, z, c, t), the order produced
 * after the backend has reversed any zarr C-order shapes.
 * <p>
 * The resolution-level count and the per-axis sizes are
 * derived on demand from the full-resolution image.
 *
 * @param <T> pixel type
 */
public final class PyramidContents<
		T extends NativeType< T > & RealType< T > >
{
	public final String name;

	public final T type;

	public final AffineTransform3D[] transforms;

	public final CachedCellImg< T, ? >[] cachedCellImgs;

	/**
	 * Axes per resolution level: {@code [resolutionLevel][axisIndex]}.
	 */
	public final AxisCalibration[][] axesPerLevel;

	/** One label per channel; used as the source name in BigDataViewer. */
	public final String[] channelLabels;

	/** OMERO rendering metadata, or {@code null} if unavailable. */
	public final Omero omero;

	private PyramidContents( final Builder< T > b )
	{
		this.name = b.name;
		this.type = b.type;
		this.transforms = b.transforms;
		this.cachedCellImgs = b.cachedCellImgs;
		this.axesPerLevel = b.axesPerLevel;
		this.channelLabels = b.channelLabels;
		this.omero = b.omero;
	}

	/**
	 * Number of resolution levels in the pyramid, i.e. the length of
	 * {@link #cachedCellImgs}.
	 */
	public int numResolutionLevels()
	{
		return cachedCellImgs.length;
	}

	/**
	 * Number of imglib2 dimensions of the full-resolution image. This is the
	 * count of axes that are actually present (a subset of x, y, z, c, t), and
	 * therefore the upper bound on every axis index reported by this class.
	 */
	public int numDimensions()
	{
		return cachedCellImgs[ 0 ].numDimensions();
	}

	/**
	 * Extent of the full-resolution image along the channel axis, or {@code 1}
	 * when the image has no channel axis. This is the size of one specific axis,
	 * independent of {@link #numDimensions()} (which only says whether the
	 * channel axis is present, not how large it is).
	 */
	public int numChannels()
	{
		return sizeAlongAxis( AxisCalibration.C );
	}

	/**
	 * Extent of the full-resolution image along the time axis, or {@code 1} when
	 * the image has no time axis. As with {@link #numChannels()}, this is the
	 * size of one specific axis, independent of {@link #numDimensions()}.
	 */
	public int numTimepoints()
	{
		return sizeAlongAxis( AxisCalibration.T );
	}

	/**
	 * imglib2 F-order index of the axis with the given OME-Zarr name
	 * ({@link AxisCalibration#X}, {@link AxisCalibration#Y},
	 * {@link AxisCalibration#Z}, {@link AxisCalibration#C} or
	 * {@link AxisCalibration#T}), or {@code -1} if that axis is not present.
	 * <p>
	 * The axis is located <em>by name</em> rather than by a fixed position: even
	 * though OME-Zarr fixes the axis order (C-order t, c, z, y, x, which the
	 * backend reverses to imglib2 F-order x, y, z, c, t), axes may be absent, so
	 * the index of any given axis shifts with the set of axes actually present
	 * (e.g. in F-order {@code c} is at index 2 in {@code xyct} but at index 3 in
	 * {@code xyzc}). A name lookup resolves the correct index for every such
	 * combination from a single source of truth, {@link #axesPerLevel}, which is
	 * in imglib2 F-order and thus aligned 1:1 with the dimensions of
	 * {@code cachedCellImgs[ 0 ]}.
	 */
	public int axisIndex( final String axisName )
	{
		final AxisCalibration[] axes = axesPerLevel[ 0 ];
		for ( int d = 0; d < axes.length; d++ )
			if ( axisName.equals( axes[ d ].name ) )
				return d;
		return -1;
	}

	/**
	 * Whether the axis with the given OME-Zarr name ({@link AxisCalibration#X},
	 * {@link AxisCalibration#Y}, {@link AxisCalibration#Z},
	 * {@link AxisCalibration#C} or {@link AxisCalibration#T}) is present, i.e.
	 * {@code axisIndex( axisName ) >= 0}.
	 */
	public boolean hasAxis( final String axisName )
	{
		return axisIndex( axisName ) >= 0;
	}

	/**
	 * Size of the full-resolution image along the axis with the given OME-Zarr
	 * name, or {@code 1} if no such axis is present.
	 */
	private int sizeAlongAxis( final String axisName )
	{
		final int index = axisIndex( axisName );
		return index < 0 ? 1 : ( int ) cachedCellImgs[ 0 ].dimension( index );
	}

	/**
	 * Returns the index of the coarsest resolution level whose x-width (index 0
	 * in imglib2 F-order) is &le; {@code preferredMaxWidth}, or 0 when
	 * {@code preferredMaxWidth} is {@code null}.
	 *
	 * @throws NoMatchingResolutionException if {@code preferredMaxWidth} is
	 *   smaller than the width of every resolution level
	 */
	public int selectResolutionLevel( final Integer preferredMaxWidth )
	{
		if ( preferredMaxWidth == null )
			return 0;
		int smallestWidth = Integer.MAX_VALUE;
		for ( int level = 0; level < cachedCellImgs.length; level++ )
		{
			final int width = ( int ) cachedCellImgs[ level ].dimension( 0 );
			if ( width <= preferredMaxWidth )
				return level;
			smallestWidth = Math.min( smallestWidth, width );
		}
		throw new NoMatchingResolutionException( preferredMaxWidth, smallestWidth );
	}

	public static <
			T extends NativeType< T > & RealType< T > > Builder< T > builder()
	{
		return new Builder<>();
	}

	public static final class Builder<
			T extends NativeType< T > & RealType< T > >
	{
		private String name;

		private T type;

		private AffineTransform3D[] transforms;

		private CachedCellImg< T, ? >[] cachedCellImgs;

		private AxisCalibration[][] axesPerLevel;

		private String[] channelLabels;

		private Omero omero;

		public Builder< T > name( final String name )
		{
			this.name = name;
			return this;
		}

		public Builder< T > type( final T t )
		{
			this.type = t;
			return this;
		}

		public Builder< T > transforms( final AffineTransform3D[] t )
		{
			this.transforms = t;
			return this;
		}

		public Builder< T > cachedCellImgs( final CachedCellImg< T, ? >[] i )
		{
			this.cachedCellImgs = i;
			return this;
		}

		public Builder< T > axesPerLevel( final AxisCalibration[][] a )
		{
			this.axesPerLevel = a;
			return this;
		}

		public Builder< T > channelLabels( final String[] l )
		{
			this.channelLabels = l;
			return this;
		}

		public Builder< T > omero( final Omero o )
		{
			this.omero = o;
			return this;
		}

		public PyramidContents< T > build()
		{
			return new PyramidContents<>( this );
		}
	}
}
