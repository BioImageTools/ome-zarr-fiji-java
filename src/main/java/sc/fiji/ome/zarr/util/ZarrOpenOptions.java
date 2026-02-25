package sc.fiji.ome.zarr.util;

import java.util.NoSuchElementException;

/**
 * Options for opening Zarr datasets in different viewers and resolutions.
 */
public enum ZarrOpenOptions
{
	/**
	 * Open the highest resolution possible in ImageJ.
	 */
	IMAGEJ_HIGHEST_RESOLUTION( "Open highest possible resolution in ImageJ" ),

	/**
	 * Open a custom resolution configurable max width pixel in ImageJ.
	 */
	IMAGEJ_CUSTOM_RESOLUTION( "Open resolution closest to custom in ImageJ" ),

	/**
	 * Open as multi-resolution in BigDataViewer (BDV).
	 */
	BDV_MULTI_RESOLUTION( "Open multi resolution in BigDataViewer" ),

	/**
	 * Always open the selection dialog with different icons.
	 */
	SHOW_SELECTION_DIALOG( "Open selection dialog with choices" );

	private final String description;

	ZarrOpenOptions( final String description )
	{
		this.description = description;
	}

	public static ZarrOpenOptions getByName( final String name )
	{
		for ( final ZarrOpenOptions option : values() )
			if ( option.name().equals( name ) )
				return option;
		throw new NoSuchElementException( name );
	}

	public static ZarrOpenOptions getByDescription( final String description )
	{
		for ( final ZarrOpenOptions option : values() )
			if ( option.description.equals( description ) )
				return option;
		return null;
	}

	public String getDescription()
	{
		return description;
	}
}
