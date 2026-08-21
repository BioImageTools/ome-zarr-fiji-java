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
package ome.zarr.imglib2;

import java.lang.invoke.MethodHandles;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;

/**
 * Immutable snapshot of everything a {@link PyramidBackend} produces when
 * opening an OME-Zarr multi-resolution image.
 * <p>
 * Indices in {@code cachedCellImgs} and {@code transforms}
 * are in resolution-level order (index 0 is the highest resolution).
 * The imglib2 axis indices follow F-order (x, y, z, c, t), the order produced
 * after the backend has reversed any zarr C-order shapes.
 * <p>
 * The resolution-level count and the per-axis sizes are derived on demand from
 * the full-resolution image and its axis list ({@code cachedCellImgs[ 0 ]} and
 * {@code axesPerLevel[ 0 ]}), rather than stored. Because every count comes from
 * this single source, {@link #numDimensions()}, {@link #numChannels()},
 * {@link #numTimepoints()} and {@link #axisIndex} cannot disagree with one
 * another. Note that {@link #numChannels()}/{@link #numTimepoints()} are
 * independent per-axis extents that fall back to {@code 1} for an absent axis
 * and are <em>not</em> summands of {@link #numDimensions()} (which counts only
 * axes that are actually present). The constructor enforces the one cross-axis
 * invariant they all rely on: the axis list and the image have the same number
 * of dimensions.
 *
 * @param <T> pixel type
 */
public final class PyramidContents< T extends NativeType< T > & RealType< T > >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * Returned by {@link #suggestResolutionLevel} when no resolution level matches
	 * the requested width.
	 */
	public static final int NO_MATCHING_LEVEL = -1;

	public final String name;

	public final T type;

	/**
	 * Per-resolution-level transform from that level's image coordinates to the
	 * shared physical world coordinate system, in resolution-level order (index 0
	 * is the highest resolution; aligned with {@link #cachedCellImgs}).
	 * <p>
	 * <b>Source</b> coordinates are the discrete pixel/voxel coordinates of
	 * resolution level i, in imglib2 F-order (x, y, z). <b>Target</b> coordinates
	 * are a single physical world space — in the spatial axes' unit (see
	 * {@link #axesPerLevel}) — shared by all levels, so that every level overlays
	 * correctly when displayed together. The transform combines the level's voxel
	 * spacing (scale) with its origin offset (translation) in that world space.
	 */
	public final AffineTransform3D[] transforms;

	public final CachedCellImg< T, ? >[] cachedCellImgs;

	/**
	 * Axes per resolution level: {@code [resolutionLevel][axisIndex]}.
	 */
	public final AxisCalibration[][] axesPerLevel;

	/** OMERO rendering metadata, or {@code null} if unavailable. */
	public final Omero omero;

