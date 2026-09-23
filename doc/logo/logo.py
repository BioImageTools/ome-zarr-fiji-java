"""Repository logo: a glyph of cubes. `pixi run python logo.py [variant]` writes
<variant>.png, 1024x1024 RGBA; variant is a key of VARIANTS.
"""

import io
import pathlib
import sys

import cairosvg          # only to rasterise the Zarr logo
import numpy as np
import matplotlib.colors as mcolors
import matplotlib.pyplot as plt
from PIL import Image
from mpl_toolkits.mplot3d import proj3d
from mpl_toolkits.mplot3d.art3d import Poly3DCollection


def make_cube_faces(origin, size=1.0):
    """Return the 6 faces of a cube as a list of 4-vertex polygons. `size` is an edge
    length, or one per axis for a box, e.g. half of a split cube."""
    x0, y0, z0 = origin
    w, d, h = np.broadcast_to(size, 3)
    # 8 corners
    c = np.array([
        [x0,     y0,     z0    ],
        [x0 + w, y0,     z0    ],
        [x0 + w, y0 + d, z0    ],
        [x0,     y0 + d, z0    ],
        [x0,     y0,     z0 + h],
        [x0 + w, y0,     z0 + h],
        [x0 + w, y0 + d, z0 + h],
        [x0,     y0 + d, z0 + h],
    ])
    faces = [
        [c[0], c[1], c[2], c[3]],  # bottom
        [c[4], c[5], c[6], c[7]],  # top
        [c[0], c[1], c[5], c[4]],  # front
        [c[2], c[3], c[7], c[6]],  # back
        [c[0], c[3], c[7], c[4]],  # left
        [c[1], c[2], c[6], c[5]],  # right
    ]
    return faces


SIZE  = 1.0   # edge length of each cube
GAP   = 0.2   # gap between cubes

step = SIZE + GAP   # cell pitch; everything below is in cells, multiplied by this when drawn

# One entry per cube, in grid cells (x right, y up). Gaps are what shape the letters.
origins = [
    # x, y, z
    # outer bracket: left edge
    (0, 0, 0),
    (0, 1, 0),
    (0, 2, 0),
    (0, 3, 0),
    (0, 4, 0),
    (0, 5, 0),
    (0, 6, 0),
    # … and top edge
    (1, 6, 0),
    (2, 6, 0),
    (3, 6, 0),
    (4, 6, 0),
    (5, 6, 0),
    (6, 6, 0),

    # foot of the inner glyph
    (2, 0, 0),
    (3, 0, 0),
    (4, 0, 0),
    (4, 1, 0),

    # inner uprights at x = 2, 4, 6
    (2, 2, 0),
    (4, 2, 0),
    (6, 2, 0),
    (2, 3, 0),
    (4, 3, 0),
    (6, 3, 0),
    (2, 4, 0),
    (4, 4, 0),
    (6, 4, 0),
]

ZARR_PINK = "#E01073"
FIJI_BLUE = "#65a4e3"
FIJI_EDGE = mcolors.to_rgba("black", 0.85)   # alpha baked in, not passed per collection

ROOM_DEPTH  = 3.0    # cells the floor and the left wall reach back from the glyph plane
ROOM_MARGIN = 1.0    # cells of floor in front of and to the left of the glyph
ROOM_PITCH  = step   # grid spacing

ART_ALPHA = 0.55     # opacity of the Zarr part

ZARR_SVG = pathlib.Path(__file__).with_name("zarr-pink-horizontal.svg")


def split(art, gap=max):
    """`art` cut at a run of empty columns, both halves cropped to their ink. `gap` picks
    the run: the widest splits mark from word, the first splits off one letter."""
    empty = np.array(art.getchannel("A")).sum(axis=0) == 0
    runs, start = [], None
    for x, blank in enumerate(list(empty) + [False]):
        if blank and start is None:
            start = x
        elif not blank and start is not None:
            runs.append((x - start, start))
            start = None
    gap_width, gap_start = gap(runs)
    left  = art.crop((0, 0, gap_start, art.height))
    right = art.crop((gap_start + gap_width, 0, art.width, art.height))
    return left.crop(left.getbbox()), right.crop(right.getbbox())


png = cairosvg.svg2png(url=str(ZARR_SVG), output_width=1800)
lockup = Image.open(io.BytesIO(png)).convert("RGBA")
_, WORDMARK = split(lockup.crop(lockup.getbbox()))       # drop the cube mark
Z, _ = split(WORDMARK, gap=lambda runs: runs[0])         # …and keep the "z" of "zarr"


