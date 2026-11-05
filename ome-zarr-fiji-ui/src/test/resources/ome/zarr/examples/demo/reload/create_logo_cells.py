###
# #%L
# OME-Zarr integration into FIJI
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
import sys
from pathlib import Path

import numpy as np
import zarr
from PIL import Image
from ome_zarr.writer import write_multiscale
from ome_zarr.io import parse_url
from ome_zarr.format import FormatV05

# the layout of logo_spots.ome.zarr, whose script lives with the shared test data
sys.path.insert(0, str(Path(__file__).resolve().parents[9] / "test-shared/resources/ome/zarr/testdata/bdv_testing"))
from create_logo_spots import GLYPH, wordmark, letter_cuts, holes, omero_channel

T = 5
SCALE = 16                      # times logo_spots.ome.zarr: 8192 × 8192 px
Y = X = 512 * SCALE
PITCH = 60 * SCALE              # px per glyph cell
LEFT, TOP = 40 * SCALE, 50 * SCALE
CHUNKS = (1, 128, 128)
LEVELS = 5                      # down to 512 × 512


def create_logo_cells(path):
    """
    Creates a 2D OME-Zarr v0.5 uint8 time lapse (T, Y, X) with the layout of
    logo_spots.ome.zarr, 16 times larger: one big irregular, textured cell per cube
    of the repository logo's glyph, and the Zarr wordmark spelled out by small
    cells. Over the timepoints the cells drift, change their outline and brightness.
    """
    rng = np.random.default_rng(42)
    cells = [big_cell(rng, cx, cy) for cx, cy in GLYPH] + small_cells(rng)
    texture = smooth_noise(rng, sigma=12)

    levels = [np.empty((T, Y >> i, X >> i), dtype=np.uint8) for i in range(LEVELS)]
    for t in range(T):
        frame = np.zeros((Y, X), dtype=np.float32)
        for cell in cells:
            add_cell(frame, cell, t)
        frame *= 0.7 + 0.6 * texture
        frame = frame * 190 + 15 + 4 * rng.standard_normal((Y, X), dtype=np.float32)
        levels[0][t] = np.clip(frame, 0, 255)
        for i in range(1, LEVELS):
            levels[i][t] = downsample(levels[i - 1][t])
        print(f"timepoint {t} done, {len(cells)} cells")
    write(levels, path)


def big_cell(rng, cx, cy):
    return cell(rng,
                x=LEFT + (cx + 0.5) * PITCH + rng.normal(0, 0.1 * PITCH),
                y=TOP + (6 - cy + 0.5) * PITCH + rng.normal(0, 0.1 * PITCH),
                radius=rng.uniform(0.25, 0.33) * PITCH,
                wobble=0.12, step=0.04 * PITCH)


def small_cells(rng):
    """Small cells on a jittered grid, wherever the wordmark is drawn, placed like logo_spots' wordmark."""
    word = wordmark(width=int(3.2 * PITCH)) > 0.5
    cuts = letter_cuts(word)
    word[:, :cuts[0]] |= holes(word[:, :cuts[0]]) > 0     # fill the outlined "z"
    wx, wy = LEFT + int(4.1 * PITCH), TOP + (PITCH - word.shape[0]) // 2
    spacing = 0.07 * PITCH
    cells = []
    for gy in np.arange(spacing / 2, word.shape[0], spacing):
        for gx in np.arange(spacing / 2, word.shape[1], spacing):
            y, x = gy + rng.normal(0, 0.15 * spacing), gx + rng.normal(0, 0.15 * spacing)
            if 0 <= y < word.shape[0] and 0 <= x < word.shape[1] and word[int(y), int(x)]:
                cells.append(cell(rng, x=wx + x, y=wy + y, radius=rng.uniform(0.3, 0.45) * spacing,
                                  wobble=0.08, step=0.06 * spacing))
    return cells


def cell(rng, x, y, radius, wobble, step):
    """A cell whose outline is a circle bent by harmonics 2–5 of the angle, plus its path over time."""
    return dict(
        path=np.cumsum(np.vstack([[x, y], rng.normal(0, step, (T - 1, 2))]), axis=0),
        radius=radius * np.cumprod(np.r_[1, rng.uniform(0.95, 1.08, T - 1)]),
        brightness=np.clip(rng.uniform(0.55, 1.0) + np.r_[0, rng.normal(0, 0.08, T - 1)].cumsum(), 0.3, 1),
        amplitudes=rng.uniform(0.3, 1, 4) * wobble,
        phases=rng.uniform(0, 2 * np.pi, 4),
        phase_speeds=rng.normal(0, 0.4, 4),
    )


def add_cell(frame, cell, t):
    """Draws `cell` at timepoint `t` into `frame` (by maximum), with a soft edge."""
    (x, y), r = cell["path"][t], cell["radius"][t]
    half = r * 1.6
    x0, y0 = max(int(x - half), 0), max(int(y - half), 0)
    x1, y1 = min(int(x + half) + 1, X), min(int(y + half) + 1, Y)
    if x1 <= x0 or y1 <= y0:
        return
    yy, xx = np.ogrid[y0:y1, x0:x1]
    dist = np.hypot(xx - x, yy - y)
    angle = np.arctan2(yy - y, xx - x)
    outline = r * (1 + sum(a * np.sin(k * angle + p + s * t) for k, a, p, s in
                           zip(range(2, 6), cell["amplitudes"], cell["phases"], cell["phase_speeds"])))
    edge = 1 / (1 + np.exp((dist - outline) / max(r * 0.06, 1.5)))
    region = frame[y0:y1, x0:x1]
    np.maximum(region, (cell["brightness"][t] * edge).astype(np.float32), out=region)


def smooth_noise(rng, sigma):
    """Gaussian-filtered white noise in 0..1, filtered at 1/8 resolution and scaled up, to spare memory."""
    n, s = Y // 8, sigma / 8
    f = np.fft.fftfreq(n)[:, None] ** 2 + np.fft.rfftfreq(n)[None, :] ** 2
    small = np.fft.irfft2(np.fft.rfft2(rng.normal(size=(n, n))) * np.exp(-2 * (np.pi * s) ** 2 * f), s=(n, n))
    small = ((small - small.min()) / (small.max() - small.min())).astype(np.float32)
    return np.asarray(Image.fromarray(small, mode="F").resize((X, Y), Image.BICUBIC))


def downsample(a):
    """Halves both axes by averaging 2 × 2 blocks."""
    y, x = a.shape
    return a.reshape(y // 2, 2, x // 2, 2).mean(axis=(1, 3), dtype=np.float32).round().astype(np.uint8)


def write(levels, path):
    store = parse_url(path, mode="w").store
    root = zarr.group(store=store, overwrite=True, zarr_format=3)
    write_multiscale(
        levels,
        group=root,
        axes=[{"name": "t", "type": "time"}, {"name": "y", "type": "space"}, {"name": "x", "type": "space"}],
        fmt=FormatV05(),
        storage_options=dict(chunks=CHUNKS),
        name="logo_cells",
        metadata={"omero": omero_channel("cells", "00FFFF", start=15, end=220, min=0, max=255)},
    )
    print(f"OME-Zarr v0.5 written to {path}")


if __name__ == "__main__":
    create_logo_cells(sys.argv[1] if len(sys.argv) > 1 else "logo_cells.ome.zarr")
