#!/usr/bin/env python3
"""Draw original, right-facing pixel-art cat and dog animation loops.

Requires Pillow; no source artwork, fonts, downloads, or randomness:
    build/pet-art-tools/bin/python tools/prepare_cat_dog.py \
        --preview-dir build/reports/air-pets/cat-dog-art

Art is drawn on a 32-pixel grid, then enlarged with nearest-neighbor sampling.
Use --check to verify the bundled GIFs against freshly drawn frames without
rewriting them. Optional previews use Pillow's built-in font for labels only.
"""

import argparse
import base64
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, __version__ as PILLOW_VERSION


ROOT = Path(__file__).resolve().parents[1]
SIZE = 64
GRID = 32
FRAMES = 12
CLIPS = {"idle": 120, "walk": 90, "run": 60, "waiting": 120, "done": 90}
PETS = ("cat", "dog")
PREVIEW_ROOT = ROOT / "build/reports/air-pets/cat-dog-art"
COLORS = {
    "outline": "#302a32",
    "ginger": "#e78c42",
    "ginger_light": "#f7b75d",
    "ginger_shadow": "#aa5332",
    "stripe": "#bd6333",
    "cream": "#fff0c9",
    "cream_shadow": "#dfc798",
    "pink": "#eaa0a0",
    "nose": "#b55f72",
    "green": "#91ae68",
    "white": "#fff9e8",
    "tan": "#c79359",
    "tan_light": "#e0b47a",
    "brown": "#96613d",
    "ear": "#795039",
    "ear_light": "#aa7147",
    "blue_shadow": "#245477",
    "blue": "#408ab5",
    "blue_light": "#79c6da",
    "gold": "#edc45a",
}
PALETTE = [0, 0, 0] + [int(color[i:i + 2], 16)
                       for color in COLORS.values() for i in (1, 3, 5)]
PALETTE += [0] * (768 - len(PALETTE))
INDICES = {tuple(int(color[i:i + 2], 16) for i in (1, 3, 5)): index
           for index, color in enumerate(COLORS.values(), 1)}


def polygon(draw, points, color, outline=False, offset=(0, 0)):
    points = [(x + offset[0], y + offset[1]) for x, y in points]
    draw.polygon(points, fill=COLORS[color], outline=COLORS["outline"] if outline else None)


def line(draw, points, color, width=1, offset=(0, 0)):
    draw.line([(x + offset[0], y + offset[1]) for x, y in points],
              fill=COLORS[color], width=width, joint="curve")


def box(draw, bounds, color, offset=(0, 0)):
    x0, y0, x1, y1 = bounds
    draw.rectangle((x0 + offset[0], y0 + offset[1], x1 + offset[0], y1 + offset[1]),
                   fill=COLORS[color])


def tail(draw, pet, phase, lift):
    dx, dy = round(2 * math.cos(phase)), round(2 * math.sin(phase))
    if pet == "cat":
        points = [(11, 22), (7, 21), (5, 18), (5, 13), (6, 10), (8, 9), (8 + dx, 11 + dy)]
        fill = "ginger"
    else:
        points = [(11, 21), (8, 20), (6, 17), (6 + dx, 12 + dy)]
        fill = "tan"
    line(draw, points, "outline", 5, (0, lift))
    line(draw, points, fill, 3, (0, lift))
    if pet == "cat":
        line(draw, [(4, 15), (6, 15)], "stripe", offset=(0, lift))
        line(draw, [(5, 19), (7, 18)], "stripe", offset=(0, lift))
        line(draw, [points[-2], points[-1]], "ginger_shadow", 2, (0, lift))
        box(draw, (7, 9, 8, 9), "ginger_light", (0, lift))
    else:
        end_x, end_y = points[-1]
        box(draw, (end_x, end_y + lift, end_x + 1, end_y + lift + 1), "cream")


def leg(draw, root, foot, pet, far=False, raised=False):
    x, y = root
    fx, fy = foot
    knee = (round((x + fx) / 2) - (1 if raised else 0), round((y + fy) / 2))
    fill = ("ginger_shadow" if far else "ginger") if pet == "cat" else ("brown" if far else "tan")
    line(draw, [root, knee, (fx, fy)], "outline", 4)
    line(draw, [root, knee, (fx, fy)], fill, 2)
    box(draw, (fx - 1, fy, fx + 2, fy + 1), "outline")
    box(draw, (fx, fy, fx + 1, fy), "cream_shadow" if far else "cream")


