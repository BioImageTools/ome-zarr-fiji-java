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
package ome.zarr.fiji.open;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.service.SciJavaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.fiji.read.OmeZarr;

/**
 * Finds the registered {@link OmeZarrOpener}s and runs them.
 * <p>
 * Discovery is SciJava's: every {@code @Plugin( type = OmeZarrOpener.class )} class
 * on the classpath is listed here, in priority order, no matter which jar it
 * came from. Scripts can add one at runtime through
 * {@link org.scijava.plugin.PluginService#addPlugin(PluginInfo)}.
 * <p>
 * The methods taking a {@code name} work on the {@code name} of the
 * {@link Plugin} annotation, which is what the user's settings persist. The
 * sentinel {@link #ASK} is not an opener but the alternative to one: it means
 * the user wants the selection dialog instead of a fixed choice.
 */
@Plugin( type = SciJavaService.class )
public class OmeZarrOpenerService extends AbstractPTService< OmeZarrOpener > implements SciJavaService
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * Persisted instead of an opener name when the user wants to be asked every
	 * time. Not a {@link OmeZarrOpener} and never returned by {@link #getOpenerInfos()}
	 * — the selection dialog opens nothing itself, it only picks an opener.
	 */
	public static final String ASK = "ask";

	@Override
	public Class< OmeZarrOpener > getPluginType()
	{
		return OmeZarrOpener.class;
	}

	/**
	 * All enabled openers, highest priority first.
	 *
	 * @return the registered openers' metadata, never {@code null}
	 */
	public List< PluginInfo< OmeZarrOpener > > getOpenerInfos()
	{
		final List< PluginInfo< OmeZarrOpener > > enabled = new ArrayList<>();
		for ( final PluginInfo< OmeZarrOpener > info : getPlugins() )
			if ( info.isEnabled() )
				enabled.add( info );
		return enabled;
	}

	/**
	 * @param name the {@link Plugin} name of the wanted opener
	 * @return the opener registered under {@code name}, or {@code null} if there
	 *   is none (an uninstalled third-party opener, or {@link #ASK})
	 */
	public PluginInfo< OmeZarrOpener > getOpenerInfo( final String name )
	{
		if ( name == null )
			return null;
		for ( final PluginInfo< OmeZarrOpener > info : getOpenerInfos() )
			if ( name.equals( nameOf( info ) ) )
				return info;
		return null;
	}

	/**
	 * Resolves what a persisted setting means right now.
	 * <p>
	 * A name the user chose is returned unchanged, even when no opener answers to
	 * it any more — the caller reports that, rather than this silently picking
	 * something else. Only when nothing was ever persisted does the
	 * highest-priority registered opener take over, which is how a freshly
	 * installed opener can make itself the default without overruling anybody.
	 *
	 * @param persistedName the stored opener name, or {@code null} if the user
	 *   never made a choice
	 * @return the opener name (or {@link #ASK}) to act on, or {@code null} when
	 *   nothing is persisted and no opener is registered either
	 */
	public String effectiveOpenerName( final String persistedName )
	{
		if ( persistedName != null && !persistedName.isEmpty() )
			return persistedName;
		final List< PluginInfo< OmeZarrOpener > > infos = getOpenerInfos();
		return infos.isEmpty() ? null : nameOf( infos.get( 0 ) );
	}

	/**
	 * Runs the named opener on {@code omeZarr}.
	 *
	 * @param name the {@link Plugin} name of the opener to run
	 * @param omeZarr the location to open and the settings to open it with
	 * @return {@code false} if no opener answers to {@code name}, in which case
	 *   nothing was opened
	 */
	public boolean open( final String name, final OmeZarr omeZarr )
	{
		final PluginInfo< OmeZarrOpener > info = getOpenerInfo( name );
		if ( info == null )
		{
			logger.debug( "No OME-Zarr opener named '{}' is registered.", name );
			return false;
		}
		open( info, omeZarr );
		return true;
	}

	/**
	 * Runs the given opener on {@code omeZarr}. An exception from the opener is
	 * logged and swallowed: a third-party opener must not take the whole opening
	 * pipeline down with it.
	 *
	 * @param info the opener to run
	 * @param omeZarr the location to open and the settings to open it with
	 */
	public void open( final PluginInfo< OmeZarrOpener > info, final OmeZarr omeZarr )
	{
		final OmeZarrOpener opener = createOpener( info );
		if ( opener == null )
			return;
		if ( logger.isDebugEnabled() )
			logger.debug( "Opening {} with the '{}' opener.", omeZarr.uri(), nameOf( info ) );
		try
		{
			opener.open( omeZarr );
		}
		catch ( final RuntimeException e )
		{
			logger.warn( "The '{}' OME-Zarr opener failed on {}", nameOf( info ), omeZarr.uri(), e );
		}
	}

	/**
	 * The identifier an opener is persisted and looked up under: its {@link Plugin}
	 * {@code name}, falling back to the fully qualified class name when the
	 * annotation names none.
	 *
	 * @param info the opener's metadata
	 * @return the opener's stable name
	 */
	public static String nameOf( final PluginInfo< OmeZarrOpener > info )
	{
		final String name = info.getName();
		return name == null || name.isEmpty() ? info.getClassName() : name;
	}

	/**
	 * The text shown to the user for an opener: its {@link Plugin} {@code label},
	 * falling back to its name.
	 *
	 * @param info the opener's metadata
	 * @return a non-empty display text
	 */
	public static String labelOf( final PluginInfo< OmeZarrOpener > info )
	{
		final String label = info.getLabel();
		return label == null || label.isEmpty() ? nameOf( info ) : label;
	}

	/**
	 * The explanatory text for an opener: its {@link Plugin} {@code description},
	 * falling back to its label.
	 *
	 * @param info the opener's metadata
	 * @return a non-empty description
	 */
	public static String descriptionOf( final PluginInfo< OmeZarrOpener > info )
	{
		final String description = info.getDescription();
		return description == null || description.isEmpty() ? labelOf( info ) : description;
	}

	/**
	 * The tooltip to show for an opener: what {@link OmeZarrOpener#tooltip} answers
	 * for this OME-Zarr, falling back to the static description.
	 *
	 * @param info the opener's metadata
	 * @param omeZarr the OME-Zarr the tooltip is asked for
	 * @return a non-empty tooltip text
	 */
	public String tooltipOf( final PluginInfo< OmeZarrOpener > info, final OmeZarr omeZarr )
	{
		final OmeZarrOpener opener = createOpener( info );
		final String tooltip = opener == null ? null : opener.tooltip( omeZarr );
		return tooltip == null || tooltip.isEmpty() ? descriptionOf( info ) : tooltip;
	}

	/** Instantiates an opener, reporting rather than throwing when that fails. */
	private OmeZarrOpener createOpener( final PluginInfo< OmeZarrOpener > info )
	{
		final OmeZarrOpener opener = pluginService().createInstance( info );
		if ( opener == null )
			logger.warn( "Could not instantiate the OME-Zarr opener {}.", info.getClassName() );
		return opener;
	}
}
