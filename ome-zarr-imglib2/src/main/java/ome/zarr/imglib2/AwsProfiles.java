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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads profile names and regions from the shared AWS files
 * ({@code ~/.aws/config}, {@code ~/.aws/credentials}).
 * <p>
 * Deliberately a small parser of its own instead of the AWS SDK's
 * {@code ProfileFile}: the settings dialog lists these profiles, and it must
 * keep working on installations without the AWS SDK (Fiji-Stable). The files
 * are located the way the SDK does it – system property, then environment
 * variable, then the home folder – so both see the same profiles.
 */
public final class AwsProfiles
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/** The characters the AWS SDK accepts in a profile name. */
	private static final Pattern VALID_NAME = Pattern.compile( "[A-Za-z0-9\\-/.%@_:+]+" );

	private AwsProfiles()
	{
		// prevent instantiation
	}

	/**
	 * The profile names defined in the AWS config and credentials files, in file
	 * order, config file first.
	 *
	 * @return the profile names; empty when neither file exists or defines one
	 */
	public static List< String > names()
	{
		final Set< String > names = new LinkedHashSet<>( read( configFile(), true ).keySet() );
		names.addAll( read( credentialsFile(), false ).keySet() );
		return new ArrayList<>( names );
	}

	/**
	 * Whether the AWS config or credentials file sets a {@code region} for
	 * {@code profile}; the SDK merges both files, so either counts.
	 *
	 * @param profile a profile name
	 * @return {@code false} also when the profile is not defined at all
	 */
	public static boolean hasRegion( final String profile )
	{
		return hasRegion( read( configFile(), true ), profile ) || hasRegion( read( credentialsFile(), false ), profile );
	}

	private static boolean hasRegion( final Map< String, Map< String, String > > profiles, final String profile )
	{
		final Map< String, String > properties = profiles.get( profile );
		return properties != null && properties.containsKey( "region" );
	}

	private static Path configFile()
	{
		return file( "aws.configFile", "AWS_CONFIG_FILE", "config" );
	}

	private static Path credentialsFile()
	{
		return file( "aws.sharedCredentialsFile", "AWS_SHARED_CREDENTIALS_FILE", "credentials" );
	}

	private static Path file( final String systemProperty, final String environmentVariable, final String defaultName )
	{
		String path = System.getProperty( systemProperty );
		if ( path == null )
			path = System.getenv( environmentVariable );
		return path == null ? Paths.get( System.getProperty( "user.home" ), ".aws", defaultName ) : Paths.get( path );
	}

	/**
	 * Profile name to its properties. The config file names a profile
	 * {@code [profile name]} (only {@code default} may go without the prefix) and
	 * also holds other sections, e.g. {@code [sso-session name]}, which are
	 * skipped; the credentials file uses the bare {@code [name]}.
	 */
	private static Map< String, Map< String, String > > read( final Path file, final boolean isConfigFile )
	{
		final Map< String, Map< String, String > > profiles = new LinkedHashMap<>();
		if ( !Files.isRegularFile( file ) )
			return profiles;
		final List< String > lines;
		try
		{
			lines = Files.readAllLines( file, StandardCharsets.UTF_8 );
		}
		catch ( IOException e )
		{
			logger.debug( "Could not read AWS profiles from {}: {}", file, e.getMessage() );
			return profiles;
		}
		Map< String, String > current = null;
		for ( final String rawLine : lines )
		{
			final String line = rawLine.trim();
			if ( line.isEmpty() || line.startsWith( "#" ) || line.startsWith( ";" ) )
				continue;
			if ( line.startsWith( "[" ) && line.endsWith( "]" ) )
			{
				final String name = profileName( line.substring( 1, line.length() - 1 ).trim(), isConfigFile );
				current = name == null ? null : profiles.computeIfAbsent( name, k -> new HashMap<>() );
				continue;
			}
			final int equals = line.indexOf( '=' );
			if ( current != null && equals > 0 )
				current.put( line.substring( 0, equals ).trim(), line.substring( equals + 1 ).trim() );
		}
		return profiles;
	}

	/**
	 * The profile a section header names, or {@code null} for a non-profile
	 * section or a name the SDK would ignore (it logs and skips those).
	 */
	private static String profileName( final String section, final boolean isConfigFile )
	{
		final String name;
		if ( !isConfigFile || section.equals( "default" ) )
			name = section;
		else
			name = section.startsWith( "profile " ) ? section.substring( "profile ".length() ).trim() : null;
		return name != null && VALID_NAME.matcher( name ).matches() ? name : null;
	}
}
