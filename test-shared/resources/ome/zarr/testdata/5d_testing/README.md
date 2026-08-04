# 5D Test OME-Zarr Datasets

Two 5D datasets are provided here. Their content is the same,
they differ only in the version of OME-Zarr used for storage:

- `test_5d_v0.4.ome.zarr` → OME-Zarr v0.4
- `test_5d_v0.5.ome.zarr` → OME-Zarr v0.5 (current format)

Each dataset stores:

- 4 timepoints (T)
- 3 channels (C)
- 3D volumes (Z × Y × X)
- 2 resolution levels (multiscale pyramid)

The two channels have different intensity ranges, making them visually distinct.  
The multiscale pyramids are intentionally simple, for testing and demonstration purposes.

## Purpose

These datasets are designed for testing OME-Zarr reading and visualization in programs such as:

- [BigDataViewer](https://imagej.net/plugins/bdv/) in [Fiji](https://fiji.sc/)
- [Napari](https://napari.org/stable/) with [napari-ome-zarr](https://github.com/ome/napari-ome-zarr)

Because the datasets are small and deterministic, they are suitable for:

- Unit tests
- Demonstrations
- Git repository inclusion

## How to Reproduce

To recreate the datasets, first create a Conda environment using the provided `conda.yml`:

```bash
conda env create -f conda.yml
conda activate ome-zarr-test
```

Then, run the dataset creation script:

```bash
python create_5d.py
```

This will generate:

* test_5d_v0.4.ome.zarr
* test_5d_v0.5.ome.zarr
