package sc.fiji.ome.zarr.util;

import java.util.NoSuchElementException;

/**
 * Options for opening Zarr datasets in different viewers and resolutions.
 */
public enum ZarrOpenBehavior
{
	/**
	 * Open the highest available single-resolution in ImageJ.
	 */
	IMAGEJ_HIGHEST_RESOLUTION( "Open the highest available single-resolution in ImageJ" ),

	/**
	 * Open a matching single-resolution image in ImageJ.
	 */
	IMAGEJ_CUSTOM_RESOLUTION( "Open a matching single-resolution image in ImageJ" ),

	/**
	 * Open as multi-resolution in BigDataViewer (BDV).
	 */
	BDV_MULTI_RESOLUTION( "Open as a multi-resolution source in BigDataViewer" ),

	/**
	 * Always open the selection dialog with different icons.
	 */
	SHOW_SELECTION_DIALOG( "Open selection dialog with choices" );

	private final String description;

	ZarrOpenBehavior( final String description )
	{
		this.description = description;
	}

	public static ZarrOpenBehavior getByName( final String name )
	{
		for ( final ZarrOpenBehavior option : values() )
			if ( option.name().equals( name ) )
				return option;
		throw new NoSuchElementException( name );
	}

	public static ZarrOpenBehavior getByDescription( final String description )
	{
		for ( final ZarrOpenBehavior behavior : values() )
			if ( behavior.description.equals( description ) )
				return behavior;
		return null;
	}

	public String getDescription()
	{
		return description;
	}
}
