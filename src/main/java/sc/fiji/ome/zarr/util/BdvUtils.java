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
package sc.fiji.ome.zarr.util;

import java.awt.Color;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;
import java.util.List;

import net.imglib2.type.numeric.ARGBType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import sc.fiji.ome.zarr.pyramid.PyramidalDataset;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

public class BdvUtils
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private BdvUtils()
	{
		// prevent instantiation of this class
	}

	/**
	 * Displays the given pyramidal dataset in a BigDataViewer (BDV) window.<br>
	 * Increments the dataset's reference count.<br>
	 * Ensures that the dataset's reference count is properly decreased when the BDV window is closed.
	 * <br>
	 * @param pyramidalDataset the input dataset to be displayed in BDV; this dataset
	 *                         contains multi-resolution image data along with associated metadata.
	 * @return a {@code BdvHandle} instance representing the BDV window.
	 */
	public static BdvHandle showBdvAndRegisterDataset( final PyramidalDataset< ? > pyramidalDataset )
	{
		BdvHandle bdvHandle = BdvFunctions.show( pyramidalDataset.asSources(), pyramidalDataset.numTimepoints(),
				BdvOptions.options().frameTitle( pyramidalDataset.getName() ) ).getBdvHandle();

		setTimepoint( pyramidalDataset.getOmeroProperties(), bdvHandle );

		setChannelProperties( pyramidalDataset, bdvHandle );

		Container topLevelContainer = bdvHandle.getViewerPanel().getRootPane().getParent();
		if ( topLevelContainer instanceof Window )
		{
			// notify scijava about "usage" (and "no longer usage" later) of this Dataset
			// only if we're able to listen for when Bdv window closes
			pyramidalDataset.incrementReferences();
			( ( Window ) topLevelContainer ).addWindowListener( new WindowAdapter()
			{
				@Override
				public void windowClosed( WindowEvent e )
				{
					pyramidalDataset.decrementReferences();
				}
			} );
		}
		return bdvHandle;
	}

	private static void setChannelProperties( final PyramidalDataset< ? > pyramidalDataset, final BdvHandle bdvHandle )
	{
		Omero omero = pyramidalDataset.getOmeroProperties();
		if ( omero == null || omero.channels == null || omero.channels.isEmpty() )
			return;
		List< Omero.Channel > omeroChannels = omero.channels;
		if ( omeroChannels.size() != pyramidalDataset.asSources().size() )
		{
			logger.warn(
					"The number of channels in the Omero metadata ({}) does not match the number of sources in the dataset ({}). Channel properties will not be applied.",
					omeroChannels.size(), pyramidalDataset.asSources().size() );
			return;
		}
		for ( int channelNumber = 0; channelNumber < pyramidalDataset.asSources().size(); channelNumber++ )
		{
			SourceAndConverter< ? > source = pyramidalDataset.asSources().get( channelNumber );
			final Omero.Channel omeroChannel = omeroChannels.get( channelNumber );
			ConverterSetup converterSetup = bdvHandle.getConverterSetups().getConverterSetup( source );
			Color color = omeroChannel == null || omeroChannel.color == null ? Color.white : Color.decode( "#" + omeroChannel.color );
			int opaque = 255;
			converterSetup.setColor( new ARGBType( ARGBType.rgba( color.getRed(), color.getGreen(), color.getBlue(), opaque ) ) );
			if ( omeroChannel != null && omeroChannel.window != null )
				converterSetup.setDisplayRange( omeroChannel.window.start, omeroChannel.window.end );
			bdvHandle.getViewerPanel().state().setSourceActive( source, omeroChannel == null || omeroChannel.active );
		}
	}

	private static void setTimepoint( final Omero omero, final BdvHandle bdvHandle )
	{
		int timepoint = omero == null || omero.rdefs == null ? 0 : omero.rdefs.defaultT;
		bdvHandle.getViewerPanel().state().setCurrentTimepoint( timepoint );
	}
}
