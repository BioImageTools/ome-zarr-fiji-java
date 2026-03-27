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
