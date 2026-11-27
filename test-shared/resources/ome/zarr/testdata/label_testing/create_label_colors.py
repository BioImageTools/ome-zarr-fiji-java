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
import numpy as np
import zarr
from PIL import Image, ImageDraw, ImageFont
from ome_zarr.format import FormatV05
from ome_zarr.io import parse_url
from ome_zarr.writer import write_multiscale, write_multiscale_labels

# Spot centers (y, x) at full resolution: 3 columns of 4 spots each, below their titles
COLUMNS_X = (24, 64, 104)
ROWS_Y = (46, 68, 90, 112)
RADIUS = 8
# Column titles, one line per word. "different" is wider than a column, but its
# neighbors in that line are short, so the lines do not run into each other.
TITLES = (("all", "same", "grey"), ("different", "colors", "red"), ("no", "colors", "LUT"))
LINE_HEIGHT = 10
# Label id per column title, colored like its column. The "LUT" title gets 17
# because its palette color, a light blue, stands apart from the red column,
# while 15 would be a dark teal.
TITLE_LABELS = (13, 14, 17)

# Label ids per column: 1-4, 5-8, 9-12
GREY = [128, 128, 128, 128]
REDS = [[255, 0, 0, 255], [192, 0, 0, 255], [128, 0, 0, 255], [64, 0, 0, 255]]


def create_label_colors():
    """
    Creates label_colors.ome.zarr, an OME-Zarr v0.5 image with one label image:
      - the image: uint8, 12 Gaussian spots on a dark background
      - labels/labels: int8, one disk per spot with label ids 1-12, 0 is background,
        and image-label colors that differ per column:
          * column 1 (ids 1-4): all the same grey, at half alpha
          * column 2 (ids 5-8): four different reds
          * column 3 (ids 9-12): no color at all
        Above each column its title says what its labels show, drawn as one more
        label id per column (13, 14, 17), colored like the column.
        Every label has image-label properties of each JSON value type, and the
        image-label names its image as source.
    Both have 2 multiscale levels (Y, X): 128 x 128 and 64 x 64.
    """
    Y, X = 128, 128
    yy, xx = np.mgrid[0:Y, 0:X]
    image = np.full((Y, X), 10.0)
    labels = np.zeros((Y, X), dtype=np.int8)
    for column, x in enumerate(COLUMNS_X):
        for row, y in enumerate(ROWS_Y):
            image += 200 * np.exp(-((xx - x) ** 2 + (yy - y) ** 2) / (2 * (RADIUS / 2) ** 2))
            labels[(xx - x) ** 2 + (yy - y) ** 2 <= RADIUS ** 2] = 4 * column + row + 1
    title_labels = titles(Y, X)
    labels[title_labels > 0] = title_labels[title_labels > 0]
    image = np.clip(image, 0, 255).astype(np.uint8)

    # A string, integers, a float and a boolean, so each JSON value type is read
    properties = [{"label-value": 4 * column + row + 1, "title": False, "column": column + 1, "row": row + 1,
                   "area": int((labels == 4 * column + row + 1).sum()),
                   "mean intensity": round(float(image[labels == 4 * column + row + 1].mean()), 2)}
                  for column in range(len(COLUMNS_X)) for row in range(len(ROWS_Y))] \
                 + [{"label-value": label, "title": True, "column": column + 1, "text": " ".join(title)}
                    for column, (title, label) in enumerate(zip(TITLES, TITLE_LABELS))]

    colors = [{"label-value": i, "rgba": GREY} for i in range(1, 5)] \
             + [{"label-value": i, "rgba": rgba} for i, rgba in zip(range(5, 9), REDS)] \
             + [{"label-value": TITLE_LABELS[0], "rgba": GREY}, {"label-value": TITLE_LABELS[1], "rgba": REDS[0]}]

    axes = [{"name": "y", "type": "space"}, {"name": "x", "type": "space"}]
    path = "label_colors.ome.zarr"
    root = zarr.group(store=parse_url(path, mode="w").store, overwrite=True, zarr_format=3)
    # The second level is made by subsampling, not averaging, so the label ids stay valid.
    write_multiscale([image, image[::2, ::2]], group=root, axes=axes, fmt=FormatV05(),
                     storage_options=dict(chunks=(Y, X)), name="raw")
    write_multiscale_labels([labels, labels[::2, ::2]], group=root, name="labels", axes=axes, fmt=FormatV05(),
                            storage_options=dict(chunks=(Y, X)), label_metadata={"colors": colors, "properties": properties,
                                                                                "source": {"image": "../../"}})
    # ome-zarr-py leaves the version out of the labels group, which OME-Zarr 0.5 puts in every ome object.
    labels_group = root["labels"]
    labels_group.attrs["ome"] = {"version": FormatV05().version, **labels_group.attrs["ome"]}
    print(f"OME-Zarr v0.5 written to {path}")


def titles(height, width):
    """The label id of its column's title where one is drawn, else 0: each title
    centered above its column, in Pillow's built-in bitmap font so the pixels stay crisp."""
    canvas = Image.new("L", (width, height))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default_imagefont()
    for x, title, label in zip(COLUMNS_X, TITLES, TITLE_LABELS):
        for line, word in enumerate(title):
            draw.text((x - font.getbbox(word)[2] // 2, 1 + line * LINE_HEIGHT), word, fill=label, font=font)
    return np.array(canvas).astype(np.int8)


if __name__ == "__main__":
    create_label_colors()
