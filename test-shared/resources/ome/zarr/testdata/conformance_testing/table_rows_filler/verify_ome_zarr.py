"""
Verify selected OME-Zarr *multiscales* parameters against expected values,
using only the ngff-zarr library (no direct zarr / numpy-chunk assumptions).

Chunk access uses the native OME-Zarr chunk grid as exposed by ngff-zarr:
``NgffImage.data`` is a Dask array whose blocks map 1:1 onto the on-disk
Zarr chunks (for sharded arrays, onto the inner sub-chunks). We therefore
slice exactly one such block — no re-tiling, no numpy/dask-invented chunking.

Tested with: ngff-zarr 0.45.0, zarr 3.3.0, dask 2026.8.0
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import urlsplit, unquote
import random

import numpy as np
from ngff_zarr import from_ngff_zarr
from ngff_zarr.from_ngff_zarr import ( _open_root_node, RemoteZarrStore, REMOTE_URL_SCHEMES )
from ngff_zarr.parse_metadata import _detect_version

# Axis order in which the caller supplies ChunkCoord: the OME-Zarr/NGFF
# canonical order (t, c, z, y, x). Always 5 elements, in this order.
_CHUNKCOORD_ORDER = ("t", "c", "z", "y", "x")

_SCALAR_KEYS = (
    "PixelType", "AxesNames",
    "SizeX", "SizeY", "SizeZ", "SizeC", "SizeT",
    "ScaleX", "ScaleY", "ScaleZ",
    "NumberOfResLevels",
    # ChunkCoord, ChunkHash
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
# opening helpers
# --------------------------------------------------------------------------
def _normalize_store(url: str) -> str:
    """Turn a 'file:' URL into a bare local path; pass everything else through.

    ngff-zarr reads local paths, http(s), s3, gs, ... natively, but its local
    reader expects a filesystem path rather than a 'file:' scheme, so we strip
    that one case here. http/s3/gs/bare-path are returned unchanged.
    """
    parts = urlsplit(url)
    if parts.scheme == "file":
        path = unquote(parts.path)
        if parts.netloc and parts.netloc not in ("", "localhost"):
            # tolerate malformed 'file://tmp/x' where 'tmp' lands in netloc
            path = "/" + parts.netloc + path
        return path
    return url


def _full_store(url: str, multiscales_path: str | None) -> str:
    """Join the dataset URL with the in-store path of the desired multiscales group.

    An OME-Zarr may hold several 'multiscales' groups; `multiscales_path`
    selects one by its group path inside the store (e.g. 'B', 'nucleus/raw').
    None / '' means the root group is itself the multiscales group.
    """
    base = _normalize_store(url)
    if not multiscales_path:
        return base
    return f"{base.rstrip('/')}/{multiscales_path.lstrip('/')}"


def _open_multiscales(url, multiscales_path, storage_options):
    return from_ngff_zarr(_full_store(url, multiscales_path), validate=False,
                          storage_options=storage_options)


# --------------------------------------------------------------------------
# native-chunk access (via ngff-zarr's dask array only)
# --------------------------------------------------------------------------
def _random_chunk_index(image, *, include_partial=True, rng=None):
    """Pick a random valid chunk index from `image.data.chunks`.

    Parameters
    ----------
    image : NgffImage
        Base-level image; `image.data.chunks` is the per-axis tuple of block
        sizes on the native chunk grid, and `image.dims` the storage-order axes.
    include_partial : bool, default True
        If True, the last block along an axis may be chosen even when it is a
        partial (incomplete) chunk. If False, a partial last block is excluded
        from the choice on that axis — only full-size blocks can be picked.
    rng : random.Random | int | None
        Source of randomness. An int seeds a local Random; None uses the module
        default. Pass a Random instance for reproducibility without global state.

    Returns
    -------
    list[int]
        Chunk index in (t, c, z, y, x) order; axes not present in the image are 0.

    Raises
    ------
    ValueError
        If `include_partial=False` leaves an axis with no full-size block.
    """
    r = rng if isinstance(rng, random.Random) else random.Random(rng)

    dims = list(image.dims)
    grid = image.data.chunks            # e.g. ((1,1,1),(1,1),(2,2,1),(32,32),(32,16))
    nominal = image.data.chunksize      # full-block size per axis

    idx_by_dim = {}
    for d, dim in enumerate(dims):
        blocks = grid[d]
        n = len(blocks)
        if include_partial:
            # last usable (inclusive) index for the random choosing...
            hi = n - 1
        else:
            # a trailing block is "partial" iff smaller than the nominal size;
            # only the last block can ever be partial on a regular Zarr grid.
            hi = n - 1 if blocks[-1] == nominal[d] else n - 2
            if hi < 0:
                raise ValueError(
                    f"axis '{dim}' has no full-size chunk "
                    f"(single partial block of size {blocks[-1]}, nominal {nominal[d]})"
                )
        idx_by_dim[dim] = r.randint(0, hi)

    return [idx_by_dim.get(a, 0) for a in _CHUNKCOORD_ORDER]


def _chunk_block(image, coord_tczyx):
    """Return the ndarray of one native OME-Zarr chunk.

    `image` is the base-level NgffImage. `image.data.chunks` is the per-axis
    tuple of block sizes on the native chunk grid. `coord_tczyx` is the chunk
    index in canonical (t, c, z, y, x) order; the index for each axis present
    in this image's `dims` is taken from that position.
    """
    if len(coord_tczyx) != len(_CHUNKCOORD_ORDER):
        raise ValueError(
            f"ChunkCoord must have {len(_CHUNKCOORD_ORDER)} elements in "
            f"{_CHUNKCOORD_ORDER} order; got {coord_tczyx!r}"
        )
    dims = list(image.dims)
    arr = image.data                       # dask array on the native chunk grid
    grid = arr.chunks                      # e.g. ((1,1,1),(2,2,1),(32,32),(32,16))
    coord_by_dim = dict(zip(_CHUNKCOORD_ORDER, coord_tczyx))

    slices = []
    for d, dim in enumerate(dims):
        idx = int(coord_by_dim[dim])
        nblocks = len(grid[d])
        if not (0 <= idx < nblocks):
            raise IndexError(
                f"chunk index {idx} out of range for axis '{dim}' ({nblocks} chunks)"
            )
        offsets = np.cumsum((0,) + tuple(grid[d]))
        slices.append(slice(int(offsets[idx]), int(offsets[idx + 1])))

    return np.asarray(arr[tuple(slices)].compute())


def _hash_block(block: np.ndarray, algo: str) -> str:
    """Hash dtype + shape + C-contiguous raw bytes of one chunk block."""
    h = hashlib.new(algo)
    h.update(block.dtype.str.encode())
    h.update(repr(tuple(block.shape)).encode())
    h.update(np.ascontiguousarray(block).tobytes())
    return h.hexdigest()


def _fetch_version(url, multiscales_path, storage_options=None):
    loc = _full_store(url, multiscales_path)
    if isinstance(loc, str) and loc.startswith(REMOTE_URL_SCHEMES):
        loc = RemoteZarrStore(loc, storage_options=storage_options)
    return str(_detect_version( _open_root_node(loc, None).attrs.asdict() ))


def _create_expected(url, multiscales_path, *,
                     hash_algo="sha256", storage_options=None) -> dict:

    ms = _open_multiscales(url, multiscales_path, storage_options)
    base = ms.images[0]                        # full-resolution level
    dims = list(base.dims)                     # storage order
    size = dict(zip(dims, base.data.shape))
    scale = dict(base.scale or {})

    benchdata: dict[str, Any] = {
        "PixelType": str(base.data.dtype),
        "AxesNames": ";".join(dims),
        "SizeX": size.get("x"),
        "SizeY": size.get("y"),
        "SizeZ": size.get("z", 1),
        "SizeC": size.get("c", 1),
        "SizeT": size.get("t", 1),
        "ScaleX": scale.get("x"),
        "ScaleY": scale.get("y"),
        "ScaleZ": scale.get("z"),
        "NumberOfResLevels": len(ms.images),
    }

    chunk_coord = _random_chunk_index(base, include_partial=False)
    chunkhash = _hash_block(_chunk_block(base, chunk_coord), hash_algo)
    benchdata['ChunkCoord'] = chunk_coord
    benchdata['ChunkHash'] = chunkhash


    # general stuff
    benchdata['OmeZarrVersion'] = _fetch_version(url, multiscales, storage_options)
    benchdata['StudyName'] = 'TBA'
    benchdata['SrcUrl'] = url
    benchdata['License'] = 'TBA'

    # this makes it specific for a 'plain' multiscales (non-scene, non-hcs, etc.) benchmark
    benchdata['PathToImageMultiscales'] = multiscales_path


# --------------------------------------------------------------------------
# public API
# --------------------------------------------------------------------------
def compute_chunk_hash(url, multiscales_path, chunk_coord, *,
                       hash_algo="sha256", storage_options=None) -> str:
    """Compute ChunkHash for a (t,c,z,y,x) native chunk index — use to build benchmarks."""
    ms = _open_multiscales(url, multiscales_path, storage_options)
    block = _chunk_block(ms.images[0], list(chunk_coord))
    return _hash_block(block, hash_algo)


def verify_multiscales(url, multiscales_path, expected, *,
                       hash_algo="sha256", storage_options=None,
                       strict_unknown_keys=True) -> CheckResult:
    """
    Open one OME-Zarr multiscales group and check its parameters against `expected`.

    Parameters
    ----------
    url : str
        Dataset location: bare path, 'file:' URL, http(s), s3://, gs://, ...
    multiscales_path : str | None
        In-store group path selecting which 'multiscales' to inspect
        (None/'' = root group).
    expected : dict
        Recognized keys: PixelType, AxesNames, SizeX/Y/Z/C/T, ScaleX/Y/Z,
        NumberOfResLevels, ChunkCoord (+ ChunkHash).
        ChunkCoord is a list of native chunk indices in (t,c,z,y,x) order.
    """
    ms = _open_multiscales(url, multiscales_path, storage_options)
    base = ms.images[0]                        # full-resolution level
    dims = list(base.dims)                     # storage order
    size = dict(zip(dims, base.data.shape))
    scale = dict(base.scale or {})

    actual: dict[str, Any] = {
        "PixelType": str(base.data.dtype),
        "AxesNames": ";".join(dims),
        "SizeX": size.get("x"),
        "SizeY": size.get("y"),
        "SizeZ": size.get("z", 1),
        "SizeC": size.get("c", 1),
        "SizeT": size.get("t", 1),
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

    if "ChunkCoord" in expected and "ChunkHash" in expected:
        block = _chunk_block(base, list(expected["ChunkCoord"]))
        actual_hash = _hash_block(block, hash_algo)
        ok = actual_hash == expected["ChunkHash"]
        res.checked["ChunkHash"] = (expected["ChunkHash"], actual_hash, ok)
        res.passed &= ok
    else:
        res.notes.append("ChunkCoord and ChunkHash must be provided together.")
        # res.passed = False - don't influence the outcome if the Chunk* stuff is absent/broken in this particular benchmark

    known = set(_SCALAR_KEYS) | {"ChunkCoord", "ChunkHash"}
    for key in expected:
        if key not in known:
            res.missing.append(key)
            if strict_unknown_keys:
                res.passed = False

    return res
