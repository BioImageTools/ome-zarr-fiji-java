/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package sc.fiji.ome.zarr.settings;

import java.util.NoSuchElementException;

/**
 * Options for opening OME-Zarr datasets in different viewers and resolutions.
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
