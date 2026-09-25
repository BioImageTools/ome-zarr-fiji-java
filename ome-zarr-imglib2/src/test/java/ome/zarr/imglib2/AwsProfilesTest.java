/*-
 * #%L
 * OME-Zarr reader based on imglib2
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
package ome.zarr.imglib2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AwsProfilesTest
{
	private static final String CONFIG_PROPERTY = "aws.configFile";

	private static final String CREDENTIALS_PROPERTY = "aws.sharedCredentialsFile";

	@TempDir
	Path tempDir;

	private String previousConfig;

	private String previousCredentials;

	private Path config;

	private Path credentials;

	@BeforeEach
	void pointAtTempFiles()
	{
		previousConfig = System.getProperty( CONFIG_PROPERTY );
		previousCredentials = System.getProperty( CREDENTIALS_PROPERTY );
		config = tempDir.resolve( "config" );
		credentials = tempDir.resolve( "credentials" );
		System.setProperty( CONFIG_PROPERTY, config.toString() );
		System.setProperty( CREDENTIALS_PROPERTY, credentials.toString() );
	}

	@AfterEach
	void restoreProperties()
	{
		restore( CONFIG_PROPERTY, previousConfig );
		restore( CREDENTIALS_PROPERTY, previousCredentials );
	}

	@Test
	void testNamesIsEmptyWhenNoFileExists()
	{
		assertEquals( Collections.emptyList(), AwsProfiles.names() );
	}

	@Test
	void testNamesIsEmptyForFilesWithoutProfiles() throws IOException
	{
		write( config, "# only a comment", "" );
		write( credentials );
		assertEquals( Collections.emptyList(), AwsProfiles.names() );
	}

	@Test
	void testNamesListsConfigThenCredentialsProfilesWithoutDuplicates() throws IOException
	{
		write( config,
				"[default]",
				"region = eu-west-2",
				"[profile shared]",
				"endpoint_url = https://s3.example.org",
				"[profile   spaced  ]" );
		write( credentials,
				"[shared]",
				"aws_access_key_id = AKID",
				"[credentials-only]",
				"aws_access_key_id = AKID2" );
		assertEquals( Arrays.asList( "default", "shared", "spaced", "credentials-only" ), AwsProfiles.names() );
	}

	@Test
	void testNamesSkipsNonProfileSectionsOfTheConfigFile() throws IOException
	{
		write( config,
				"[sso-session corp]",
				"sso_region = eu-west-1",
				"[services my-services]",
				"[profile real]" );
		assertEquals( Collections.singletonList( "real" ), AwsProfiles.names() );
	}

	@Test
	void testNamesSkipsNamesTheSdkIgnores() throws IOException
	{
		// Only the config file uses the "profile " prefix; in the credentials file
		// it would be part of the name, and the space makes the SDK skip it.
		write( config, "[profile has space]", "[profile ok]" );
		write( credentials, "[profile odd]", "[a-b/c.d%e@f_g:h+i]" );
		assertEquals( Arrays.asList( "ok", "a-b/c.d%e@f_g:h+i" ), AwsProfiles.names() );
	}

	@Test
	void testHasRegion() throws IOException
	{
		write( config,
				"[profile with-region]",
				"  region=eu-central-1  ",
				"; a comment",
				"[profile without-region]",
				"endpoint_url = https://s3.example.org" );
		assertTrue( AwsProfiles.hasRegion( "with-region" ) );
		assertFalse( AwsProfiles.hasRegion( "without-region" ) );
		assertFalse( AwsProfiles.hasRegion( "undefined" ) );
	}

	@Test
	void testHasRegionAlsoReadsTheCredentialsFile() throws IOException
	{
		write( credentials, "[creds]", "region = eu-central-1" );
		assertTrue( AwsProfiles.hasRegion( "creds" ) );
	}

	private static void write( final Path file, final String... lines ) throws IOException
	{
		Files.write( file, Arrays.asList( lines ), StandardCharsets.UTF_8 );
	}

	private static void restore( final String property, final String previous )
	{
		if ( previous == null )
			System.clearProperty( property );
		else
			System.setProperty( property, previous );
	}
}
