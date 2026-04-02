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

import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import java.util.Random;

public class BdvHandleServiceDemo {
	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		BdvHandleService bdvService = ij.context().getService(BdvHandleService.class);

		//fake image
		final Random rng = new Random();
		final Img<UnsignedShortType> img = ArrayImgs.unsignedShorts(100, 100, 30);
		img.forEach(p -> p.setReal(rng.nextFloat()));

		bdvService.openNewBdv(img,"source 1");

		int iters = 20;
		while (iters > 0 && bdvService.isLastBdvStillAlive()) {
			try {
				System.out.println("Seconds before adding a another image: "+iters);
				Thread.sleep(1000);
				iters--;
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		System.out.println("adding new image.");
		final Img<UnsignedShortType> imgB = ArrayImgs.unsignedShorts(100, 100, 30);
		imgB.forEach(p -> p.setReal(rng.nextFloat()));
		bdvService.addToLastOrInNewBdv(imgB,"source 2");

		iters = 20;
		while (iters > 0 && bdvService.isLastBdvStillAlive()) {
			try {
				System.out.println("Seconds before finishing the program: "+iters);
				Thread.sleep(1000);
				iters--;
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		System.out.println("done.");
	}
}
