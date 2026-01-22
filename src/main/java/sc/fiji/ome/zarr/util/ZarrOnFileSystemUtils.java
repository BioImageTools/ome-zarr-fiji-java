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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ZarrOnFileSystemUtils
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private ZarrOnFileSystemUtils()
	{
		// prevent instantiation
	}

	/**
	 * Determines whether the given path appears to be the root of a Zarr dataset.
	 * <p>
	 * The method checks for the presence of well-known Zarr metadata files:
	 * <ul>
	 *   <li>{@code .zgroup}, {@code .zattrs} or {@code .zarray} for Zarr v2</li>
	 *   <li>{@code zarr.json} for Zarr v3</li>
	 * </ul>
	 * The existence of any one of these files is considered sufficient to
	 * identify the folder as a Zarr dataset folder.
	 *
	 * @param folder the path to the directory to check
	 * @return {@code true} if the folder contains Zarr metadata files indicating
	 *         a Zarr v2 or v3 dataset, {@code false} otherwise
	 */
	public static boolean isZarrFolder( final Path folder )
	{
		return ( Files.exists( folder.resolve( ".zgroup" ) ) || //Zarr v2
				Files.exists( folder.resolve( ".zattrs" ) ) || //Zarr v2. // NB: .zattrs should not normally appear without .zgroup
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
	 * Attempts to locate the root image folder for a given starting path within a Zarr dataset.
	 * <p>
	 * The method works as follows:
	 * <ol>
	 *     <li>Starts at the given {@code startingFolder} and traverses upward through parent folders
	 *     as long as each folder qualifies as a Zarr folder according to {@link #isZarrFolder(Path)}.</li>
	 *     <li>The top-most Zarr folder found is considered the "top-level Zarr folder".</li>
	 *     <li>If a folder below the top-level Zarr folder was visited during traversal, it is returned
	 *     as the image folder.</li>
	 *     <li>If no such folder exists, the method inspects the top-level Zarr folder. If it contains
	 *     <b>exactly one</b> subfolder (excluding any folder named "OME"), that subfolder is returned as
	 *     the image folder.</li>
	 *     <li>If no suitable candidate folder is found or an I/O error occurs while listing subfolders,
	 *     the method returns {@code null}.</li>
	 * </ol>
	 *
	 * @param startingFolder the folder path somewhere within a Zarr dataset to start the search from; must not be {@code null}
	 * @return the path to the candidate image folder below the top-level Zarr folder, or {@code null} if no suitable folder is found
	 */
	public static Path findImageRootFolder( final Path startingFolder )
	{
		Path currentFolder = startingFolder;
		Path topLevelZarrFolder = null;
		Path imageFolder = null;

		// Traverse up the folder tree as long as we're inside a Zarr folder
		while ( isZarrFolder( currentFolder ) )
		{
			imageFolder = topLevelZarrFolder; // last visited folder just below potential top-level Zarr
			topLevelZarrFolder = currentFolder;
			currentFolder = currentFolder.getParent();
		}

		// If no Zarr folder was ever found
		if ( topLevelZarrFolder == null )
			return null;

		// If there was a folder below top-level Zarr while traversing up, return it
		if ( imageFolder != null )
			return imageFolder;

		// Otherwise, check if top-level Zarr has only one suitable subfolder
		try (Stream< Path > stream = Files.list( topLevelZarrFolder ))
		{
			Path[] subFolders = stream
					.filter( Files::isDirectory )
					.filter( path -> !path.getFileName().toString().equals( "OME" ) )
					.limit( 2 )
					.toArray( Path[]::new );

			if ( subFolders.length == 1 )
				imageFolder = subFolders[ 0 ];
		}
		catch ( IOException e )
		{
			// If anything went wrong, give up
			return null;
		}

		return imageFolder;
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
	 * Returns the relative path from the {@code ancestorPath} to the {@code descendantPath},
	 * as a list of folder names in the order needed to traverse from {@code ancestorPath}
	 * to reach {@code descendantPath}.
	 * <p>
	 * For example, if {@code ancestorPath} is "/a/b" and {@code descendantPath} is "/a/b/c",
	 * this method returns ["c"].
	 * <p>
	 * This method assumes that {@code ancestorPath} is a parent (or ancestor) of {@code descendantPath}.
	 * If not, an {@link IllegalArgumentException} is thrown.
	 *
	 * @param ancestorPath the shorter path (must be an ancestor of {@code descendantPath})
	 * @param descendantPath the longer path (must be a descendant of {@code ancestorPath})
	 * @return an ordered list of folder names from {@code ancestorPath} to {@code descendantPath},
	 *         or an empty list if the paths are equal
	 * @throws IllegalArgumentException if {@code ancestorPath} is not an ancestor of {@code descendantPath}
	 *         or if either path is null
	 */
	public static List< String > relativePathElements( final Path ancestorPath, final Path descendantPath )
	{
		// Null checks
		if ( descendantPath == null )
			throw new IllegalArgumentException( "ancestorPath must not be null" );
		if ( ancestorPath == null )
			throw new IllegalArgumentException( "descendantPath must not be null" );

		// If paths are equal, no difference
		if ( descendantPath.equals( ancestorPath ) )
			return Collections.emptyList(); // immutable empty list (Java 8)

		// Build the path from longerPath up to shorterPath
		List< String > pathElements = new ArrayList<>();
		Path current = descendantPath;

		while ( current != null && !current.equals( ancestorPath ) )
		{
			Path fileName = current.getFileName();
			if ( fileName == null )
			{
				throw new IllegalArgumentException(
						"Cannot determine path difference: " + descendantPath + " is not a descendant of " + ancestorPath
				);
			}
			pathElements.add( fileName.toString() );
			current = current.getParent();
		}

		// If we reached null without matching shorterPath, it's not a descendant
		if ( current == null )
		{
			throw new IllegalArgumentException(
					"Path " + descendantPath + " is not a descendant of " + ancestorPath
			);
		}

		// Reverse to get the path from shorterPath to longerPath
		Collections.reverse( pathElements );
		return Collections.unmodifiableList( pathElements ); // immutable result
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
	public static URI getUriFromPath( final Path path )
	{
		if ( path == null )
			return null;

		final URI pathAsStr = path.toUri();
		logger.info( "URI: {}", pathAsStr );
		return pathAsStr;
	}
}
