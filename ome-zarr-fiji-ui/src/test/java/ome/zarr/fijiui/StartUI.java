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
package ome.zarr.fijiui;

import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.write.PyramidContentsUtils;
import ome.zarr.n5.N5PyramidSaver;

import java.net.URI;

public class StartUI
{

	static
	{
		net.imagej.patcher.LegacyInjector.preinit();
	}

	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		Img< UnsignedShortType > i = ArrayImgs.unsignedShorts( 300, 305, 205 );
		i.forEach( p -> p.setInteger( 10 ) );

		//N5PyramidSaver.writeFullImmediately( i, , "dgrp" );
		PyramidContents< UnsignedShortType > pc = PyramidContentsUtils.create(
				"image1",
				i.firstElement(),
				i.dimensionsAsLongArray(),
				0, 0,
				new PyramidContentsUtils.AxisCalibrationsBuilder()
						.setAxisXCalibration( 1, "micrometer" )
						.setAxisYCalibration( 1, "micrometer" )
						.setAxisZCalibration( 1, "micrometer" )
						.build(),
				new double[] { 3.0, 6.0 } );
		System.out.println( pc );

		N5PyramidSaver< ? > saver = new N5PyramidSaver<>( URI.create( "/temp/FIJIZARRWRITE2.zarr" ) );
		saver.initEmptyContainer();
		saver.initEmptyMultiscales( "image1", ( PyramidContents ) pc );
		saver.writeRegion( ( Img ) i, 0 );
	}
}
