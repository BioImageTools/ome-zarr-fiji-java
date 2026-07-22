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
package sc.fiji.ome.zarr.plugins;

import java.util.ArrayList;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import sc.fiji.ome.zarr.pyramid.fiji.Pyramidal;
import sc.fiji.ome.zarr.pyramid.fiji.PyramidalDataset;
import sc.fiji.ome.zarr.pyramid.fiji.plugins.PyramidalService;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Resolution Level..." )
public class OpenResolutionLevelCommand extends DynamicCommand
{
	@Parameter
	private LogService logService;

	@Parameter
	private UIService uiService;

	@Parameter
	private PyramidalService pyramidalService;

	@Parameter
	private Pyramidal pyramidal;

	@Parameter( label = "Resolution Level" )
	private String resolutionLevel;

	@Override
	public void initialize()
	{
		// At this point, @Parameter pyramidal has not been populated yet.
		// We have to manually check: Is it already staged in the module inputs?
		Pyramidal active = ( Pyramidal ) getInput( "pyramidal" );
		if ( active == null )
		{
			// Nothing yet? Then get it from the service.
			active = pyramidalService.getActivePyramidal();
		}

		if ( active == null )
		{
			cancel( "The active image is not an OME-Zarr multi resolution dataset." );
			return;
		}
		final int numResolutions = active.getPyramidContents().numResolutionLevels();
		final List< String > choices = new ArrayList<>();
		for ( int i = 0; i < numResolutions; i++ )
			choices.add( "Resolution " + i );
		final MutableModuleItem< String > item = getInfo().getMutableInput( "resolutionLevel", String.class );
		item.setChoices( choices );
		if ( resolutionLevel == null || !choices.contains( resolutionLevel ) )
			item.setValue( this, choices.get( 0 ) );
	}

	@Override
	public void run()
	{
		if ( pyramidal == null )
		{
			logService.error( "Cannot open resolution level: the active image is not an OME-Zarr pyramidal dataset." );
			return;
		}
		final int level = Integer.parseInt( resolutionLevel.replace( "Resolution ", "" ) );
		final PyramidalDataset levelDataset = new PyramidalDataset( pyramidal.getContext(), pyramidal.getPyramidContents(), level );
		uiService.show( levelDataset );
	}
}
