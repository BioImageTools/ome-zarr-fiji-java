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

import java.lang.invoke.MethodHandles;
import java.util.List;

import dev.zarr.conventions.ome.MultiscaleImage;
import dev.zarr.conventions.ome.metadata.Axis;
import dev.zarr.conventions.ome.v0_6.metadata.CoordinateSystem;
import dev.zarr.conventions.ome.v0_6.metadata.MultiscalesEntry;
import dev.zarr.conventions.ome.v0_6.metadata.transform.AffineCoordinateTransformation;
import dev.zarr.conventions.ome.v0_6.metadata.transform.CoordinateSystemRef;
import dev.zarr.conventions.ome.v0_6.metadata.transform.CoordinateTransformation;
import dev.zarr.conventions.ome.v0_6.metadata.transform.IdentityCoordinateTransformation;
import dev.zarr.conventions.ome.v0_6.metadata.transform.RotationCoordinateTransformation;
import dev.zarr.conventions.ome.v0_6.metadata.transform.ScaleCoordinateTransformation;
import dev.zarr.conventions.ome.v0_6.metadata.transform.SequenceCoordinateTransformation;
import dev.zarr.conventions.ome.v0_6.metadata.transform.TranslationCoordinateTransformation;
import dev.zarr.zarrjava.ZarrException;

import net.imglib2.realtransform.AffineTransform3D;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.imglib2.metadata.AxisCalibration;

/**
 * Reads the multiscales-level {@code coordinateTransformations} of an OME-Zarr
 * v0.6 image, which map the intrinsic coordinate system (the one the datasets'
 * scale/translation lead into) to another one, e.g. a rotation into "world".
 * <p>
 * The version-independent {@code MultiscalesEntry} that zarr-java exposes for
 * every version carries only the per-dataset transformations, so this reads the
 * typed v0.6 entry instead.
 * <p>
 * Only the linear types (identity, scale, translation, rotation, affine and
 * sequences of them, with inline parameters) can be shown as a BigDataViewer
 * source transform. Anything else (displacements, coordinates, mapAxis,
 * byDimension, bijection, or parameters stored in an array via {@code path})
 * is skipped with a warning, so the image still opens in its intrinsic space.
 */