def body(draw, pet, lift, breathe):
    offset = (0, lift)
    draw.ellipse((8, 16 + lift - breathe, 23, 25 + lift), fill=COLORS["outline"])
    draw.ellipse((9, 17 + lift - breathe, 22, 24 + lift),
                 fill=COLORS["ginger"] if pet == "cat" else COLORS["tan"])
    if pet == "cat":
        line(draw, [(11, 23), (14, 24), (19, 24), (22, 22)], "ginger_shadow", offset=offset)
        line(draw, [(11, 18 - breathe), (14, 17 - breathe), (19, 17 - breathe)],
             "ginger_light", offset=offset)
        for x in (12, 15, 18):
            line(draw, [(x, 17 - breathe), (x, 18), (x + 1, 19)], "stripe", offset=offset)
    else:
        polygon(draw, [(10, 18), (12, 17 - breathe), (17, 17 - breathe),
                       (19, 19), (18, 21), (13, 22), (10, 20)], "brown", offset=offset)
        line(draw, [(12, 18 - breathe), (16, 18 - breathe), (17, 19)], "ear_light", offset=offset)
        line(draw, [(11, 23), (14, 24), (19, 24), (22, 22)], "brown", offset=offset)
    polygon(draw, [(20, 19), (22, 18), (23, 20), (22, 24), (20, 24), (19, 22)],
            "cream_shadow", offset=offset)
    polygon(draw, [(21, 19), (22, 19), (22, 22), (21, 23), (20, 21)], "cream", offset=offset)


def cat_head(draw, lift, blink, happy, curious):
    offset = (0, lift)
    polygon(draw, [(17, 12), (17, 5), (18, 5), (22, 8), (24, 8), (27, 5), (28, 6),
                   (28, 11), (29, 13), (29, 17), (27, 19), (22, 20), (19, 18), (17, 15)],
            "ginger", True, offset)
    polygon(draw, [(18, 7), (20, 9), (18, 10)], "pink", offset=offset)
    polygon(draw, [(27, 7), (26, 9), (27, 10)], "pink", offset=offset)
    polygon(draw, [(20, 10), (22, 9), (25, 9), (27, 11), (26, 13), (20, 13)],
            "ginger_light", offset=offset)
    line(draw, [(21, 9), (22, 10)], "stripe", offset=offset)
    line(draw, [(24, 9), (24, 10)], "stripe", offset=offset)
    line(draw, [(18, 14), (19, 15), (20, 15)], "stripe", offset=offset)
    line(draw, [(19, 17), (20, 17)], "stripe", offset=offset)
    for x in (20, 25):
        if blink or happy:
            line(draw, [(x, 13), (x + 1, 12 if happy else 14), (x + 2, 13)],
                 "outline", offset=offset)
        else:
            box(draw, (x, 12, x + 2, 14), "outline", offset)
            box(draw, (x, 13, x, 14), "green", offset)
            box(draw, (x + 1, 12, x + 1, 12), "white", offset)
        if curious:
            line(draw, [(x, 10), (x + 1, 10)], "ginger_shadow", offset=offset)
    polygon(draw, [(23, 15), (26, 15), (27, 16), (28, 16), (28, 18), (24, 19), (21, 17)],
            "cream", offset=offset)
    box(draw, (28, 15, 29, 15), "outline", offset)
    box(draw, (28, 15, 28, 15), "nose", offset)
    line(draw, [(27, 17), (27, 18), (26, 18)], "outline", offset=offset)
    box(draw, (23, 16, 23, 16), "ginger_shadow", offset)
    line(draw, [(22, 17), (20, 16)], "outline", offset=offset)
    line(draw, [(22, 18), (20, 18)], "ginger_shadow", offset=offset)


