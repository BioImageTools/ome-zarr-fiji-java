# Reload Demo: a Growing Dataset

Two scripts for demonstrating how an open OME-Zarr image is reloaded while its data is still being written.

## `create_logo_cells.py`: the dataset

Writes `logo_cells.ome.zarr`, a 2D uint8 time lapse (T × Y × X = 5 × 8192 × 8192) with the layout of the shared test
dataset `logo_spots.ome.zarr`, 16 times larger. It has one big irregular, textured cell per cube of the repository
logo's glyph, and the Zarr wordmark spelled out by about 380 small cells. From one timepoint to the next, every cell
drifts, changes its outline and its brightness. It has 5 resolution levels (factor 2, averaged, down to 512 × 512) and
chunks of 1 × 128 × 128.

At about 310 MB in 27,000 files it is not committed. Generate it with the Conda environment of
`test-shared/resources/ome/zarr/testdata/bdv_testing/conda.yml`, whose `create_logo_spots.py` this script imports the
glyph and the wordmark from (about 40 s):

```bash
python create_logo_cells.py /path/to/logo_cells.ome.zarr
```

## `stream_logo_cells.py`: simulating the acquisition

Copies the dataset to a new location the way an acquisition would write it. First the skeleton, i.e. every
`zarr.json`, so the image can be opened right away, empty. Then the chunks one after the other, spread evenly over 2
minutes or the given number of seconds: timepoint by timepoint, and within a timepoint from top to bottom, with every
resolution level's chunks of a region together, so a timepoint fills in band by band at any zoom. Each chunk is written to a temporary file and renamed, so a
reader never sees half a chunk. A rerun deletes the chunks of the previous run and keeps the skeleton, copying only what
is missing of it. It needs nothing but Python 3.

```bash
python stream_logo_cells.py /path/to/logo_cells.ome.zarr /path/to/growing.ome.zarr [SECONDS]
```

Open the target in Fiji as soon as the skeleton is there, then reload it while the chunks arrive.
