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
package sc.fiji.ome.zarr.util;

import net.imglib2.realtransform.AffineTransform3D;

public class Affine3DUtils
{
	private Affine3DUtils()
	{
		// prevent instantiation
	}

	/**
	 * Checks whether the linear (3x3) part of the transform is diagonal.
	 *
	 * <p>A diagonal matrix means that only the elements on the main diagonal
	 * are non-zero. In geometric terms, this corresponds to scaling along
	 * the coordinate axes without any rotation or shear.</p>
	 *
	 * @param transform the affine transform
	 * @param epsilon tolerance for floating-point comparisons
	 * @return true if all off-diagonal elements are approximately zero
	 */
	public static boolean isDiagonal( final AffineTransform3D transform, final double epsilon )
	{
		for ( int rowIndex = 0; rowIndex < 3; rowIndex++ )
		{
			for ( int columnIndex = 0; columnIndex < 3; columnIndex++ )
			{
				if ( rowIndex != columnIndex &&
						Math.abs( transform.get( rowIndex, columnIndex ) ) > epsilon )
				{
					return false;
				}
			}
		}
		return true;
	}


	/**
	 * Checks whether the transform represents pure scaling (no rotation or shear).	 *
	 * @param transform the affine transform to be analyzed
	 * @param epsilon tolerance for floating-point comparisons to consider off-diagonal elements as zero
	 * @return true if the transformation is a scaling transformation, false otherwise
	 */
	public static boolean isScaling( final AffineTransform3D transform, final double epsilon )
	{
		return isDiagonal( transform, epsilon );
	}
}
