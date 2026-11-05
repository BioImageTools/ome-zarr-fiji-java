package ome.zarr.n5;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.write.OmeZarrWritingOptions;
import ome.zarr.imglib2.write.PyramidSaver;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import java.net.URI;

public class N5PyramidSaver< T extends RealType< T > & NativeType< T > > implements PyramidSaver< T >
{
	public N5PyramidSaver( final URI uri, final OmeZarrWritingOptions options )
	{
		this.uri = uri;
		this.options = options;
		this.n5w = N5Factory.createWriter( uri.toString() );
	}

	private final URI uri;

	private final OmeZarrWritingOptions options;

	private final N5Writer n5w;

	@Override
	public void initEmptyContainer()
	{
		n5w.setVersion();
	}

	private String datasetPath;

	private DatasetAttributes datasetAttributes;

	@Override
	public void initEmptyMultiscales( final String path,
			final PyramidContents< T > pc,
			final OmeZarrWritingOptions opts )
	{
		final Img< T > refImg = pc.asLargestImg();
		datasetAttributes = n5w.createDataset( path,
				refImg.dimensionsAsLongArray(),
				opts.getChunkShapePerLevel()[ 0 ],
				N5Utils.dataType( refImg.firstElement() ),
				new RawCompression() );
		n5w.createDataset( path, datasetAttributes );
		datasetPath = path;
	}

	@Override
	public void writeRegion( final RandomAccessibleInterval< T > region,
			final int level )
	{
		N5Utils.saveRegion( region, n5w, datasetPath + "/S" + level );
	}

	// ======================================================================
	// ======================================================================
	// ======================================================================
	// ======================================================================

	public static < T extends NativeType< T > > void writeFullImmediately(
			final RandomAccessibleInterval< T > img,
			final String containerPath,
			final String dsetSubPath )
	{
		final long[] dimensions = img.dimensionsAsLongArray();
		final int[] blockSize = new int[ dimensions.length ];
		for ( int i = 0; i < blockSize.length; i++ )
			blockSize[ i ] = Math.min( 128, ( int ) dimensions[ i ] );

		final N5Writer n5w = N5Factory.createWriter( containerPath );
		final DatasetAttributes attributes = n5w.createDataset( dsetSubPath, dimensions, blockSize,
				N5Utils.dataType( img.firstElement() ), new RawCompression() );

		N5Utils.saveRegion( img, n5w, dsetSubPath, attributes );

		// the region of the image covered by each block
		//final CellGrid grid = new CellGrid( dimensions, blockSize );
	}
}
