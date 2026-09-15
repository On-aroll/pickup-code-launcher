# -*- coding: utf-8 -*-
"""Render a demo image: real input screenshot -> segmentation boxes -> OCR result."""
import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import torch

HERE = os.path.dirname(os.path.abspath(__file__))
import verify_pipeline as vp

OUT = os.path.join(HERE, "ocr-demo.png")
random.seed(42)
np.random.seed(42)


def main():
    model = vp.TinyCNN()
    model.load_state_dict(torch.load(os.path.join(HERE, "models", "pickup_cnn.pt"), map_location="cpu"))
    model.eval()

    code = "836204"
    binary = vp.render_string(code)
    boxes = vp.segment_digits(binary)
    chars = []
    confs = []
    for box in boxes:
        vec = vp.to_28x28(binary, box)
        with torch.no_grad():
            logits = model(torch.from_numpy(vec).unsqueeze(0).unsqueeze(0))
            probs = torch.softmax(logits, dim=1)[0]
            p, idx = probs.max(dim=0)
            chars.append(str(int(idx.item())))
            confs.append(float(p.item()))
    predicted = "".join(chars)
    print("truth:", code, "pred:", predicted, "conf:", [round(c, 3) for c in confs])

    # build annotated canvas
    h, w = binary.shape
    scale = 3
    canvas = Image.new("RGB", (w * scale + 40, h * scale + 220), (245, 247, 250))
    d = ImageDraw.Draw(canvas)
    d.rectangle([10, 10, 10 + w * scale, 10 + h * scale], fill=(255, 255, 255), outline=(200, 205, 210))
    canvas.paste(Image.fromarray(binary).resize((w * scale, h * scale)), (10, 10))
    for (top, x0, x1, bottom) in boxes:
        d.rectangle([10 + x0 * scale, 10 + top * scale, 10 + x1 * scale, 10 + bottom * scale],
                    outline=(255, 80, 60), width=3)
    d.text((14, 10 + h * scale + 12), "输入：取件码截图", fill=(70, 75, 85), font=ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 26))
    d.text((14, 10 + h * scale + 52), "分割：红色框 = 连通域分割结果（6 个字符）", fill=(70, 75, 85), font=ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 26))
    d.text((14, 10 + h * scale + 92), "识别：" + predicted + "   置信度 " + " ".join(f"{c:.2f}" for c in confs),
           fill=(20, 120, 60), font=ImageFont.truetype("C:/Windows/Fonts/msyhbd.ttc", 34))
    canvas.save(OUT)
    print("saved ->", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