def upright(art, x0, z0, depth, width):
    """Corners of `art` in the plane `depth`, bottom left at cell (x0, z0), clockwise from
    the top left — the order warp() wants. Height follows from `art`."""
    height = width*art.height/art.width
    return [(x0, depth, z0 + height), (x0 + width, depth, z0 + height),
            (x0 + width, depth, z0),  (x0, depth, z0)]


VARIANTS = {
    # name:             room?  artwork    where it goes                          in front?
    "logo":            (False, None,      None,                                  False),
    "logo-room-wall":  (True,  WORDMARK,  upright(WORDMARK, 4.2, 5.85, ROOM_DEPTH, 2.2), False),
    "logo-plane":      (False, WORDMARK,  upright(WORDMARK, 5.5, 0.05, 0, 3.0),  True),
    "logo-big-z":      (False, Z,         upright(Z, 2.05, 1.05, 0, 3.7),       False),
    "logo-pink":       (False, None,      None,                                  False),
    "logo-z-behind":   (False, None,      None,                                  False),
    "logo-z-in-plane": (False, None,      None,                                  False),
}
variant = sys.argv[1] if len(sys.argv) > 1 else "logo"
with_room, ART, art_quad, art_in_front = VARIANTS[variant]

# Artwork behind the cubes shows through only as far as they are transparent.
FIJI_FACE = mcolors.to_rgba(ZARR_PINK if variant == "logo-pink" else FIJI_BLUE,
                            0.55 if variant == "logo-big-z" else 0.85)
face = {}   # cell -> colour, where it is not FIJI_FACE
z_cells = {(x, y) for x in range(7) for y in (0, 6)} | {(k, k) for k in range(1, 6)}

if variant == "logo-z-behind":
    # A pink "z" on its own layer, one cell behind the glyph, so neither hides the other.
    Z_DEPTH = 1
    face = {(x, y, Z_DEPTH): mcolors.to_rgba(ZARR_PINK, FIJI_FACE[3]) for x, y in z_cells}
    origins += sorted(face)
elif variant == "logo-z-in-plane":
    # The "z" in the glyph plane: its missing cubes added in pink, and where it shares
    # cubes with the glyph, pink picks out enough of them to trace it.
    shared_pink = {(1, 6), (3, 6), (5, 6)}
    pink = z_cells - {o[:2] for o in origins} | shared_pink
    face = {(x, y, 0): mcolors.to_rgba(ZARR_PINK, FIJI_FACE[3]) for x, y in pink}
    origins += sorted(set(face) - set(origins))

# (x, y, z) -> (x, y, z, (width along x, height along y), colour)
origins = [o + ((SIZE, SIZE), face.get(o, FIJI_FACE)) for o in origins]

if variant == "logo-z-in-plane":
    # Where the "z" turns or crosses the glyph, the cube is split in two:
    # cell -> (split along, left/bottom half, right/top half).
    blue, pink = FIJI_FACE, mcolors.to_rgba(ZARR_PINK, FIJI_FACE[3])
    splits = {
        (0, 6, 0): ("x", blue, pink),
        (6, 6, 0): ("x", pink, blue),
        (4, 4, 0): ("x", pink, blue),
        (2, 2, 0): ("x", blue, pink),
        (0, 0, 0): ("x", blue, pink),
        (3, 0, 0): ("y", blue, pink),
    }
    half = SIZE/2
    origins = [o for o in origins if o[:3] not in splits]
    for (x, y, z), (along, first, second) in splits.items():
        size = (half, SIZE) if along == "x" else (SIZE, half)
        dx, dy = (half/step, 0) if along == "x" else (0, half/step)
        origins += [(x, y, z, size, first), (x + dx, y + dy, z, size, second)]

SIDE = 1024   # final logo is SIDE x SIDE px
DPI  = 150    # only sets the first pass; the second one derives its own, see below


def warp(art, corners, size):
    """`art` mapped onto the quadrilateral `corners` of a transparent `size` canvas. PIL
    pulls each output pixel from the input, so solve the backwards map."""
    w, h = art.width, art.height
    source = [(0, 0), (w, 0), (w, h), (0, h)]
    rows, rhs = [], []
    for (cx, cy), (sx, sy) in zip(corners, source):
        rows += [[cx, cy, 1, 0, 0, 0, -sx*cx, -sx*cy],
                 [0, 0, 0, cx, cy, 1, -sy*cx, -sy*cy]]
        rhs += [sx, sy]
    coeffs = np.linalg.solve(np.array(rows), np.array(rhs))

    faded = art.copy()
    faded.putalpha(art.getchannel("A").point(lambda a: round(a*ART_ALPHA)))
    return faded.transform(size, Image.PERSPECTIVE, coeffs, Image.BICUBIC)


