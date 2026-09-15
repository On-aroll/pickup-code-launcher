# -*- coding: utf-8 -*-
"""End-to-end pipeline validation: whole pickup-code string -> digits.

Renders full strings (4-8 digits) like a delivery-notice screenshot,
then runs the same preprocess -> segment -> classify pipeline the
on-device app will use, and reports full-string accuracy.
"""
import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import torch
import torch.nn as nn
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(HERE, "models")

FONT_CANDIDATES = [
    "C:/Windows/Fonts/arial.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "C:/Windows/Fonts/consola.ttf",
    "C:/Windows/Fonts/tahomabd.ttf",
    "C:/Windows/Fonts/calibri.ttf",
    "C:/Windows/Fonts/verdanab.ttf",
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
]


class TinyCNN(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(1, 8, 3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(8, 16, 3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Flatten(),
            nn.Linear(16 * 7 * 7, 128),
            nn.ReLU(),
            nn.Linear(128, 10),
        )

    def forward(self, x):
        return self.net(x)


def render_string(code):
    """Render a code string like a screenshot: white bg, dark digits.

    Characters are drawn one by one with a clear gap (as in real delivery
    notices), then blurred/noised and binarized like the on-device path.
    """
    size = random.randint(40, 56)
    font_path = random.choice([f for f in FONT_CANDIDATES if os.path.exists(f)])
    font = ImageFont.truetype(font_path, size)
    spacing = int(size * random.uniform(0.28, 0.45))
    widths = [font.getlength(c) for c in code]
    total_w = int(sum(widths) + spacing * (len(code) - 1))
    img = Image.new("L", (total_w + size, size * 2), 255)
    d = ImageDraw.Draw(img)
    x = size // 2
    for c, w in zip(code, widths):
        bbox = d.textbbox((0, 0), c, font=font)
        y = (img.height - (bbox[3] - bbox[1])) // 2 - bbox[1]
        d.text((x, y), c, fill=0, font=font)
        x += int(w) + spacing
    if random.random() < 0.7:
        img = img.rotate(random.uniform(-2.5, 2.5), resample=Image.BICUBIC, fillcolor=255)
    if random.random() < 0.7:
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.2, 0.8)))
    arr = np.asarray(img, dtype=np.float32)
    if random.random() < 0.4:
        arr = arr + np.random.normal(0, random.uniform(3, 10), arr.shape)
    if random.random() < 0.25:
        mask = np.random.rand(*arr.shape) < 0.002
        arr[mask] = 0
    arr = np.clip(arr, 0, 255)
    arr = ndimage.median_filter(arr, size=3)  # remove salt noise before threshold
    th = random.uniform(110, 160)
    return np.where(arr < th, 0, 255).astype(np.uint8)


def segment_digits(binary):
    """Connected-component segmentation with merge for split glyphs (e.g. '4')."""
    mask = binary < 128
    labels, n = ndimage.label(mask, structure=np.ones((3, 3)))
    if n == 0:
        return []
    boxes = []
    for i in range(1, n + 1):
        ys, xs = np.where(labels == i)
        if len(ys) < 12:  # drop noise blobs
            continue
        boxes.append([int(ys.min()), int(xs.min()), int(xs.max()) + 1, int(ys.max()) + 1])
    boxes.sort(key=lambda b: b[1])  # by x0

    # merge boxes that overlap vertically a lot and sit close horizontally
    # (split strokes of one digit), but keep distinct digits apart
    widths = [b[2] - b[1] for b in boxes]
    med = float(np.median(widths)) if widths else 1.0
    merged = [boxes[0]]
    for b in boxes[1:]:
        y0, x0, x1, y1 = b
        my0, mx0, mx1, my1 = merged[-1]
        overlap = min(y1, my1) - max(y0, my0)
        height = min(y1 - y0, my1 - my0)
        gap = x0 - mx1
        if height > 0 and overlap / height > 0.5 and gap < 0.55 * med:
            merged[-1] = [min(my0, y0), mx0, max(mx1, x1), max(my1, y1)]
        else:
            merged.append(b)
    return [(b[0], b[1], b[2], b[3]) for b in merged]  # (top, left, right, bottom)


def to_28x28(binary, box):
    top, x0, x1, bottom = box
    crop = binary[top:bottom, x0:x1]
    h, w = crop.shape
    side = max(w, h) + 1
    pad = np.full((side, side), 255, dtype=np.uint8)
    y_off = (side - h) // 2
    x_off = (side - w) // 2
    pad[y_off:y_off + h, x_off:x_off + w] = crop
    img = Image.fromarray(pad).resize((28, 28), Image.BILINEAR)
    out = np.asarray(img, dtype=np.float32)
    return (255.0 - out) / 255.0


def main():
    random.seed(7)
    np.random.seed(7)
    model = TinyCNN()
    model.load_state_dict(torch.load(os.path.join(MODEL_DIR, "pickup_cnn.pt"), map_location="cpu"))
    model.eval()

    n_trials = 2000
    correct_full = 0
    correct_digit = 0
    total_digit = 0
    for _ in range(n_trials):
        length = random.randint(4, 8)
        code = "".join(random.choice("0123456789") for _ in range(length))
        binary = render_string(code)
        boxes = segment_digits(binary)
        if len(boxes) != length:
            correct_digit += 0
            total_digit += length
            continue
        chars = []
        for box in boxes:
            vec = to_28x28(binary, box)
            with torch.no_grad():
                logits = model(torch.from_numpy(vec).unsqueeze(0).unsqueeze(0))
                chars.append(str(int(logits.argmax(dim=1).item())))
        predicted = "".join(chars)
        correct_digit += sum(a == b for a, b in zip(predicted, code))
        total_digit += length
        if predicted == code:
            correct_full += 1

    print(f"trials={n_trials}")
    print(f"full-string accuracy: {correct_full / n_trials * 100:.2f}%")
    print(f"per-digit accuracy:   {correct_digit / total_digit * 100:.2f}%")


if __name__ == "__main__":
    main()
