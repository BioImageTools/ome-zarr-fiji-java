import numpy as np
import zarr
from ome_zarr.writer import write_multiscale
from ome_zarr.io import parse_url
from ome_zarr.format import FormatV04, FormatV05


def create_test_dataset_3d():
    """
    Creates a small XYZ OME-Zarr dataset with:
      - 16 Z slices (Z)
      - 2D pixel data (Y, X)
      - 2 resolution levels
      - Outputs:
          3d_dataset_v4.ome.zarr → OME-Zarr v0.4
          3d_dataset_v5.ome.zarr → OME-Zarr v0.5
      - Image name: 'image'
      - Pixel datatype: uint8
    """

    # -----------------------------
    # Dataset dimensions
    # -----------------------------
    Z, Y, X = 16, 64, 64
    dtype = np.uint8

    # -----------------------------
    # Base resolution data
    # -----------------------------
    data = np.zeros((Z, Y, X), dtype=dtype)
    yy, xx = np.meshgrid(np.linspace(0, 1, Y), np.linspace(0, 1, X), indexing="ij")

    for z in range(Z):
        slice_offset = z * 10
        data[z] = np.clip(slice_offset + (yy + xx) * 50, 0, 255).astype(dtype)

    # -----------------------------
    # Downsampled resolution (factor 2)
    # -----------------------------
    data_ds = data[::2, ::2, ::2]

    axes = [
        {"name": "z", "type": "space"},
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]

    chunks = (1, Y, X)
    multiscales = [data, data_ds]

    # -----------------------------
    # OME-Zarr v0.4 → Zarr format 2
    # -----------------------------
    store_v4 = parse_url("3d_dataset_v4.ome.zarr", mode="w").store
    root_v4 = zarr.group(store=store_v4, overwrite=True, zarr_format=2)

    write_multiscale(
        multiscales,
        group=root_v4,
        axes=axes,
        fmt=FormatV04(),
        storage_options=dict(chunks=chunks),
        name="image"
    )

    print("OME-Zarr v0.4 written to 3d_dataset_v4.ome.zarr")

    # -----------------------------
    # OME-Zarr v0.5 → Zarr format 3
    # -----------------------------
    store_v5 = parse_url("3d_dataset_v5.ome.zarr", mode="w").store
    root_v5 = zarr.group(store=store_v5, overwrite=True, zarr_format=3)

    write_multiscale(
        multiscales,
        group=root_v5,
        axes=axes,
        fmt=FormatV05(),
        storage_options=dict(chunks=chunks),
        name="image"
    )

    print("OME-Zarr v0.5 written to 3d_dataset_v5.ome.zarr")


if __name__ == "__main__":
    create_test_dataset_3d()
