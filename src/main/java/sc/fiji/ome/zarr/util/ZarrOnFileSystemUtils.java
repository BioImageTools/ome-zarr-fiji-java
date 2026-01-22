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
package sc.fiji.ome.zarr.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class ZarrOnFileSystemUtils
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private ZarrOnFileSystemUtils()
	{
		// prevent instantiation
	}

	/**
	 * Checks if within the given folder there exists any of
	 * these files: .zgroup, .zarray or zarr.json.
	 *
	 * @param folder the folder to check.
	 * @return @{@code true} if at least one of the three files is found, {@code false} otherwise.
	 */
	public static boolean isZarrFolder( final Path folder )
	{
		return ( Files.exists( folder.resolve( ".zgroup" ) ) || //Zarr v2
				Files.exists( folder.resolve( ".zarray" ) ) || //Zarr v2
				Files.exists( folder.resolve( "zarr.json" ) ) ); //Zarr v3
	}

	/**
	 * Traverses up the folder tree as long as {@link #isZarrFolder(Path)}
	 * says we are inside a Zarr dataset. The last such folder is returned, which is
	 * supposed to be the top-level/root folder of the pointed at dataset.
	 *
	 * @param somewhereInZarrFolder Pointer (folder) to somewhere inside an OME Zarr.
	 * @return Root of that OME Zarr, or NULL if the provided path is NOT within an OME Zarr.
	 */
	public static Path findRootFolder( final Path somewhereInZarrFolder )
	{
		Path parentFolder = somewhereInZarrFolder;
		Path lastValidFolder = null;

		while ( isZarrFolder( parentFolder ) )
		{
			lastValidFolder = parentFolder;
			parentFolder = parentFolder.getParent();
		}

		return lastValidFolder;
	}

	/**
	 * Traverses to parent folders as long as they are Zarr folders. And returns the last visited folder just one below,
	 * if there was such. If the traversing started already with the top-level folder, it would return one folder below
	 * if there is exactly one. Otherwise, it returns null.
	 */
	public static Path findImageRootFolder( final Path somewhereInZarrFolder )
	{
		Path currFolder = somewhereInZarrFolder;
		Path prevFolder = null;
		Path prevprevFolder = null;

		while ( isZarrFolder( currFolder ) )
		{
			prevprevFolder = prevFolder;
			prevFolder = currFolder;
			currFolder = currFolder.getParent();
		}

		// ever found a top-level zarr?
		if ( prevFolder == null) return null;

		// prevFolder is now the top-level zarr, and
		// prevprevFolder is the last visited one just below (if there is such)
		if ( prevprevFolder != null) return prevprevFolder;

		//see if there's only one image subfolder, and choose it possibly
		try {
			//Files.list(prevFolder).forEach(p -> System.out.println("sub-item: "+p));
			Path[] subFolders = Files.list(prevFolder)
					  .filter(Files::isDirectory)
					  .filter(p -> !p.getFileName().toString().equals("OME"))
					  .limit(2)
					  .toArray(Path[]::new);
			if (subFolders.length == 1) prevprevFolder = subFolders[0];
		} catch (IOException e) {
			//if anything went wrong, signal giving up...
			return null;
		}

		return prevprevFolder;
	}

	/**
	 * Given several datasets, which often are spatially downscaled variants
	 * of each other (aka resolution pyramids), it chooses the first one whose
	 * name ends with 's0' -- typically signifying the best spatial resolution,
	 * the finest variant. If multiple such exists in the input array, the first
	 * one is taken. If none such is found, the first element from the input array
	 * is returned.
	 *
	 * @param datasets Non-null (not test thought!) array with "s?" endings.
	 * @return First array item with "s0" or just the first array item.
	 */
	public static String findHighestResolutionByName( final String[] datasets )
	{
		for ( String s : datasets )
		{
			if ( s.endsWith( "s0" ) )
				return s;
		}
		return datasets[ 0 ];
	}

	/**
	 * If not a top-level path is drag-and-dropped to Fiji, but instead a folder from
	 * inside an OME Zarr folders structure, this routine returns the list of folders
	 * that the 'shorterPath' would need to traverse to arrive to the 'longerPath'.
	 *
	 * @param longerPath Target path, presumably the top-level OME Zarr folder.
	 * @param shorterPath Starting path, under which folders need to be opened to reach the 'longerPath'.
	 * @return An ordered list of folders or an empty list if no solution was found.
	 */
	public static List< String > listPathDifferences( final Path longerPath, final Path shorterPath )
	{
		List< String > diffPathElems = new LinkedList<>();
		Path currPath = longerPath;
		while ( !currPath.equals( shorterPath ) )
		{
			diffPathElems.add( 0, currPath.getFileName().toString() );
			currPath = currPath.getParent();
			//NB: OS-agnostic finding of the difference of the folders
		}
		return diffPathElems;
	}

	/**
	 * Checks if the current OS is Windows.
	 *
	 * @return True if the OS is Windows, false otherwise.
	 */
	public static boolean isWindows()
	{
		final String myOS = System.getProperty( "os.name" ).toLowerCase();
		return !( myOS.contains( "mac" ) || myOS.contains( "nux" ) || myOS.contains( "nix" ) );
	}

	/**
	 * The method ensures compatibility with different operating systems by formatting the path accordingly.
	 * If the provided path is null, the method returns null.
	 *
	 * @param path the file system path pointing anywhere
	 * @return the absolute path formatted as a string,
	 *         or null if the provided path is null
	 */
	public static URI getUriFromPath(final Path path )
	{
		if (path == null) return null;

		final URI pathAsStr = path.toUri();
		logger.info( "URI: {}", pathAsStr );
		return pathAsStr;
	}
}
