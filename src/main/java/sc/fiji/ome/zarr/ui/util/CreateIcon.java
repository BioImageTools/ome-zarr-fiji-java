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
package sc.fiji.ome.zarr.ui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.lang.invoke.MethodHandles;
import java.net.URL;

public class CreateIcon
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private CreateIcon()
	{
		// prevent instantiation
	}

	/**
	 * Loads an image from the classpath (e.g., in src/main/resources)
	 * and scales it to 32x32 pixels.
	 *
	 * @param resourcePath path to the image relative to the classpath,
	 *                     e.g. "/icons/myicon.png"
	 * @return a scaled ImageIcon, or an empty one if loading fails
	 */
	public static ImageIcon getAndResizeIcon( final String resourcePath )
	{

		try
		{
			URL resourceUrl = CreateIcon.class.getResource( resourcePath );
			if ( resourceUrl == null )
			{
				throw new IllegalArgumentException( "Resource not found: " + resourcePath );
			}

			Image image = new ImageIcon( resourceUrl ).getImage().getScaledInstance( 32, 32, Image.SCALE_SMOOTH );

			return new ImageIcon( image );
		}
		catch ( Exception e )
		{
			logger.debug( "Failed to load icon from path: " + resourcePath, e );
			// Fallback to an empty image if the resource can't be loaded
			return new ImageIcon();
		}
	}
}
