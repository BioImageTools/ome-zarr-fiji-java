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
package sc.fiji.ome.zarr.pyramid.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;

/**
 * One entry in a multiscale pyramid: the dataset path, its position in the
 * pyramid, and the axis/scale metadata needed to describe it.
 * <p>
 * Holds either {@link Axis} entries (OME-NGFF v04/v05) <em>or</em> axis names
 * and units as separate arrays (OME-NGFF v03).
 */
public class ResolutionLevel
{
	private final String datasetPath;

	private final int index;

	private final DatasetAttributes attributes;

	private final Axis[] axes;

	private final String[] axisNames;

	private final String[] units;

	private final double[] scales;

	public ResolutionLevel(
			final String datasetPath, final int index, final DatasetAttributes attributes, final Axis[] axes, final String[] axisNames,
			final String[] units, final double[] scales )
	{
		this.datasetPath = datasetPath;
		this.index = index;
		this.attributes = attributes;
		this.axes = axes;
		this.axisNames = axisNames;
		this.units = units;
		this.scales = scales;
	}

	public String getDatasetPath()
	{
		return datasetPath;
	}

	public int getIndex()
	{
		return index;
	}

	public DatasetAttributes getAttributes()
	{
		return attributes;
	}

	public Axis[] getAxes()
	{
		return axes;
	}

	public String[] getAxisNames()
	{
		return axisNames;
	}

	public String[] getUnits()
	{
		return units;
	}

	public double[] getScales()
	{
		return scales;
	}
}