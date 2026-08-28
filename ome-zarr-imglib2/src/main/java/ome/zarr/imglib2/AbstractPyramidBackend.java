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

import java.lang.invoke.MethodHandles;
import java.net.URI;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.zarr.imglib2.exceptions.NotAMultiscaleImageException;
import ome.zarr.imglib2.exceptions.ReaderLibraryUnavailableException;
import ome.zarr.imglib2.exceptions.SingleArrayAxesUnknownException;

/**
 * Base class for {@link PyramidBackend} implementations that owns the
 * backend-agnostic part of {@link #load}: which kind of OME-Zarr node to try, in
 * what order, and what to do when none of them fits. Subclasses supply only the
 * reader-specific steps, so all backends resolve a given location the same way
 * and report the same failure for it.
 * <p>
 * {@link #load} and {@link #loadSingleArray} are {@code final} because that
 * order is the contract {@link PyramidBackend#load} documents to callers, not a
 * per-backend choice. Everything that does vary per backend sits behind a
 * {@code protected} abstract method:
 * <ul>
 *   <li>{@link #loadMultiscale} – open the node as a multiscales group;</li>
 *   <li>{@link #tryLoadLevelFromParent} – open it as one level of its parent
 *       multiscales group;</li>
 *   <li>{@link #tryLoadArrayNodeOnly} – open it from its own metadata alone.</li>
 * </ul>
 * The two {@code try*} hooks return {@code null} to mean "not applicable, try
 * the next thing" rather than throwing, so that declining is cheap and cannot be
 * confused with a genuine read failure.
 */
public abstract class AbstractPyramidBackend implements PyramidBackend
{
	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * {@inheritDoc}
	 * <p>
	 * Opens {@code inputUri} as a multiscales group via
	 * {@link #loadMultiscale}, falling back to {@link #loadSingleArray} when that
	 * reports the node carries no multiscales metadata.
	 */
	@Override
	public final < T extends NativeType< T > & RealType< T > > PyramidContents< T > load( final URI inputUri )
	{
		try
		{
			return loadMultiscaleOrSingleArray( inputUri );
		}
		catch ( NoClassDefFoundError e )
		{
			throw new ReaderLibraryUnavailableException( inputUri.toString(), e );
		}
	}

	private < T extends NativeType< T > & RealType< T > > PyramidContents< T > loadMultiscaleOrSingleArray(
			final URI inputUri )
	{
		try
		{
			return loadMultiscale( inputUri );
		}
		catch ( NotAMultiscaleImageException e )
		{
			// The location is a bare array (a single resolution level), not a
			// multiscales group. Open it as a one-level pyramid instead.
			return loadSingleArray( inputUri );
		}
	}

	/**
	 * Opens the multiscales group at {@code inputUri} as a pyramid with one level
	 * per resolution described by its multiscales metadata.
	 *
	 * @param <T> pixel type
	 * @param inputUri location of the OME-Zarr node to open
	 * @return the group as a pyramid of one or more resolution levels
	 * @throws NotAMultiscaleImageException if the node carries no multiscales
	 *   metadata — this is what makes {@link #load} fall back to
	 *   {@link #loadSingleArray}, so it must be thrown rather than reported some
	 *   other way
	 */
	protected abstract < T extends NativeType< T > & RealType< T > > PyramidContents< T > loadMultiscale( URI inputUri );

	/**
	 * Opens the array at {@code arrayUri} as a one-level pyramid, trying in order:
	 * <ol>
	 *   <li>{@link #tryLoadLevelFromParent} against the immediate parent (see
	 *       {@link ZarrUtils#parentUri}), when a parent exists — this is the route
	 *       that recovers axes, scale and OMERO from the parent multiscales group,
	 *       and the only one that can interpret an OME-Zarr v0.4 / Zarr v2 level;</li>
	 *   <li>{@link #tryLoadArrayNodeOnly} against the array itself — typically the
	 *       Zarr v3 {@code dimension_names}, opened uncalibrated.</li>
	 * </ol>
	 * Taking the second route means the scale and unit are placeholders rather than
	 * anything the dataset states (see
	 * {@link ome.zarr.imglib2.metadata.AxisCalibration#createPlaceholderCalibration}).
	 * <p>
	 * If both decline (return {@code null}), the array cannot be interpreted and a
	 * {@link SingleArrayAxesUnknownException} is thrown.
	 *
	 * @param <T> pixel type
	 * @param arrayUri location of the array node (a single resolution level)
	 * @return the level as a one-level {@link PyramidContents}
	 * @throws SingleArrayAxesUnknownException if neither hook can open the array
	 */
	protected final < T extends NativeType< T > & RealType< T > > PyramidContents< T > loadSingleArray( final URI arrayUri )
	{
		final URI parentUri = ZarrUtils.parentUri( arrayUri );
		if ( parentUri != null )
		{
			final PyramidContents< T > viaParent = tryLoadLevelFromParent( parentUri, arrayUri );
			if ( viaParent != null )
				return viaParent;
		}
		final PyramidContents< T > nodeOnly = tryLoadArrayNodeOnly( arrayUri );
		if ( nodeOnly != null )
		{
			logger.warn( "Opening {} with a placeholder calibration: its axis names are correct, but "
					+ "neither scale nor unit were supplied. "
					+ "Measurements taken from this image are in pixels, not in physical units.", arrayUri );
			return nodeOnly;
		}
		throw new SingleArrayAxesUnknownException( arrayUri.toString() );
	}

	/**
	 * Attempts to open {@code arrayUri} as one level of the multiscales group at
	 * {@code parentUri}, returning a one-level pyramid carrying that level's axes,
	 * scale and transform plus the group's OMERO metadata.
	 *
	 * @param <T> pixel type
	 * @param parentUri location of the presumed parent multiscales group
	 * @param arrayUri location of the array node being opened
	 * @return the level as a one-level pyramid, or {@code null} when the parent is
	 *   not a readable multiscales group or does not list this array
	 */
	protected abstract < T extends NativeType< T > & RealType< T > > PyramidContents< T > tryLoadLevelFromParent(
			URI parentUri, URI arrayUri );

	/**
	 * Attempts to open {@code arrayUri} purely from its own metadata, without a
	 * parent multiscales group: typically from the Zarr v3
	 * {@code dimension_names}, opened uncalibrated (unit scale, no units, no
	 * OMERO).
	 *
	 * @param <T> pixel type
	 * @param arrayUri location of the array node being opened
	 * @return the array as a one-level pyramid, or {@code null} when it cannot be
	 *   opened as an array or declares no usable axis names of its own (e.g. a
	 *   Zarr v2 array)
	 */
	protected abstract < T extends NativeType< T > & RealType< T > > PyramidContents< T > tryLoadArrayNodeOnly( URI arrayUri );
}
