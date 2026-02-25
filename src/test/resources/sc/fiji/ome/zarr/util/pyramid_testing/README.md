# Special Pyramid Images

Two 3D images are provided here. Their content is the same,
they differ only versions of OME-Zarr in which each is stored.

Both 3D images are stored using resolution pyramids. That said,
a full-, mid- and low-resolution variants of the image are
available in each `.zarr`.

However, the pyramids paradigm is here intentionally corrupted.
While the pyramids are technically correct (featuring images of
decreasing size), their content is always different:

- Full-resolution image is brightest and shows a horizontal-vertical grid,
- Mid-resolution image is moderate bright and shows a diagonal grid,
- Low-resolution image is darkest and shows a horizontal-vertical grid.

## Purpose

The purpose of such corrupted pyramids is to demonstrate their usage
in visualisation programs such as BigDataViewer in Fiji or in Napari.
When pyramids are build correctly, it is hard to visually confirm
(observe) if a low-resolution was loaded at first, is currently
displayed (when view is zoomed-out sufficiently).

# How to Reproduce

Both images can be re-created as follows:

```bash
pixi run python create_pyramid.py
```

