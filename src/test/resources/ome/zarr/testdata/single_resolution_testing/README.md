# Single-Resolution Test OME-Zarr Dataset

A single 2D dataset is provided here, stored in the current OME-Zarr v0.5 format:

* `single_resolution_dataset_v5.ome.zarr` → OME-Zarr v0.5

The dataset stores:

* A single 2D image (Y × X = 16 × 16)
* 1 resolution level (no pyramid)

## Purpose

This dataset is used to test behaviour specific to images that have **no resolution pyramid** — in particular that the window title suffix is `(V)` rather than `(V,R)`.

Because the dataset is small and deterministic, it is suitable for:

* Unit tests
* Demonstrations
* Git repository inclusion

## How to Reproduce

To recreate the dataset, first create a Conda environment using the provided `conda.yml`:

```bash
conda env create -f conda.yml
conda activate ome-zarr-test
```

Then, run the dataset creation script:

```bash
python create_single_resolution.py
```

This will generate:

* `single_resolution_dataset_v5.ome.zarr`