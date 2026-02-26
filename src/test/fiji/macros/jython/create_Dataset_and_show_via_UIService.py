#@ DatasetService dsService
#@ ObjectService objService
#@ UIService ui
#@ boolean display_image (label="Display the created image:")
#@ string image_label (label="Title of the created image:", value="ImgPlus turned into Dataset #1")

from net.imagej import ImgPlus
from net.imglib2.img.planar import PlanarImgs

img = PlanarImgs.bytes(303, 302)
imp = ImgPlus(img, image_label)
ds = dsService.create(imp)

print("Created   img  of  id: "+str(id(img)))
print("Created ImgPlus of id: "+str(id(imp)))
print("Created dataset of id: "+str(id(ds)))

if display_image:
	ui.show(ds)
else:
	objService.addObject(ds)