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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.imglib2.realtransform.AffineTransform3D;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Affine3DUtils} class.
 *
 * <p>This test class specifically focuses on testing the behavior of the
 * {@link Affine3DUtils#isDiagonal(AffineTransform3D, double)} method,
 * which determines if the given affine transform's 3x3 linear portion is diagonal.
 */
class Affine3DUtilsTest
{

	@Test
	void testIsDiagonal_trueForPureDiagonalMatrix()
	{
		AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				2.0, 0, 0, 0,
				0, 3.0, 0, 0,
				0, 0, 4.0, 0
		);
		double epsilon = 1e-10;

		boolean result = Affine3DUtils.isDiagonal( transform, epsilon );

		assertTrue( result, "Expected a matrix with only diagonal elements to be identified as diagonal." );
	}

	@Test
	void testIsDiagonal_falseForNonDiagonalMatrix()
	{
		AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				1.0, 0.1, 0, 0,
				0, 2.0, 0.2, 0,
				0, 0, 3.0, 0
		);
		double epsilon = 1e-10;

		boolean result = Affine3DUtils.isDiagonal( transform, epsilon );

		assertFalse( result, "Expected a matrix with off-diagonal elements to not be identified as diagonal." );
	}

	@Test
	void testIsDiagonal_trueForApproximatelyDiagonalMatrix()
	{
		AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				2.0, 1e-9, 0, 0,
				1e-9, 3.0, 1e-9, 0,
				0, 0, 4.0, 0
		);
		double epsilon = 1e-8;

		boolean result = Affine3DUtils.isDiagonal( transform, epsilon );

		assertTrue( result, "Expected a matrix to be identified as diagonal when off-diagonal elements are below epsilon." );
	}

	@Test
	void testIsDiagonal_falseForLargerEpsilonViolations()
	{
		AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				1.0, 0.01, 0, 0,
				0, 2.0, 0.02, 0,
				0, 0, 3.0, 0
		);
		double epsilon = 1e-3;

		boolean result = Affine3DUtils.isDiagonal( transform, epsilon );

		assertFalse( result, "Expected a matrix to not be identified as diagonal when off-diagonal elements exceed epsilon." );
	}

	@Test
	void testIsDiagonal_trueForIdentityMatrix()
	{
		AffineTransform3D transform = new AffineTransform3D(); // Identity matrix by default
		double epsilon = 1e-10;

		boolean result = Affine3DUtils.isDiagonal( transform, epsilon );

		assertTrue( result, "Expected the identity matrix to be identified as diagonal." );
	}
}
