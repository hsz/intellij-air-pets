#!/usr/bin/env python3
"""Draw transparent Clippy-inspired paperclip loops from vector-like primitives.

Requires Pillow, with no external artwork, fonts, or network requests:
    python tools/prepare_clippy.py --preview-dir build/reports/air-pets/clippy-art

This is a newly drawn interpretation of the supplied visual reference, not an
export of Microsoft's Office artwork or an official Clippy animation.
"""

import argparse
import base64
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SIZE = 128
SCALE = 4
FRAMES = 24
CLIPS = {"idle": 100, "walk": 70, "run": 40, "waiting": 100, "done": 70}


def curve(start, control1, control2, end, steps=24):
    points = []
    for index in range(steps + 1):
        t = index / steps
        u = 1 - t
        points.append(tuple(u ** 3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t ** 3 * d
                            for a, b, c, d in zip(start, control1, control2, end)))
    return points


def stroke(draw, points, fill, width, offset=(0, 0)):
    scaled = [((x + offset[0]) * SCALE, (y + offset[1]) * SCALE) for x, y in points]
    draw.line(scaled, fill=fill, width=round(width * SCALE), joint="curve")
    radius = width * SCALE / 2
    for x, y in (scaled[0], scaled[-1]):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=fill)


def ellipse(draw, box, color):
    draw.ellipse(tuple(round(value * SCALE) for value in box), fill=color)


def eye(draw, x, y, openness, gaze):
    width, height = 12.7, 10.7 * openness
    ellipse(draw, (x - width, y - height, x + width, y + height), "#696379")
    for index in range(18):
        t = index / 17
        inset = 0.8 + 2.9 * t
        value = round(178 + 77 * t)
        ellipse(draw, (x - width + inset, y - height + inset * openness * 0.5,
                       x + width - inset * 0.55, y + height - inset * openness),
                (value, value, min(255, value + 7), 255))
    if openness > 0.45:
        px, py = x + gaze[0], y + gaze[1] + 0.9
        ellipse(draw, (px - 6.6, py - 6.1, px + 6.6, py + 6.1), "#454053")
        ellipse(draw, (px - 5.7, py - 5.5, px + 5.7, py + 5.5), "#171623")
        ellipse(draw, (px - 4.2, py - 4.6, px - 0.8, py - 1.8), "#ffffff")
        ellipse(draw, (px + 2.4, py + 2.2, px + 3.9, py + 3.7), "#777183")
    else:
        stroke(draw, curve((x - 8, y), (x - 3, y + 3), (x + 3, y + 3), (x + 8, y)),
               "#454053", 1.5)


def eyebrow(draw, x, y, lift=0, tilt=0):
    points = curve((x - 13, y + 3 + tilt), (x - 6, y - 5 - lift),
                   (x + 6, y - 5 - lift), (x + 12, y + tilt))
    points += curve((x + 12, y + tilt), (x + 6, y - 1 - lift),
                    (x - 5, y - 1 - lift), (x - 13, y + 3 + tilt))
    draw.polygon([(px * SCALE, py * SCALE) for px, py in points], fill="#302d3a")


def sparkle(draw, x, y, radius):
    points = [(x, y - radius), (x + radius * 0.28, y - radius * 0.28), (x + radius, y),
              (x + radius * 0.28, y + radius * 0.28), (x, y + radius),
              (x - radius * 0.28, y + radius * 0.28), (x - radius, y),
              (x - radius * 0.28, y - radius * 0.28)]
    draw.polygon([(px * SCALE, py * SCALE) for px, py in points], fill="#e6b448")
    ellipse(draw, (x - 0.8, y - 0.8, x + 0.8, y + 0.8), "#fff3bf")


