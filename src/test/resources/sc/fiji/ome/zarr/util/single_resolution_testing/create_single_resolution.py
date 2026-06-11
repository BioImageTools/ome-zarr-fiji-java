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
from ome_zarr.format import FormatV05


def create_test_dataset_single_resolution():
    """
    Creates a small 2D OME-Zarr dataset with:
      - 2D pixel data (Y, X)
      - 1 multiscale level (no downsampled resolution)
      - Output:
          single_resolution_dataset_v5.ome.zarr → OME-Zarr v0.5
      - Image name: 'image'
      - Pixel datatype: uint8
    """

    Y, X = 16, 16
    dtype = np.uint8
    data = np.arange(Y * X, dtype=dtype).reshape(Y, X)

    axes = [
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]
    chunks = (Y, X)

    store = parse_url("single_resolution_dataset_v5.ome.zarr", mode="w").store
    root = zarr.open_group(store=store, zarr_format=3)
    write_multiscale(
        [data],
        group=root,
        axes=axes,
        fmt=FormatV05(),
        storage_options=dict(chunks=chunks),
        name="image",
    )
    print("OME-Zarr v0.5 written to single_resolution_dataset_v5.ome.zarr")


if __name__ == "__main__":
    create_test_dataset_single_resolution()