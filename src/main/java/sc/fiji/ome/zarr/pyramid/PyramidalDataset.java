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
package sc.fiji.ome.zarr.pyramid;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imagej.DefaultDataset;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;

/**
 * A {@code net.imagej.Dataset} that can be viewed
 * in ImageJ, backed by 5D multi-resolution image data,
 * containing additional methods presenting that
 * multi-resolution image data in convenient ways.
 *
 * @param <T> the type of the data.
 */
public class PyramidalDataset extends DefaultDataset implements Pyramidal
{
	private final Pyramidal5DImageData< ? > data;

	public PyramidalDataset( Pyramidal5DImageData< ? > data )
	{
		super( data.asDataset().context(), data.asDataset().getImgPlus() );

		this.data = data;
		if ( data.numResolutionLevels() > 1 )
			setName( multiResolutionName( data.getName() ) );
	}

	public PyramidalDataset( Pyramidal5DImageData< ? > data, int resolutionLevel )
	{
		super( data.asDataset().context(), data.asDataset( resolutionLevel ).getImgPlus() );

		this.data = data;
		if ( data.numResolutionLevels() > 1 )
			setName( multiResolutionName( data.getName() ) );
	}

	private static String multiResolutionName( final String baseName )
	{
		return ( baseName != null && !baseName.isEmpty() ) ? baseName + " (R)" : "(R)";
	}

	@Override
	public Pyramidal5DImageData< ? > getPyramidal5DImageData()
	{
		return data;
	}

	/**
	 * Converts this {@code PyramidalDataset} into an {@code ImagePlus} object for a given resolution level,
	 * channel, and timepoint.
	 *
	 * @param resolutionLevel The resolution level to use for extracting the image data.
	 * @param channel The channel index to extract from the dataset.
	 * @param timepoint The timepoint index to extract from the dataset.
	 * @return An {@code ImagePlus} object representing the extracted image data.
	 */
	// TODO: remove?
	public ImagePlus getImagePlus( final int resolutionLevel, final int channel, final int timepoint )
	{
		final SourceAndConverter< ? > sourceAndConverter = data.asSources().get( channel );
		final RandomAccessibleInterval randomAccessibleInterval =
				sourceAndConverter.getSpimSource().getSource( timepoint, resolutionLevel );
		return ImageJFunctions.wrap( randomAccessibleInterval, sourceAndConverter.getSpimSource().getName() );
	}
}
