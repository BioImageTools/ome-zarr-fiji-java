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
import io
import pathlib

import cairosvg
import numpy as np
import zarr
from PIL import Image, ImageDraw
from ome_zarr.writer import write_multiscale
from ome_zarr.io import parse_url
from ome_zarr.format import FormatV05

# The Zarr logo the repository logo uses, see doc/logo
ZARR_SVG = pathlib.Path(__file__).resolve().parents[6] / "doc" / "logo" / "zarr-pink-horizontal.svg"

# Cells of the repository logo's glyph (x right, y up), as in doc/logo/logo.py's "logo-zarr"
# variant: the wordmark replaces the top edge's three rightmost cubes.
GLYPH = [(0, y) for y in range(7)] + [(x, 6) for x in range(1, 4)] \
        + [(2, 0), (3, 0), (4, 0), (4, 1)] \
        + [(x, y) for x in (2, 4, 6) for y in (2, 3, 4)]


def create_logo_spots_and_labels():
    """
    Creates two related 2D OME-Zarr v0.5 datasets with identical extents:
      - logo_spots.ome.zarr  → uint8 intensity image: Gaussian spots laid out like the
                               repository logo's cubes, the blurred Zarr wordmark, and noise
      - logo_labels.ome.zarr → uint16 segmentation, one label id per spot and per letter
    Both have 2 multiscale levels (Y, X), so they overlay exactly in a viewer,
    and OMERO metadata with a channel color and contrast limits.
    """

    Y, X = 512, 512
    pitch = 60            # px per glyph cell
    left, top = 40, 50    # px from the image corner to the top left cell's corner
    rng = np.random.default_rng(42)

    yy, xx = np.mgrid[0:Y, 0:X]
    intensity = np.zeros((Y, X), dtype=np.float64)
    labels = np.zeros((Y, X), dtype=np.uint16)
    best = np.zeros((Y, X), dtype=np.float64)

    # ----------------------------------------------------------------------
    # One spot per cube, jittered, stretched and dimmed so the grid is not exact
    # ----------------------------------------------------------------------
    for i, (cx, cy) in enumerate(GLYPH, start=1):
        x = left + (cx + 0.5) * pitch + rng.normal(0, 6)
        y = top + (6 - cy + 0.5) * pitch + rng.normal(0, 6)
        sx, sy = rng.uniform(10, 16, size=2)
        spot = np.exp(-(xx - x) ** 2 / (2 * sx ** 2) - (yy - y) ** 2 / (2 * sy ** 2)) * rng.uniform(0.7, 1.0)
        intensity += spot
        # pixel belongs to the spot that is brightest there, if above half its max
        mask = (spot > 0.5 * spot.max()) & (spot > best)
        labels[mask] = i
        best = np.maximum(best, spot)
    intensity /= intensity.max()

    # ---------------------------------------------------------------------
    # The wordmark right of the top edge, blurred like the spots: each letter
    # scaled to its own peak, dimmed on its own, and labelled where above half
    # its peak, like a spot. The outlined "z" is filled first, as a blurred
    # outline would come out a dim ring.
    # ---------------------------------------------------------------------
    word = wordmark(width=int(3.2 * pitch))
    cuts = letter_cuts(word > 0.5)
    word[:, :cuts[0]] = np.maximum(word[:, :cuts[0]], holes(word[:, :cuts[0]] > 0.5))
    pad = 12   # px around the wordmark for the blur to spread into
    word = np.pad(word, pad)
    letter = np.searchsorted(cuts, np.arange(word.shape[1]) - pad)   # letter index per column
    glow = blur(word, sigma=3)
    peak = np.array([glow[:, letter == j].max() for j in range(len(cuts) + 1)])
    glow /= peak[letter]
    mask = glow > 0.5
    glow *= 0.9 * rng.uniform(0.7, 1.0, size=len(cuts) + 1)[letter]
    wx, wy = left + int(4.1 * pitch) - pad, top + (pitch - word.shape[0]) // 2
    intensity[wy:wy + word.shape[0], wx:wx + word.shape[1]] += glow
    region = labels[wy:wy + word.shape[0], wx:wx + word.shape[1]]
    region[mask] = (len(GLYPH) + 1 + np.broadcast_to(letter, word.shape))[mask]
    n_labels = int(labels.max())

    intensity = intensity * 200 + rng.normal(20, 5, (Y, X))
    image = np.clip(intensity, 0, 255).astype(np.uint8)

    axes = [
        {"name": "y", "type": "space"},
        {"name": "x", "type": "space"},
    ]

    # Raw data in green (GFP-like), from the background level to the brightest spot
    write(image, axes, "logo_spots.ome.zarr", "logo_spots",
          omero_channel("spots", "00FF00", start=20, end=230, min=0, max=255))
    # Labels in magenta; a window of 0..1 shows every label id >= 1 at full brightness
    write(labels, axes, "logo_labels.ome.zarr", "logo_labels",
          omero_channel("labels", "FF00FF", start=0, end=1, min=0, max=n_labels))


