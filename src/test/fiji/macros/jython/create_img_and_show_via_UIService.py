#@ UIService ui

from net.imglib2.img.planar import PlanarImgs

img = PlanarImgs.bytes(303, 302)
ui.show("img via UIService", img)

print("Created image of id: "+str(id(img)))