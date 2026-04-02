#@ DatasetService ds

print("----")
for dimg in ds.getDatasets():
	print("image '"+dimg.getImgPlus().getName()+"' (id: "+str(id(dimg))+\
	") of width x height: "+str(dimg.getWidth())+'x'+str(dimg.getHeight()))
print("----")