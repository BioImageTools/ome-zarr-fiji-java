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
package ome.zarr.fiji.bdv;

import java.util.HashMap;
import java.util.Map;

import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

import ome.zarr.imglib2.metadata.ImageLabel;

/**
 * Colors a label image for BigDataViewer: each label value gets one color,
 * {@code 0} (background) stays transparent.
 * <p>
 * Colors come from the {@code image-label} metadata where it gives one, and
 * otherwise from the {@link GlasbeyOnDarkLut Glasbey on dark} palette, whose
 * colors stay distinguishable. BigDataViewer adds up the sources' RGB values and ignores
 * alpha, so a metadata color is premultiplied by its alpha.
 * <p>
 * The display range of BigDataViewer filters label values: labels outside it
 * are not shown. It starts as the full range of the pixel type. The color cannot
 * be changed, since the label colors come from the metadata.
 */
class LabelConverter< R extends RealType< ? > > implements Converter< R, ARGBType >, ColorConverter
{
	private final Map< Long, Integer > metadataColors = new HashMap<>();

	private double min;

	private double max;

	/**
	 * @param labelMetadata the label image's metadata, may be {@code null}
	 * @param min the smallest label value to show at first, e.g. the pixel type's minimum
	 * @param max the largest label value to show at first, e.g. the pixel type's maximum
	 */
	LabelConverter( final ImageLabel labelMetadata, final double min, final double max )
	{
		this.min = min;
		this.max = max;
		if ( labelMetadata == null || labelMetadata.colors() == null )
			return;
		for ( final ImageLabel.Color color : labelMetadata.colors() )
		{
			final int[] rgba = color.rgba();
			if ( rgba != null && rgba.length == 4 )
				metadataColors.put( ( long ) color.labelValue(), premultiplied( rgba ) );
		}
	}

	@Override
	public void convert( final R input, final ARGBType output )
	{
		final double value = input.getRealDouble();
		final long label = ( long ) value;
		if ( label == 0 || value < min || value > max )
		{
			output.set( 0 );
			return;
		}
		final Integer metadataColor = metadataColors.get( label );
		output.set( metadataColor != null ? metadataColor : GlasbeyOnDarkLut.argb( label ) );
	}

	private static int premultiplied( final int[] rgba )
	{
		final int alpha = rgba[ 3 ];
		return ARGBType.rgba( rgba[ 0 ] * alpha / 255, rgba[ 1 ] * alpha / 255, rgba[ 2 ] * alpha / 255, alpha );
	}

	@Override
	public ARGBType getColor()
	{
		return new ARGBType( 0xffffffff );
	}

	@Override
	public void setColor( final ARGBType c )
	{
		// the label colors come from the metadata
	}

	@Override
	public boolean supportsColor()
	{
		return false;
	}

	@Override
	public double getMin()
	{
		return min;
	}

	@Override
	public double getMax()
	{
		return max;
	}

	@Override
	public void setMin( final double min )
	{
		this.min = min;
	}

	@Override
	public void setMax( final double max )
	{
		this.max = max;
	}
}
