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