def make_frame(name, index):
    phase = 2 * math.pi * index / FRAMES
    wave = math.sin(phase)
    image = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE))
    draw = ImageDraw.Draw(image)
    flex = {"idle": 0.5, "walk": 1.8, "run": 3.0, "waiting": 0.9, "done": 1.3}[name] * math.sin(phase + 0.7)
    segments = [
        ((55, 58), (51, 75), (52, 92), (60, 94)),
        ((60, 94), (68, 98), (73, 91), (74, 79)),
        ((74, 79), (75, 63), (78, 40), (79, 28)),
        ((79, 28), (82, 11), (65, 7), (56, 14)),
        ((56, 14), (47, 20), (47, 30), (45, 44)),
        ((45, 44), (44, 57), (42, 73), (42, 83)),
        ((42, 83), (40, 104), (50, 117), (64, 117)),
        ((64, 117), (79, 117), (84, 105), (84, 89)),
        ((84, 89), (83, 77), (84, 67), (88, 60)),
    ]
    wire = [(x + flex * ((118 - y) / 106) ** 1.4, y)
            for segment in segments for x, y in curve(*segment)]
    stroke(draw, wire, "#565169", 8.0)
    stroke(draw, wire, "#8b84a8", 6.5)
    stroke(draw, wire, "#b4adc9", 4.7, (-0.55, -0.2))
    stroke(draw, wire, "#dedbe9", 2.2, (-1.15, -0.35))
    stroke(draw, wire, "#f0eef7", 0.7, (-1.6, -0.4))

    blink = {18: 0.65, 19: 0.18, 20: 0.65}.get(index, 1.0)
    gaze = {"idle": (1.2 + 1.3 * wave, 0.4 * math.cos(phase)), "walk": (2.8, 0.3), "run": (3.0, -0.6),
            "waiting": (0.7 * wave, -1.6), "done": (0.5, -0.6)}[name]
    left_x, right_x = 44 + flex * 0.72, 79 + flex * 0.64
    eye(draw, left_x, 40, blink, gaze)
    eye(draw, right_x, 46, 0.18 if name == "done" and 10 <= index <= 13 else blink, gaze)
    eyebrow(draw, left_x, 25, lift=3.2 if name == "waiting" else 0.7,
            tilt=-0.8 if name == "run" else 0)
    eyebrow(draw, right_x, 31, lift=1.2 if name == "done" else 0,
            tilt=2.4 if name == "waiting" else 1.2)

    angle = {"idle": 1.2 * wave, "walk": 2.4 * wave, "run": -4.5 + 2 * wave,
             "waiting": 3.8 * math.sin(phase) ** 3, "done": 2.7 * wave}[name]
    hop = {"idle": 0, "walk": 1.1, "run": 2.8, "waiting": 0, "done": 3.0}[name]
    image = image.rotate(angle, Image.Resampling.BICUBIC, center=(64 * SCALE, 112 * SCALE),
                         translate=(0, -hop * (1 - math.cos(2 * phase)) / 2 * SCALE))
    if name == "done":
        draw = ImageDraw.Draw(image)
        sparkle(draw, 25, 32, 2.7 + 1.2 * math.sin(phase))
        sparkle(draw, 101, 21, 2.7 + 1.2 * math.cos(phase))
        sparkle(draw, 102, 72, 2.0 + 0.8 * math.sin(phase + 1))
    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def save_clips(destination, animations):
    atlas = Image.new("RGB", (SIZE, SIZE * FRAMES * len(CLIPS)))
    for row, frame in enumerate(frame for frames in animations.values() for frame in frames):
        atlas.paste(frame.convert("RGB"), (0, row * SIZE))
    indexed = atlas.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    palette = [0, 0, 0] + indexed.getpalette()[:765]
    destination.mkdir(parents=True, exist_ok=True)
    cursor = 0
    for name, frames in animations.items():
        encoded = []
        for frame in frames:
            pixels = indexed.crop((0, cursor * SIZE, SIZE, (cursor + 1) * SIZE)).tobytes()
            alpha = frame.getchannel("A").tobytes()
            output = Image.frombytes("P", (SIZE, SIZE),
                                     bytes(color + 1 if opacity >= 128 else 0
                                           for color, opacity in zip(pixels, alpha)))
            output.putpalette(palette)
            encoded.append(output)
            cursor += 1
        encoded[0].save(destination / f"{name}.gif", save_all=True, append_images=encoded[1:],
                        duration=CLIPS[name], loop=0, transparency=0, background=0,
                        disposal=2, optimize=False)