def dog_head(draw, lift, blink, happy, curious, phase):
    offset = (0, lift)
    polygon(draw, [(20, 19), (24, 19), (25, 21), (23, 22), (20, 21)], "blue_shadow", True, offset)
    line(draw, [(21, 20), (23, 20), (24, 21)], "blue", 2, offset)
    box(draw, (22, 20, 23, 20), "blue_light", offset)
    box(draw, (23, 22, 24, 23), "outline", offset)
    box(draw, (23, 22, 23, 22), "gold", offset)
    polygon(draw, [(19, 12), (20, 10), (23, 9), (26, 9), (28, 11), (28, 14),
                   (29, 15), (29, 19), (27, 20), (23, 20), (21, 18), (19, 15)],
            "tan", True, offset)
    polygon(draw, [(22, 11), (24, 10), (26, 10), (27, 12), (26, 14), (23, 14)],
            "tan_light", offset=offset)
    if blink or happy:
        line(draw, [(24, 13), (25, 12 if happy else 14), (26, 13)], "outline", offset=offset)
    else:
        box(draw, (24, 12, 26, 14), "outline", offset)
        box(draw, (25, 12, 25, 12), "white", offset)
        box(draw, (24, 14, 24, 14), "brown", offset)
    if curious:
        line(draw, [(24, 10), (25, 10)], "brown", offset=offset)
    polygon(draw, [(26, 15), (28, 14), (28, 16), (29, 16), (29, 18),
                   (27, 19), (24, 18), (24, 16)], "cream", offset=offset)
    box(draw, (28, 14, 29, 15), "outline", offset)
    box(draw, (28, 14, 28, 14), "brown", offset)
    line(draw, [(27, 17), (27, 18), (26, 18)], "outline", offset=offset)
    if happy:
        box(draw, (26, 19, 27, 19), "pink", offset)
    ear_drop = round(math.sin(phase)) if happy else 0
    polygon(draw, [(20, 11), (22, 12), (22, 17 + ear_drop), (21, 20 + ear_drop),
                   (19, 20), (18, 18), (18, 13)], "ear", True, offset)
    line(draw, [(20, 12), (19, 14), (19, 17), (20, 18 + ear_drop)], "ear_light", offset=offset)
    box(draw, (20, 12, 20, 13), "brown", offset)


def sparkle(draw, x, y, large):
    radius = 2 if large else 1
    line(draw, [(x - radius, y), (x + radius, y)], "gold")
    line(draw, [(x, y - radius), (x, y + radius)], "gold")
    box(draw, (x, y, x, y), "white")


def make_frame(pet, name, index):
    phase = 2 * math.pi * index / FRAMES
    image = Image.new("RGBA", (GRID, GRID))
    draw = ImageDraw.Draw(image)
    lift = 0
    breathe = int(name in ("idle", "waiting") and 2 <= index <= 6)
    if name == "walk":
        lift = -round((1 + math.cos(2 * phase)) / 2)
    elif name == "run":
        lift = -round(2 * max(0, math.sin(phase)))
    elif name == "done":
        lift = -round(3 * (1 - math.cos(phase)) / 2)
    tail(draw, pet, phase, lift)
    # Opposite diagonal pairs trot; the run changes to a staggered bound.
    for far in (True, False):
        for front in (False, True):
            root_x = (22 if front else 14) if far else (21 if front else 11)
            foot_x, foot_y = root_x, 27 if far else 28
            if name in ("walk", "run"):
                leg_phase = phase + (math.pi if front != far else 0)
                if name == "run":
                    leg_phase = phase + (math.pi if front else 0) + (0.9 if far else 0)
                foot_x += round((4 if name == "run" else 3) * math.sin(leg_phase))
                foot_y -= round((4 if name == "run" else 2) * max(0, math.cos(leg_phase)))
                if name == "run":
                    foot_y += min(lift + 1, 0)
            elif name == "done":
                foot_y += lift
                foot_x += round(math.sin(phase) * (1 if front else -1))
            raised = name == "waiting" and front and not far
            if raised:
                foot_x = 25
                foot_y = 23 - round((1 - math.cos(phase)) / 2)
            leg(draw, (root_x, 22 + lift), (foot_x, foot_y), pet, far, raised)
    body(draw, pet, lift, breathe)
    head_lift = lift - (1 if name == "waiting" and 3 <= index <= 7 else 0)
    blink = name in ("idle", "waiting") and index in (8, 9)
    happy = name == "done" and 2 <= index <= 9
    if pet == "cat":
        cat_head(draw, head_lift, blink, happy, name == "waiting")
    else:
        dog_head(draw, head_lift, blink, happy, name == "waiting", phase)
    if name == "done":
        sparkle(draw, 5, 5, index in (1, 2, 3, 7, 8, 9))
        sparkle(draw, 27, 4, index in (0, 1, 5, 6, 7, 11))
    return image.resize((SIZE, SIZE), Image.Resampling.NEAREST)


