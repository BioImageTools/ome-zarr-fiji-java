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

import net.imagej.Dataset;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.util.List;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

/**
 * 5D multi-resolution array data
 * represented as various 5D images objects
 * that can be visualized in different ImageJ
 * viewers.
 *
 * @param <T> pixel type
 */
public interface Pyramidal5DImageData< T extends NativeType< T > & RealType< T > >
{
	/**
	 * @return an IJ2 {@code net.imagej.Dataset}
	 *   with additional methods for retrieving the
	 *   underlying multi-resolution data.
	 */
	PyramidalDataset< T > asPyramidalDataset();

	/**
	 * @return a IJ2 {@code net.imagej.Dataset} wrapping the full resolution
	 *   5D (XYZCT) image; this will indirectly also serve the ImagePlus.
	 *
	 */
	Dataset asDataset();

	/**
	 * @return a list of BigDataViewer sources, representing a 5D (XYZCT) multi-resolution image, one source for each channel of the dataset.
	 * 	 The sources provide nested volatile versions. The sources are
	 * 	 multi-resolution, reflecting the resolution pyramid of the OME-Zarr.
	 */
	List< SourceAndConverter< T > > asSources();

	int numChannels();

	int numTimepoints();

	int numResolutionLevels();

	T getType();

	VoxelDimensions voxelDimensions();

	String getName();

	default Omero getOmeroProperties()
	{
		return null;
	}

	ImagePlus asImagePlus();
}