def validate(destination):
    report = {}
    for name, duration in CLIPS.items():
        path = destination / f"{name}.gif"
        with Image.open(path) as gif:
            assert gif.size == (SIZE, SIZE) and gif.n_frames == FRAMES, name
            assert gif.info["loop"] == 0, name
            decoded = []
            for index in range(gif.n_frames):
                gif.seek(index)
                frame = gif.convert("RGBA")
                bounds = frame.getchannel("A").getbbox()
                assert bounds and min(bounds[:2]) >= 3 and max(bounds[2:]) <= SIZE - 3, (name, index, bounds)
                assert frame.getpixel((0, 0))[3] == 0 and gif.info["duration"] == duration, name
                assert SIZE * SIZE / 20 < sum(a > 0 for a in frame.getchannel("A").tobytes()) < SIZE * SIZE * 0.8, name
                decoded.append(frame)
            assert len({frame.tobytes() for frame in decoded}) == FRAMES, name
            differences = [sum(ImageChops.difference(decoded[i - 1], decoded[i]).convert("RGB")
                               .resize((1, 1), Image.Resampling.BOX).getpixel((0, 0)))
                           for i in range(FRAMES)]
            assert differences[0] <= max(differences[1:]) + 3, (name, "loop seam")
        report[name] = {"dimensions": [SIZE, SIZE], "frames": FRAMES,
                        "loop_ms": FRAMES * duration, "bytes": path.stat().st_size,
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    return report


def previews(directory, destination, report):
    directory.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (SIZE * 8 + 90, (SIZE + 25) * len(CLIPS)), "#40444c")
    draw = ImageDraw.Draw(sheet)
    tiles = []
    for row, name in enumerate(CLIPS):
        draw.text((8, row * (SIZE + 25) + 18), name, fill="white")
        with Image.open(destination / f"{name}.gif") as gif:
            for column in range(8):
                gif.seek(column * 3)
                frame = gif.convert("RGBA")
                tile = Image.new("RGB", (SIZE, SIZE), "#25282e" if column % 2 == 0 else "#f7f8fa")
                tile.paste(frame, (0, 0), frame)
                sheet.paste(tile, (90 + column * SIZE, row * (SIZE + 25) + 20))
        data = base64.b64encode((destination / f"{name}.gif").read_bytes()).decode("ascii")
        tiles.append(f'<figure><figcaption>{name}</figcaption><img width="128" height="128" '
                     f'alt="{name}" src="data:image/gif;base64,{data}"></figure>')
    sheet.save(directory / "contact-sheet.png")
    row = "\n".join(tiles)
    (directory / "preview.html").write_text(f'''<!doctype html>
<html lang="en"><meta charset="utf-8"><title>Paperclip companion</title>
<style>body {{ background:#40444c; color:white; font:16px system-ui; margin:24px; }}
section {{ display:flex; gap:16px; padding:16px; width:max-content; margin:16px 0; }}
figure {{ margin:0; text-align:center; }} img {{ display:block; }}
.dark {{ background:#25282e; }} .light {{ background:#f7f8fa; color:#222; }}</style>
<h3>Clippy-inspired paperclip companion</h3><p>Newly drawn, unofficial animation loops.</p>
<section class="dark">{row}</section><section class="light">{row}</section></html>
''', encoding="utf-8")
    (directory / "metadata.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "src/main/resources/pets/clippy")
    parser.add_argument("--preview-dir", type=Path)
    args = parser.parse_args()
    animations = {name: [make_frame(name, index) for index in range(FRAMES)] for name in CLIPS}
    save_clips(args.output_dir, animations)
    report = validate(args.output_dir)
    if args.preview_dir:
        previews(args.preview_dir, args.output_dir, report)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()