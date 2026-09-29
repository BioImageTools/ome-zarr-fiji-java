package ome.zarr.fiji;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import ome.zarr.imglib2.write.InMemoryPyramidSaver;
import org.scijava.Context;

public class PyramidalDatasetInMemory extends PyramidalDataset implements Pyramidal
{
	public < T extends NativeType< T > & RealType< T > > PyramidalDatasetInMemory( Context context, InMemoryPyramidSaver< T > saver,
			int resolutionLevel )
	{
		super( context, saver.getPyramidContents(), resolutionLevel );
		this.saver = saver;
	}

	private final InMemoryPyramidSaver< ? > saver;

	public InMemoryPyramidSaver< ? > getInMemoryPyramidSaver()
	{
		return saver;
	}
}
