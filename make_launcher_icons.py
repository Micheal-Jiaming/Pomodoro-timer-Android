"""Regenerate the Android launcher icons - the same tomato as the desktop app.

The drawing is the one from `Pomodoro timer\\make_icon.py`, rendered at 1024 px
and downsampled, so the phone icon and the Windows icon are the same picture.

Produces, under app/src/main/res/:
  mipmap-*/ic_launcher.png          legacy square icon
  mipmap-*/ic_launcher_round.png    legacy round icon
  mipmap-*/ic_launcher_fg.png       adaptive foreground (Android 8+)
  mipmap-anydpi-v26/ic_launcher.xml adaptive icon, written by hand alongside

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

# Legacy icons are 48dp, adaptive foregrounds 108dp, at each density bucket.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def draw_tomato():
    """The tomato, drawn on a transparent 1024 px square."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    body = (70, 215, 954, 1000)          # wider than tall, which reads as tomato
    cx = (body[0] + body[2]) / 2
    cy = (body[1] + body[3]) / 2
    d.ellipse(body, fill=RED)

    base = (512, 250)                    # calyx: five leaves from the top
    for angle, length in [(-176, 205), (-142, 250), (-90, 215), (-38, 250), (-4, 205)]:
        a = math.radians(angle)
        tip = (base[0] + length * math.cos(a), base[1] + length * math.sin(a))
        px, py = -math.sin(a), math.cos(a)
        half = 62
        d.polygon(
            [
                (base[0] + half * px, base[1] + half * py),
                tip,
                (base[0] - half * px, base[1] - half * py),
            ],
            fill=GREEN,
        )
    d.ellipse((base[0] - 70, base[1] - 70, base[0] + 70, base[1] + 70), fill=GREEN)

    d.line([(512, 255), (512, 110)], fill=GREEN_DEEP, width=76)
    d.ellipse((474, 74, 550, 150), fill=GREEN_DEEP)

    r, width = 252, 86                   # the countdown ring inside the body
    ring = (cx - r, cy - r, cx + r, cy + r)
    d.arc(ring, start=-90, end=160, fill=WHITE, width=width)
    d.arc(ring, start=160, end=270, fill=TRACK, width=width)
    return img


def save(img, folder, name, size):
    path = os.path.join(RES, folder)
    os.makedirs(path, exist_ok=True)
    out = os.path.join(path, name)
    img.resize((size, size), Image.LANCZOS).save(out, format="PNG")
    return out


def main():
    tomato = draw_tomato()

    # Adaptive icons are masked to the middle ~66%, so the tomato is inset into
    # a transparent 108dp square rather than filling it.
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


if __name__ == "__main__":
    main()
