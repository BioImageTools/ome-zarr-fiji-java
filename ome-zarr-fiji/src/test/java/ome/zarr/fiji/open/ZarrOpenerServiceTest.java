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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.Priority;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;

/**
 * Unit tests for {@link ZarrOpenerService}. The openers are added to the
 * context programmatically rather than through the annotation index: this module
 * deliberately ships none of its own, so the registry is empty here unless a
 * test fills it.
 */
class ZarrOpenerServiceTest
{
	private static final URI URI_UNDER_TEST = URI.create( "file:/tmp/does-not-need-to-exist.ome.zarr" );

	/** Records that it ran, without reading anything. */
	public static class RecordingOpener implements ZarrOpener
	{
		static int opened;

		@Override
		public void open( final ZarrOpenRequest request )
		{
			opened++;
		}
	}

	/** Stands in for a third-party opener that throws on its own. */
	public static class FailingOpener implements ZarrOpener
	{
		@Override
		public void open( final ZarrOpenRequest request )
		{
			throw new IllegalStateException( "deliberate failure" );
		}

		@Override
		public String tooltip( final ZarrOpenRequest request )
		{
			return "tooltip for " + request.uri();
		}
	}

	private static PluginInfo< ZarrOpener > register( final Context context,
			final Class< ? extends ZarrOpener > openerClass, final String name, final double priority )
	{
		final PluginInfo< ZarrOpener > info = new PluginInfo<>( openerClass, ZarrOpener.class );
		info.setName( name );
		info.setPriority( priority );
		context.getService( PluginService.class ).addPlugin( info );
		return info;
	}

	private static ZarrOpenRequest requestFor( final Context context )
	{
		return new ZarrOpenRequest( URI_UNDER_TEST, context, null, null, message -> {} );
	}

	@Test
	void anUnconfiguredUserGetsTheHighestPriorityOpener()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			// Nothing registered: there is nothing to fall back to either.
			assertTrue( openerService.getOpenerInfos().isEmpty() );
			assertNull( openerService.effectiveOpenerName( null ) );

			register( context, RecordingOpener.class, "low", Priority.LOW );
			register( context, FailingOpener.class, "high", Priority.HIGH );

			assertEquals( "high", openerService.effectiveOpenerName( null ),
					"With nothing persisted the highest-priority opener takes over" );
			assertEquals( "high", openerService.effectiveOpenerName( "" ),
					"An empty persisted name counts as nothing persisted" );
			assertEquals( "low", openerService.effectiveOpenerName( "low" ),
					"An explicit choice wins over priority" );
			assertEquals( "uninstalled", openerService.effectiveOpenerName( "uninstalled" ),
					"A name whose opener is gone is reported, not silently replaced" );
			assertEquals( ZarrOpenerService.ASK, openerService.effectiveOpenerName( ZarrOpenerService.ASK ) );
		}
	}

	@Test
	void openersAreListedByPriority()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			register( context, RecordingOpener.class, "low", Priority.LOW );
			register( context, FailingOpener.class, "high", Priority.HIGH );

			final List< PluginInfo< ZarrOpener > > infos = openerService.getOpenerInfos();
			assertEquals( 2, infos.size() );
			assertEquals( "high", ZarrOpenerService.nameOf( infos.get( 0 ) ) );
			assertEquals( "low", ZarrOpenerService.nameOf( infos.get( 1 ) ) );
		}
	}

	@Test
	void openerInfoIsFoundByName()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			final PluginInfo< ZarrOpener > info = register( context, RecordingOpener.class, "recording", Priority.NORMAL );

			assertSame( info, openerService.getOpenerInfo( "recording" ) );
			assertNull( openerService.getOpenerInfo( null ) );
			assertNull( openerService.getOpenerInfo( ZarrOpenerService.ASK ),
					"The ask sentinel is not an opener" );
			assertNull( openerService.getOpenerInfo( "uninstalled" ) );
		}
	}

	@Test
	void openRunsTheNamedOpenerAndReportsAnUnknownName()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			register( context, RecordingOpener.class, "recording", Priority.NORMAL );
			RecordingOpener.opened = 0;

			assertTrue( openerService.open( "recording", requestFor( context ) ) );
			assertEquals( 1, RecordingOpener.opened );

			assertFalse( openerService.open( "uninstalled", requestFor( context ) ),
					"An unknown name opens nothing and says so" );
			assertEquals( 1, RecordingOpener.opened );
		}
	}

	@Test
	void aFailingOpenerDoesNotTakeThePipelineDown()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			register( context, FailingOpener.class, "failing", Priority.NORMAL );

			assertDoesNotThrow( () -> openerService.open( "failing", requestFor( context ) ) );
		}
	}

	@Test
	void metadataFallsBackFromDescriptionToLabelToName()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			final ZarrOpenRequest request = requestFor( context );

			// Neither label nor description given, so both fall back to the name.
			final PluginInfo< ZarrOpener > bare = register( context, RecordingOpener.class, "bare", Priority.NORMAL );
			assertEquals( "bare", ZarrOpenerService.labelOf( bare ) );
			assertEquals( "bare", ZarrOpenerService.descriptionOf( bare ) );
			assertEquals( "bare", openerService.tooltipOf( bare, request ),
					"Without a dynamic tooltip the description is used" );

			bare.setLabel( "Bare opener" );
			assertEquals( "Bare opener", ZarrOpenerService.descriptionOf( bare ),
					"A missing description falls back to the label" );
			bare.setDescription( "Opens bare things" );
			assertEquals( "Opens bare things", ZarrOpenerService.descriptionOf( bare ) );

			// An unnamed opener is still identifiable, by its class name.
			final PluginInfo< ZarrOpener > unnamed = new PluginInfo<>( RecordingOpener.class, ZarrOpener.class );
			assertEquals( RecordingOpener.class.getName(), ZarrOpenerService.nameOf( unnamed ) );

			final PluginInfo< ZarrOpener > failing = register( context, FailingOpener.class, "failing", Priority.LOW );
			assertEquals( "tooltip for " + URI_UNDER_TEST, openerService.tooltipOf( failing, request ),
					"A dynamic tooltip wins over the static description" );
		}
	}

	@Test
	void aDisabledOpenerIsNotOffered()
	{
		try (Context context = new Context())
		{
			final ZarrOpenerService openerService = context.getService( ZarrOpenerService.class );
			final PluginInfo< ZarrOpener > info = register( context, RecordingOpener.class, "recording", Priority.NORMAL );
			info.setEnabled( false );

			assertTrue( openerService.getOpenerInfos().isEmpty() );
			assertNull( openerService.getOpenerInfo( "recording" ) );
		}
	}

	@Test
	void thePluginTypeIsTheExtensionPoint()
	{
		try (Context context = new Context())
		{
			assertSame( ZarrOpener.class, context.getService( ZarrOpenerService.class ).getPluginType() );
		}
	}
}