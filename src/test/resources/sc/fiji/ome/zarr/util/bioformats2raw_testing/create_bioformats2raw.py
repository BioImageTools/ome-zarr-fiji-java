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
import json
import os

import numpy as np
import zarr
from ome_zarr.format import FormatV05
from ome_zarr.io import parse_url
from ome_zarr.writer import write_multiscale

DEST = "bf2raw_dataset_v5.ome.zarr"

OME_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<OME xmlns="http://www.openmicroscopy.org/Schemas/OME/2016-06"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.openmicroscopy.org/Schemas/OME/2016-06
     http://www.openmicroscopy.org/Schemas/OME/2016-06/ome.xsd"
     Creator="bioformats2raw 0.7.0">
  <Image ID="Image:0" Name="image 0">
    <Pixels ID="Pixels:0" DimensionOrder="XYZCT" Type="uint8"
            SizeX="64" SizeY="64" SizeZ="1" SizeC="1" SizeT="1"
            PhysicalSizeX="1.0" PhysicalSizeXUnit="µm"
            PhysicalSizeY="1.0" PhysicalSizeYUnit="µm">
      <Channel ID="Channel:0:0" SamplesPerPixel="1"/>
      <TiffData PlaneCount="1"/>
    </Pixels>
  </Image>
  <Image ID="Image:1" Name="image 1">
    <Pixels ID="Pixels:1" DimensionOrder="XYZCT" Type="uint8"
            SizeX="64" SizeY="64" SizeZ="1" SizeC="1" SizeT="1"
            PhysicalSizeX="1.0" PhysicalSizeXUnit="µm"
            PhysicalSizeY="1.0" PhysicalSizeYUnit="µm">
      <Channel ID="Channel:1:0" SamplesPerPixel="1"/>
      <TiffData PlaneCount="1"/>
    </Pixels>
  </Image>
</OME>
"""


def create_bioformats2raw_dataset():
    """
    Creates a small OME-Zarr v0.5 collection that mimics the layout
    produced by bioformats2raw, containing two child images and an OME/
    sidecar directory.

    Outputs:
        bf2raw_dataset_v5.ome.zarr/
            zarr.json              bioformats2raw.layout = 3 marker
            0/                     first child image (y+x gradient)
            1/                     second child image (y-x gradient)
            OME/METADATA.ome.xml   OME-XML sidecar for both images
    """

    # ------------------------------------------------------------------
    # Dataset dimensions
    # ------------------------------------------------------------------
    Y, X = 64, 64
    dtype = np.uint8

    yy, xx = np.meshgrid(np.arange(Y), np.arange(X), indexing="ij")
    norm = Y + X - 2  # = 126 for Y=X=64; maps both gradients to [0, 255]

    # Image 0: bottom-right corner brightest (y + x)
    data0 = ((yy + xx) / norm * 255).astype(dtype)

    # Image 1: top-right corner brightest (y - x, shifted to [0, 1])
    data1 = ((yy - xx + X - 1) / norm * 255).astype(dtype)

    axes = [
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]
    chunks = (Y, X)

    # ------------------------------------------------------------------
    # Write child images
    # ------------------------------------------------------------------
    for idx, (data, name) in enumerate([(data0, "image 0"), (data1, "image 1")]):
        multiscales = [data, data[::2, ::2]]
        store = parse_url(f"{DEST}/{idx}", mode="w").store
        grp = zarr.group(store=store, overwrite=True, zarr_format=3)
        write_multiscale(
            multiscales,
            group=grp,
            axes=axes,
            fmt=FormatV05(),
            storage_options=dict(chunks=chunks),
            name=name,
        )
        print(f"Written child image {idx} ({name}) to {DEST}/{idx}/")

    # ------------------------------------------------------------------
    # Root zarr.json — bioformats2raw.layout marker only (no multiscales)
    # ------------------------------------------------------------------
    os.makedirs(DEST, exist_ok=True)
    root_meta = {
        "attributes": {"ome": {"bioformats2raw.layout": 3, "version": "0.5"}},
        "zarr_format": 3,
        "node_type": "group",
    }
    with open(f"{DEST}/zarr.json", "w") as fh:
        json.dump(root_meta, fh, indent=2)
    print(f"Written root zarr.json to {DEST}/zarr.json")

    # ------------------------------------------------------------------
    # OME/ sidecar — plain directory (no zarr.json), as emitted by
    # bioformats2raw; contains only METADATA.ome.xml
    # ------------------------------------------------------------------
    os.makedirs(f"{DEST}/OME", exist_ok=True)
    with open(f"{DEST}/OME/METADATA.ome.xml", "w", encoding="utf-8") as fh:
        fh.write(OME_XML)
    print(f"Written OME-XML sidecar to {DEST}/OME/METADATA.ome.xml")


if __name__ == "__main__":
    create_bioformats2raw_dataset()
