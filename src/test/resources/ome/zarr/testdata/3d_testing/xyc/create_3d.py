###
# #%L
# OME-Zarr extras for Fiji
# %%
# Copyright (C) 2022 - 2026 SciJava developers
# %%
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
# #L%
###
import numpy as np
import zarr
from ome_zarr.writer import write_multiscale
from ome_zarr.io import parse_url
from ome_zarr.format import FormatV04, FormatV05


def create_test_dataset_3d():
    """
    Creates a small 3D OME-Zarr dataset with:
      - 3 channels (C)
      - 2D pixel data (Y, X)
      - 2 multiscale levels
      - Outputs:
          3d_dataset_v4.ome.zarr → OME-Zarr v0.4
          3d_dataset_v5.ome.zarr → OME-Zarr v0.5
      - Image name: 'image'
      - Pixel datatype: uint8
    """

    # -----------------------------
    # Dataset dimensions
    # -----------------------------
    C, Y, X = 3, 64, 64
    dtype = np.uint8

    # -----------------------------
    # Base resolution data
    # -----------------------------
    data = np.zeros((C, Y, X), dtype=dtype)
    yy, xx = np.meshgrid(np.linspace(0, 1, Y), np.linspace(0, 1, X), indexing="ij")

    for c in range(C):
        base_value = (c + 1) * 50
        data[c] = np.clip(base_value + (yy + xx) * 50, 0, 255).astype(dtype)

    # -----------------------------
    # Downsampled resolution (factor 2)
    # -----------------------------
    data_ds = data[:, ::2, ::2]

    axes = [
        {"name": "c", "type": "channel"},
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