def draw_room(ax, width, height, dpi):
    """Zarr's setting: a gridded floor and two walls, in data coordinates. Extents are
    rounded out to whole squares — no wall ends on half a one — and returned."""
    out = lambda v: np.ceil(v/ROOM_PITCH - 1e-9)*ROOM_PITCH
    back, front = out(ROOM_DEPTH*step), -out(ROOM_MARGIN*step)
    left, right = -out(ROOM_MARGIN*step), out(width)
    top = out(height)
    line = dict(color=ZARR_PINK, alpha=0.22, lw=dpi/120)

    def span(a, b):
        return np.arange(a, b + ROOM_PITCH/2, ROOM_PITCH)

    for x in span(left, right):                         # floor, z = 0
        ax.plot([x, x], [front, back], [0, 0], **line)
    for y in span(front, back):
        ax.plot([left, right], [y, y], [0, 0], **line)

    for x in span(left, right):                         # back wall, y = back
        ax.plot([x, x], [back, back], [0, top], **line)
    for z in span(0, top):
        ax.plot([left, right], [back, back], [z, z], **line)

    for y in span(front, back):                         # left wall, x = left
        ax.plot([left, left], [y, y], [0, top], **line)
    for z in span(0, top):
        ax.plot([left, left], [front, back], [z, z], **line)

    return front, back, left, right, top


def render(dpi):
    """Draw everything and return (figure, PNG). The caller crops the axes' padding."""
    fig = plt.figure(figsize=(SIDE/DPI, SIDE/DPI))
    ax  = fig.add_subplot(111, projection="3d")

    # Cell (x, y) -> data (x, 0, y): the glyph stands up, depth goes into the screen.
    for x, y, z, (w, h), colour in origins:
        ax.add_collection3d(Poly3DCollection(
            make_cube_faces((step*x, step*z, step*y), (w, SIZE, h)),
            alpha=None,             # in the colours
            facecolor=colour,
            edgecolor=FIJI_EDGE,
            linewidths=dpi*SIZE/150,   # points, so scale with the DPI
        ))

    # add_collection3d() does not autoscale.
    width  = max(step*o[0] + o[3][0] for o in origins)
    height = max(step*o[1] + o[3][1] for o in origins)
    depth  = max(step*o[2] + SIZE for o in origins)
    x0, y0, z0 = 0.0, min(step*o[2] for o in origins), 0.0
    if art_quad:   # the wordmark may stick out of the glyph
        x0     = min([x0]     + [step*p[0] for p in art_quad])
        width  = max([width]  + [step*p[0] for p in art_quad])
        y0     = min([y0]     + [step*p[1] for p in art_quad])
        depth  = max([depth]  + [step*p[1] for p in art_quad])
        height = max([height] + [step*p[2] for p in art_quad])
    if with_room:
        y0, depth, x0, width, height = draw_room(ax, width, height, dpi)

    ax.set_xlim(x0, width)
    ax.set_ylim(y0, depth)
    ax.set_zlim(z0, height)

    # Proportional to the ranges = isotropic scaling = cubes stay cubic.
    ax.set_box_aspect([width - x0, depth - y0, height - z0])

    # Pinned against matplotlib changing its default view.
    ax.view_init(elev=28, azim=-56)

    # Hide everything but the geometry
    ax.set_axis_off()
    ax.set_facecolor("none")
    fig.patch.set_visible(False)
    fig.tight_layout()

    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=dpi)
    buf.seek(0)
    img = Image.open(buf)

    if art_quad:
        # The quad's corners in saved-image pixels: transData works in the figure's dpi,
        # savefig used another, and image rows count from the top.
        scale = dpi/fig.dpi
        corners = []
        for point in art_quad:
            px, py = proj3d.proj_transform(*[step*c for c in point], ax.get_proj())[:2]
            px, py = ax.transData.transform((px, py))
            corners.append((px*scale, img.height - py*scale))
        layers = [warp(ART, corners, img.size), img.convert("RGBA")]
        img = Image.alpha_composite(*layers[::-1] if art_in_front else layers)

    return fig, img


# Two passes: measure, then re-render at the DPI that makes the longer side SIDE px.
# Re-rendering rather than upscaling a crop keeps the edges sharp.
fig, img = render(DPI)
left, top, right, bottom = img.getbbox()
plt.close(fig)

fig, img = render(DPI*SIDE/max(right - left, bottom - top))
glyph = img.crop(img.getbbox())
if max(glyph.size) != SIDE:   # pixel rounding, never more than a few px
    scale = SIDE/max(glyph.size)
    glyph = glyph.resize((round(glyph.width*scale), round(glyph.height*scale)), Image.LANCZOS)

canvas = Image.new("RGBA", (SIDE, SIDE), (255, 255, 255, 0))
canvas.paste(glyph, ((SIDE - glyph.width)//2, (SIDE - glyph.height)//2))
canvas.save(f"{variant}.png")
plt.show()
