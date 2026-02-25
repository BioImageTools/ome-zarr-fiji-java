# Special Pyramid Images

Two 3D images are provided here. Their content is the same,
they differ only version of OME-Zarr in which each is stored.

Both 3D images are stored using resolution pyramids. That said,
a full-, mid- and low-resolution variants of the images are
available in each `.zarr`.

However, the pyramids paradigm is here intentionally corrupted.
While the pyramids are technically correct (featuring images of
decreasing sizes), their content is always different:

- Full-resolution image is the brightest and shows a horizontal-vertical grid,
- Mid-resolution image is moderate bright and shows a diagonal grid,
- Low-resolution image is the darkest and shows a horizontal-vertical grid.

## Purpose

The purpose of such corrupted images is to demonstrate the usage of the pyramids
in visualisation programs such as [BigDataViewer](https://imagej.net/plugins/bdv/)
in [Fiji](https://fiji.sc/), or in [Napari](https://napari.org/stable/) with
[napari-ome-zarr](https://github.com/ome/napari-ome-zarr).

When the pyramids are build correctly, it is hard to visually confirm (observe)
if a low-resolution was loaded at first, or if it is currently displayed (when
view is zoomed-out sufficiently).

# How to Reproduce

Both images can be re-created as follows:

```bash
pixi run python create_pyramid.py
```

Note that [pixi](https://pixi.prefix.dev/latest/) packages manager is required.

