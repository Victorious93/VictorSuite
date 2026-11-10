#!/usr/bin/env python3
"""Regenerate VictorSuite launcher icons from art/ic_launcher_source.png.

The artwork (neon-cyan helmet in a HUD ring on a near-black field) is kept
as-is. This script only re-renders it cleanly:

  * legacy square mipmaps at 48/72/96/144/192, downscaled from the 1024px
    source with Lanczos (never upscaled from a smaller PNG)
  * circular-masked ic_launcher_round counterparts
  * adaptive foregrounds on a 108dp canvas with the full emblem inside the
    66dp safe zone, plus a matching <monochrome> vector for API 33 themed icons

Requires: Pillow. Monochrome vector generation also needs the 'potracer'
package (pip). Run from the art/ directory or pass --repo-root.
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

# Sampled from the existing logo's dominant dark field. Not vs_neon_bg
# (#0B0F1A), which is the in-app theme and is visibly lighter than the icon.
BG_HEX = "00020E"
BG_RGB = (0x00, 0x02, 0x0E)

# Adaptive-icon canvas is 108dp; anything outside the centre 66dp may be
# cropped by a launcher mask.
ADAPTIVE_DP = 108
SAFE_DP = 66

# Explicit dp multipliers (mdpi = 160dpi = 1.0)
DENSITY_SCALE = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

LEGACY_DP = 48
MONO_THRESHOLD = 90


def dp(density: str, value: float) -> int:
    return int(round(value * DENSITY_SCALE[density]))


def load_source(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    if im.size[0] != im.size[1]:
        raise SystemExit(f"source must be square, got {im.size}")
    return im


def resize(im: Image.Image, size: int) -> Image.Image:
    if im.size == (size, size):
        return im.copy()
    return im.resize((size, size), Image.Resampling.LANCZOS)


def save_png(im: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, format="PNG", optimize=True, compress_level=9)


def circle_mask(size: int, supersample: int = 4) -> Image.Image:
    s = size * supersample
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, s - 1, s - 1), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def apply_circle(im: Image.Image) -> Image.Image:
    from PIL import ImageChops

    out = im.copy()
    out.putalpha(ImageChops.multiply(im.split()[3], circle_mask(im.size[0])))
    return out


def make_legacy(src: Image.Image, size: int) -> Image.Image:
    return resize(src, size)


def make_foreground(src: Image.Image, canvas: int, safe: int) -> Image.Image:
    """Place the full source square in the centre `safe` px of `canvas` px."""
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    emblem = resize(src, safe)
    inset = (canvas - safe) // 2
    # If canvas-safe is odd, leftover 1px goes to the bottom-right.
    fg.alpha_composite(emblem, (inset, inset))
    return fg


def make_monochrome_bitmap(src: Image.Image) -> Image.Image:
    """White-on-transparent glyph from the neon artwork (max-channel threshold)."""
    rgb = src.convert("RGB")
    pixels = rgb.load()
    w, h = rgb.size
    out = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    dest = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = pixels[x, y]
            if max(r, g, b) >= MONO_THRESHOLD:
                dest[x, y] = (255, 255, 255, 255)
    return out


def trace_monochrome_vector(mono: Image.Image, canvas_dp: float, safe_dp: float) -> str:
    """Potrace the monochrome bitmap and return a VectorDrawable XML string."""
    try:
        import numpy as np
        import potrace
    except ImportError as e:
        raise SystemExit(
            "potracer (and numpy) are required to build the monochrome vector: " + str(e)
        )

    # Do not use Image.convert("RGB") here: Pillow composites RGBA onto
    # white, which would wipe the transparent field and yield an empty trace.
    # potrace treats True as the interior to outline. Passing the dark
    # field (the inverse of the neon glyph) yields a white-on-transparent
    # emblem; passing the glyph itself fills the 66dp square and holes out
    # the artwork, which is wrong for a themed icon.
    if mono.mode == "RGBA":
        arr = np.asarray(mono.getchannel("A")) < 128
    else:
        arr = np.asarray(mono.convert("RGB")).max(axis=2) < 128
    # aapt2 cannot encode a pathData string longer than ~32 KiB
    # (it becomes STRING_TOO_LARGE and the glyph is dropped). Trace a
    # 384px bitmap with slightly looser curves so the vector stays under
    # that limit without losing the helmet / prompt / ring.
    TRACE_PX = 384
    if arr.shape[0] != TRACE_PX:
        # Resize the boolean mask via a grayscale image for Lanczos.
        mask_im = Image.fromarray((~arr).astype("uint8") * 255, "L")  # glyph=white
        mask_im = mask_im.resize((TRACE_PX, TRACE_PX), Image.Resampling.LANCZOS)
        arr = np.asarray(mask_im) < 128  # dark field = True, as above

    path = potrace.Bitmap(arr).trace(
        turdsize=8,
        turnpolicy=potrace.POTRACE_TURNPOLICY_MINORITY,
        alphamax=1.0,
        opticurve=1,
        opttolerance=0.4,
    )

    src_w, src_h = arr.shape[1], arr.shape[0]
    inset = (canvas_dp - safe_dp) / 2.0
    sx = safe_dp / src_w
    sy = safe_dp / src_h

    def tx(x: float, y: float) -> str:
        return f"{inset + x * sx:.3f},{inset + y * sy:.3f}"

    d_parts: list[str] = []
    nseg = 0
    for curve in path:
        parts = [f"M{tx(curve.start_point.x, curve.start_point.y)}"]
        for segment in curve:
            nseg += 1
            if segment.is_corner:
                parts.append(f"L{tx(segment.c.x, segment.c.y)}")
                parts.append(f"L{tx(segment.end_point.x, segment.end_point.y)}")
            else:
                parts.append(
                    "C"
                    + tx(segment.c1.x, segment.c1.y)
                    + " "
                    + tx(segment.c2.x, segment.c2.y)
                    + " "
                    + tx(segment.end_point.x, segment.end_point.y)
                )
        parts.append("Z")
        d_parts.append("".join(parts))

    path_data = "".join(d_parts)
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{int(canvas_dp)}dp"\n'
        f'    android:height="{int(canvas_dp)}dp"\n'
        f'    android:viewportWidth="{canvas_dp}"\n'
        f'    android:viewportHeight="{canvas_dp}">\n'
        "    <!-- Themed/monochrome glyph traced from the existing launcher artwork. -->\n"
        '    <path\n'
        '        android:fillColor="#FFFFFFFF"\n'
        '        android:fillType="evenOdd"\n'
        f'        android:pathData="{path_data}" />\n'
        "</vector>\n"
    )
    print(f"monochrome vector: {nseg} segments, {len(xml)} bytes", file=sys.stderr)
    return xml



def qs_tile_vector(monochrome_xml: str) -> str:
    """Crop the 108dp monochrome glyph to its 66dp safe zone at 24dp."""
    import re

    m = re.search(r'android:pathData="([^"]+)"', monochrome_xml)
    if not m:
        raise SystemExit("monochrome XML missing pathData")
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="66"\n'
        '    android:viewportHeight="66">\n'
        "    <!-- Quick Settings / notification small icon: the monochrome glyph cropped\n"
        "         to the 66dp safe zone so it fills a 24dp tile. Same artwork as\n"
        "         ic_launcher_monochrome (white-on-transparent, evenOdd). -->\n"
        "    <group\n"
        '        android:translateX="-21"\n'
        '        android:translateY="-21">\n'
        "        <path\n"
        '            android:fillColor="#FFFFFFFF"\n'
        '            android:fillType="evenOdd"\n'
        f'            android:pathData="{m.group(1)}" />\n'
        "    </group>\n"
        "</vector>\n"
    )


ADAPTIVE_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=None,
        help="Repository root (default: parent of this script's directory)",
    )
    args = parser.parse_args()
    art_dir = Path(__file__).resolve().parent
    root = args.repo_root.resolve() if args.repo_root else art_dir.parent
    source_path = art_dir / "ic_launcher_source.png"
    if not source_path.exists():
        raise SystemExit(f"missing source artwork: {source_path}")

    src = load_source(source_path)
    print(f"source {source_path} {src.size}")

    res = root / "app" / "src" / "main" / "res"

    # Legacy + adaptive foreground at each density, rendered from the 1024 source.
    for density in DENSITY_SCALE:
        folder = res / f"mipmap-{density}"
        legacy_px = dp(density, LEGACY_DP)
        canvas_px = dp(density, ADAPTIVE_DP)
        safe_px = dp(density, SAFE_DP)

        legacy = make_legacy(src, legacy_px)
        save_png(legacy, folder / "ic_launcher.png")
        save_png(apply_circle(legacy), folder / "ic_launcher_round.png")
        save_png(make_foreground(src, canvas_px, safe_px), folder / "ic_launcher_foreground.png")
        print(
            f"{density}: legacy {legacy_px}px, foreground {canvas_px}px "
            f"(safe {safe_px}px)"
        )

    anydpi = res / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(ADAPTIVE_XML)
    (anydpi / "ic_launcher_round.xml").write_text(ADAPTIVE_XML)

    mono_bmp = make_monochrome_bitmap(src)
    xml = trace_monochrome_vector(mono_bmp, ADAPTIVE_DP, SAFE_DP)
    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "ic_launcher_monochrome.xml").write_text(xml)
    # 24dp QS / notification glyph: same path, cropped to the 66dp safe zone
    # so the emblem fills the tile instead of sitting in 108dp of padding.
    tile = qs_tile_vector(xml)
    (drawable / "ic_qs_tile_victorsuite.xml").write_text(tile)
    print("wrote adaptive XMLs, ic_launcher_monochrome.xml, ic_qs_tile_victorsuite.xml")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
