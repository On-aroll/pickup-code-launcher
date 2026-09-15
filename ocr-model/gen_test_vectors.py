# -*- coding: utf-8 -*-
"""Generate JVM test vectors for TinyCnn: real pipeline char images + PyTorch outputs.

Binary layout: int count, then per sample: 784 float32 input, 10 float32 probs, 1 byte argmax.
"""
import os
import struct
import random
import numpy as np
import torch

HERE = os.path.dirname(os.path.abspath(__file__))
import verify_pipeline as vp

OUT = os.path.join(HERE, "..", "app", "src", "test", "resources", "test_vectors.bin")
N = 200

random.seed(7)
np.random.seed(7)

model = vp.TinyCNN()
model.load_state_dict(torch.load(os.path.join(HERE, "models", "pickup_cnn.pt"), map_location="cpu"))
model.eval()

os.makedirs(os.path.dirname(OUT), exist_ok=True)
total = 0
with open(OUT, "wb") as f:
    f.write(struct.pack(">i", N))
    for _ in range(N):
        code = "".join(random.choice("0123456789") for _ in range(random.randint(4, 8)))
        binary = vp.render_string(code)
        boxes = vp.segment_digits(binary)
        for box in boxes:
            vec = vp.to_28x28(binary, box)  # [28][28] float32
            with torch.no_grad():
                logits = model(torch.from_numpy(vec).unsqueeze(0).unsqueeze(0))
                probs = torch.softmax(logits, dim=1)[0].numpy().astype(np.float32)
            f.write(np.asarray(vec, dtype=">f4").tobytes())
            f.write(np.asarray(probs, dtype=">f4").tobytes())
            f.write(struct.pack(">B", int(probs.argmax())))
            total += 1
    print(f"wrote {total} char samples -> {OUT}")
