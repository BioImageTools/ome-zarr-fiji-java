# @ DatasetService ds
# @ PyramidalService ps

print("----")
for dimg in ds.getDatasets():
    print("image '" + dimg.getImgPlus().getName() + "' (id: " + str(id(dimg)) + \
          ") of width x height: " + str(dimg.getWidth()) + 'x' + str(dimg.getHeight()))
for pyramidal in ps.getPyramidals():
    print("pyramidal '" + pyramidal.getPyramidName() + "' (id: " + str(id(pyramidal)) + ")")
print("----")
