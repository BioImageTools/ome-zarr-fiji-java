from ij.process import ByteProcessor
from ij import ImagePlus

p = ByteProcessor(303, 302)
imp = ImagePlus("pure ImagePlus", p)
imp.show()

print("Created image of id: "+str(id(imp)))