def save_clip(path, frames, duration):
    encoded = []
    for frame in frames:
        rgba = frame.tobytes()
        pixels = bytes(INDICES[tuple(rgba[i:i + 3])] if rgba[i + 3] else 0
                       for i in range(0, len(rgba), 4))
        output = Image.frombytes("P", (SIZE, SIZE), pixels)
        output.putpalette(PALETTE)
        encoded.append(output)
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded[0].save(path, save_all=True, append_images=encoded[1:], duration=duration,
                    loop=0, transparency=0, background=0, disposal=2, optimize=False)


def gif_controls(data):
    """Read every graphics-control block, including transparent indices after frame zero."""
    assert data[:6] == b"GIF89a", "GIF89a header"
    cursor = 13 + (3 * 2 ** ((data[10] & 7) + 1) if data[10] & 128 else 0)
    controls = []
    images = 0
    while data[cursor] != 0x3b:
        marker = data[cursor]
        cursor += 1
        if marker == 0x21:
            label = data[cursor]
            cursor += 1
            if label == 0xf9:
                assert data[cursor] == 4 and data[cursor + 5] == 0, "graphics control block"
                packed = data[cursor + 1]
                controls.append({"disposal": (packed >> 2) & 7,
                                 "transparent": bool(packed & 1),
                                 "transparent_index": data[cursor + 4],
                                 "duration_ms": int.from_bytes(data[cursor + 2:cursor + 4], "little") * 10})
        elif marker == 0x2c:
            images += 1
            packed = data[cursor + 8]
            cursor += 9 + (3 * 2 ** ((packed & 7) + 1) if packed & 128 else 0)
            cursor += 1  # LZW minimum code size precedes the image-data subblocks.
        else:
            raise AssertionError(f"Unexpected GIF block: {marker:#x}")
        while data[cursor]:
            cursor += data[cursor] + 1
        cursor += 1
    assert images == len(controls) == FRAMES, "encoded image/control count"
    return controls


