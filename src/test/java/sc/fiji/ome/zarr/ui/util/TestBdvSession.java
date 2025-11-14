package sc.fiji.ome.zarr.ui.util;

import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import sc.fiji.ome.zarr.BdvHandleService;

import java.util.Random;

public class TestBdvSession {
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
