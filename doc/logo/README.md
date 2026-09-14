# Logo drafts

Scratch material for a repository logo.

| File           | What it is                                                                          |
|----------------|-------------------------------------------------------------------------------------|
| `logo.py`  | matplotlib script that draws the glyph as a grid of cubes and writes `logo.png` |
| `logo.png` | the rendered logo, 1024×1024 RGBA with a transparent background                     |
| `pixi.toml`    | pixi environment (Python + matplotlib + numpy) for running the script               |

## Running

```bash
cd doc/logo
pixi run python logo.py   # writes logo.png and opens a window
```

## Editing the shape

The glyph is the `origins` list in `logo.py`: one `(x, y, z)` grid cell per cube,
x to the right, y up. `SIZE`/`GAP` control cube size and spacing, `color` the fill.

The axes and figure patch are hidden, so `logo.png` comes out RGBA with a fully
transparent background. It is rendered twice: the first pass measures the glyph, the
second re-renders at the DPI that makes it exactly 1024 px tall, so it touches the top and
bottom edges and only the sides are padded.

The view is pinned with `ax.view_init(elev=28, azim=-56)`.
