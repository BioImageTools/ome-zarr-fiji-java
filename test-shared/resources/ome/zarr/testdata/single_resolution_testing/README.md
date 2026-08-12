# Single-Resolution Test OME-Zarr Datasets

Datasets for images that are opened as a **single resolution level**, i.e. as a
one-level pyramid:

| Dataset | Format | Layout |
| --- | --- | --- |
| `single_resolution_dataset_v5.ome.zarr` | OME-Zarr v0.5 | multiscale image with only one level |
| `nested_multiscale_v4.ome.zarr` | OME-Zarr v0.4 (Zarr v2) | multiscale image whose levels live at `sub/0`, `sub/1` |
| `nested_multiscale_v5.ome.zarr` | OME-Zarr v0.5 (Zarr v3) | multiscale image whose levels live at `sub/0`, `sub/1` |

All of them store uint8 data. The `nested_multiscale_*` arrays are 16 × 16
(Y × X; level 1 is 8 × 8) and carry the ramp `value(y, x) = y * 16 + x`, so
single pixels can be asserted in tests.

## Purpose

`single_resolution_dataset_v5.ome.zarr` tests behaviour specific to images that
have **no resolution pyramid** — in particular that the window title suffix is
`(V)` rather than `(V,R)`.

The `nested_multiscale_*` datasets put the multiscales metadata *two* levels
above the arrays, so that the **immediate parent group of an array carries no
multiscales metadata**. That is what reaches the last fallback in
`AbstractPyramidBackend.loadSingleArray`, where the array has to be interpreted
from its own metadata alone:

* `nested_multiscale_v5.ome.zarr/sub/0` opens from the Zarr v3
  `dimension_names` of the array — uncalibrated, i.e. unit scale, no units and
  no OMERO metadata, because the multiscales group two levels up is not
  consulted.
* `nested_multiscale_v4.ome.zarr/sub/0` cannot be interpreted at all (Zarr v2
  arrays have no `dimension_names`) and must be rejected with a
  `SingleArrayAxesUnknownException`.
* Both are also opened *as a whole*, to cover multiscales metadata whose
  `datasets[].path` points into a subgroup rather than at a direct child.

### Validator status

`nested_multiscale_v4.ome.zarr` and `nested_multiscale_v5.ome.zarr` are valid
OME-Zarr images: they validate against the official
[NGFF JSON schemas](https://ngff.openmicroscopy.org/0.4/schemas/image.schema)
(0.4 and 0.5) and against `ome-zarr-models` 1.7. Nesting is legal because the
schema constrains `datasets[].path` only to `"type": "string"` and the spec
requires it to be a path relative to the multiscales group — which `sub/0` is.
It is unusual, though: no common writer emits it.

## How to Reproduce

To recreate the datasets, first create a Conda environment using the provided
`conda.yml`:

```bash
conda env create -f conda.yml
conda activate ome-zarr-test
```

Then run the dataset creation scripts:

```bash
python create_single_resolution.py   # single_resolution_dataset_v5.ome.zarr
python create_nested_multiscale.py   # nested_multiscale_v4/v5.ome.zarr
```

`create_single_resolution.py` writes through `ome-zarr-py`;
`create_nested_multiscale.py` uses plain `zarr-python`, because it lays out the
multiscales attributes by hand.