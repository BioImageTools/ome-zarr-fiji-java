import numpy as np
import zarr
from ome_zarr.writer import write_multiscale
from ome_zarr.io import parse_url
from ome_zarr.format import FormatV04, FormatV05


def create_test_dataset_4d_tzyx():
    """
    Creates a small 4D OME-Zarr dataset with:
      - 4 timepoints (T)
      - 3D pixel data (Z, Y, X)
      - 2 multiscale levels
      - Outputs:
          4d_dataset_v4.ome.zarr → OME-Zarr v0.4
          4d_dataset_v5.ome.zarr → OME-Zarr v0.5
      - Image name: 'image'
      - Pixel datatype: uint8
    """

    # -----------------------------
    # Dataset dimensions
    # -----------------------------
    T, Z, Y, X = 4, 16, 64, 64
    dtype = np.uint8

    # -----------------------------
    # Base resolution data
    # -----------------------------
    data = np.zeros((T, Z, Y, X), dtype=dtype)

    for t in range(T):
        time_offset = t * 30

        zz, yy, xx = np.meshgrid(
            np.linspace(0, 1, Z),
            np.linspace(0, 1, Y),
            np.linspace(0, 1, X),
            indexing="ij"
        )

        volume = (zz + yy + xx) * 50
        data[t] = np.clip(time_offset + volume.astype(dtype), 0, 255)

    # -----------------------------
    # Downsampled resolution (factor 2)
    # -----------------------------
    data_ds = data[:, ::2, ::2, ::2]

    axes = [
        {"name": "t", "type": "time"},
        {"name": "z", "type": "space"},
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]

    chunks = (1, Z, Y, X)
    multiscales = [data, data_ds]

    # -----------------------------
    # OME-Zarr v0.4 → Zarr format 2
    # -----------------------------
    store_v4 = parse_url("4d_dataset_v4.ome.zarr", mode="w").store
    root_v4 = zarr.group(store=store_v4, overwrite=True, zarr_format=2)

    write_multiscale(
        multiscales,
        group=root_v4,
        axes=axes,
        fmt=FormatV04(),
        storage_options=dict(chunks=chunks),
        name="image"
    )

    print("OME-Zarr v0.4 written to 4d_dataset_v4.ome.zarr")

    # -----------------------------
    # OME-Zarr v0.5 → Zarr format 3
    # -----------------------------
    store_v5 = parse_url("4d_dataset_v5.ome.zarr", mode="w").store
    root_v5 = zarr.group(store=store_v5, overwrite=True, zarr_format=3)

    write_multiscale(
        multiscales,
        group=root_v5,
        axes=axes,
        fmt=FormatV05(),
        storage_options=dict(chunks=chunks),
        name="image"
    )

    print("OME-Zarr v0.5 written to 4d_dataset_v5.ome.zarr")


if __name__ == "__main__":
    create_test_dataset_4d_tzyx()