final class MultiscaleTransformV06
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private MultiscaleTransformV06()
	{}

	/**
	 * Spatial part of the first multiscales-level transformation leaving the
	 * intrinsic coordinate system, or {@code null} if {@code image} is not v0.6
	 * or has no usable such transformation.
	 */
	static AffineTransform3D read( final MultiscaleImage image, final Object location )
	{
		if ( !( image instanceof dev.zarr.conventions.ome.v0_6.MultiscaleImage ) )
			return null;
		final MultiscalesEntry entry;
		try
		{
			entry = ( ( dev.zarr.conventions.ome.v0_6.MultiscaleImage ) image ).getMultiscalesEntry( 0 );
		}
		catch ( ZarrException e )
		{
			return null;
		}
		final CoordinateSystem intrinsic = entry.intrinsicCoordinateSystem();
		if ( entry.coordinateTransformations == null || intrinsic == null || intrinsic.axes == null )
			return null;

		for ( final CoordinateTransformation ct : entry.coordinateTransformations )
		{
			if ( !startsAt( ct, intrinsic ) )
				continue;
			final CoordinateSystem output = findCoordinateSystem( entry.coordinateSystems, ct.getOutput() );
			final List< Axis > outputAxes = output != null && output.axes != null ? output.axes : intrinsic.axes;
			final double[][] m = toHomogeneous( ct, intrinsic.axes.size() );
			if ( m == null || outputAxes.size() != intrinsic.axes.size() )
			{
				logger.warn( "Ignoring multiscales coordinate transformation of type '{}' at {}: "
						+ "only identity, scale, translation, rotation, affine and sequences of these are supported",
						ct.getType(), location );
				return null;
			}
			return toAffine3D( m, spatialIndices( intrinsic.axes ), spatialIndices( outputAxes ) );
		}
		return null;
	}

	/** True if {@code ct} reads from {@code intrinsic} in the same image (or names no input). */
	private static boolean startsAt( final CoordinateTransformation ct, final CoordinateSystem intrinsic )
	{
		final CoordinateSystemRef input = ct.getInput();
		return input == null || ( input.path == null && ( input.name == null || input.name.equals( intrinsic.name ) ) );
	}

	private static CoordinateSystem findCoordinateSystem( final List< CoordinateSystem > systems,
			final CoordinateSystemRef ref )
	{
		if ( systems == null || ref == null || ref.path != null || ref.name == null )
			return null;
		for ( final CoordinateSystem cs : systems )
			if ( ref.name.equals( cs.name ) )
				return cs;
		return null;
	}

	/** Zarr axis index of x, y and z (imglib2 order), or -1 where absent. */
	private static int[] spatialIndices( final List< Axis > axes )
	{
		final String[] names = { AxisCalibration.X, AxisCalibration.Y, AxisCalibration.Z };
		final int[] idx = { -1, -1, -1 };
		for ( int d = 0; d < 3; d++ )
			for ( int i = 0; i < axes.size(); i++ )
				if ( names[ d ].equals( axes.get( i ).name ) )
					idx[ d ] = i;
		return idx;
	}

	/**
	 * Picks the x/y/z rows and columns out of an (n+1)×(n+1) homogeneous matrix
	 * in zarr axis order. A missing spatial axis keeps the identity.
	 */
	private static AffineTransform3D toAffine3D( final double[][] m, final int[] in, final int[] out )
	{
		final int n = m.length - 1;
		final AffineTransform3D t = new AffineTransform3D();
		for ( int r = 0; r < 3; r++ )
		{
			if ( out[ r ] < 0 )
				continue;
			for ( int c = 0; c < 3; c++ )
				t.set( in[ c ] < 0 ? ( r == c ? 1 : 0 ) : m[ out[ r ] ][ in[ c ] ], r, c );
			t.set( m[ out[ r ] ][ n ], r, 3 );
		}
		return t;
	}

	/**
	 * {@code ct} as an (n+1)×(n+1) homogeneous matrix in zarr axis order, or
	 * {@code null} if it is not linear, stores its parameters in an array, or has
	 * the wrong dimensionality.
	 */
	@SuppressWarnings( "java:S1168" )
	private static double[][] toHomogeneous( final CoordinateTransformation ct, final int n )
	{
		if ( ct instanceof IdentityCoordinateTransformation )
			return identity( n );
		if ( ct instanceof ScaleCoordinateTransformation )
		{
			final List< Double > s = ( ( ScaleCoordinateTransformation ) ct ).scale;
			if ( s == null || s.size() != n )
				return null;
			final double[][] m = identity( n );
			for ( int i = 0; i < n; i++ )
				m[ i ][ i ] = s.get( i );
			return m;
		}
		if ( ct instanceof TranslationCoordinateTransformation )
		{
			final List< Double > v = ( ( TranslationCoordinateTransformation ) ct ).translation;
			if ( v == null || v.size() != n )
				return null;
			final double[][] m = identity( n );
			for ( int i = 0; i < n; i++ )
				m[ i ][ n ] = v.get( i );
			return m;
		}
		if ( ct instanceof RotationCoordinateTransformation )
			return fromRows( ( ( RotationCoordinateTransformation ) ct ).rotation, n, n );
		if ( ct instanceof AffineCoordinateTransformation )
			return fromRows( ( ( AffineCoordinateTransformation ) ct ).affine, n, n + 1 );
		if ( ct instanceof SequenceCoordinateTransformation )
		{
			final List< CoordinateTransformation > steps = ( ( SequenceCoordinateTransformation ) ct ).transformations;
			if ( steps == null )
				return null;
			double[][] m = identity( n );
			for ( final CoordinateTransformation step : steps )
			{
				final double[][] s = toHomogeneous( step, n );
				if ( s == null )
					return null;
				m = multiply( s, m );
			}
			return m;
		}
		return null;
	}

	@SuppressWarnings( "java:S1168" )
	private static double[][] fromRows( final List< List< Double > > rows, final int n, final int cols )
	{
		if ( rows == null || rows.size() != n )
			return null;
		final double[][] m = identity( n );
		for ( int i = 0; i < n; i++ )
		{
			final List< Double > row = rows.get( i );
			if ( row == null || row.size() != cols )
				return null;
			for ( int j = 0; j < cols; j++ )
				m[ i ][ j ] = row.get( j );
		}
		return m;
	}

	private static double[][] identity( final int n )
	{
		final double[][] m = new double[ n + 1 ][ n + 1 ];
		for ( int i = 0; i <= n; i++ )
			m[ i ][ i ] = 1;
		return m;
	}

	private static double[][] multiply( final double[][] a, final double[][] b )
	{
		final int k = a.length;
		final double[][] c = new double[ k ][ k ];
		for ( int i = 0; i < k; i++ )
			for ( int j = 0; j < k; j++ )
				for ( int l = 0; l < k; l++ )
					c[ i ][ j ] += a[ i ][ l ] * b[ l ][ j ];
		return c;
	}
}
