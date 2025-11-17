package sc.fiji.ome.zarr.pyramid;

import java.util.Collections;
import java.util.List;

/**
 * This is basically one pyramid, which is a list of
 * incrementally down-scaled images (referred here as 'scales').
 */
public class Multiscales
{
    private final String name;
    private final List< Axis > axes;    //length matches number of dimensions
    private final List< Scale > scales; //length matches the number of resolution levels
    private final int numDimensions;

    public Multiscales(String name, List< Axis > axes, List< Scale > scales) {
        this.name = name;
        this.axes = axes;
        this.scales = scales;
        this.numDimensions = axes.size();
    }

    /**
     * This is a representation of a single scale, typically this
     * is one image of a resolution pyramid. Just note that a chain
     * of transformations could be provided.
     *
     * The lengths of the both arrays should be the same, and should
     * match the number of dimensions.
     */
    public static class Scale {
        public String path; //TODO why this is here??
        public double[] scaleFactors;
        public double[] offsets;
    }

    public static class Axis
    {
        public static final String CHANNEL_TYPE = "channel";
        public static final String TIME_TYPE = "time";
        public static final String SPATIAL_TYPE = "space";
        //TODO: renamed to SPATIAL_TYPE_X and drop X_AXIS_NAME, and

        public static final String X_AXIS_NAME = "x";
        public static final String Y_AXIS_NAME = "y";
        public static final String Z_AXIS_NAME = "z";

        public String name;
        public String type;
        public String unit;
    }

    public int getChannelAxisIndex()
    {
        for ( int d = 0; d < numDimensions; d++ )
            if ( axes.get( d ).type.equals( Axis.CHANNEL_TYPE ) )
                return d;
        return -1;
    }

    public int getTimepointAxisIndex()
    {
        for ( int d = 0; d < numDimensions; d++ )
            if ( axes.get( d ).type.equals( Axis.TIME_TYPE ) )
                return d;
        return -1;
    }

    public int getSpatialAxisIndex( String axisName )
    {
        for ( int d = 0; d < numDimensions; d++ )
            if ( axes.get( d ).type.equals( Axis.SPATIAL_TYPE )
                 && axes.get( d ).name.equals( axisName ) )
                return d;
        return -1;
    }

    public List< Axis > getAxes()
    {
        return Collections.unmodifiableList( axes );
    }

    public List< Scale > getScales()
    {
        return Collections.unmodifiableList( scales );
    }

    public int numDimensions()
    {
        return numDimensions;
    }
}
