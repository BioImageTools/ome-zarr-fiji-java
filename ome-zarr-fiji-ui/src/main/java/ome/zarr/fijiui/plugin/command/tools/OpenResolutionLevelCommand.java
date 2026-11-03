/*-
 * #%L
 * OME-Zarr integration into FIJI
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
package ome.zarr.fijiui.plugin.command.tools;

import java.util.ArrayList;
import java.util.List;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ome.zarr.fiji.Pyramidal;
import ome.zarr.fiji.PyramidalDataset;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.fijiui.util.PyramidTexts;
import ome.zarr.imglib2.PyramidContents;

@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Open Current OME-Zarr Image in ImageJ...", description = "Open the current OME-Zarr image in ImageJ at a chosen resolution level" )
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

	/** Explains the choices; set in {@link #initialize()} to match the image's axes. */
	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String legend;

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
		final PyramidContents< ? > contents = active.getPyramidContents();
		legend = PyramidTexts.describeAxes( contents );
		final int numResolutions = contents.numResolutionLevels();
		final List< String > choices = new ArrayList<>();
		for ( int i = 0; i < numResolutions; i++ )
			choices.add( PyramidTexts.describeLevel( contents, i ) );
		final MutableModuleItem< String > item = getInfo().getMutableInput( "resolutionLevel", String.class );
		item.setChoices( choices );
		if ( !choices.contains( resolutionLevel ) )
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
		// one choice per level, in level order (see initialize), so the index is the 0-based level
		final int level = getInfo().getMutableInput( "resolutionLevel", String.class ).getChoices().indexOf( resolutionLevel );
		final PyramidalDataset levelDataset = new PyramidalDataset( pyramidal.getContext(), pyramidal.getPyramidContents(), level );
		uiService.show( levelDataset );
	}
}
