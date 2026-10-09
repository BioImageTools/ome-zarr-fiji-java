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
import java.util.List;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.fijiui.open.options.OmeZarrOpeningSettings;
import ome.zarr.imglib2.s3.AwsProfiles;

/**
 * A FIJI/ImageJ command to choose the AWS profile used for {@code s3://}
 * locations.
 */
@Plugin( type = Command.class, menuPath = "Plugins > OME-Zarr > Settings > S3 Settings", initializer = "init" )
public class S3Settings extends DynamicCommand
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final Integer WIDTH = 20;

	/** The choice that stands for no named AWS profile, i.e., the AWS SDK default. */
	static final String NO_PROFILE_LABEL = "No profile";

	@SuppressWarnings( "unused" )
	@Parameter
	private PrefService prefService;

	@SuppressWarnings( "unused" )
	@Parameter( label = "AWS profile for s3:// locations", description = "A profile from ~/.aws/config or ~/.aws/credentials", initializer = "initAwsProfiles" )
	private String awsProfile;

	@SuppressWarnings( "all" )
	@Parameter( visibility = ItemVisibility.MESSAGE, required = false, persist = false )
	private String awsProfileInfo = "<html>"
			+ "<body width=" + WIDTH + "cm align=left>"
			+ "Region, endpoint_url and credentials of pasted s3:// locations are taken from this profile in ~/.aws/config and ~/.aws/credentials.<br>"
			+ "'" + NO_PROFILE_LABEL
			+ "' uses the profile named by the AWS_PROFILE environment variable, else the 'default' profile, else anonymous access, always with region us-east-1.<br>"
			+ "Profiles added to these files show up here the next time this dialog is opened."
			+ "</body>"
			+ "</html>";

	private OmeZarrOpeningSettings settings;

	@Override
	public void run()
	{
		settings.setAwsProfile( NO_PROFILE_LABEL.equals( awsProfile ) ? null : awsProfile );
		logger.debug( "Now saving OME-Zarr settings to user preferences. awsProfile: {}", settings.getAwsProfile() );
		settings.saveSettingsToPreferences( prefService );
	}

	@SuppressWarnings( "unused" )
	private void init()
	{
		settings = OmeZarrOpeningSettings.loadSettingsFromPreferences( prefService );
		awsProfile = awsProfileChoiceFor( settings.getAwsProfile() );
	}

	@SuppressWarnings( "unused" )
	private void initAwsProfiles()
	{
		getInfo().getMutableInput( "awsProfile", String.class ).setChoices( availableProfiles() );
	}

	/** {@link #NO_PROFILE_LABEL}, then the profiles defined in the AWS files, if any. */
	static List< String > availableProfiles()
	{
		final List< String > choices = new ArrayList<>();
		choices.add( NO_PROFILE_LABEL );
		choices.addAll( AwsProfiles.names() );
		return choices;
	}

	/** The choice standing for {@code awsProfile}, or {@link #NO_PROFILE_LABEL} when it is unset or no longer defined. */
	private static String awsProfileChoiceFor( final String awsProfile )
	{
		if ( awsProfile == null )
			return NO_PROFILE_LABEL;
		if ( AwsProfiles.names().contains( awsProfile ) )
			return awsProfile;
		logger.debug( "The configured AWS profile '{}' is no longer defined, offering '{}' instead.", awsProfile, NO_PROFILE_LABEL );
		return NO_PROFILE_LABEL;
	}
}
