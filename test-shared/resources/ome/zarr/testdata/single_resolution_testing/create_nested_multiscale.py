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
"""
Creates nested_multiscale_v4.ome.zarr and nested_multiscale_v5.ome.zarr
(OME-Zarr v0.4 / Zarr v2 and OME-Zarr v0.5 / Zarr v3): valid OME-Zarr multiscale
images whose dataset paths point one level deeper ('sub/0', 'sub/1'), so that the
arrays' *immediate parent* ('sub') is a plain group and the multiscales metadata
sits two levels up.

Opening such an array on its own therefore has to interpret it from its own
metadata alone, which is the fallback route these datasets are written for.

Written with plain zarr-python (no ome-zarr writer) because the multiscales
attributes are laid out by hand here; the array codecs and the Zarr v2
dimension separator are chosen to match the datasets written by ome-zarr-py in
the sibling scripts.
"""
import os
import shutil

import numcodecs
import numpy as np
import zarr

Y, X = 16, 16
DTYPE = "uint8"

# uint8 ramp: value at (y, x) == y * X + x, so tests can assert single pixels.
DATA = np.arange(Y * X, dtype=np.uint16).reshape(Y, X).astype(np.uint8)


def _array_options(zarr_format):
    """Codec and chunk-key options matching the ome-zarr-py written datasets."""
    if zarr_format == 2:
        return dict(
            compressors=numcodecs.Blosc(cname="zstd", clevel=5, shuffle=1),
            chunk_key_encoding={"name": "v2", "configuration": {"separator": "/"}},
        )
    # Zarr v3 defaults (bytes + zstd, '/' separator) already match; only v3
    # arrays can carry dimension_names.
    return dict(dimension_names=("y", "x"))


def _fresh_group(dest, zarr_format):
    if os.path.exists(dest):
        shutil.rmtree(dest)
    return zarr.open_group(dest, mode="w", zarr_format=zarr_format)


def _write_array(group, name, data, zarr_format):
    array = group.create_array(
        name,
        shape=data.shape,
        chunks=data.shape,
        dtype=DTYPE,
        **_array_options(zarr_format),
    )
    array[:] = data
    return array


def create_nested_multiscale(dest, zarr_format):
    """
    A valid OME-Zarr multiscale image with its two resolution levels stored
    under 'sub/', i.e. the multiscales metadata is two levels above the arrays.
    """
    root = _fresh_group(dest, zarr_format)
    sub = root.create_group("sub")
    _write_array(sub, "0", DATA, zarr_format)
    _write_array(sub, "1", DATA[::2, ::2], zarr_format)

    axes = [
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]
    datasets = [
        {
            "path": "sub/0",
            "coordinateTransformations": [{"type": "scale", "scale": [1.0, 1.0]}],
        },
        {
            "path": "sub/1",
            "coordinateTransformations": [{"type": "scale", "scale": [2.0, 2.0]}],
        },
    ]
    if zarr_format == 2:
        # OME-Zarr v0.4: multiscales in .zattrs, version per multiscale entry.
        root.attrs["multiscales"] = [
            {"version": "0.4", "name": "image", "axes": axes, "datasets": datasets}
        ]
    else:
        # OME-Zarr v0.5: multiscales under the versioned 'ome' namespace.
        root.attrs["ome"] = {
            "version": "0.5",
            "multiscales": [{"name": "image", "axes": axes, "datasets": datasets}],
        }
    print(f"Written {dest} (Zarr v{zarr_format}, multiscales two levels up)")


if __name__ == "__main__":
    create_nested_multiscale("nested_multiscale_v4.ome.zarr", 2)
    create_nested_multiscale("nested_multiscale_v5.ome.zarr", 3)
