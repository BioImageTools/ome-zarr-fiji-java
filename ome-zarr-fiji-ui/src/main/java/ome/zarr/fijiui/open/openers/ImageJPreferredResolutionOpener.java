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
package ome.zarr.fijiui.open.openers;

import ome.zarr.fiji.open.ZarrOpenRequest;
import ome.zarr.fiji.open.ZarrOpener;

import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 * Opens the dataset in ImageJ at the finest resolution level that is still no
 * wider than {@link ZarrOpenRequest#preferredMaxWidth()}.
 * <p>
 * This is the highest-priority opener shipped here, and therefore what a user
 * who never configured a choice gets.
 */
@Plugin( type = ZarrOpener.class, name = ImageJPreferredResolutionOpener.NAME,
		label = "ImageJ (preferred resolution)",
		description = "Open a matching single-resolution image in ImageJ",
		iconPath = "/ome/zarr/fijiui/dialog/ij_icon.png", priority = Priority.HIGH )
public class ImageJPreferredResolutionOpener implements ZarrOpener
{
	/** The stable identifier this opener is persisted under. */
	public static final String NAME = "imagej-preferred-resolution";

	@Override
	public void open( final ZarrOpenRequest request )
	{
		request.reader().openIJWithImage();
	}
}
