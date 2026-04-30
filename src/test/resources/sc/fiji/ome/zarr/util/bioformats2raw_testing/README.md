# bioformats2raw.layout Test OME-Zarr Dataset

A small OME-Zarr v0.5 collection that mimics the layout produced by
`bioformats2raw` — a top-level group containing several child images plus
an `OME/` sidecar group with `METADATA.ome.xml`:

```
bf2raw_dataset_v5.ome.zarr/
├── zarr.json                 # bioformats2raw.layout = 3 marker (under "ome")
├── 0/                        # first image (multiscale, 2 levels)
├── 1/                        # second image (multiscale, 2 levels)
└── OME/                      # plain directory (no zarr.json), as emitted
    └── METADATA.ome.xml      # by bioformats2raw — OME-XML for both images
```

Each child image stores:

* A single 2D image (Y × X), uint8
* 2 resolution levels (multiscale pyramid)
* No T / C / Z axes — just the spatial Y / X axes

The two images differ only in their gradient orientation (`y + x` vs.
`y − x + 1`) so they are visually distinguishable when opened.

## Purpose

This dataset is **not** a single multiscale image. Both backends, `N5PyramidBackend` and
`ZarrJavaPyramidBackend` detect the case by listing the root's children and
finding the conventional `OME/` folder.

Use this dataset to verify:

* behavior when the root of a
  `bioformats2raw.layout` collection is opened.
* The child images at `0/` and `1/` are themselves valid OME-Zarr v0.5
  multiscale images that can be opened directly.

## How to Reproduce

To recreate the dataset, first create a Conda environment using the provided
`conda.yml`:

```bash
conda env create -f conda.yml
conda activate ome-zarr-test
```

Then run the dataset creation script:

```bash
python create_bioformats2raw.py
```

This generates:

* `bf2raw_dataset_v5.ome.zarr`
