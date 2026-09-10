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

/**
 * Finds the registered {@link ZarrOpener}s and runs them.
 * <p>
 * Discovery is SciJava's: every {@code @Plugin( type = ZarrOpener.class )} class
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
public class ZarrOpenerService extends AbstractPTService< ZarrOpener > implements SciJavaService
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * Persisted instead of an opener name when the user wants to be asked every
	 * time. Not a {@link ZarrOpener} and never returned by {@link #getOpenerInfos()}
	 * — the selection dialog opens nothing itself, it only picks an opener.
	 */
	public static final String ASK = "ask";

	@Override
	public Class< ZarrOpener > getPluginType()
	{
		return ZarrOpener.class;
	}

	/**
	 * All enabled openers, highest priority first.
	 *
	 * @return the registered openers' metadata, never {@code null}
	 */
	public List< PluginInfo< ZarrOpener > > getOpenerInfos()
	{
		final List< PluginInfo< ZarrOpener > > enabled = new ArrayList<>();
		for ( final PluginInfo< ZarrOpener > info : getPlugins() )
			if ( info.isEnabled() )
				enabled.add( info );
		return enabled;
	}

	/**
	 * @param name the {@link Plugin} name of the wanted opener
	 * @return the opener registered under {@code name}, or {@code null} if there
	 *   is none (an uninstalled third-party opener, or {@link #ASK})
	 */
	public PluginInfo< ZarrOpener > getOpenerInfo( final String name )
	{
		if ( name == null )
			return null;
		for ( final PluginInfo< ZarrOpener > info : getOpenerInfos() )
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
		final List< PluginInfo< ZarrOpener > > infos = getOpenerInfos();
		return infos.isEmpty() ? null : nameOf( infos.get( 0 ) );
	}

	/**
	 * Runs the named opener on {@code request}.
	 *
	 * @param name the {@link Plugin} name of the opener to run
	 * @param request the location to open and the settings to open it with
	 * @return {@code false} if no opener answers to {@code name}, in which case
	 *   nothing was opened
	 */
	public boolean open( final String name, final ZarrOpenRequest request )
	{
		final PluginInfo< ZarrOpener > info = getOpenerInfo( name );
		if ( info == null )
		{
			logger.debug( "No OME-Zarr opener named '{}' is registered.", name );
			return false;
		}
		open( info, request );
		return true;
	}

	/**
	 * Runs the given opener on {@code request}. An exception from the opener is
	 * logged and swallowed: a third-party opener must not take the whole opening
	 * pipeline down with it.
	 *
	 * @param info the opener to run
	 * @param request the location to open and the settings to open it with
	 */
	public void open( final PluginInfo< ZarrOpener > info, final ZarrOpenRequest request )
	{
		final ZarrOpener opener = createOpener( info );
		if ( opener == null )
			return;
		if ( logger.isDebugEnabled() )
			logger.debug( "Opening {} with the '{}' opener.", request.uri(), nameOf( info ) );
		try
		{
			opener.open( request );
		}
		catch ( final RuntimeException e )
		{
			logger.warn( "The '{}' OME-Zarr opener failed on {}", nameOf( info ), request.uri(), e );
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
	public static String nameOf( final PluginInfo< ZarrOpener > info )
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
	public static String labelOf( final PluginInfo< ZarrOpener > info )
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
	public static String descriptionOf( final PluginInfo< ZarrOpener > info )
	{
		final String description = info.getDescription();
		return description == null || description.isEmpty() ? labelOf( info ) : description;
	}

	/**
	 * The tooltip to show for an opener: what {@link ZarrOpener#tooltip} answers
	 * for this request, falling back to the static description.
	 *
	 * @param info the opener's metadata
	 * @param request the request the tooltip is asked for
	 * @return a non-empty tooltip text
	 */
	public String tooltipOf( final PluginInfo< ZarrOpener > info, final ZarrOpenRequest request )
	{
		final ZarrOpener opener = createOpener( info );
		final String tooltip = opener == null ? null : opener.tooltip( request );
		return tooltip == null || tooltip.isEmpty() ? descriptionOf( info ) : tooltip;
	}

	/** Instantiates an opener, reporting rather than throwing when that fails. */
	private ZarrOpener createOpener( final PluginInfo< ZarrOpener > info )
	{
		final ZarrOpener opener = pluginService().createInstance( info );
		if ( opener == null )
			logger.warn( "Could not instantiate the OME-Zarr opener {}.", info.getClassName() );
		return opener;
	}
}
