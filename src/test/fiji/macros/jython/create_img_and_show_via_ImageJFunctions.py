from net.imglib2.img.planar import PlanarImgs
from net.imglib2.img.display.imagej import ImageJFunctions

img = PlanarImgs.bytes(303, 302)
ImageJFunctions.show(img, "img via ImageJFunctions")
# NB: ImageJFunctions internally convert to ImagePlus