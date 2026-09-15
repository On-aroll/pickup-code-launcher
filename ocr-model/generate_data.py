# -*- coding: utf-8 -*-
"""Generate synthetic single-digit training data for pickup-code OCR.

Digits are rendered with multiple system fonts, random size/thickness,
background noise, blur and slight geometric jitter, then binarized to
28x28 with the same pipeline the on-device app will use.
"""
import io
import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
TRAIN_DIR = os.path.join(OUT_DIR, "train")
VAL_DIR = os.path.join(OUT_DIR, "val")

FONT_CANDIDATES = [
    "C:/Windows/Fonts/arial.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "C:/Windows/Fonts/consola.ttf",
    "C:/Windows/Fonts/tahomabd.ttf",
    "C:/Windows/Fonts/calibri.ttf",
    "C:/Windows/Fonts/verdanab.ttf",
    "C:/Windows/Fonts/segoeui.ttf",
    "C:/Windows/Fonts/simhei.ttf",
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/times.ttf",
]

DIGITS = [str(d) for d in range(10)]


def pick_font(size):
    path = random.choice([f for f in FONT_CANDIDATES if os.path.exists(f)])
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()


def render_digit(ch, size=32):
    font = pick_font(size)
    img = Image.new("L", (size * 2, size * 2), 255)
    d = ImageDraw.Draw(img)
    bbox = d.textbbox((0, 0), ch, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = (img.width - w) // 2 - bbox[0]
    y = (img.height - h) // 2 - bbox[1]
    d.text((x, y), ch, fill=0, font=font)
    return img


def degrade(img):
    """Add realistic variation: rotation, blur, noise, stroke thickness."""
    angle = random.uniform(-12, 12)
    img = img.rotate(angle, resample=Image.BICUBIC, fillcolor=255)
    if random.random() < 0.7:
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 1.1)))
    arr = np.asarray(img, dtype=np.float32)
    if random.random() < 0.4:
        # simulate uneven lighting / threshold noise
        arr = arr + np.random.normal(0, random.uniform(8, 22), arr.shape)
    if random.random() < 0.25:
        # salt & pepper
        mask = np.random.rand(*arr.shape) < 0.004
        arr[mask] = 0
        mask = np.random.rand(*arr.shape) < 0.004
        arr[mask] = 255
    arr = np.clip(arr, 0, 255)
    # binarize like on-device: adaptive-ish global threshold
    arr = np.where(arr < random.uniform(100, 155), 0, 255).astype(np.uint8)
    img = Image.fromarray(arr)
    return img


def center_crop_28(img):
    """Tight-crop bounding box, pad to square, resize to 28x28.

    Matches the tight crops produced by the on-device segmenter, so
    train/inference share the same input distribution.
    """
    arr = np.asarray(img, dtype=np.uint8)
    ys, xs = np.where(arr < 128)
    if len(xs) == 0:
        return np.zeros((28, 28), dtype=np.float32)
    x0, x1 = xs.min(), xs.max()
    y0, y1 = ys.min(), ys.max()
    w = x1 - x0
    h = y1 - y0
    side = max(w, h) + 1
    cx = (x0 + x1) // 2
    cy = (y0 + y1) // 2
    half = side // 2
    crop = img.crop((max(0, cx - half), max(0, cy - half),
                     min(img.width, cx + half), min(img.height, cy + half)))
    crop = crop.resize((28, 28), Image.BILINEAR)
    out = np.asarray(crop, dtype=np.float32)
    out = (255.0 - out) / 255.0  # invert: digit = 1, bg = 0
    return out


def make_sample(ch):
    img = render_digit(ch)
    img = degrade(img)
    return center_crop_28(img)


def write_binary(samples, labels, path):
    """Write npy so training can reload fast."""
    np.savez_compressed(path, x=np.stack(samples), y=np.array(labels))


def main():
    random.seed(20260915)
    np.random.seed(20260915)
    os.makedirs(TRAIN_DIR, exist_ok=True)
    os.makedirs(VAL_DIR, exist_ok=True)

    per_class_train = 6000
    per_class_val = 800

    for d in DIGITS:
        xs, ys = [], []
        for _ in range(per_class_train):
            xs.append(make_sample(d))
            ys.append(int(d))
        write_binary(xs, ys, os.path.join(TRAIN_DIR, f"class_{d}.npz"))

        xs, ys = [], []
        for _ in range(per_class_val):
            xs.append(make_sample(d))
            ys.append(int(d))
        write_binary(xs, ys, os.path.join(VAL_DIR, f"class_{d}.npz"))

    total = per_class_train * 10
    print(f"OK generated {total} train samples + {per_class_val * 10} val samples -> {OUT_DIR}")


if __name__ == "__main__":
    main()
