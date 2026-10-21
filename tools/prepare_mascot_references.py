#!/usr/bin/env python3
"""Record independently decoded GIF frames for the JVM compositor regression test.

Requires Pillow. Run only after intentionally changing and reviewing mascot art:
    python tools/prepare_mascot_references.py
"""

import hashlib
from pathlib import Path

import PIL
from PIL import Image, ImageSequence


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources/pets"
DESTINATION = ROOT / "src/test/resources/pets/mascot-frames.properties"


def main():
    lines = [
        "# Independent Pillow-composited reference frames; ARGB, transparent RGB normalized to zero.",
        f"# Generated with Pillow {PIL.__version__}; regenerate only after reviewing artwork changes.",
    ]
    for kind in ("kodee", "runzo", "clippy"):
        for animation in ("idle", "walk", "run", "waiting", "done"):
            with Image.open(RESOURCES / kind / f"{animation}.gif") as image:
                for index, frame in enumerate(ImageSequence.Iterator(image)):
                    pixels = frame.convert("RGBA").get_flattened_data()
                    data = bytes(channel for r, g, b, a in pixels
                                 for channel in ((a, r, g, b) if a else (0, 0, 0, 0)))
                    lines.append(f"{kind}.{animation}.{index}={hashlib.sha256(data).hexdigest()}")
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    DESTINATION.write_text("\n".join(lines) + "\n", encoding="ascii")
    print(f"Recorded {len(lines) - 2} reference frames in {DESTINATION.relative_to(ROOT)}")


if __name__ == "__main__":
    main()