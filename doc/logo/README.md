# Logo drafts

Scratch material for a repository logo.

| File                       | What it is                                                               |
|----------------------------|--------------------------------------------------------------------------|
| `logo.py`                  | matplotlib script; writes `<variant>.png`, 1024×1024 RGBA, transparent   |
| `logo.png`                 | the plain glyph                                                          |
| `logo-room-wall.png`       | variant: the glyph in Zarr's room, "zarr" on its back wall               |
| `logo-plane.png`           | variant: "zarr" in front of the glyph, lower right                       |
| `logo-big-z.png`           | variant: a large "z" showing through the cubes from behind               |
| `zarr-pink-horizontal.svg` | the Zarr logo, verbatim from https://github.com/zarr-developers/zarr-logo |
| `pixi.toml`                | pixi environment for running the script                                  |

```bash
cd doc/logo
pixi run python logo.py                  # writes logo.png and opens a window
pixi run python logo.py logo-room-wall   # …and likewise for the other keys of VARIANTS
```

## Editing

The glyph is the `origins` list: one `(x, y, z)` grid cell per cube, x right, y up.

The Zarr part is the upstream SVG, rasterised and warped into a plane of the scene, never
redrawn here. `split()` cuts it at a run of empty columns — the widest drops the cube mark,
the first then peels the "z" off "zarr". `upright()` places the piece in glyph cells;
`render()` projects its corners and `warp()` fits PIL's `PERSPECTIVE` transform to them, so
it takes the camera's angle. A `VARIANTS` entry's last field composites it over the figure
instead of under, and under is what puts it behind the cubes.

Knobs: `SIZE`/`GAP` for the cubes, `ROOM_*` and the `lw` in `draw_room` for the room,
`ART_ALPHA` for the Zarr part, `FIJI_FACE` for how transparent the cubes are.

The view is pinned with `ax.view_init(elev=28, azim=-56)`.
