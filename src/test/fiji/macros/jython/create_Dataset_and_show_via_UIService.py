#@ DatasetService dsService
#@ UIService ui
#@ boolean display_image (label="Display the created image:")
#@ string image_label (label="Title of the created image:", value="ImgPlus turned into Dataset #1")

from net.imagej import ImgPlus
from net.imglib2.img.planar import PlanarImgs
from bdv.util import BdvFunctions

# ------- standard creation of `img` and wrapping it "till it becomes" a `Dataset` -------
img = PlanarImgs.bytes(303, 302)
imp = ImgPlus(img, image_label)
ds = dsService.create(imp)

print("Created   img  of  id: "+str(id(img)))
print("Created ImgPlus of id: "+str(id(imp)))
print("Created dataset of id: "+str(id(ds)))


# ------- this is a Jython version of attaching a listener to "onWindowClose" event in BDV -------
from java.awt.event import WindowAdapter

def register_datasetDecrement_onBdvCloseWindow(bdv_handle, dataset):
	viewer_panel = bdv_handle.getViewerPanel()
	win = viewer_panel.getRootPane().getParent()

	class OnClose(WindowAdapter):
		def windowClosed(self, event):
			print("BDV window closed, decrementing id: "+str(id(dataset)))
			dataset.decrementReferences()

	win.addWindowListener(OnClose())


# ------- finally show the `Dataset` either way(s) -------
if display_image:
	ui.show(ds)
else:
	ds.incrementReferences()
	bdv_stack_source = BdvFunctions.show(ds.getImgPlus(), image_label)
	register_datasetDecrement_onBdvCloseWindow(bdv_stack_source.getBdvHandle(), ds)
