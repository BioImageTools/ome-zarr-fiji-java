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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scijava.Context;
import org.scijava.prefs.PrefService;

import ome.zarr.fijiui.open.openers.ImageJHighestResolutionOpener;
import ome.zarr.fijiui.open.options.OmeZarrBackend;
import ome.zarr.fijiui.open.options.OmeZarrOpeningSettings;

/**
 * Unit tests for {@link S3Settings}: which profiles it offers, which one it
 * preselects, and what {@link S3Settings#run()} saves. The AWS files are
 * temporary ones, pointed at through the system properties the AWS SDK reads.
 */
class S3SettingsTest
{
	private static final String CONFIG_PROPERTY = "aws.configFile";

	private static final String CREDENTIALS_PROPERTY = "aws.sharedCredentialsFile";

	@TempDir
	Path tempDir;

	private String previousConfig;

	private String previousCredentials;

	@BeforeEach
	void pointAtTempFiles() throws IOException
	{
		previousConfig = System.getProperty( CONFIG_PROPERTY );
		previousCredentials = System.getProperty( CREDENTIALS_PROPERTY );
		final Path config = tempDir.resolve( "config" );
		Files.write( config, Arrays.asList( "[profile first]", "region = eu-central-1", "[profile second]" ), StandardCharsets.UTF_8 );
		System.setProperty( CONFIG_PROPERTY, config.toString() );
		System.setProperty( CREDENTIALS_PROPERTY, tempDir.resolve( "credentials" ).toString() );
	}

	@AfterEach
	void restoreProperties()
	{
		restore( CONFIG_PROPERTY, previousConfig );
		restore( CREDENTIALS_PROPERTY, previousCredentials );
	}

	/** {@code run()} stores the chosen profile and leaves the saved opener, preferred width and backend unchanged. */
	@Test
	void testRunSavesChosenProfileAndKeepsOtherSettings() throws ReflectiveOperationException
	{
		try (Context context = new Context())
		{
			final PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			new OmeZarrOpeningSettings( ImageJHighestResolutionOpener.NAME, 500, OmeZarrBackend.N5 )
					.saveSettingsToPreferences( prefService );

			final S3Settings ui = initializedDialog( prefService );
			setField( ui, "awsProfile", "second" );
			ui.run();

			final OmeZarrOpeningSettings settings = OmeZarrOpeningSettings.loadSettingsFromPreferences( prefService );
			assertEquals( "second", settings.getAwsProfile() );
			assertEquals( ImageJHighestResolutionOpener.NAME, settings.getOpenerName() );
			assertEquals( 500, settings.getPreferredMaxWidth() );
			assertEquals( OmeZarrBackend.N5, settings.getBackend() );
		}
	}

	/** Choosing {@link S3Settings#AWS_DEFAULT_LABEL} replaces a saved profile with none ({@code null}). */
	@Test
	void testRunWithAwsDefaultStoresNoProfile() throws ReflectiveOperationException
	{
		try (Context context = new Context())
		{
			final PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			saveProfile( prefService, "first" );

			final S3Settings ui = initializedDialog( prefService );
			setField( ui, "awsProfile", S3Settings.AWS_DEFAULT_LABEL );
			ui.run();

			assertNull( OmeZarrOpeningSettings.loadSettingsFromPreferences( prefService ).getAwsProfile() );
		}
	}

	/** {@code init()} preselects the saved profile when it is still defined. */
	@Test
	void testInitPreselectsSavedProfile() throws ReflectiveOperationException
	{
		try (Context context = new Context())
		{
			final PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();
			saveProfile( prefService, "second" );

			assertEquals( "second", getProfileFieldValue( initializedDialog( prefService ) ) );
		}
	}

	/** A fresh preferences store preselects {@link S3Settings#AWS_DEFAULT_LABEL}. */
	@Test
	void testInitPreselectsAwsDefaultWhenNothingIsSaved() throws ReflectiveOperationException
	{
		try (Context context = new Context())
		{
			final PrefService prefService = context.getService( PrefService.class );
			prefService.clearAll();

			assertEquals( S3Settings.AWS_DEFAULT_LABEL, getProfileFieldValue( initializedDialog( prefService ) ) );
		}
	}

	/** The choices are {@link S3Settings#AWS_DEFAULT_LABEL}, then the profiles in file order. */
	@Test
	void testAvailableProfilesListsAwsDefaultFirst()
	{
		assertEquals( Arrays.asList( S3Settings.AWS_DEFAULT_LABEL, "first", "second" ), S3Settings.availableProfiles() );
	}

	/** With no AWS files, only {@link S3Settings#AWS_DEFAULT_LABEL} is offered. */
	@Test
	void testAvailableProfilesWithoutAwsFilesOffersOnlyAwsDefault()
	{
		System.setProperty( CONFIG_PROPERTY, tempDir.resolve( "missing-config" ).toString() );
		assertEquals( Collections.singletonList( S3Settings.AWS_DEFAULT_LABEL ), S3Settings.availableProfiles() );
	}

	private static void saveProfile( final PrefService prefService, final String awsProfile )
	{
		final OmeZarrOpeningSettings settings = new OmeZarrOpeningSettings();
		settings.setAwsProfile( awsProfile );
		settings.saveSettingsToPreferences( prefService );
	}

	/** A dialog after its {@code init()} ran, i.e. as the user first sees it. */
	private static S3Settings initializedDialog( final PrefService prefService )
			throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException
	{
		final S3Settings ui = new S3Settings();
		setField( ui, "prefService", prefService );
		final Method initMethod = S3Settings.class.getDeclaredMethod( "init" );
		initMethod.setAccessible( true ); // bypasses private visibility
		initMethod.invoke( ui );
		return ui;
	}

	private static Object getProfileFieldValue( final S3Settings ui )
			throws NoSuchFieldException, IllegalAccessException
	{
		final Field field = S3Settings.class.getDeclaredField( "awsProfile" );
		field.setAccessible( true );
		return field.get( ui );
	}

	private static void setField( final S3Settings ui, final String fieldName, final Object value )
			throws NoSuchFieldException, IllegalAccessException
	{
		final Field field = S3Settings.class.getDeclaredField( fieldName );
		field.setAccessible( true );
		field.set( ui, value );
	}

	private static void restore( final String property, final String previous )
	{
		if ( previous == null )
			System.clearProperty( property );
		else
			System.setProperty( property, previous );
	}
}
