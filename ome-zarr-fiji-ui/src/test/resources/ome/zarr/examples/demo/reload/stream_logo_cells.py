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
"""
Simulates an acquisition of logo_cells.ome.zarr (see create_logo_cells.py), for demos of
reloading an open image: first copies the skeleton, i.e. every zarr.json, so the image can
be opened right away (empty), then copies the chunks one after the other, spread evenly
over the given duration. Timepoint by timepoint, and within a timepoint like a scan from top
to bottom, with every resolution level's chunks of a region together, so that a viewer sees
a timepoint fill in band by band at any zoom.

A rerun deletes the chunks a previous run copied and keeps the skeleton, copying only
what is missing of it.

    python stream_logo_cells.py SOURCE TARGET [SECONDS]
"""
import os
import shutil
import sys
import time
from pathlib import Path


def stream(source, target, seconds):
    skeleton = sorted(source.rglob("zarr.json"))
    chunks = sorted((p for p in source.rglob("*") if p.is_file() and "c" in p.relative_to(source).parts),
                    key=lambda p: order(source, p))

    old = list(target.glob("*/c"))
    for level_dir in old:
        shutil.rmtree(level_dir)
    print(f"deleted the chunks of {len(old)} resolution levels from a previous run")
    copied = 0
    for meta in skeleton:
        dest = target / meta.relative_to(source)
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(meta, dest)
            copied += 1
    print(f"skeleton: {copied} of {len(skeleton)} metadata files copied, the others were there already")

    start, t_last = time.monotonic(), None
    for i, chunk in enumerate(chunks):
        time.sleep(max(0.0, start + i * seconds / len(chunks) - time.monotonic()))
        t = timepoint(source, chunk)
        if t != t_last:
            print(f"{time.monotonic() - start:6.1f} s: timepoint {t}")
            t_last = t
        dest = target / chunk.relative_to(source)
        dest.parent.mkdir(parents=True, exist_ok=True)
        tmp = dest.with_name(dest.name + ".part")
        shutil.copyfile(chunk, tmp)
        os.replace(tmp, dest)   # atomic, so a reader never sees half a chunk
    print(f"{time.monotonic() - start:6.1f} s: all {len(chunks)} chunks copied")


def order(source, chunk):
    """(t, y, x, coarsest level first) of a chunk at `sN/c/t/y/x`, with y and x the position of its
    top left corner in full resolution pixels: every level has the same chunk size, so a chunk of
    level N covers 2^N times as many pixels per axis."""
    level, _, t, y, x = chunk.relative_to(source).parts
    n = int(level[1:])
    return int(t), int(y) << n, int(x) << n, -n


def timepoint(source, chunk):
    return order(source, chunk)[0]


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    stream(Path(sys.argv[1]), Path(sys.argv[2]), float(sys.argv[3]) if len(sys.argv) == 4 else 120.0)
