# 4D Test OME-Zarr Datasets

Two 4D datasets are provided here. Their content is the same,
they differ only in the version of OME-Zarr used for storage:

- `test_4d_v0.4.ome.zarr` → OME-Zarr v0.4
- `test_4d_v0.5.ome.zarr` → OME-Zarr v0.5 (current format)

Each dataset stores:

- 2 timepoints (T)
- 2 channels (C)
- 2D images (Y × X)
- 2 resolution levels (multiscale pyramid)

The two channels have different intensity ranges, making them visually distinct.  
The multiscale pyramids are intentionally simple, for testing and demonstration purposes.

## Purpose

These datasets are designed for testing OME-Zarr reading and visualization in programs such as:

- BigDataViewer in Fiji
- Napari with napari-ome-zarr

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
python create_4d.py
```

This will generate:

* `test_4d_v0.4.ome.zarr`
* `test_4d_v0.5.ome.zarr`