def wordmark(width):
    """The "zarr" of the Zarr logo as coverage in 0..1, `width` px wide, without the
    cube mark: rasterized, and cut at the widest run of empty columns."""
    png = cairosvg.svg2png(url=str(ZARR_SVG), output_width=1800)
    art = Image.open(io.BytesIO(png)).getchannel("A")
    alpha = np.array(art.crop(art.getbbox()))
    runs = empty_column_runs(alpha > 0)
    _, gap_end = max(runs, key=lambda r: r[1] - r[0])
    word = Image.fromarray(alpha[:, gap_end:])
    word = word.crop(word.getbbox())
    word = word.resize((width, round(word.height * width / word.width)), Image.LANCZOS)
    return np.array(word) / 255.0


def empty_column_runs(mask):
    """(start, end) of every run of empty columns in `mask`."""
    empty = list(~mask.any(axis=0)) + [False]
    runs, start = [], None
    for x, blank in enumerate(empty):
        if blank and start is None:
            start = x
        elif not blank and start is not None:
            runs.append((start, x))
            start = None
    return runs


def letter_cuts(mask):
    """The columns that split `mask` into letters: the middle of each run of empty columns."""
    return [(start + end) // 2 for start, end in empty_column_runs(mask)]


def holes(mask):
    """1 where `mask` encloses background, else 0: what a flood fill from outside does not reach."""
    outside = Image.fromarray(np.pad(mask, 1).astype(np.uint8) * 255).copy()   # floodfill cannot write to fromarray's buffer
    ImageDraw.floodfill(outside, (0, 0), 128)
    return (np.array(outside)[1:-1, 1:-1] == 0).astype(np.float64)


def blur(a, sigma):
    """`a` convolved with a Gaussian of `sigma` px, separably."""
    r = int(3 * sigma)
    k = np.exp(-np.arange(-r, r + 1) ** 2 / (2 * sigma ** 2))
    k /= k.sum()
    a = np.apply_along_axis(np.convolve, 0, a, k, mode="same")
    return np.apply_along_axis(np.convolve, 1, a, k, mode="same")


def omero_channel(label, color, start, end, min, max):
    return {
        "channels": [{
            "label": label,
            "color": color,
            "active": True,
            "window": {"start": start, "end": end, "min": min, "max": max},
        }],
        "rdefs": {"model": "color"},
    }


def write(data, axes, path, name, omero):
    store = parse_url(path, mode="w").store
    root = zarr.group(store=store, overwrite=True, zarr_format=3)
    write_multiscale(
        [data, data[::2, ::2]],
        group=root,
        axes=axes,
        fmt=FormatV05(),
        storage_options=dict(chunks=(256, 256)),
        name=name,
        metadata={"omero": omero}
    )
    print(f"OME-Zarr v0.5 written to {path}")


if __name__ == "__main__":
    create_logo_spots_and_labels()
