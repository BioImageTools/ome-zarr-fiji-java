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
package ome.zarr.fijiui.plugin.command.settings;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.fiji.open.ZarrOpener;
import ome.zarr.fiji.open.ZarrOpenerService;
import ome.zarr.fijiui.open.options.ZarrOpeningSettings;
import ome.zarr.fijiui.open.options.ZarrBackend;

/**
 * A FIJI/ImageJ command to select what to do when an OME-Zarr image is Drag &amp; Dropped into Fiji.
 * <p>
 * The list of choices is not fixed: it is one entry per registered
 * {@link ZarrOpener} — including those contributed by other Fiji plugins — plus
 * the "ask me" entry that opens the selection dialog instead.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Settings > Opening Behavior Settings", initializer = "init" )
public class OpeningBehaviorSettings extends DynamicCommand
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final Integer WIDTH = 20;

	/** The choice that stands for {@link ZarrOpenerService#ASK} rather than for an opener. */
	static final String ASK_LABEL = "Ask me every time";

	@SuppressWarnings( "all" )
	@Parameter
	private PrefService prefService;

	@SuppressWarnings( "all" )
	@Parameter
	private ZarrOpenerService openerService;

	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String infoMessage = "<html>"
			+ "<body width=" + WIDTH + "cm align=left>"
			+ "Configure the behavior when OME-Zarr datasets are drag & dropped (local folders) or copy & pasted (local and remote paths) into Fiji.<br>"
			+ "Choose a default behavior, optionally limit the preferred resolution and choose the reader backend libary."
			+ "</body>"
			+ "</html>";

	@SuppressWarnings( "all" )
	@Parameter( label = "Default opening behavior", description = "Choose the opening behavior if you drag & drop local OME-Zarr folders or copy & paste OME-Zarr paths (local/remote) into Fiji", initializer = "initOpenerChoices" )
	private String defaultOpener;

	@SuppressWarnings( "all" )
	@Parameter( label = "Preferred width for 'matching resolution' choice" )
	private int preferredWidth;

	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String preferredWidthInfo = "<html>"
			+ "<body width=" + WIDTH + "cm align=left>"
			+ "For the 'matching resolution' behavior, set a preferred maximum width.<br>"
			+ "Fiji will open the highest available resolution whose width is below this value.<br>"
			+ "If no such resolution exists, the image will not be opened."
			+ "</body>"
			+ "</html>";

	@SuppressWarnings( "all" )
	@Parameter( label = "Reader backend", description = "Choose which library is used to read OME-Zarr datasets", initializer = "initZarrBackends" )
	private String readerBackend;

	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String readerBackendInfo = "<html>"
			+ "<body width=" + WIDTH + "cm align=left>"
			+ "N5 supports OME-Zarr v0.3 (Zarr v2) - v0.5 (Zarr v3). <b>Fiji latest only</b><br>"
			+ "zarr-java supports OME-Zarr v0.4 (Zarr v2) - v0.5 (Zarr v3)."
			+ "</body>"
			+ "</html>";

	private ZarrOpeningSettings settings;

	/** Shown choice to the opener name (or {@link ZarrOpenerService#ASK}) it stands for. */
	private Map< String, String > choices;

	@Override
	public void run()
	{
		final String openerName = choices().get( defaultOpener );
		if ( openerName == null )
			logger.warn( "Unknown opening behavior '{}', keeping the previous one.", defaultOpener );
		else
			settings.setOpenerName( openerName );
		settings.setPreferredMaxWidth( preferredWidth );
		settings.setBackend( ZarrBackend.getByDescription( readerBackend ) );
		logger.debug( "Now saving OME-Zarr settings to user preferences. Opener: {}, preferredWidth: {}, readerBackend: {}",
				settings.getOpenerName(), preferredWidth, settings.getBackend() );
		settings.saveSettingsToPreferences( prefService );
	}

	@SuppressWarnings( "unused" )
	private void init()
	{
		settings = ZarrOpeningSettings.loadSettingsFromPreferences( prefService );
		defaultOpener = choiceFor( openerService.effectiveOpenerName( settings.getOpenerName() ) );
		preferredWidth = settings.getPreferredMaxWidth();
		readerBackend = settings.getBackend().getDescription();
	}

	@SuppressWarnings( "unused" )
	private void initOpenerChoices()
	{
		getInfo().getMutableInput( "defaultOpener", String.class ).setChoices( new ArrayList<>( choices().keySet() ) );
	}

	@SuppressWarnings( "unused" )
	private void initZarrBackends()
	{
		getInfo().getMutableInput( "readerBackend", String.class )
				.setChoices( backendDescriptions( ZarrBackend.values() ) );
	}

	/**
	 * The choices to offer, in priority order, mapped to what each one means. Two
	 * openers from different jars may well carry the same label, so a repeated one
	 * is disambiguated by its name.
	 */
	private Map< String, String > choices()
	{
		if ( choices == null )
			choices = openerChoices( openerService.getOpenerInfos() );
		return choices;
	}

	/** The choice standing for {@code openerName}, or the first one as a fallback. */
	private String choiceFor( final String openerName )
	{
		for ( final Map.Entry< String, String > choice : choices().entrySet() )
			if ( choice.getValue().equals( openerName ) )
				return choice.getKey();
		final String fallback = choices().keySet().iterator().next();
		logger.debug( "The configured opener '{}' is not installed, offering '{}' instead.", openerName, fallback );
		return fallback;
	}

	static Map< String, String > openerChoices( final List< PluginInfo< ZarrOpener > > infos )
	{
		final Map< String, String > choices = new LinkedHashMap<>();
		for ( final PluginInfo< ZarrOpener > info : infos )
		{
			final String name = ZarrOpenerService.nameOf( info );
			final String label = ZarrOpenerService.labelOf( info );
			choices.put( choices.containsKey( label ) ? label + " [" + name + "]" : label, name );
		}
		choices.put( ASK_LABEL, ZarrOpenerService.ASK );
		return choices;
	}

	static List< String > backendDescriptions( final ZarrBackend[] values )
	{
		return Arrays.stream( values ).map( ZarrBackend::getDescription ).collect( Collectors.toList() );
	}
}
