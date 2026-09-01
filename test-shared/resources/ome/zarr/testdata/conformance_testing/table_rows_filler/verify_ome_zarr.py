"""
Verify selected OME-Zarr *multiscales* parameters against expected values,
using the ngff-zarr library.

Tested with: ngff-zarr 0.45.0, zarr 3.3.0, dask 2026.8.0
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import zarr
from ngff_zarr import from_ngff_zarr

# Axis order in which the caller supplies ChunkCoord.
_CHUNKCOORD_ORDER = ("c", "t", "z", "y", "x")

_SCALAR_KEYS = (
    "AxesNames", "PixelType",
    "SizeX", "SizeY", "SizeZ", "SizeT", "SizeC",
    "ScaleX", "ScaleY", "ScaleZ",
    "NumberOfResLevels",
)


@dataclass
class CheckResult:
    passed: bool
    checked: dict[str, tuple[Any, Any, bool]] = field(default_factory=dict)  # name -> (expected, actual, ok)
    missing: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    def __bool__(self) -> bool:
        return self.passed

    def report(self) -> str:
        lines = [f"OVERALL: {'PASS' if self.passed else 'FAIL'}"]
        for name, (exp, act, ok) in self.checked.items():
            lines.append(f"  [{'ok ' if ok else 'XXX'}] {name:18s} expected={exp!r:30} actual={act!r}")
        for m in self.missing:
            lines.append(f"  [??] {m:18s} (unknown parameter / no rule)")
        for n in self.notes:
            lines.append(f"  note: {n}")
        return "\n".join(lines)


# --------------------------------------------------------------------------
# opening / model helpers
# --------------------------------------------------------------------------
def _full_store(url: str, array_path: str | None) -> str:
    return url if not array_path else f"{url.rstrip('/')}/{array_path.lstrip('/')}"


def _open_multiscales(url: str, array_path: str | None, storage_options: dict | None):
    return from_ngff_zarr(_full_store(url, array_path), validate=False,
                          storage_options=storage_options)


def _axes(metadata) -> list:
    """v0.4/v0.5 expose metadata.axes; v0.6 (RFC-5) nests them in coordinateSystems."""
    ax = getattr(metadata, "axes", None)
    if ax:
        return list(ax)
    cs = getattr(metadata, "coordinateSystems", None)
    return list(cs[0].axes) if cs else []


def _base_dataset_path(ms) -> str:
    """Path (relative to the multiscales group) of the full-resolution dataset."""
    return ms.metadata.datasets[0].path


# --------------------------------------------------------------------------
# chunk access — authoritative on-disk zarr chunk grid
# --------------------------------------------------------------------------
def _open_base_zarr_array(url, array_path, ms, storage_options):
    """Open the underlying zarr Array of the base level, honoring its true chunk grid."""
    grp_store = _full_store(url, array_path)
    ds_path = _base_dataset_path(ms)
    kwargs = {"storage_options": storage_options} if storage_options else {}
    # ds_path may itself be nested (e.g. 'scale0/image'); open relative to the group.
    z = zarr.open_group(grp_store, mode="r", **kwargs)
    return z[ds_path]


def _chunk_block_from_zarr(zarr_arr, dims, coord_cxtzyx):
    """Slice exactly one on-disk chunk, given a (c,t,z,y,x) chunk index."""
    coord_by_dim = dict(zip(_CHUNKCOORD_ORDER, coord_cxtzyx))
    cshape = zarr_arr.chunks           # on-disk chunk shape, storage order
    shape = zarr_arr.shape
    sl = []
    resolved_by_dim = {}
    for d, dim in enumerate(dims):
        idx = int(coord_by_dim.get(dim, 0))
        nblocks = -(-shape[d] // cshape[d])           # ceil division
        if not (0 <= idx < nblocks):
            raise IndexError(f"chunk index {idx} out of range for axis '{dim}' ({nblocks} chunks)")
        start = idx * cshape[d]
        stop = min(start + cshape[d], shape[d])       # last chunk may be partial
        sl.append(slice(start, stop))
        resolved_by_dim[dim] = idx
    # echo back in caller's (c,t,z,y,x) order, using 0 for axes absent from storage
    resolved = [resolved_by_dim.get(a, 0) for a in _CHUNKCOORD_ORDER]
    return zarr_arr[tuple(sl)], resolved


def _hash_block(block: np.ndarray, algo: str) -> str:
    """Hash raw bytes plus dtype+shape, so identical bytes under different dtype/shape differ."""
    h = hashlib.new(algo)
    h.update(block.dtype.str.encode())
    h.update(repr(tuple(block.shape)).encode())
    h.update(np.ascontiguousarray(block).tobytes())
    return h.hexdigest()


# --------------------------------------------------------------------------
# public API
# --------------------------------------------------------------------------
def compute_chunk_hash(url, array_path, chunk_coord, *,
                       hash_algo="sha256", storage_options=None) -> str:
    """Compute ChunkHash for a (c,t,z,y,x) chunk index — use it to build benchmark dicts."""
    ms = _open_multiscales(url, array_path, storage_options)
    dims = list(ms.images[0].dims)
    zarr_arr = _open_base_zarr_array(url, array_path, ms, storage_options)
    block, _ = _chunk_block_from_zarr(zarr_arr, dims, list(chunk_coord))
    return _hash_block(np.asarray(block), hash_algo)


def verify_multiscales(url, array_path, expected, *,
                       hash_algo="sha256", storage_options=None,
                       strict_unknown_keys=True) -> CheckResult:
    """
    Open an OME-Zarr multiscales group and check its parameters against `expected`.

    Recognized keys: AxesNames, PixelType, SizeX/Y/Z/T/C, ScaleX/Y/Z,
    NumberOfResLevels, ChunkCoord (+ ChunkHash).
    ChunkCoord is a list of on-disk chunk indices in (c,t,z,y,x) order.
    """
    ms = _open_multiscales(url, array_path, storage_options)
    base = ms.images[0]                        # full-resolution level
    dims = list(base.dims)                      # storage order
    size = dict(zip(dims, base.data.shape))
    scale = dict(base.scale or {})

    actual: dict[str, Any] = {
        "AxesNames": ";".join(dims),
        "PixelType": str(base.data.dtype),
        "SizeX": size.get("x"),
        "SizeY": size.get("y"),
        "SizeZ": size.get("z", 1),
        "SizeT": size.get("t", 1),
        "SizeC": size.get("c", 1),
        "ScaleX": scale.get("x"),
        "ScaleY": scale.get("y"),
        "ScaleZ": scale.get("z"),
        "NumberOfResLevels": len(ms.images),
    }

    res = CheckResult(passed=True)

    def _eq(exp: Any, act: Any) -> bool:
        if isinstance(exp, float) or isinstance(act, float):
            try:
                return bool(np.isclose(float(exp), float(act), rtol=1e-6, atol=1e-9))
            except (TypeError, ValueError):
                return exp == act
        return exp == act

    for key in _SCALAR_KEYS:
        if key in expected:
            ok = _eq(expected[key], actual[key])
            res.checked[key] = (expected[key], actual[key], ok)
            res.passed &= ok

    if "ChunkCoord" in expected or "ChunkHash" in expected:
        if not ("ChunkCoord" in expected and "ChunkHash" in expected):
            res.notes.append("ChunkCoord and ChunkHash must be provided together.")
            res.passed = False
        else:
            zarr_arr = _open_base_zarr_array(url, array_path, ms, storage_options)
            block, resolved = _chunk_block_from_zarr(zarr_arr, dims, list(expected["ChunkCoord"]))
            actual_hash = _hash_block(np.asarray(block), hash_algo)
            ok = actual_hash == expected["ChunkHash"]
            res.checked["ChunkCoord"] = (list(expected["ChunkCoord"]), resolved, True)
            res.checked["ChunkHash"] = (expected["ChunkHash"], actual_hash, ok)
            res.passed &= ok

    known = set(_SCALAR_KEYS) | {"ChunkCoord", "ChunkHash"}
    for key in expected:
        if key not in known:
            res.missing.append(key)
            if strict_unknown_keys:
                res.passed = False

    return res
