"""Regenerate the Android launcher icons - the same tomato as the desktop app.

The drawing is the one from `Pomodoro timer\\make_icon.py`, rendered at 1024 px
and downsampled, so the phone icon and the Windows icon are the same picture.

Writes, under app/src/main/res/:
  mipmap-*/ic_launcher.png          legacy square icon
  mipmap-*/ic_launcher_round.png    legacy round icon
  mipmap-*/ic_launcher_fg.png       adaptive foreground (Android 8+)

Does NOT write mipmap-anydpi-v26/ic_launcher.xml, the adaptive-icon descriptor.
That file is maintained by hand and merely points at the PNGs above, so running
this script leaves it alone.

And, for the F-Droid store listing:
  fastlane/metadata/android/en-US/images/icon.png    512 px listing icon

Needs Pillow:  py -m pip install pillow
Run with:      py make_launcher_icons.py
"""

import math
import os

from PIL import Image, ImageDraw

S = 1024
RED = (226, 86, 74, 255)        # the work accent
GREEN = (63, 166, 108, 255)     # the break accent
GREEN_DEEP = (44, 130, 82, 255)
WHITE = (255, 255, 255, 255)
# White at ~35% over red, pre-blended: ImageDraw writes pixels rather than
# compositing them, so a translucent fill would punch a hole in the tomato.
TRACK = (236, 145, 137, 255)

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "app", "src", "main", "res")

# F-Droid builds its listing page from files kept in the repository rather than
# from anything uploaded to a web console, so the icon it displays is this path.
FASTLANE_IMAGES = os.path.join(HERE, "fastlane", "metadata", "android", "en-US", "images")

# Legacy icons are 48dp, adaptive foregrounds 108dp, at each density bucket.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def draw_tomato():
    """The tomato, drawn on a transparent 1024 px square.

    Everything is drawn at this one large size and downsampled afterwards, which is
    why the coordinates below are absolute rather than proportional: drawing once at
    1024 px and shrinking gives smoother edges than drawing each small size directly.

    Returns the RGBA image. Nothing is written to disk here.
    """
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Wider than tall, which is what reads as a tomato rather than an apple. Inset
    # from the 1024 edges so the shape is not clipped, and pushed down (top at 215)
    # to leave room for the stalk above it.
    body = (70, 215, 954, 1000)
    cx = (body[0] + body[2]) / 2
    cy = (body[1] + body[3]) / 2
    d.ellipse(body, fill=RED)

    # The calyx: five leaves radiating from a point just inside the top of the body.
    base = (512, 250)
    # Each pair is (angle in degrees, length in pixels). Angles are measured with 0
    # pointing right and -90 straight up, so this fans from left (-176) to right (-4).
    # The two diagonals are longest (250) and the outermost pair shortest (205),
    # which is what stops the star looking mechanical.
    for angle, length in [(-176, 205), (-142, 250), (-90, 215), (-38, 250), (-4, 205)]:
        a = math.radians(angle)
        tip = (base[0] + length * math.cos(a), base[1] + length * math.sin(a))
        # A unit vector perpendicular to the leaf direction, used to spread the two
        # base corners either side of `base` and so give each leaf its width.
        px, py = -math.sin(a), math.cos(a)
        half = 62                        # half-width of a leaf at its base, in pixels
        d.polygon(
            [
                (base[0] + half * px, base[1] + half * py),
                tip,
                (base[0] - half * px, base[1] - half * py),
            ],
            fill=GREEN,
        )
    # A circle over the middle of the calyx, hiding the seam where all five leaf
    # polygons meet at `base`.
    d.ellipse((base[0] - 70, base[1] - 70, base[0] + 70, base[1] + 70), fill=GREEN)

    # The stalk, drawn as a thick vertical line with a round cap on top. Deeper green
    # than the leaves so it reads as in front of them.
    d.line([(512, 255), (512, 110)], fill=GREEN_DEEP, width=76)
    d.ellipse((474, 74, 550, 150), fill=GREEN_DEEP)

    # The countdown ring, echoing the one in the app. Radius 252 and thickness 86 put
    # it comfortably inside the body with red visible on either side.
    r, width = 252, 86
    ring = (cx - r, cy - r, cx + r, cy + r)
    # Two arcs making one circle, split at 160 degrees so the icon shows a session
    # partly elapsed rather than a full or empty ring. -90 is twelve o'clock, matching
    # where the app's ring starts. White is the remaining time, TRACK the spent part.
    d.arc(ring, start=-90, end=160, fill=WHITE, width=width)
    d.arc(ring, start=160, end=270, fill=TRACK, width=width)
    return img


def save(img, folder, name, size):
    """Write one launcher icon, downsampled to `size` pixels square.

    img is the full-size (1024 px) drawing; folder is a directory name under res/
    such as "mipmap-hdpi"; name is the file name, e.g. "ic_launcher.png"; size is
    the square edge in pixels.

    LANCZOS is specified rather than left to default because the default filter
    visibly softens the ring's edges at the smaller densities. Creates the target
    directory if needed, overwrites any existing file, and returns the path written.
    """
    path = os.path.join(RES, folder)
    os.makedirs(path, exist_ok=True)
    out = os.path.join(path, name)
    img.resize((size, size), Image.LANCZOS).save(out, format="PNG")
    return out


def save_listing_icon(img, size=512):
    """Write the F-Droid listing icon, downsampled from the 1024 px drawing.

    F-Droid asks for a 512 px PNG. Taking it from the same source as the launcher
    icons, rather than upscaling one of the small mipmap files, is both sharper
    and the reason the store icon cannot drift away from the one on the phone.

    img is the full-size (1024 px) RGBA drawing; size is the square edge in
    pixels, 512 being what F-Droid documents. Creates the target directory if it
    does not exist, and returns the path written.
    """
    os.makedirs(FASTLANE_IMAGES, exist_ok=True)
    out = os.path.join(FASTLANE_IMAGES, "icon.png")
    img.resize((size, size), Image.LANCZOS).save(out, format="PNG")
    return out


def main():
    """Draw the tomato once, then write every size Android and F-Droid need.

    Overwrites 15 launcher PNGs under res/mipmap-* plus the 512 px listing icon —
    it does not ask first, because every one of them is reproducible from this
    script. The adaptive icon's XML (mipmap-anydpi-v26/ic_launcher.xml) is written by
    hand and is NOT touched here.
    """
    tomato = draw_tomato()

    # Adaptive icons let the launcher apply its own mask, and the guaranteed-visible
    # region is only the middle ~66% of the square. The tomato is scaled to 62% and
    # centred, a little inside that guarantee: the extra margin is deliberate, so an
    # aggressive circular mask cannot shave the calyx or the ring.
    foreground = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    inner = int(S * 0.62)
    foreground.paste(
        tomato.resize((inner, inner), Image.LANCZOS),
        ((S - inner) // 2, (S - inner) // 2),
        tomato.resize((inner, inner), Image.LANCZOS),
    )

    written = 0
    for bucket, factor in DENSITIES.items():
        folder = f"mipmap-{bucket}"
        save(tomato, folder, "ic_launcher.png", int(48 * factor))
        save(tomato, folder, "ic_launcher_round.png", int(48 * factor))
        save(foreground, folder, "ic_launcher_fg.png", int(108 * factor))
        written += 3
    print(f"wrote {written} icons under {RES}")
    print(f"wrote listing icon {save_listing_icon(tomato)}")


if __name__ == "__main__":
    main()
