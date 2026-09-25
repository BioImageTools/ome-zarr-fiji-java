package ome.zarr.fijiui.plugin.command;

import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.write.InMemoryPyramidSaver;
import ome.zarr.imglib2.write.OmeZarrWritingOptions;
import ome.zarr.imglib2.write.PyramidContentsUtils;
import org.jspecify.annotations.NonNull;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import net.imglib2.img.basictypeaccess.array.ByteArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Macros > Create New Pyramidal Image" )
public class PluginToCreatePyramidContent extends DynamicCommand
{
	static final List< String > spatialUnits = Arrays.asList(
			"angstrom", "attometer", "centimeter", "decimeter", "exameter", "femtometer", "foot",
			"gigameter", "hectometer", "inch", "kilometer", "megameter", "meter", "micrometer",
			"mile", "millimeter", "nanometer", "parsec", "petameter", "picometer", "terameter",
			"yard", "yoctometer", "yottameter", "zeptometer", "zettameter" );

	static final List< String > temporalUnits = Arrays.asList(
			"attosecond", "centisecond", "day", "decisecond", "exasecond", "femtosecond", "gigasecond",
			"hectosecond", "hour", "kilosecond", "megasecond", "microsecond", "millisecond", "minute",
			"nanosecond", "petasecond", "picosecond", "second", "terasecond", "yoctosecond", "yottasecond",
			"zeptosecond", "zettasecond" );

	@Parameter( label = "Pixel type:", choices = { "uint8", "uint16", "uint32", "float", "double" } )
	String typeAsStr;

	@Parameter
	String name;

	@Parameter
	long xSize;

	@Parameter
	long ySize;

	@Parameter
	long zSize;

	@Parameter
	long numChannels;

	@Parameter
	long numTimepoints;

	@Parameter
	double xScale = 1.0;

	@Parameter
	double yScale = 1.0;

	@Parameter
	double zScale = 1.0;

	@Parameter( initializer = "unitsInitializer" )
	String xyzUnit = "micrometer";

	void unitsInitializer()
	{
		getInfo().getMutableInput( "xyzUnit", String.class ).setChoices( spatialUnits );
		getInfo().getMutableInput( "tUnit", String.class ).setChoices( temporalUnits );
	}

	@Parameter
	double cScale = 1.0;

	@Parameter
	String cUnit = "channel";

	@Parameter
	double tScale = 1.0;

	@Parameter
	String tUnit = "second";

	@Parameter( type = ItemIO.OUTPUT )
	Pyramidal pyramidal;

	@Override
	public void run()
	{
		InMemoryPyramidSaver< ? > saver = create();
		pyramidal = new PyramidalDataset( context(), saver.getPyramidContents(), 0 );

		//REMOVE LATER TODO
		System.out.println( saver.getPyramidContents() );
		System.out.println( pyramidal );
		//
		saver.getPyramidContents().asImg( 0 ).forEach( px -> px.setReal( 100 ) );
		//saver.getPyramidContents().asImg( 1 ).forEach( px -> px.set( 150 ) );
		//saver.getPyramidContents().asImg( 2 ).forEach( px -> px.set( 200 ) );
	}

	private < T extends NativeType< T > & RealType< T > > InMemoryPyramidSaver< T > create()
	{
		List< AxisCalibration > laxes = new ArrayList<>( 5 );
		laxes.add( new AxisCalibration( AxisCalibration.X, xyzUnit, xScale ) );
		laxes.add( new AxisCalibration( AxisCalibration.Y, xyzUnit, yScale ) );
		if ( zSize > 0 )
			laxes.add( new AxisCalibration( AxisCalibration.Z, xyzUnit, zScale ) );
		if ( numChannels > 0 )
			laxes.add( new AxisCalibration( AxisCalibration.C, cUnit, cScale ) );
		if ( numTimepoints > 0 )
			laxes.add( new AxisCalibration( AxisCalibration.T, tUnit, tScale ) );
		AxisCalibration[] axes = laxes.toArray( new AxisCalibration[ 0 ] );

		long[] dims = zSize > 0 ? new long[] { xSize, ySize, zSize } : new long[] { xSize, ySize };

		//primitive logic for now the downScaling:
		long frontViewSize = xSize * ySize * 2; //ad hoc factor mimicking 16bit data
		long topViewSize = xSize * zSize * 2;
		long sideViewSize = ySize * zSize * 2;
		long largestViewSize = Math.max( Math.max( frontViewSize, topViewSize ), sideViewSize );
		final long niceMaxViewSize = 128 * 128 * 2;
		int factors = 0;
		while ( largestViewSize > niceMaxViewSize )
		{
			largestViewSize /= 2;
			factors++;
		}

		double[] xyzIsoDownScales = new double[ factors ];
		for ( int i = 0; i < factors; i++ )
			xyzIsoDownScales[ i ] = Math.pow( 2.0, i + 1 );

		final T type = getType( typeAsStr );
		PyramidContents< T > p = PyramidContentsUtils.create(
				"skeleton pyramidal for " + name,
				type,
				dims,
				numChannels,
				numTimepoints,
				axes,
				xyzIsoDownScales
		);
		//System.out.println( p );

		InMemoryPyramidSaver< T > saver = new InMemoryPyramidSaver<>( name );
		assert saver.getPyramidContents() == null: "Found data in not-yet-initialized InMemorySaver.";
		//saver.initEmptyContainer();
		saver.initEmptyMultiscales( null, p, OmeZarrWritingOptions.defaultOptionsFor( p ) );

		return saver;
	}

	private < T extends RealType< T > & NativeType< T > > T getType( String type )
	{
		if ( type.startsWith( "uint" ) )
		{
			if ( type.endsWith( "8" ) )
			{
				return ( T ) new UnsignedByteType();
			}
			else if ( type.endsWith( "16" ) )
			{
				return ( T ) new UnsignedShortType();
			}
			else if ( type.endsWith( "32" ) )
			{
				return ( T ) new UnsignedIntType();
			}
		}
		else if ( "float".equals( type ) )
		{
			return ( T ) new FloatType();
		}
		else if ( "double".equals( type ) )
		{
			return ( T ) new DoubleType();
		}
		//a terrible default....
		return ( T ) new UnsignedShortType();
	}
}
