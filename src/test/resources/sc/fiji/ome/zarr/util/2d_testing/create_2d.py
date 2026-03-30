import numpy as np
import zarr
from ome_zarr.writer import write_multiscale
from ome_zarr.io import parse_url
from ome_zarr.format import FormatV04, FormatV05


def create_test_dataset_2d():
    """
    Creates a small 2D OME-Zarr dataset with:
      - 2D pixel data (Y, X)
      - 2 multiscale levels
      - Outputs:
          2d_dataset_v4.ome.zarr → OME-Zarr v0.4
          2d_dataset_v5.ome.zarr → OME-Zarr v0.5
      - Image name: 'image'
      - Pixel datatype: uint8
    """

    # -----------------------------
    # Dataset dimensions
    # -----------------------------
    Y, X = 64, 64
    dtype = np.uint8

    # -----------------------------
    # Base resolution data
    # -----------------------------
    yy, xx = np.meshgrid(
        np.linspace(0, 1, Y),
        np.linspace(0, 1, X),
        indexing="ij"
    )

    data = ((yy + xx) * 127).astype(dtype)

    # -----------------------------
    # Downsampled resolution (factor 2)
    # -----------------------------
    data_ds = data[::2, ::2]

    axes = [
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]

    chunks = (Y, X)
    multiscales = [data, data_ds]

    # -----------------------------
    # OME-Zarr v0.4 → Zarr format 2
    # -----------------------------
    store_v4 = parse_url("2d_dataset_v4.ome.zarr", mode="w").store
    root_v4 = zarr.group(store=store_v4, overwrite=True, zarr_format=2)

    write_multiscale(
        multiscales,
        group=root_v4,
        axes=axes,
        fmt=FormatV04(),
        storage_options=dict(chunks=chunks),
        name="image"
    )

    print("OME-Zarr v0.4 written to 2d_dataset_v4.ome.zarr")

    # -----------------------------
    # OME-Zarr v0.5 → Zarr format 3
    # -----------------------------
    store_v5 = parse_url("2d_dataset_v5.ome.zarr", mode="w").store
    root_v5 = zarr.group(store=store_v5, overwrite=True, zarr_format=3)

    write_multiscale(
        multiscales,
        group=root_v5,
        axes=axes,
        fmt=FormatV05(),
        storage_options=dict(chunks=chunks),
        name="image"
    )

    print("OME-Zarr v0.5 written to 2d_dataset_v5.ome.zarr")


if __name__ == "__main__":
    create_test_dataset_2d()