def validate_clip(path, authored, duration):
    data = path.read_bytes()
    controls = gif_controls(data)
    for control in controls:
        assert control == {"disposal": 2, "transparent": True,
                           "transparent_index": 0, "duration_ms": duration}, (path, control)
    decoded = []
    bounds = []
    areas = []
    changes = []
    with Image.open(path) as gif:
        assert gif.size == (SIZE, SIZE) and gif.n_frames == FRAMES, (path, "size/frame count")
        assert gif.info["loop"] == 0 and gif.info["background"] == 0, (path, "loop/background")
        assert gif.info["transparency"] == 0, (path, "transparent index")
        for index in range(FRAMES):
            gif.seek(index)
            assert gif.info["duration"] == duration and gif.disposal_method == 2, (path, index, "timing/disposal")
            frame = gif.convert("RGBA")
            alpha = frame.getchannel("A")
            frame_bounds = alpha.getbbox()
            assert frame_bounds and min(frame_bounds[:2]) >= 3 and max(frame_bounds[2:]) <= SIZE - 3, (
                path, index, "transparent margin", frame_bounds)
            assert set(alpha.tobytes()) == {0, 255}, (path, index, "binary transparency")
            area = sum(a == 255 for a in alpha.tobytes())
            assert 700 <= area <= SIZE * SIZE * 0.55, (path, index, "visible opaque area", area)
            assert frame.tobytes() == authored[index].tobytes(), (path, index, "decoded/authored mismatch or disposal trail")
            bounds.append(list(frame_bounds))
            areas.append(area)
            decoded.append(frame)
    unique = len({frame.tobytes() for frame in decoded})
    assert unique >= 6, (path, "insufficient visible animation")
    for previous, current in zip(decoded[-1:] + decoded[:-1], decoded):
        before, after = previous.tobytes(), current.tobytes()
        changes.append(sum(before[i:i + 4] != after[i:i + 4] for i in range(0, len(before), 4)))
    assert min(changes) > 0, (path, "adjacent duplicate would be merged")
    return {"dimensions": [SIZE, SIZE], "frames": FRAMES, "unique_frames": unique,
            "duration_ms": [control["duration_ms"] for control in controls],
            "loop_ms": FRAMES * duration, "infinite_loop": True,
            "transparency_index": 0, "disposal": [control["disposal"] for control in controls],
            "bounds_xyxy": bounds, "opaque_pixels": areas,
            "minimum_margin_px": min(min(b[0], b[1], SIZE - b[2], SIZE - b[3]) for b in bounds),
            "changed_pixels_from_previous": changes, "decoded_matches_authored": True,
            "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def previews(directory, animations, report):
    directory = directory.resolve()
    assert directory == PREVIEW_ROOT or PREVIEW_ROOT in directory.parents, "Preview output must stay under cat-dog-art"
    directory.mkdir(parents=True, exist_ok=True)
    width, height = 120 + FRAMES * 72, 32 + len(PETS) * len(CLIPS) * 2 * 80
    sheet = Image.new("RGB", (width, height), "#40444c")
    draw = ImageDraw.Draw(sheet)
    draw.text((12, 10), "AIR PETS / ORIGINAL CAT + DOG / 64px / ALL 12 FRAMES", fill="white")
    html_rows = []
    row = 0
    for pet in PETS:
        figures = []
        for name in CLIPS:
            frames = animations[pet][name]
            for background in ("#f7f8fa", "#25282e"):
                y = 32 + row * 80
                draw.rectangle((0, y, width, y + 79), fill=background)
                foreground = "#302a32" if background == "#f7f8fa" else "#fff0c9"
                draw.text((10, y + 12), f"{pet} / {name}", fill=foreground)
                draw.text((10, y + 28), f"12 x {CLIPS[name]} ms", fill=foreground)
                for index, frame in enumerate(frames):
                    sheet.paste(frame, (120 + index * 72, y + 4), frame)
                    draw.text((122 + index * 72, y + 67), str(index + 1), fill=foreground)
                row += 1
            data = base64.b64encode((ROOT / f"src/main/resources/pets/{pet}/{name}.gif").read_bytes()).decode("ascii")
            figures.append(f'<figure><figcaption>{name} / {CLIPS[name]} ms</figcaption>'
                           f'<img width="128" height="128" alt="{pet} {name}" '
                           f'src="data:image/gif;base64,{data}"></figure>')
        figures = "\n".join(figures)
        html_rows.append(f'<h3>{pet}</h3><section class="light">{figures}</section>'
                         f'<section class="dark">{figures}</section>')
    sheet.save(directory / "contact-sheet.png")
    (directory / "metadata.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    rows = "\n".join(html_rows)
    (directory / "preview.html").write_text(f'''<!doctype html>
<html lang="en"><meta charset="utf-8"><title>Air Pets: original cat + dog</title>
<style>body {{ background:#40444c; color:white; font:14px system-ui; margin:24px; }}
section {{ display:flex; gap:16px; padding:16px; width:max-content; }}
figure {{ margin:0; text-align:center; }} img {{ display:block; image-rendering:pixelated; }}
.light {{ background:#f7f8fa; color:#302a32; }} .dark {{ background:#25282e; }}</style>
<h3>Air Pets / original pixel companions</h3>
<p>32px drawing grid, 64px transparent GIFs, 2x nearest-neighbor display. No external art.</p>
{rows}</html>
''', encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview-dir", type=Path)
    parser.add_argument("--check", action="store_true", help="Validate existing GIFs without rewriting them")
    args = parser.parse_args()
    if args.preview_dir:
        args.preview_dir = args.preview_dir.resolve()
        if args.preview_dir != PREVIEW_ROOT and PREVIEW_ROOT not in args.preview_dir.parents:
            parser.error("Preview output must stay under build/reports/air-pets/cat-dog-art")
    animations = {pet: {name: [make_frame(pet, name, index) for index in range(FRAMES)]
                        for name in CLIPS} for pet in PETS}
    report = {"generator": "tools/prepare_cat_dog.py", "pillow": PILLOW_VERSION,
              "canvas": [SIZE, SIZE], "drawing_grid": [GRID, GRID],
              "palette": COLORS, "source": "Original procedural pixel art; no external artwork or fonts",
              "pets": {pet: {} for pet in PETS}}
    for pet in PETS:
        for name, duration in CLIPS.items():
            path = ROOT / f"src/main/resources/pets/{pet}/{name}.gif"
            if not args.check:
                save_clip(path, animations[pet][name], duration)
            result = validate_clip(path, animations[pet][name], duration)
            report["pets"][pet][name] = result
            print(f"{pet}/{name}: {FRAMES} frames, {result['unique_frames']} distinct, "
                  f"{duration} ms/frame, {result['loop_ms']} ms loop, "
                  f"margin >= {result['minimum_margin_px']} px, "
                  f"{min(result['opaque_pixels'])}-{max(result['opaque_pixels'])} opaque px, "
                  f"{result['bytes']} bytes; decoded pixels match")
    if args.preview_dir:
        previews(args.preview_dir, animations, report)
        print(f"Previews and metadata: {args.preview_dir}")


if __name__ == "__main__":
    main()