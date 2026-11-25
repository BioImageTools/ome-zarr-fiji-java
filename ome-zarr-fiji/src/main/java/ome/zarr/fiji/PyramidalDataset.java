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
package ome.zarr.fiji;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.Context;
import org.scijava.convert.ConvertService;

import ij.ImagePlus;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;

/**
 * A {@code net.imagej.Dataset} that can be viewed
 * in ImageJ, backed by 5D multi-resolution image data,
 * containing additional methods presenting that
 * multi-resolution image data in convenient ways.
 */
@SuppressWarnings( { "java:S2060", "java:S1948", "java:S110", "ExternalizableWithoutPublicNoArgConstructor" } )
public class PyramidalDataset extends DefaultDataset implements Pyramidal
{
	private final PyramidContents< ? > contents;

	private final int resolutionLevel;

	/**
	 * Create a new IJ2 {@code net.imagej.Dataset} wrapping the image at the
	 * specified resolution level of the {@code contents}.
	 *
	 * @param context
	 * 		the SciJava context to associate with this dataset
	 * @param contents
	 * 		multi-resolution pyramid images and metadata
	 * @param resolutionLevel
	 * 		0-based index into the resolution pyramid (0 = highest resolution)
	 */
	public < T extends NativeType< T > & RealType< T > > PyramidalDataset( final Context context, final PyramidContents< T > contents,
			final int resolutionLevel )
	{
		super( context, createImgPlus( contents, resolutionLevel ) );
		this.contents = contents;
		this.resolutionLevel = resolutionLevel;
		setRGBMerged( false );
		if ( contents.numResolutionLevels() > 1 )
			setName( multiResolutionName( contents.name(), resolutionLevel, contents.numResolutionLevels() ) );
	}

	public int resolutionLevel()
	{
		return resolutionLevel;
	}

	/**
	 * {@code baseName} with the resolution level, counted from 1 as in the level
	 * dialog, and the number of levels appended, e.g. {@code "image (R=3/6)"} for
	 * the 0-based {@code resolutionLevel} 2.
	 */
	private static String multiResolutionName( final String baseName, final int resolutionLevel,
			final int numResolutionLevels )
	{
		final String suffix = "(R=" + ( resolutionLevel + 1 ) + "/" + numResolutionLevels + ")";
		return ( baseName != null && !baseName.isEmpty() ) ? baseName + " " + suffix : suffix;
	}

	@Override
	public PyramidContents< ? > getPyramidContents()
	{
		return contents;
	}

	/**
	 * Convenience method to convert this {@code PyramidalDataset} to an IJ1
	 * {@link ij.ImagePlus} via SciJava's {@link org.scijava.convert.ConvertService}
	 */
	public ImagePlus asImagePlus()
	{
		return getContext().service( ConvertService.class ).convert( this, ImagePlus.class );
	}

	private static final Map< String, AxisType > AXIS_TYPE_MAP;

	static
	{
		final Map< String, AxisType > map = new HashMap<>();
		map.put( AxisCalibration.X, Axes.X );
		map.put( AxisCalibration.Y, Axes.Y );
		map.put( AxisCalibration.Z, Axes.Z );
		map.put( AxisCalibration.C, Axes.CHANNEL );
		map.put( AxisCalibration.T, Axes.TIME );
		AXIS_TYPE_MAP = Collections.unmodifiableMap( map );
	}

	private static < T extends NativeType< T > & RealType< T > > ImgPlus< T > createImgPlus( final PyramidContents< T > contents,
			final int resolutionLevel )
	{
		if ( resolutionLevel < 0 || resolutionLevel >= contents.numResolutionLevels() )
		{
			throw new IndexOutOfBoundsException( "Invalid resolution level: " + resolutionLevel +
					" (numResolutionLevels = " + contents.numResolutionLevels() + ")" );
		}

		final List< AxisCalibration > selectedAxes = contents.axes( resolutionLevel );
		final ImgPlus< T > imgPlus = new ImgPlus<>( contents.asImg( resolutionLevel ), contents.name() );
		for ( int i = 0; i < selectedAxes.size(); i++ )
		{
			final AxisType axisType = AXIS_TYPE_MAP.getOrDefault( selectedAxes.get( i ).name, Axes.unknown() );
			imgPlus.setAxis( new DefaultLinearAxis( axisType, selectedAxes.get( i ).unit, selectedAxes.get( i ).scale ), i );
		}
		setDisplayRanges( imgPlus, contents.omeroChannels() );
		setColorTables( imgPlus, contents.omeroChannels() );
		return imgPlus;
	}

	/**
	 * Gives each channel the display range of its OMERO window. ImageJ only computes
	 * one (auto-contrast) for a channel without it, and ImageJ 1 takes it over too.
	 * A window without a range, e.g. one whose {@code start}/{@code end} are missing,
	 * is skipped.
	 *
	 * @param channels as returned by {@link PyramidContents#omeroChannels()}
	 */
	private static void setDisplayRanges( final ImgPlus< ? > imgPlus, final List< Omero.Channel > channels )
	{
		for ( int c = 0; c < channels.size(); c++ )
		{
			final Omero.Channel.Window window = channels.get( c ) == null ? null : channels.get( c ).window();
			if ( window != null && window.end() > window.start() )
			{
				imgPlus.setChannelMinimum( c, window.start() );
				imgPlus.setChannelMaximum( c, window.end() );
			}
		}
	}

	/**
	 * Colors each channel with a lookup table from black to its OMERO color.
	 * A channel without a color, or with one that is not an {@code RRGGBB} hex string,
	 * keeps ImageJ's default.
	 *
	 * @param channels as returned by {@link PyramidContents#omeroChannels()}
	 */
	private static void setColorTables( final ImgPlus< ? > imgPlus, final List< Omero.Channel > channels )
	{
		if ( channels.isEmpty() )
			return;
		// ImageJ reads the table of a channel at the index of the channel's first plane.
		int planesPerChannel = 1;
		for ( int d = 2; d < imgPlus.dimensionIndex( Axes.CHANNEL ); d++ )
			planesPerChannel *= ( int ) imgPlus.dimension( d );
		imgPlus.initializeColorTables( ( channels.size() - 1 ) * planesPerChannel + 1 );
		for ( int channelIndex = 0; channelIndex < channels.size(); channelIndex++ )
		{
			final Color color = channels.get( channelIndex ) == null ? null : channels.get( channelIndex ).awtColor();
			if ( color != null )
				imgPlus.setColorTable( colorTable( color ), channelIndex * planesPerChannel );
		}
	}

	/** A lookup table from black to {@code color}. */
	private static ColorTable8 colorTable( final Color color )
	{
		final byte[][] values = new byte[ 3 ][ 256 ];
		for ( int i = 0; i < 256; i++ )
		{
			values[ 0 ][ i ] = ( byte ) ( color.getRed() * i / 255 );
			values[ 1 ][ i ] = ( byte ) ( color.getGreen() * i / 255 );
			values[ 2 ][ i ] = ( byte ) ( color.getBlue() * i / 255 );
		}
		return new ColorTable8( values );
	}
}
