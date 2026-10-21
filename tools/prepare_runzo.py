#!/usr/bin/env python3
"""Extract original Runzo artwork and make modest, unofficial pose-rocking loops.

Requires Pillow and PyMuPDF. No Illustrator, Ghostscript, or external renderer is
needed. Source documents are opened read-only and changed only in memory.

    python tools/prepare_runzo.py /path/to/Runzo --preview-dir build/reports/air-pets/runzo-art

The pose sheet deliberately has blank faces. Facial Beziers/strokes are copied
from the expression sheet, registered to the identical inner head edge. There
is no tracing, redrawing, limb deformation, or inferred animation keyframe.
"""

import argparse
import base64
import hashlib
import io
import json
import math
from pathlib import Path

import PIL
import pymupdf as fitz
from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
POSES = "Copy of JetBrains_RunButton_2D_Poses.ai"
FACES = "Copy of JetBrains_RunButton_2D_Face.ai"
GUIDE = "Copy of JetBrains_Characer Guide.pdf"
SUPERSAMPLE = 4

# Coordinates are PDF points on page 1; rows/columns count from the upper left.
# Path indices refer to get_drawings() BEFORE removing the artboard background.
EXPRESSIONS = {
    "neutral": {"head": 1, "paths": (53, 54, 59)},
    "happy": {"head": 3, "paths": (49, 50, 51)},
    "confident": {"head": 17, "paths": (24, 62, 63, 64)},
}
CLIPS = {
    "idle": {
        "cell": (1, 1), "description": "relaxed standing, arms down",
        "crop": (164, 258, 270, 417), "head": 78, "expression": "neutral",
        "degrees": 1.1, "offset": 0.0, "duration_ms": 120,
    },
    "walk": {
        "cell": (1, 8), "description": "high forward step, one planted foot",
        "crop": (1224, 243, 1388, 401), "head": 86, "expression": "happy",
        "degrees": 1.0, "offset": -1.0, "duration_ms": 80,
    },
    "run": {
        "cell": (2, 4), "description": "running, rear leg curled upward",
        "crop": (558, 462, 718, 619), "head": 82, "expression": "neutral",
        "degrees": 2.5, "offset": 0.0, "duration_ms": 50,
    },
    "waiting": {
        "cell": (1, 2), "description": "hands on hips, one leg angled outward",
        "crop": (286, 264, 405, 418), "head": 108, "expression": "confident",
        "degrees": 1.4, "offset": 0.0, "duration_ms": 140,
    },
    "done": {
        "cell": (1, 6), "description": "both arms raised/open, standing",
        "crop": (901, 240, 1051, 400), "head": 90, "expression": "happy",
        "degrees": 1.8, "offset": 0.0, "duration_ms": 100,
    },
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def registration(source_head, target_head):
    """Rigidly match the original inner left head edges, with no scale change."""
    source_edge = source_head["items"][1]
    target_edge = target_head["items"][1]
    require(source_edge[0] == target_edge[0] == "l", "Unexpected head topology")
    s0, s1 = source_edge[1:]
    t0, t1 = target_edge[1:]
    sv, tv = s1 - s0, t1 - t0
    require(abs(abs(sv) - abs(tv)) < 0.02, "Head sizes differ; refusing to stretch artwork")
    angle = math.atan2(tv.y, tv.x) - math.atan2(sv.y, sv.x)
    a, b = math.cos(angle), math.sin(angle)
    matrix = fitz.Matrix(a, b, -b, a, t0.x - a * s0.x + b * s0.y,
                         t0.y - b * s0.x - a * s0.y)
    require(abs(s0 * matrix - t0) < 0.02 and abs(s1 * matrix - t1) < 0.02,
            "Head registration failed")
    return matrix


def copy_expression_path(page, path, matrix):
    """Replay the supplied vector primitives, preserving original stroke styles."""
    shape = page.new_shape()
    for item in path["items"]:
        points = [point * matrix for point in item[1:]]
        if item[0] == "l":
            shape.draw_line(*points)
        elif item[0] == "c":
            shape.draw_bezier(*points)
        else:
            raise ValueError(f"Unsupported original facial primitive: {item[0]}")
    shape.finish(
        color=path["color"], fill=path["fill"], width=path["width"] or 1,
        lineCap=max(path["lineCap"] or (0,)), lineJoin=path["lineJoin"] or 0,
        dashes=path["dashes"], closePath=bool(path["closePath"]),
        even_odd=bool(path["even_odd"]),
        stroke_opacity=1 if path["stroke_opacity"] is None else path["stroke_opacity"],
        fill_opacity=1 if path["fill_opacity"] is None else path["fill_opacity"],
    )
    shape.commit(overlay=True)


def extract_pose(pose_bytes, face_paths, pose_paths, config):
    with fitz.open(stream=pose_bytes, filetype="pdf") as doc:
        page = doc[0]
        # Only the full-page black rectangle intersects this empty corner.
        page.add_redact_annot(fitz.Rect(1, 1, 2, 2), fill=False)
        page.apply_redactions(images=0, graphics=2, text=1)
        require(len(page.get_drawings()) == len(pose_paths) - 1,
                "Artboard removal changed more than the single background path")
        expression = EXPRESSIONS[config["expression"]]
        matrix = registration(face_paths[expression["head"]], pose_paths[config["head"]])
        for index in expression["paths"]:
            copy_expression_path(page, face_paths[index], matrix)
        crop = fitz.Rect(config["crop"])
        pixmap = page.get_pixmap(matrix=fitz.Matrix(SUPERSAMPLE, SUPERSAMPLE),
                                 clip=crop, alpha=True)
        # PNG encoding unpremultiplies MuPDF's RGBA; using raw samples darkens edges.
        image = Image.open(io.BytesIO(pixmap.tobytes("png"))).convert("RGBA")
        bounds = image.getbbox()
        require(bounds is not None, "Empty source crop")
        require(bounds[0] > 0 and bounds[1] > 0
                and bounds[2] < image.width and bounds[3] < image.height,
                "Source crop clips artwork")
        return image.crop(bounds), list(matrix)


def visible_mask(image):
    return image.getchannel("A").point(lambda value: 255 if value >= 128 else 0)


def make_frames(pose, config, size, count):
    scale = 0.92 * size / 160
    pose = pose.resize((round(pose.width * scale), round(pose.height * scale)),
                       Image.Resampling.LANCZOS)
    extent = size * SUPERSAMPLE
    baseline = round(size * 0.92)
    pivot = (extent / 2, (baseline + 1) * SUPERSAMPLE)
    base = Image.new("RGBA", (extent, extent))
    base.alpha_composite(pose, (round((extent - pose.width) / 2),
                               round(pivot[1] - pose.height)))
    frames = []
    for index in range(count):
        phase = 2 * math.pi * index / count
        angle = config["offset"] + config["degrees"] * math.sin(phase)
        rotated = base.rotate(angle, Image.Resampling.BICUBIC, center=pivot)
        frame = rotated.resize((size, size), Image.Resampling.LANCZOS)
        bounds = visible_mask(frame).getbbox()
        require(bounds is not None, "Empty animation frame")
        # GIF uses binary alpha: align the actual opaque sole, not filter fringe.
        shift = baseline + 1 - bounds[3]
        aligned = Image.new("RGBA", (size, size))
        aligned.alpha_composite(frame, (0, shift))
        frames.append(aligned)
    return frames


def encode_gifs(animations, destination, size):
    # One palette for every state/frame; black inside the head stays OPAQUE.
    frames = [frame for animation in animations.values() for frame in animation]
    atlas = Image.new("RGB", (size, size * len(frames)))
    for index, frame in enumerate(frames):
        atlas.paste(frame.convert("RGB"), (0, index * size))
    indexed = atlas.quantize(colors=255, method=Image.Quantize.MEDIANCUT,
                             dither=Image.Dither.NONE)
    palette = [0, 0, 0] + indexed.getpalette()[:765]
    destination.mkdir(parents=True, exist_ok=True)
    cursor = 0
    for name, animation in animations.items():
        encoded = []
        for frame in animation:
            pixels = indexed.crop((0, cursor * size, size, (cursor + 1) * size)).tobytes()
            mask = visible_mask(frame).tobytes()
            require(max(pixels) < 255, "Palette overflow")
            output = Image.frombytes("P", (size, size),
                                     bytes(value + 1 if alpha else 0
                                           for value, alpha in zip(pixels, mask)))
            output.putpalette(palette)
            encoded.append(output)
            cursor += 1
        encoded[0].save(destination / f"{name}.gif", save_all=True,
                        append_images=encoded[1:], duration=CLIPS[name]["duration_ms"],
                        loop=0, transparency=0, background=0, disposal=2, optimize=False)


def validate_gif(path, size, expected_duration):
    bounds, durations, decoded = [], [], []
    with Image.open(path) as image:
        require(image.size == (size, size), f"Wrong canvas: {path.name}")
        require(image.n_frames >= 6, f"Too few animation frames: {path.name}")
        require(image.info.get("loop") == 0, f"Not an infinite loop: {path.name}")
        for index in range(image.n_frames):
            image.seek(index)
            frame = image.convert("RGBA")
            alpha = frame.getchannel("A")
            require(set(alpha.get_flattened_data()) == {0, 255}, f"Wrong transparency: {path.name}")
            box = alpha.getbbox()
            require(box and min(box[:2]) >= 4 and max(box[2:]) <= size - 4,
                    f"Clipped artwork: {path.name} frame {index}")
            require(box[3] == round(size * 0.92) + 1, f"Unstable baseline: {path.name}")
            colors = frame.get_flattened_data()
            require(sum(a == 255 and r < 20 and g < 20 and b < 20
                        for r, g, b, a in colors) > size, f"Lost black head: {path.name}")
            require(sum(a == 255 and min(r, g, b) > 220
                        for r, g, b, a in colors) > 10, f"Lost original face: {path.name}")
            bounds.append(list(box))
            durations.append(image.info["duration"])
            decoded.append(frame.copy())
    require(sum(durations) == expected_duration, f"Changed loop duration: {path.name}")
    differences = [sum(ImageChops.difference(decoded[i - 1], decoded[i]).convert("RGB")
                       .resize((1, 1), Image.Resampling.BOX).getpixel((0, 0)))
                   for i in range(len(decoded))]
    require(differences[0] <= max(differences[1:]) + 3, f"Abrupt loop seam: {path.name}")
    return {
        "frames": len(decoded), "dimensions": [size, size], "bytes": path.stat().st_size,
        "frame_durations_ms": durations, "loop_duration_ms": sum(durations),
        "bounds_per_frame": bounds, "baseline_y": round(size * 0.92),
        "sha256": sha256(path), "transparency": "index 0; disposal 2; infinite loop",
    }


def checker(size, dark=False):
    image = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(image)
    colors = ("#25282e", "#30343b") if dark else ("#eeeeee", "#ffffff")
    for y in range(0, size, 10):
        for x in range(0, size, 10):
            draw.rectangle((x, y, x + 9, y + 9), fill=colors[(x // 10 + y // 10) % 2])
    return image


def previews(directory, destination, report, size):
    directory.mkdir(parents=True, exist_ok=True)
    strip = Image.new("RGB", (size * 8 + 120, (size + 30) * len(CLIPS)), "#40444c")
    draw = ImageDraw.Draw(strip)
    for row, name in enumerate(CLIPS):
        with Image.open(destination / f"{name}.gif") as gif:
            frames = []
            for index in range(gif.n_frames):
                gif.seek(index)
                frames.append(gif.convert("RGBA"))
        draw.text((10, row * (size + 30) + 15), name, fill="white")
        for col in range(8):
            frame = frames[round(col * (len(frames) - 1) / 7)]
            tile = checker(size, dark=col % 2 == 0)
            tile.paste(frame, (0, 0), frame)
            strip.paste(tile, (120 + col * size, row * (size + 30) + 25))
    strip.save(directory / "contact-sheet.png")
    # Embed the actual exported GIFs, each keeping its independent loop timing.
    tiles = []
    for name in CLIPS:
        data = base64.b64encode((destination / f"{name}.gif").read_bytes()).decode("ascii")
        tiles.append(f'<figure><figcaption>{name}</figcaption>'
                     f'<img width="{size}" height="{size}" alt="{name}" '
                     f'src="data:image/gif;base64,{data}"></figure>')
    row = "\n".join(tiles)
    html = f'''<!doctype html>
<html lang="en"><meta charset="utf-8"><title>Runzo original-art pose loops</title>
<style>
body {{ background: #40444c; color: white; font: 16px system-ui; margin: 24px; }}
section {{ display: flex; gap: 16px; padding: 16px; margin: 16px 0; width: max-content; }}
figure {{ margin: 0; text-align: center; }}
figcaption {{ padding: 8px; }}
img {{ display: block; background: repeating-conic-gradient(#25282e 0% 25%, #30343b 0% 50%) 0 / 20px 20px; }}
.light {{ background: #eee; color: #222; }}
.light img {{ background: repeating-conic-gradient(#fff 0% 25%, #eee 0% 50%) 0 / 20px 20px; }}
</style>
<h3>Runzo: supplied vector poses + original facial expressions</h3>
<p>Actual {size} × {size} GIFs, native size. Gentle pose-rocking, not official animation.</p>
<section>{row}</section><section class="light">{row}</section>
</html>
'''
    (directory / "preview.html").write_text(html, encoding="utf-8")
    (directory / "metadata.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source_dir", type=Path, help="Directory containing the three supplied documents")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "src/main/resources/pets/runzo")
    parser.add_argument("--preview-dir", type=Path, help="Optional scratch-only previews/metadata directory")
    parser.add_argument("--size", type=int, choices=(128, 160), default=128)
    parser.add_argument("--frames", type=int, default=12, help="Frames per sinusoidal loop (at least 8)")
    args = parser.parse_args()
    require(args.frames >= 8, "At least eight frames are required")
    paths = {name: args.source_dir / name for name in (POSES, FACES, GUIDE)}
    require(all(path.is_file() for path in paths.values()), "Source directory is missing a supplied document")
    # Guard against accidentally using an input location as an output directory.
    require(args.output_dir.resolve() != args.source_dir.resolve(), "Output must not be the input directory")
    require(args.preview_dir is None or args.preview_dir.resolve() != args.source_dir.resolve(),
            "Previews must not be written to the input directory")
    hashes = {name: sha256(path) for name, path in paths.items()}
    pose_bytes = paths[POSES].read_bytes()
    with fitz.open(stream=pose_bytes, filetype="pdf") as poses, fitz.open(paths[FACES]) as faces, fitz.open(paths[GUIDE]) as guide:
        pose_paths, face_paths = poses[0].get_drawings(), faces[0].get_drawings()
        require(len(pose_paths) == 114 and len(face_paths) == 67,
                "Unrecognized artwork revision; crop/path mapping must be reviewed")
        require(max(abs(a - b) for a, b in zip(pose_paths[0]["rect"], poses[0].rect)) < 0.01
                and pose_paths[0]["fill"] == (0, 0, 0),
                "Expected single full-page black artboard")
        report = {
            "generator": "tools/prepare_runzo.py", "pillow": PIL.__version__, "pymupdf": fitz.VersionBind,
            "sources": {name: {"sha256": hashes[name], "pdf_metadata": doc.metadata}
                        for name, doc in ((POSES, poses), (FACES, faces), (GUIDE, guide))},
            "guide_pages": [{"page": page.number + 1, "text": page.get_text().strip()} for page in guide],
            "provenance_notes": [
                "The supplied guide calls the character Runee; resource naming follows the requested Runzo identifier.",
                "No license or redistribution grant was found in the supplied guide text or PDF metadata. This is not a license determination.",
                "Original vector poses have blank faces; original facial paths are rigidly registered from the separate face sheet.",
                "Animations are new gentle rigid-pose rocking loops, not supplied or claimed official animation cycles.",
                "No limb stretching, mirroring, substituted mascot, artboard, pose labels, or guidelines are used.",
                "Walk uses a supplied high-step pose; run uses the supplied curled-rear-leg pose, not full alternating-foot gait cycles.",
                "The walk head is already tilted 10 degrees; animation only reduces that tilt by up to 2 degrees.",
                "GIF has binary transparency; opaque black head interiors are intentional original artwork.",
            ],
            "common_pdf_point_scale": 0.92 * args.size / 160, "clips": {},
        }
        animations = {}
        for name, config in CLIPS.items():
            pose, matrix = extract_pose(pose_bytes, face_paths, pose_paths, config)
            animations[name] = make_frames(pose, config, args.size, args.frames)
            report["clips"][name] = {**config, "face_source": EXPRESSIONS[config["expression"]],
                                     "face_registration_matrix": matrix}
    encode_gifs(animations, args.output_dir, args.size)
    for name, config in CLIPS.items():
        result = validate_gif(args.output_dir / f"{name}.gif", args.size,
                              args.frames * config["duration_ms"])
        report["clips"][name].update(result)
        print(f"{name}.gif: {result['frames']} frames, {args.size}x{args.size}, {result['bytes']} bytes, "
              f"{result['loop_duration_ms']} ms, validation passed")
    require(hashes == {name: sha256(path) for name, path in paths.items()}, "An input document changed")
    if args.preview_dir is not None:
        previews(args.preview_dir, args.output_dir, report, args.size)
        print(f"Previews and provenance metadata: {args.preview_dir}")


if __name__ == "__main__":
    main()