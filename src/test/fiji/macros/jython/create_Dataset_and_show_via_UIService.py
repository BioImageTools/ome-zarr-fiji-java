#@ Context ctx
#@ UIService ui

from net.imagej import ImgPlus
from net.imglib2.img.planar import PlanarImgs
from net.imagej import DefaultDataset

img = PlanarImgs.bytes(303, 302)
imp = ImgPlus(img, "ImgPlus turned into Dataset")
ds = DefaultDataset(ctx, imp)

ui.show(ds)