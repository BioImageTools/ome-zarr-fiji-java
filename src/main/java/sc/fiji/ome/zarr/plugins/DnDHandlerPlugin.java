/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2025 SciJava developers
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
package sc.fiji.ome.zarr.plugins;

import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.location.FileLocation;
import org.scijava.io.location.Location;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.fiji.ome.zarr.ui.DnDActionChooser;
import sc.fiji.ome.zarr.util.BdvHandleService;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import net.imglib2.util.Cast;

@Plugin(type = IOPlugin.class, attrs = @Attr(name = "eager"))
public class DnDHandlerPlugin extends AbstractIOPlugin< Object >
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	//the "innocent" product of the (hypothetical) file reading... which Fiji will not display
	private static final Object FAKE_INPUT = new ArrayList<>( 0 );

	@Parameter
	private BdvHandleService bdvHandleService; //TODO, is it really used down-stream?

	// ========================= IOPlugin stuff =========================
	@Override
	public boolean supportsOpen( final Location source )
	{
		logger.debug(
				"Zarr DnD plugin: supportsOpen check, location type={}, path={}", source.getClass().getSimpleName(),
				source.getURI().getPath()
		);

		if ( !( source instanceof FileLocation ) )
			return false;

		return ZarrOnFileSystemUtils.isZarrFolder( Paths.get( source.getURI() ) );
	}

	@Override
	public Object open( final Location source ) throws IOException
	{
		logger.debug( "Zarr DnD plugin: opening {}", source.getURI().getPath() );

		final FileLocation fileLocation = Cast.unchecked( source );
		final Path droppedInPath = fileLocation.getFile().toPath();

		//TODO: this should ideally go into a separate thread... as an independent follow-up story after the DnD event is over
		new DnDActionChooser( droppedInPath, this.context() ).show();

		// Returning such an object makes Scijava's DnD subsystem believe that the dropped object
		// has been already fully loaded, and Scijava (Fiji) will attempt to display it now (and
		// will realize that it doesn't know how to display it and will silently not display, which
		// is exactly what is desired now). The processing of this DnD event will then finish finally.
		// (While our DnDActionChoose window will still be up there...)
		return FAKE_INPUT;
	}

	@Override
	public Class< Object > getDataType()
	{
		return Object.class;
	}
}