	private PyramidContents( final Builder< T > b )
	{
		this.name = b.name;
		this.type = b.type;
		this.transforms = b.transforms;
		this.cachedCellImgs = b.cachedCellImgs;
		this.axesPerLevel = b.axesPerLevel;
		this.omero = b.omero;

		final int numDimensions = cachedCellImgs[ 0 ].numDimensions();
		if ( axesPerLevel[ 0 ].length != numDimensions )
			throw new IllegalArgumentException( "Full-resolution axis count (" + axesPerLevel[ 0 ].length
					+ ") does not match the number of image dimensions (" + numDimensions + ")." );
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
	 * Index of the coarsest resolution level, i.e., the smallest image of the
	 * pyramid: {@code numResolutionLevels() - 1}.
	 */
	public int smallestResolutionLevel()
	{
		return cachedCellImgs.length - 1;
	}

	/**
	 * Full-resolution image (resolution level 0). Same as
	 * {@link #asLargestImg()}.
	 * <p>
	 * The dimensions are in imglib2 F-order — a subset of (x, y, z, c, t) — so use
	 * {@link #axisIndex} / {@link #hasAxis} together with {@link #axesPerLevel} to
	 * map dimension indices to logical axes.
	 */
	public Img< T > asImg()
	{
		return asImg( 0 );
	}

	/**
	 * Largest image of the pyramid, i.e., the full-resolution level {@code 0}. See
	 * {@link #asImg()} for the axis ordering.
	 */
	public Img< T > asLargestImg()
	{
		return asImg( 0 );
	}

	/**
	 * Smallest image of the pyramid, i.e., the coarsest level
	 * {@link #smallestResolutionLevel()}. For a single-level pyramid this is the
	 * same image as {@link #asLargestImg()}. See {@link #asImg()} for the axis
	 * ordering.
	 */
	public Img< T > asSmallestImg()
	{
		return asImg( smallestResolutionLevel() );
	}

	/**
	 * Image at the given resolution level ({@code 0} = highest resolution). See
	 * {@link #asImg()} for the axis ordering; {@link #suggestResolutionLevel} can
	 * pick a level by preferred width.
	 *
	 * @throws IndexOutOfBoundsException if {@code resolutionLevel} is not in
	 *   {@code [0, numResolutionLevels())}
	 */
	public Img< T > asImg( final int resolutionLevel )
	{
		if ( resolutionLevel < 0 || resolutionLevel >= cachedCellImgs.length )
			throw new IndexOutOfBoundsException( "Invalid resolution level: " + resolutionLevel
					+ " (numResolutionLevels = " + cachedCellImgs.length + ")" );
		return cachedCellImgs[ resolutionLevel ];
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
	 * Extent of the full-resolution image along the channel axis, or {@code 1} if
	 * the image has no channel axis.
	 * <p>
	 * This is an independent per-axis extent, <em>not</em> a component of
	 * {@link #numDimensions()}. The {@code 1} returned for an absent axis is a
	 * virtual count, so {@code numChannels()} alone cannot distinguish "no channel
	 * axis" from "a channel axis of size 1" — use {@link #hasAxis} for that.
	 */
	public int numChannels()
	{
		return ( int ) sizeAlongAxis( AxisCalibration.C );
	}

	/**
	 * Extent of the full-resolution image along the time axis, or {@code 1} if the
	 * image has no time axis.
	 * <p>
	 * This is an independent per-axis extent, <em>not</em> a component of
	 * {@link #numDimensions()}. The {@code 1} returned for an absent axis is a
	 * virtual count, so {@code numTimepoints()} alone cannot distinguish "no time
	 * axis" from "a time axis of size 1" — use {@link #hasAxis} for that.
	 */
	public int numTimepoints()
	{
		return ( int ) sizeAlongAxis( AxisCalibration.T );
	}

	/**
	 * One label per channel.
	 * Derived from the OMERO channel metadata when available and consistent with
	 * {@link #numChannels()}, otherwise falling back to the dataset {@link #name}.
	 */
	public String[] channelLabels()
	{
		final int numChannels = numChannels();
		final boolean omeroValid = omero != null && omero.channels != null && omero.channels.size() == numChannels;
		if ( omeroValid )
			logger.trace( "Creating with OMERO metadata: {}", omero );
		else
			logger.trace( "Creating without OMERO metadata (not consistent or not available)" );

		final String[] labels = new String[ numChannels ];
		for ( int i = 0; i < numChannels; i++ )
			labels[ i ] = omeroValid ? omero.channels.get( i ).label : name;
		return labels;
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
	 * Extent of the full-resolution image along the axis with the given OME-Zarr
	 * name, or {@code 1} if no such axis is present. Shorthand for
	 * {@code sizeAlongAxis( axisName, 0 )}.
	 */
	public long sizeAlongAxis( final String axisName )
	{
		return sizeAlongAxis( axisName, 0 );
	}

	/**
	 * Extent of the image at the given resolution level along the axis with the
	 * given OME-Zarr name, or {@code 1} if no such axis is present.
	 * <p>
	 * The axis is located by name via {@link #axisIndex}, so callers never depend
	 * on a fixed dimension position. The width of a level, for instance, is
	 * {@code sizeAlongAxis( AxisCalibration.X, level )}.
	 *
	 * @throws IndexOutOfBoundsException if {@code resolutionLevel} is not in
	 *   {@code [0, numResolutionLevels())}
	 */
	public long sizeAlongAxis( final String axisName, final int resolutionLevel )
	{
		final int index = axisIndex( axisName );
		final Img< T > img = asImg( resolutionLevel );
		return index < 0 ? 1 : img.dimension( index );
	}

	/**
	 * Returns the index of the highest-resolution level that is still no wider
	 * than {@code preferredMaxWidth}, or {@code 0} when {@code preferredMaxWidth}
	 * is {@code null}.
	 * <p>
	 * When no level is narrow enough — including the trivial case of a single-level
	 * pyramid that is already too wide — {@link #NO_MATCHING_LEVEL} is returned. It
	 * is up to the caller to decide what to do then: offer
	 * {@link #asSmallestImg()} as the closest available match, ask the user, or
	 * open nothing at all.
	 */
	public int suggestResolutionLevel( final Integer preferredMaxWidth )
	{
		if ( preferredMaxWidth == null )
			return 0;
		for ( int level = 0; level < cachedCellImgs.length; level++ )
		{
			if ( sizeAlongAxis( AxisCalibration.X, level ) <= preferredMaxWidth )
				return level;
		}
		return NO_MATCHING_LEVEL;
	}

	public static < T extends NativeType< T > & RealType< T > > Builder< T > builder()
	{
		return new Builder<>();
	}

	/**
	 * Convenience factory for a single-resolution-level pyramid: a
	 * {@link PyramidContents} with exactly one level. Wraps the one image,
	 * transform and axis list into the length-1 arrays the builder expects.
	 * Used by {@link PyramidBackend} implementations when opening a bare array
	 * node (a single resolution level) rather than a whole multiscale image.
	 *
	 * @param <T> pixel type
	 * @param name dataset name
	 * @param type pixel type instance
	 * @param transform level-to-world transform for the single level
	 * @param img the single-level cached cell image
	 * @param axes per-axis calibration for the single level, in imglib2 F-order
	 * @param omero OMERO rendering metadata, or {@code null} if unavailable
	 */
	public static < T extends NativeType< T > & RealType< T > > PyramidContents< T > singleLevel(
			final String name, final T type, final AffineTransform3D transform,
			final CachedCellImg< T, ? > img, final AxisCalibration[] axes, final Omero omero )
	{
		return PyramidContents.< T >builder()
				.name( name )
				.type( type )
				.transforms( new AffineTransform3D[] { transform } )
				.cachedCellImgs( Cast.unchecked( new CachedCellImg[] { img } ) )
				.axesPerLevel( new AxisCalibration[][] { axes } )
				.omero( omero )
				.build();
	}

	public static final class Builder< T extends NativeType< T > & RealType< T > >
	{
		private String name;

		private T type;

		private AffineTransform3D[] transforms;

		private CachedCellImg< T, ? >[] cachedCellImgs;

		private AxisCalibration[][] axesPerLevel;

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
