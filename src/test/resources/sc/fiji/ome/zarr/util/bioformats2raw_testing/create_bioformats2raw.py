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
import shutil
from pathlib import Path

import numpy as np
import zarr
from ome_zarr.format import FormatV05
from ome_zarr.io import parse_url
from ome_zarr.writer import write_multiscale


def create_bioformats2raw_dataset():
    """
    Creates an OME-NGFF v0.5 'bioformats2raw.layout' collection containing
    two 2D multiscale images plus an OME/METADATA.ome.xml sidecar:

      bf2raw_dataset_v5.ome.zarr/
        zarr.json                 # bioformats2raw.layout = 3 marker
        0/                        # first image (multiscale, 2 levels)
        1/                        # second image (multiscale, 2 levels)
        OME/                      # plain directory (no zarr.json)
          METADATA.ome.xml        # OME-XML for both images

    The point of this dataset is to exercise the OME-folder detection in
    the Fiji backends: an attempt to open the root with one of the
    multiscale-image readers should raise MultiImageDatasetException
    rather than NotAMultiscaleImageException.

    Each image:
      - 2D pixel data (Y, X)
      - 2 multiscale levels
      - No T / C / Z axes
      - Pixel datatype: uint8
    """

    output_path = Path("bf2raw_dataset_v5.ome.zarr")
    if output_path.exists():
        shutil.rmtree(output_path)

    Y, X = 64, 64
    dtype = np.uint8

    # -----------------------------
    # Root group + bioformats2raw.layout marker
    # -----------------------------
    store = parse_url(str(output_path), mode="w").store
    root = zarr.group(store=store, overwrite=True, zarr_format=3)
    # Per the OME-NGFF v0.5 transitional spec, the layout indicator lives
    # under the "ome" attribute key on the root group.
    root.attrs["ome"] = {
        "version": "0.5",
        "bioformats2raw.layout": 3,
    }

    axes = [
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]

    yy, xx = np.meshgrid(
        np.linspace(0, 1, Y),
        np.linspace(0, 1, X),
        indexing="ij",
    )

    # -----------------------------
    # Two child images as multiscale sub-groups
    # -----------------------------
    for img_idx in (0, 1):
        # Different gradient orientation per image so they are
        # visually distinguishable when opened.
        if img_idx == 0:
            data = ((yy + xx) * 127).astype(dtype)
        else:
            data = ((yy - xx + 1) * 127).astype(dtype)
        data_ds = data[::2, ::2]

        img_group = root.create_group(str(img_idx))
        write_multiscale(
            [data, data_ds],
            group=img_group,
            axes=axes,
            fmt=FormatV05(),
            storage_options=dict(chunks=(Y, X)),
            name=f"image_{img_idx}",
        )

    # -----------------------------
    # OME/ sidecar directory containing METADATA.ome.xml
    # -----------------------------
    # Real bioformats2raw output emits this as a plain directory (no
    # zarr.json / .zgroup), so do the same here. Per OME-NGFF v0.5 the
    # transitional spec describes OME as a "directory", not a group.
    ome_dir = output_path / "OME"
    ome_dir.mkdir(parents=True, exist_ok=True)
    (ome_dir / "METADATA.ome.xml").write_text(_ome_xml(Y, X))

    print(f"OME-Zarr v0.5 bioformats2raw.layout written to {output_path}")


def _ome_xml(size_y: int, size_x: int) -> str:
    """Return a minimal OME-XML document describing the two child images."""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<OME xmlns="http://www.openmicroscopy.org/Schemas/OME/2016-06"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.openmicroscopy.org/Schemas/OME/2016-06 http://www.openmicroscopy.org/Schemas/OME/2016-06/ome.xsd">
    <Image ID="Image:0" Name="image_0">
        <Pixels ID="Pixels:0" DimensionOrder="XYZCT" Type="uint8"
                SizeX="{size_x}" SizeY="{size_y}" SizeZ="1" SizeC="1" SizeT="1"
                BigEndian="false" Interleaved="false">
            <Channel ID="Channel:0:0" SamplesPerPixel="1"/>
            <MetadataOnly/>
        </Pixels>
    </Image>
    <Image ID="Image:1" Name="image_1">
        <Pixels ID="Pixels:1" DimensionOrder="XYZCT" Type="uint8"
                SizeX="{size_x}" SizeY="{size_y}" SizeZ="1" SizeC="1" SizeT="1"
                BigEndian="false" Interleaved="false">
            <Channel ID="Channel:1:0" SamplesPerPixel="1"/>
            <MetadataOnly/>
        </Pixels>
    </Image>
</OME>
"""


if __name__ == "__main__":
    create_bioformats2raw_dataset()