package sc.fiji.ome.zarr.pyramid;

public class PyramidalBdvDataset implements Pyramidal {


	private final Pyramidal5DImageData<?> data;

	PyramidalBdvDataset( final Pyramidal5DImageData<?> data ) {
		this.data = data;




	}

	@Override
	public Pyramidal5DImageData<?> getPyramidal5DImageData() {
		return data;
	}
}
