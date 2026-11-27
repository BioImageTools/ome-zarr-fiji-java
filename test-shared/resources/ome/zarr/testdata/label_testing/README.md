# Label Colors Test OME-Zarr Dataset

A 2D image (OME-Zarr v0.5) with one label image, modelled on IDR's
[`idr0062A/6001240_labels.zarr`](https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0062A/6001240_labels.zarr), but small:

* `label_colors.ome.zarr` → intensity image `raw` (uint8): 12 Gaussian spots in 3 columns of 4, on a dark background
* `label_colors.ome.zarr/labels/labels` → label image (int8): one disk per spot with the label ids 1–12, 0 is
  background, and above each column a title saying what its labels show ("all same grey", "different colors red", "no colors LUT"), one more label id per column

The `image-label` colors differ per column:

| Column | Label ids  | `colors` entry                                                              |
|--------|------------|-----------------------------------------------------------------------------|
| 1      | 1–4        | all the same grey at half alpha, `[128,128,128,128]`, as in the IDR dataset |
| 2      | 5–8        | four different reds                                                         |
| 3      | 9–12       | none                                                                        |
| titles | 13, 14, 17 | like their column: the grey, the first red, none                            |

Every label also has `image-label` properties, one value of each JSON type: `title` (boolean), `column`, `row` and
`area` (integers), `mean intensity` (a float) for a spot, `text` (a string) for a title. The `image-label` names its
image as `source` (`../../`) and carries its `version`.

Each image stores:

* A single 2D image (Y × X), 128 × 128 pixels
* 2 resolution levels (multiscale pyramid, factor 2)

The second level is made by subsampling, not averaging, so the label ids stay valid.

## Purpose

This dataset is designed for testing how a label image is colored in BigDataViewer (issue #205): metadata colors are
shown as written, and label ids without a color get one from a palette.

## How to Reproduce

To recreate the dataset, first create a Conda environment using the provided `conda.yml`:

```bash
conda env create -f conda.yml
conda activate ome-zarr-test
```

Then, run the dataset creation script:

```bash
python create_label_colors.py
```

It generates `label_colors.ome.zarr`.
