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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.scijava.convert.ConvertService;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import sc.fiji.ome.zarr.pyramid.backend.PyramidContents;
import sc.fiji.ome.zarr.pyramid.metadata.AxisCalibration;

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

	/*
	 * TODO: Should the preferredMaxWidth handling really happen at the
	 *   Pyramidal5DImageData level? or somewhere else? Maybe just when the
	 *   pyramid is initially opened as a Dataset via the Command?
	 *
	 * (from previous javadoc in Pyramidal5DImageData)
	 * @return the IJ2 {@code net.imagej.Dataset} at the default resolution level
	 *   (resolution level 0 = highest resolution, unless a {@code preferredMaxWidth}
	 *   was specified at construction time, in which case a coarser level may be used);
	 *   always returns the same cached object
	 */

	/**
	 * Create a new IJ2 {@code net.imagej.Dataset} wrapping the image at the
	 * preferred resolution level of the {@code pyramid}.
	 *
	 * @param pyramid
	 * 		multi-resolution pyramid images and metadata
	 */
	public PyramidalDataset( final Pyramidal5DImageData< ? > pyramid )
	{
		this( pyramid, pyramid.preferredResolutionLevel() );
	}

	/**
	 * Create a new IJ2 {@code net.imagej.Dataset} wrapping the image at the
	 * specified resolution level of the {@code pyramid}.
	 *
	 * @param pyramid
	 * 		multi-resolution pyramid images and metadata
	 * @param resolutionLevel
	 * 		0-based index into the resolution pyramid (0 = highest resolution)
	 */
	public PyramidalDataset( final Pyramidal5DImageData< ? > pyramid, final int resolutionLevel )
	{
		super( pyramid.context(), createImgPlus( pyramid, resolutionLevel ) );
		data = pyramid;
		if ( pyramid.numResolutionLevels() > 1 )
			setName( multiResolutionName( pyramid.getName() ) );
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
	 * Convenience method to convert this {@code PyramidalDataset} to an IJ1
	 * {@link ij.ImagePlus} via SciJava's {@link org.scijava.convert.ConvertService}
	 */
	public ImagePlus asImagePlus()
	{
		return context().service( ConvertService.class ).convert( this, ImagePlus.class );
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

	private static < T extends NativeType< T > & RealType< T > >
	ImgPlus< T > createImgPlus( final Pyramidal5DImageData< T > data, final int resolutionLevel )
	{
		final PyramidContents< T, ? > contents = data.getPyramidContents();

		if ( resolutionLevel < 0 || resolutionLevel >= data.numResolutionLevels() )
		{
			throw new IndexOutOfBoundsException( "Invalid resolution level: " + resolutionLevel +
					" (numResolutionLevels = " + data.numResolutionLevels() + ")" );
		}

		final AxisCalibration[] selectedAxes = contents.axesPerLevel[ resolutionLevel ];
		final ImgPlus< T > imgPlus = new ImgPlus<>( contents.cachedCellImgs[ resolutionLevel ], contents.name );
		for ( int i = 0; i < selectedAxes.length; i++ )
		{
			final AxisType axisType = AXIS_TYPE_MAP.getOrDefault( selectedAxes[ i ].name, Axes.unknown() );
			imgPlus.setAxis( new DefaultLinearAxis( axisType, selectedAxes[ i ].unit, selectedAxes[ i ].scale ), i );
		}
		return imgPlus;
	}
}
