package sc.fiji.ome.zarr.pyramid;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;

public class N5ReaderDemo
{
	public static void main( String[] args )
	{
		//final String path = "/home/ulman/Documents/talks/CEITEC/2025_11_ZarrSymposium_Zurich/data/MitoEM_fixedRes.zarr/MitoEM_fixedRes";
		//final String path = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr/0";
		final String path = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr/0";
		final N5Reader n5reader = new N5Factory().openReader( path );
		System.out.println( "got reader: " + n5reader );
		N5Metadata m = N5DatasetDiscoverer.discover( n5reader ).getMetadata();
		System.out.println( "got metadata: " + m );

		if ( m instanceof OmeNgffMetadata )
		{
			OmeNgffMetadata mv04 = ( OmeNgffMetadata ) m;
			System.out.println( "name: " + mv04.getName() );
			for ( OmeNgffMultiScaleMetadata ms : mv04.multiscales )
			{
				System.out.println( "----------------" );
				System.out.println( "name: " + ms.name + "     type: " + ms.type + "     version: " + ms.version );
				System.out.println( ms.coordinateTransformations );
			}
		}

		/*
		if (m instanceof OmeNgffV05Metadata) {
			OmeNgffV05Metadata mv05 = (OmeNgffV05Metadata)m;
			System.out.println("name: "+mv05.getName());
			for (OmeNgffMultiScaleMetadata ms : mv05.multiscales) {
				System.out.println("----------------");
				System.out.println("name: "+ms.name+"     type: "+ms.type+"     version: "+ms.version);
				System.out.println(ms.coordinateTransformations);
			}
		}
		*/

		//TODO fetch v0.5 NGFF Metadata -> ask John
		//there was supposed to be some class for it
	}
}
