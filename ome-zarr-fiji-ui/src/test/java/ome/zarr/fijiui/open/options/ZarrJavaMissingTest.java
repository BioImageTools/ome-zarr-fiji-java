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
package ome.zarr.fijiui.open.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import ome.zarr.ZarrTestUtils;
import ome.zarr.fiji.read.OmeZarr;
import ome.zarr.fijiui.open.OmeZarrOpenActions;
import ome.zarr.fijiui.open.openers.ImageJPreferredResolutionOpener;
import ome.zarr.fijiui.plugin.command.settings.OpeningBehaviorSettings;

/**
 * What a user sees in a Fiji without zarr-java, e.g. without the OME-Zarr update site (issue #187).
 * <p>
 * Runs only in this module's {@code without-zarr-java} surefire execution, which drops the zarr-java jar from the
 * test classpath, so the fallback is exercised for real rather than faked.
 * <p>
 * Run from the IDE as a plain JUnit test, the whole class is skipped, as zarr-java is on the classpath there. Run
 * {@code mvn -pl ome-zarr-fiji-ui test -Dtest=ZarrJavaMissingTest} instead.
 */
class ZarrJavaMissingTest
{
	private static final String DATASET = "ome/zarr/testdata/5d_testing/5d_dataset_v4.ome.zarr";

	private static final String UPDATE_SITE_HINT = "enable the OME-Zarr update site";

	@BeforeAll
	static void zarrJavaIsMissing()
	{
		assumeTrue( Boolean.getBoolean( "ome.zarr.test.withoutZarrJava" ), "needs the without-zarr-java execution" );
		assertFalse( OmeZarrBackend.ZARR_JAVA.isAvailable() );
	}

	/**
	 * With the default backend (zarr-java), a dataset still opens, read with N5, and the user is told so once.
	 * A later {@code .ozx} archive, which N5 cannot read, names the update site.
	 * <p>
	 * The context is a mock that offers only the {@link UIService}: the default settings then apply, and
	 * {@link OmeZarrOpenActions#openWithSettings} hands the image to a (mocked) {@link ImageJPreferredResolutionOpener}.
	 */
	@Test
	void openingFallsBackToN5AndPointsToTheUpdateSite( @TempDir final Path tempDir ) throws Exception
	{
		final UIService ui = mock( UIService.class );
		final Context context = mock( Context.class );
		when( context.getService( UIService.class ) ).thenReturn( ui );
		final OmeZarrOpeningSettings settings = new OmeZarrOpeningSettings();
		assertEquals( OmeZarrBackend.N5, settings.getBackend() );

		final URI dataset = ZarrTestUtils.resourcePath( DATASET ).toUri();
		try (MockedConstruction< ImageJPreferredResolutionOpener > openers =
				mockConstruction( ImageJPreferredResolutionOpener.class ))
		{
			OmeZarrOpenActions.openWithSettings( dataset, context );
			OmeZarrOpenActions.openWithSettings( dataset, context );
			final ArgumentCaptor< OmeZarr > opened = ArgumentCaptor.forClass( OmeZarr.class );
			verify( openers.constructed().get( 0 ) ).open( opened.capture() );
			assertNotNull( opened.getValue().readContents() );
		}

		final AtomicReference< String > error = new AtomicReference<>();
		final URI archive = ZarrTestUtils.zipDataset( DATASET, tempDir.resolve( "5d.ozx" ) ).toUri();
		assertNull( settings.createOmeZarr( archive, context, error::set ).showInImageJ() );
		assertTrue( error.get().contains( UPDATE_SITE_HINT ), error.get() );

		verify( ui, times( 1 ) ).showDialog(
				argThat( message -> message.contains( "read with N5" ) && message.contains( "OME-Zarr update site" ) ),
				any(), eq( DialogPrompt.MessageType.INFORMATION_MESSAGE ) );
	}

	/** The settings dialog offers only N5, preselects it, and points to the update site. */
	@Test
	void settingsDialogOffersOnlyN5() throws Exception
	{
		try (Context context = new Context())
		{
			context.getService( PrefService.class ).clearAll();
			final OpeningBehaviorSettings dialog = new OpeningBehaviorSettings();
			context.inject( dialog );
			invoke( dialog, "init" );
			invoke( dialog, "initOmeZarrBackends" );

			assertEquals( Collections.singletonList( "N5" ),
					dialog.getInfo().getMutableInput( "readerBackend", String.class ).getChoices() );
			assertEquals( "N5", field( dialog, "readerBackend" ) );
			final String info = ( String ) field( dialog, "readerBackendInfo" );
			assertTrue( info.contains( "zarr-java is not installed" ), info );
		}
	}

	private static void invoke( final OpeningBehaviorSettings dialog, final String name ) throws Exception
	{
		final Method method = OpeningBehaviorSettings.class.getDeclaredMethod( name );
		method.setAccessible( true );
		method.invoke( dialog );
	}

	private static Object field( final OpeningBehaviorSettings dialog, final String name ) throws Exception
	{
		final Field field = OpeningBehaviorSettings.class.getDeclaredField( name );
		field.setAccessible( true );
		return field.get( dialog );
	}
}
