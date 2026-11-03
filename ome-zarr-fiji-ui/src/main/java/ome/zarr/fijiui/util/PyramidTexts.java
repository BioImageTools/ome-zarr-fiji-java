/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.fijiui.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.function.UnaryOperator;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.util.ImageSizes;

/** User-facing texts about a pyramid, for the dialogs that let the user pick a resolution level. */
public final class PyramidTexts
{
	private PyramidTexts()
	{
		// utility class
	}

	/**
	 * A formatted resolution level description, e.g. {@code <b>1</b> (1024×1024×35, c=4, t=18, uint16,
	 * slice 1.0 Mpx ~ 2.0 MB, total 4.9 GB)} for {@code level} 0. Absent z, c and t
	 * axes are left out.
	 * <p>
	 * {@code level} is 0-based, the number shown is 1-based. Nothing parses that
	 * number back: callers take the level from the choice's position.
	 */
	public static < T extends NativeType< T > & RealType< T > > String describeLevel( final PyramidContents< T > contents,
			final int level )
	{
		final StringBuilder sb = new StringBuilder( "<html><b>" ).append( level + 1 ).append( "</b>&nbsp; (" )
				.append( spatialAxes( contents, axis -> String.valueOf( contents.sizeAlongAxis( axis, level ) ) ) );
		if ( contents.hasAxis( AxisCalibration.C ) )
			sb.append( ", c=" ).append( contents.sizeAlongAxis( AxisCalibration.C, level ) );
		if ( contents.hasAxis( AxisCalibration.T ) )
			sb.append( ", t=" ).append( contents.sizeAlongAxis( AxisCalibration.T, level ) );
		return sb.append( ", " ).append( dataTypeName( contents.type() ) )
				.append( ", slice " ).append( ImageSizes.formatPixels( contents.numXYSlicePixels( level ) ) )
				.append( " ~ " ).append( ImageSizes.formatBytes( contents.uncompressedXYSliceBytes( level ) ) )
				.append( ", total " ).append( ImageSizes.formatBytes( contents.uncompressedBytes( level ) ) )
				.append( ")</html>" ).toString();
	}

	/**
	 * The legend for the {@link #describeLevel} choices, naming only the axes they
	 * show, e.g. {@code Extents in x×y×z order, c = channels, t = time points;
	 * slice = one xy-plane}.
	 */
	public static String describeAxes( final PyramidContents< ? > contents )
	{
		final StringBuilder sb = new StringBuilder( "Extents in x×y" );
		if ( contents.hasAxis( AxisCalibration.Z ) )
			sb.append( "×z" );
		sb.append( " order" );
		if ( contents.hasAxis( AxisCalibration.C ) )
			sb.append( ", c = channels" );
		if ( contents.hasAxis( AxisCalibration.T ) )
			sb.append( ", t = time points" );
		return sb.append( "; slice = one xy-plane" ).toString();
	}

	/**
	 * A resolution level description for choosing the level to segment, e.g.
	 * {@code <b>1</b> (1024×1024×35, pixel 0.52×0.52×2 micrometer)} for
	 * {@code level} 0. Unlike {@link #describeLevel}, it leaves out everything that
	 * is the same on every level, which {@link #describeImage} names once.
	 */
	public static String describeSegmentationLevel( final PyramidContents< ? > contents, final int level )
	{
		final List< AxisCalibration > axes = contents.axes( level );
		final String unit = axes.get( contents.axisIndex( AxisCalibration.X ) ).unit;
		return "<html><b>" + ( level + 1 ) + "</b>&nbsp; ("
				+ spatialAxes( contents, axis -> String.valueOf( contents.sizeAlongAxis( axis, level ) ) )
				+ ", pixel " + spatialAxes( contents, axis -> formatScale( axes.get( contents.axisIndex( axis ) ).scale ) )
				+ ( unit.isEmpty() ? "" : " " + unit ) + ")</html>";
	}

	/**
	 * What all resolution levels share, e.g. {@code 4 channels, 18 time points,
	 * uint16}, followed on a second line by the axis order of the
	 * {@link #describeSegmentationLevel} choices. Absent c and t axes are left out.
	 */
	public static < T extends NativeType< T > & RealType< T > > String describeImage( final PyramidContents< T > contents )
	{
		final StringBuilder sb = new StringBuilder( "<html>" );
		if ( contents.hasAxis( AxisCalibration.C ) )
			sb.append( count( contents.sizeAlongAxis( AxisCalibration.C ), "channel" ) ).append( ", " );
		if ( contents.hasAxis( AxisCalibration.T ) )
			sb.append( count( contents.sizeAlongAxis( AxisCalibration.T ), "time point" ) ).append( ", " );
		return sb.append( dataTypeName( contents.type() ) )
				.append( "<br>Extent and pixel size in " )
				.append( spatialAxes( contents, axis -> axis ) ).append( " order</html>" ).toString();
	}

	/** {@code format} applied to x, y and, if present, z, joined by "×". */
	private static String spatialAxes( final PyramidContents< ? > contents, final UnaryOperator< String > format )
	{
		final String xy = format.apply( AxisCalibration.X ) + "×" + format.apply( AxisCalibration.Y );
		return contents.hasAxis( AxisCalibration.Z ) ? xy + "×" + format.apply( AxisCalibration.Z ) : xy;
	}

	/** Three significant digits without trailing zeros, e.g. {@code "0.52"} or {@code "2"}. */
	private static String formatScale( final double scale )
	{
		return BigDecimal.valueOf( scale ).round( new MathContext( 3 ) ).stripTrailingZeros().toPlainString();
	}

	private static String count( final long n, final String noun )
	{
		return n + " " + noun + ( n == 1 ? "" : "s" );
	}

	/** Zarr-style name such as {@code "uint16"}. */
	private static < T extends NativeType< T > > String dataTypeName( final T type )
	{
		final DataType dataType = N5Utils.dataType( type );
		return dataType != null ? dataType.toString() : type.getClass().getSimpleName();
	}
}
