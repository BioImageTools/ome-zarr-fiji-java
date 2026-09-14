"""Repository logo: a glyph of cubes. Writes logo.png (1024x1024, transparent).

Run with ``pixi run python logo.py``. The shape is the hand-edited ``origins`` list.
"""

import io

import numpy as np
import matplotlib.pyplot as plt
from PIL import Image   # ships with matplotlib; only used to re-centre the saved PNG
from mpl_toolkits.mplot3d.art3d import Poly3DCollection


def make_cube_faces(origin, size=1.0):
    """Return the 6 faces of a cube as a list of 4-vertex polygons."""
    x0, y0, z0 = origin
    s = size
    # 8 corners
    c = np.array([
        [x0,     y0,     z0    ],
        [x0 + s, y0,     z0    ],
        [x0 + s, y0 + s, z0    ],
        [x0,     y0 + s, z0    ],
        [x0,     y0,     z0 + s],
        [x0 + s, y0,     z0 + s],
        [x0 + s, y0 + s, z0 + s],
        [x0,     y0 + s, z0 + s],
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

step = SIZE + GAP   # cell pitch; origins below are in cells, multiplied by this when drawn

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

# Per-cube colours: use this list instead and re-enable the zip below.
#colors = ["#4C72B0", "#DD8452", "#55A868", "#C44E52"]  # muted, distinct
color = "#65a4e3"

SIDE = 1024   # final logo is SIDE x SIDE px
DPI  = 150    # only sets the first pass; the second one derives its own, see below


def render(dpi):
    """Draw the glyph and return (figure, rendered PNG). Margins are whatever a 3D axes
    happens to pad with; the caller crops them away."""
    fig = plt.figure(figsize=(SIDE/DPI, SIDE/DPI))
    ax  = fig.add_subplot(111, projection="3d")

    # Cell (x, y) -> (x, 0, y): the glyph stands in the x–z plane, depth goes into the
    # screen. Laid flat in x–y, the camera foreshortens depth and cubes look like slabs.
    #for origin, color in zip(origins, colors):
    for origin in origins:
        scaled_origin = (step*origin[0], step*origin[2], step*origin[1])
        ax.add_collection3d(Poly3DCollection(
            make_cube_faces(scaled_origin, SIZE),
            alpha=0.85,
            facecolor=color,
            edgecolor="black",
            linewidths=dpi/150,   # in points, so scale it with the DPI to keep the look
        ))

    # add_collection3d() does not autoscale; derived from `origins` so editing it suffices.
    width  = step*max(o[0] for o in origins) + SIZE
    height = step*max(o[1] for o in origins) + SIZE
    ax.set_xlim(0, width)
    ax.set_ylim(0, SIZE)      # one cube deep
    ax.set_zlim(0, height)

    # Box proportional to the ranges = isotropic scaling = cubes stay cubic.
    ax.set_box_aspect([width, SIZE, height])

    # Pinned, so a change of matplotlib's default view does not change the logo.
    ax.view_init(elev=28, azim=-56)

    # Hide everything except the geometry
    ax.set_axis_off()
    ax.set_facecolor("none")
    fig.patch.set_visible(False)
    fig.tight_layout()

    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=dpi)
    buf.seek(0)
    return fig, Image.open(buf)


# Two passes: the first only measures how tall the glyph comes out, the second re-renders
# at the DPI that makes it exactly SIDE px tall. Re-rendering rather than upscaling a crop
# keeps the edges sharp; the glyph is taller than wide, so only the sides need padding.
fig, img = render(DPI)
top, bottom = img.getbbox()[1], img.getbbox()[3]
plt.close(fig)

fig, img = render(DPI*SIDE/(bottom - top))
glyph = img.crop(img.getbbox())
if glyph.height != SIDE:   # pixel rounding, never more than a few px
    glyph = glyph.resize((round(glyph.width*SIDE/glyph.height), SIDE), Image.LANCZOS)

canvas = Image.new("RGBA", (SIDE, SIDE), (255, 255, 255, 0))
canvas.paste(glyph, ((SIDE - glyph.width)//2, 0))
canvas.save("logo.png")
plt.show()
