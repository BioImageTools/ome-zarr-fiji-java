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

/**
 * Lightweight, framework-agnostic axis descriptor produced by a
 * {@link sc.fiji.ome.zarr.pyramid.backend.PyramidBackend}. Carries just the
 * information needed to calibrate one image dimension: a logical axis name
 * (e.g. {@code "x"}, {@code "t"}), a physical unit string, and the pixel
 * spacing at the selected resolution level.
 */
public final class AxisCalibration
{
	/** OME-Zarr axis name for the x (horizontal) spatial axis. */
	public static final String X = "x";

	/** OME-Zarr axis name for the y (vertical) spatial axis. */
	public static final String Y = "y";

	/** OME-Zarr axis name for the z (depth) spatial axis. */
	public static final String Z = "z";

	/** OME-Zarr axis name for the channel axis. */
	public static final String C = "c";

	/** OME-Zarr axis name for the time axis. */
	public static final String T = "t";

	/** Logical axis name as it appears in OME-Zarr metadata (e.g. "x", "y", "z", "c", "t"). */
	public final String name;

	/** Physical unit string, or an empty string when absent. */
	public final String unit;

	/** Pixel spacing at the selected resolution level in the given unit. */
	public final double scale;

	public AxisCalibration( final String name, final String unit, final double scale )
	{
		this.name = name;
		this.unit = unit;
		this.scale = scale;
	}
}
