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
package sc.fiji.ome.zarr.ui.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class ZarrOnFileSystemUtils {

    private ZarrOnFileSystemUtils() {
        // prevent instantiation
    }

	/**
	 * Checks if under the given folder there exists any of
	 * the files: .zgroup, .zarray or zarr.json.
	 *
	 * @param zarrFolder Supposedly the top-level Zarr folder.
	 * @return True if some of the three files is found.
	 */
	public static boolean isZarrFolder(final Path zarrFolder) {
		return ( Files.exists( zarrFolder.resolve( ".zgroup" ) ) || //Zarr v2
				  Files.exists( zarrFolder.resolve( ".zarray" ) ) || //Zarr v2
				  Files.exists( zarrFolder.resolve("zarr.json") ) ); //Zarr v3
	}

	/**
	 * Traverses up the folders tree as long as {@link ZarrOnFileSystemUtils#isZarrFolder(Path)}
	 * says we are inside a Zarr dataset. The last such folder is returned, which is
	 * supposed to be the top-level/root folder of the pointed at dataset.
	 *
	 * @param somewhereInZarrFolder Pointer (folder) to somewhere inside an OME Zarr.
	 * @return Root of that OME Zarr, or NULL if the provided path is NOT within an OME Zarr.
	 */
	public static Path findRootFolder(final Path somewhereInZarrFolder) {
		Path parentFolder = somewhereInZarrFolder;
		Path lastValidFolder = null;

		while (isZarrFolder(parentFolder)) {
			lastValidFolder = parentFolder;
			parentFolder = parentFolder.getParent();
		}

		return lastValidFolder;
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
	public static String findHighestResByName(final String[] datasets) {
		for (String s : datasets) {
			if (s.endsWith("s0")) return s;
		}
		return datasets[0];
	}

	/**
	 * If not a top-level path is drag-and-dropped to Fiji, but instead a folder from
	 * inside an OME Zarr folders structure, this routine returns the list of folders
	 * that the 'shorterPath' would need to traverse to arrive to the 'longerPath'.
	 *
	 * @param longerPath Target path, presumably the top-level OME Zarr folder.
	 * @param shorterPath Starting path, under which folders need to be opened to reach the 'longerPath'.
	 * @return An ordered list of folders, or an empty list if no solution was found.
	 */
	public static List<String> listPathDifferences(final Path longerPath, final Path shorterPath) {
		List<String> diffPathElems = new LinkedList<>();
		Path currPath = longerPath;
		while ( !currPath.equals(shorterPath) ) {
			diffPathElems.add(0, currPath.getFileName().toString());
			currPath = currPath.getParent();
			//NB: OS-agnostic finding of the difference of the folders
		}
		return diffPathElems;
	}

    public static boolean isWindows() {
        final String myOS = System.getProperty("os.name").toLowerCase();
        return !( myOS.contains( "mac" ) || myOS.contains( "nux" ) || myOS.contains( "nix" ) );
    }
}
