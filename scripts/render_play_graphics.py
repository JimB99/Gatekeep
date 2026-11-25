#!/usr/bin/env python3
"""Render Google Play icon and feature graphic from the vector launcher foreground."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from svg.path import parse_path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "docs/play-store"

BACKGROUND = (0x1A, 0x73, 0xE8)
GRADIENT_START = (0x12, 0x56, 0xB8)
GRADIENT_END = (0x1A, 0x73, 0xE8)

# From res/drawable/ic_launcher_foreground.xml (108×108 viewport)
FOREGROUND_PATHS: tuple[tuple[str, str], ...] = (
    ("#1A73E8", "M54,8 L92,28 V58 C92,78 74,92 54,98 C34,92 16,78 16,58 V28 Z"),
    ("#FFFFFF", "M54,30 L72,40 V56 C72,66 63,72 54,75 C45,72 36,66 36,56 V40 Z"),
    ("#1A73E8", "M48,48 H60 V64 H48 Z"),
)


def _path_to_polygon(path_d: str, scale: float, samples: int = 96) -> list[tuple[float, float]]:
    path = parse_path(path_d)
    if path.length() <= 0:
        return []
    return [
        (path.point(i / samples).real * scale, path.point(i / samples).imag * scale)
        for i in range(samples + 1)
    ]


def _rasterize_foreground(size: int) -> Image.Image:
    scale = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for fill, path_d in FOREGROUND_PATHS:
        polygon = _path_to_polygon(path_d, scale)
        if len(polygon) >= 3:
            draw.polygon(polygon, fill=fill)
    return img


def _path_bounds(path_d: str) -> tuple[float, float, float, float]:
    path = parse_path(path_d)
    points = [path.point(i / 96) for i in range(97)]
    xs = [p.real for p in points]
    ys = [p.imag for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def _play_icon(size: int = 512, *, fill_ratio: float = 0.9) -> Image.Image:
    """Scale from the white shield path so the emblem fills the square (not the outer blue hull)."""
    canvas = Image.new("RGB", (size, size), BACKGROUND)
    x0, y0, x1, y1 = _path_bounds(FOREGROUND_PATHS[1][1])
    content_w, content_h = x1 - x0, y1 - y0
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    pixels = size * fill_ratio
    viewport_scale = pixels / max(content_w, content_h)
    raster_px = max(256, int(108 * viewport_scale))
    fg = _rasterize_foreground(raster_px)
    paste_x = int(size / 2 - cx * viewport_scale)
    paste_y = int(size / 2 - cy * viewport_scale)
    canvas.paste(fg, (paste_x, paste_y), fg)
    return canvas


def linear_gradient(size: tuple[int, int], angle_deg: float = 135) -> Image.Image:
    w, h = size
    img = Image.new("RGB", (w, h))
    px = img.load()
    rad = math.radians(angle_deg)
    cos_a, sin_a = math.cos(rad), math.sin(rad)
    for y in range(h):
        for x in range(w):
            t = (x / max(w - 1, 1) * cos_a + y / max(h - 1, 1) * sin_a + 1) / 2
            t = max(0.0, min(1.0, t))
            r = int(GRADIENT_START[0] + (GRADIENT_END[0] - GRADIENT_START[0]) * t)
            g = int(GRADIENT_START[1] + (GRADIENT_END[1] - GRADIENT_START[1]) * t)
            b = int(GRADIENT_START[2] + (GRADIENT_END[2] - GRADIENT_START[2]) * t)
            px[x, y] = (r, g, b)
    return img


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    icon_512 = _play_icon()
    icon_path = OUT_DIR / "play-store-icon-512.png"
    icon_512.save(icon_path, format="PNG", optimize=True)
    print(f"Wrote {icon_path} ({icon_path.stat().st_size // 1024} KiB)")

    fg_w, fg_h = 1024, 500
    margin_r = 40
    banner = linear_gradient((fg_w, fg_h))

    icon_side = 360
    icon_on_banner = _play_icon(icon_side, fill_ratio=0.9)
    icon_x = 40
    banner.paste(icon_on_banner, (icon_x, (fg_h - icon_side) // 2))

    draw = ImageDraw.Draw(banner)
    title = "Gatekeep"
    subtitle_lines = ("Screen-time limits", "with real enforcement")
    tagline = "Profiles · Schedules · Friction unlocks · HUD"

    text_x = icon_x + icon_side + 48
    max_text_w = fg_w - text_x - margin_r

    font_title = _fit_font(draw, title, max_text_w, start_size=68, bold=True)
    font_sub = _fit_font(draw, subtitle_lines[0], max_text_w, start_size=34, bold=False)
    font_tag = _fit_font(draw, tagline, max_text_w, start_size=24, bold=False)

    sub_h = _line_height(font_sub)
    block_top = (fg_h - (68 + sub_h * 2 + 36 + 8)) // 2
    y = max(48, block_top)
    draw.text((text_x, y), title, fill=(255, 255, 255), font=font_title)
    y += 78
    for line in subtitle_lines:
        draw.text((text_x, y), line, fill=(225, 235, 250), font=font_sub)
        y += sub_h + 4
    y += 8
    draw.text((text_x, y), tagline, fill=(190, 215, 245), font=font_tag)

    fg_path = OUT_DIR / "play-store-feature-graphic-1024x500.png"
    banner.save(fg_path, format="PNG", optimize=True)
    print(f"Wrote {fg_path} ({fg_path.stat().st_size // 1024} KiB)")


def _text_width(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont) -> int:
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0]


def _line_height(font: ImageFont.ImageFont) -> int:
    bbox = font.getbbox("Ag")
    return bbox[3] - bbox[1]


def _fit_font(
    draw: ImageDraw.ImageDraw,
    text: str,
    max_width: int,
    *,
    start_size: int,
    bold: bool,
    min_size: int = 18,
) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    size = start_size
    while size >= min_size:
        font = _load_font(size, bold=bold)
        if _text_width(draw, text, font) <= max_width:
            return font
        size -= 2
    return _load_font(min_size, bold=bold)


def _load_font(size: int, bold: bool) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = (
        ["C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf"]
        if bold
        else ["C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"]
    )
    for path in candidates:
        p = Path(path)
        if p.is_file():
            return ImageFont.truetype(str(p), size=size)
    return ImageFont.load_default()


if __name__ == "__main__":
    main()