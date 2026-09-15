# -*- coding: utf-8 -*-
"""Debug segmentation: check segment count vs truth, save sample crops."""
import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
import verify_pipeline as vp  # reuse render/segment

OUT = os.path.join(HERE, "debug")
os.makedirs(OUT, exist_ok=True)


def main():
    random.seed(7)
    np.random.seed(7)
    correct_seg = 0
    total = 0
    hist = {}
    for i in range(300):
        length = random.randint(4, 8)
        code = "".join(random.choice("0123456789") for _ in range(length))
        binary = vp.render_string(code)
        boxes = vp.segment_digits(binary)
        total += 1
        ok = len(boxes) == length
        correct_seg += ok
        key = f"len={length}->{len(boxes)}"
        hist[key] = hist.get(key, 0) + 1
        if i < 12:
            img = Image.fromarray(binary)
            draw = ImageDraw.Draw(img)
            for (top, x0, x1, bottom) in boxes:
                draw.rectangle([x0, top, x1, bottom], outline=0, width=2)
            img.save(os.path.join(OUT, f"seg_{i}_truth_{code}_n{len(boxes)}.png"))
    print(f"segmentation exact-count accuracy: {correct_seg / total * 100:.2f}%")
    for k in sorted(hist):
        print(k, hist[k])


if __name__ == "__main__":
    main()
