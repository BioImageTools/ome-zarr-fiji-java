# XYZ Test OME-Zarr Datasets

Two XYZ datasets are provided here. Their content is the same,
they differ only in the version of OME-Zarr used for storage:

- `3d_dataset_v4.ome.zarr` → OME-Zarr v0.4
- `3d_dataset_v5.ome.zarr` → OME-Zarr v0.5 (current format)

Each dataset stores:

- 16 slices (Z)
- 2D images (Y × X)
- 2 resolution levels (multiscale pyramid)

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
