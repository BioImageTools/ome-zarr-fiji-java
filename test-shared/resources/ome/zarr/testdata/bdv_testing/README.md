# Logo Spots and Spot Segmentation Test OME-Zarr Datasets

Two related 2D datasets (OME-Zarr v0.5) with identical extents, so that they overlay exactly:

* `logo_spots.ome.zarr` → intensity image (uint8): 23 Gaussian spots laid out like the cubes of this repository's logo,
  next to the Zarr wordmark in the same blurred style, on a noisy background
* `logo_labels.ome.zarr` → segmentation (uint16): one label id per spot (1–23) and per letter of "zarr"
  (24–27), 0 is background

They are deliberately not called "blobs", so they do not get mixed up with Fiji's well-known *Blobs* sample image.

Each dataset stores:

* A single 2D image (Y × X), 512 × 512 pixels
* 2 resolution levels (multiscale pyramid, factor 2)

The second level is made by subsampling, not averaging, so the label ids stay valid.

Both carry OMERO metadata with a channel color and contrast limits: the spots are green with a window of 20–230, the
labels magenta with a window of 0–1, so every label is shown at full brightness right after opening.

## Purpose

These datasets are designed for testing the display of two OME-Zarr images in the same viewer, e.g. adding a
segmentation to an already open BigDataViewer (issue #127).

## How to Reproduce

To recreate the datasets, first create a Conda environment using the provided `conda.yml`:

```bash
conda env create -f conda.yml
conda activate ome-zarr-test
```

Then, run the dataset creation script:

```bash
python create_logo_spots.py
```

It reads the Zarr wordmark from `doc/logo/zarr-pink-horizontal.svg` in this repository and generates:

* `logo_spots.ome.zarr`
* `logo_labels.ome.zarr`
