# Logo

The repository logo is `logo-zarr.png`; `drafts/` holds the other candidates.

| File                         | What it is                                                                |
|------------------------------|---------------------------------------------------------------------------|
| `logo.py`                    | matplotlib script; writes `<variant>.png`, 1024×1024 RGBA, transparent    |
| `logo-zarr.png`              | variant: "zarr" replacing the glyph's top-right cubes                     |
| `logo-zarr-<n>.png`          | `logo-zarr` downscaled to n×n icons, n = 22, 24, 32, 64                   |
| `drafts/logo-raw.png`        | the plain glyph                                                           |
| `drafts/logo-room-wall.png`  | variant: the glyph in Zarr's room, "zarr" replacing its top-right cubes   |
| `drafts/logo-plane.png`      | variant: "zarr" in front of the glyph, lower right                        |
| `drafts/logo-big-z.png`      | variant: a large "z" showing through the cubes from behind                |
| `drafts/logo-pink.png`       | variant: the plain glyph, cubes in Zarr pink                              |
| `drafts/logo-z-behind.png`   | variant: a pink "z" of cubes one layer behind the glyph                   |
| `drafts/logo-z-in-plane.png` | variant: the "z" in the glyph plane, pink and half-pink cubes among blue  |
| `zarr-pink-horizontal.svg`   | the Zarr logo, verbatim from https://github.com/zarr-developers/zarr-logo |
| `pixi.toml`                  | pixi environment for running the script                                   |

```bash
cd doc/logo
pixi run python logo.py                  # writes drafts/logo-raw.png and opens a window
pixi run python logo.py logo-zarr        # writes logo-zarr.png and its icons
pixi run python logo.py logo-room-wall   # …and likewise for the other keys of VARIANTS
```

## Editing

The glyph is the `origins` list in `logo.py`: one `(x, y, z)` grid cell per cube,
x to the right, y up. `SIZE`/`GAP` control cube size and spacing, `color` the fill.

The axes and figure patch are hidden, so `logo-raw.png` comes out RGBA with a fully
transparent background. It is rendered twice: the first pass measures the glyph, the
second re-renders at the DPI that makes it exactly 1024 px tall, so it touches the top and
bottom edges and only the sides are padded.

The Zarr part is the upstream SVG, rasterized and warped into a plane of the scene.
`split()` cuts it at a run of empty columns — the widest drops the cube mark,
the first then peels the "z" off "zarr". `upright()` places the piece in glyph cells;
`render()` projects its corners and `warp()` fits PIL's `PERSPECTIVE` transform to them, so
it takes the camera's angle. A `VARIANTS` entry's last field composites it over the figure
instead of under, and under is what puts it behind the cubes.

Knobs: `SIZE`/`GAP` for the cubes, `ROOM_*` and the `lw` in `draw_room` for the room,
`ART_ALPHA` for the Zarr part, `FIJI_FACE` for the cubes' colour and transparency.

The view is pinned with `ax.view_init(elev=28, azim=-56)`.
