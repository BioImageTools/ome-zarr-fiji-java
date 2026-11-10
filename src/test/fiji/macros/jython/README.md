remarks:


# Use `Dataset` over `ImagePlus` in scripts

This refers to the situation when a Jython script has (among others) this input:

```python
#@ ImagePlus imp

def somework_over_imageplus(imp):
	pass
```

Such script will also work with pure `Datasets`, in which case
Fiji (most likely the ImageJ2-flavour of it) will internally prepare
a new `ImagePlus` that is wrapped around the `Dataset`, and *increases its
internal usage counter*. In fact, `ImageJFunctions.wrap()` does similar thing:
It also constructs a new `ImagePlus` that's around a virtual stack that's
around the given Imglib2's `img`. Coming back to the script example, the
auto-conversion happens only once, that said, executing the script again
will re-use the previously constructed `ImagePlus`.

If ever a "system" auto-conversion of a `Dataset` to `ImagePlus` has taken
place, the created `ImagePlus` *blocks* releasing/freeing of the `Dataset`,
and one has to call `ImagePlus.close()` or similar function to effectively
loose this `ImagePlus` that in turn will unlock (*decrease the internal
usage counter*) the `Dataset`. **However**, if `ImagePlus` is created from
a `Dataset` (e.g., see below), this new `ImagePlus` is not blocking anything.

Take home message for script programmers is to design their Zarr-expecting
scripts to consume

```python
#@ Dataset dimg
```

and possibly build `ImagePlus` (if they really need it) in their script.
They could explicitly `ImagePlus.close()` before the end of their script,
but it doesn't seem to be any important.

## Example

```python
#@ Dataset dimg

from net.imglib2.img.display.imagej import ImageJFunctions

def get_imageplus(dataset):
	img = dataset.getImgPlus()
	return ImageJFunctions.wrap(img, img.getName())


def somework_over_imageplus(imp):
	# 2D Python-native array mapped over pixels from the current xy plane
	pixels = imp.getProcessor().getPixels()
	pass


imp = get_imageplus(dimg)
somework_over_imageplus(imp)

# optional clean up:
imp.close()
```

