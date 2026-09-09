package ome.zarr.fijiui.plugin.command;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
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
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.AxisCalibration;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import net.imglib2.img.basictypeaccess.array.ByteArray;

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

	@Parameter
	PyramidalService pyramidalService;

	@Parameter
	PyramidContents< ? > pyramidContents;

	@Override
	public void run()
	{
		AxisCalibration[] baseResAxes = new AxisCalibration[] {
				new AxisCalibration( AxisCalibration.X, xyzUnit, xScale ),
				new AxisCalibration( AxisCalibration.Y, xyzUnit, yScale ),
				new AxisCalibration( AxisCalibration.Z, xyzUnit, zScale ),
				new AxisCalibration( AxisCalibration.C, cUnit, cScale ),
				new AxisCalibration( AxisCalibration.T, tUnit, tScale ) };
		AxisCalibration[][] axes = new AxisCalibration[ 3 ][];
		axes[ 0 ] = baseResAxes;
		axes[ 1 ] = downScaledAxes( baseResAxes, 2.0 );
		axes[ 2 ] = downScaledAxes( baseResAxes, 4.0 );

		AffineTransform3D[] transforms = new AffineTransform3D[ 3 ];
		transforms[ 0 ] = new AffineTransform3D();
		transforms[ 1 ] = new AffineTransform3D();
		transforms[ 2 ] = new AffineTransform3D();

		final CellGrid baseCellGrid = new CellGrid(
				new long[] { xSize, ySize, zSize, numChannels, numTimepoints },
				new int[] { 256, 256, 256, 1, 1 }); //chunks size basically...

		LazyCellImg.Get<Cell<?>> emptyCellProvider = index -> {
			final long[] cellMin  = new long[5];
			final int[]  cellDims = new int[5];
			baseCellGrid.getCellDimensions(index, cellMin, cellDims);
			return new Cell<>(cellDims, cellMin,
					getBackingArray(typeAsStr, (int)Intervals.numElements(cellDims));
		};
		LazyCellImg baseImg = new LazyCellImg<>(baseCellGrid, (NativeType) getType(typeAsStr), emptyCellProvider);
		CachedCellImg[] imgs = new CachedCellImg[ 3 ];
		imgs[0] = baseImg;

		pyramidContents = builderForType( getType( typeAsStr ) )
				.axesPerLevel( axes )
				.transforms( transforms )
				.cachedCellImgs(imgs)
				.name( name )
				.build();
	}

	private AxisCalibration[] downScaledAxes( AxisCalibration[] axes, double downSizeFactor )
	{
		AxisCalibration[] newAxes = new AxisCalibration[ axes.length ];
		for ( int i = 0; i < newAxes.length; ++i )
		{
			double scale = axes[ i ].name.equals( AxisCalibration.C ) ||
					axes[ i ].name.equals( AxisCalibration.T ) ? axes[ i ].scale
							: axes[ i ].scale / downSizeFactor;
			newAxes[ i ] = new AxisCalibration( axes[ i ].name, axes[ i ].unit, scale );
		}
		return newAxes;
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

	private < A extends ArrayDataAccess< A > > ArrayDataAccess<A> getBackingArray(
			  String type, int numEntities )
	{
		if ( type.startsWith( "uint" ) )
		{
			if ( type.endsWith( "8" ) )
			{
				return (A)new ByteArray(numEntities);
			}
			else if ( type.endsWith( "16" ) )
			{
				return ( A ) new ShortArray(numEntities);
			}
			else if ( type.endsWith( "32" ) )
			{
				return ( A ) new IntArray(numEntities);
			}
		}
		else if ( "float".equals( type ) )
		{
			return ( A ) new FloatArray(numEntities);
		}
		else if ( "double".equals( type ) )
		{
			return ( A ) new DoubleArray(numEntities);
		}
		//a terrible default....
		return ( A ) new ShortArray(numEntities);
	}

	private < T extends RealType< T > & NativeType< T > > PyramidContents.Builder< T > builderForType( T type )
	{
		final PyramidContents.Builder< T > b = PyramidContents.builder();
		b.type( type );
		return b;
	}
}
