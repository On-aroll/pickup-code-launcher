# -*- coding: utf-8 -*-
"""Export trained CNN weights to a flat float32 binary for the Java kernel.

Order (must match TinyCnn.java loader):
  conv1.w[8][1][3][3]  conv1.b[8]
  conv2.w[16][8][3][3] conv2.b[16]
  fc1.w[128][784]      fc1.b[128]
  fc2.w[10][128]       fc2.b[10]
"""
import os
import struct
import numpy as np
import torch

HERE = os.path.dirname(os.path.abspath(__file__))
PT = os.path.join(HERE, "models", "pickup_cnn.pt")
BIN = os.path.join(HERE, "..", "app", "src", "main", "assets", "pickup_ocr.bin")

state = torch.load(PT, map_location="cpu")
print("state keys:", list(state.keys()))

blocks = [
    ("net.0.weight", np.float32), ("net.0.bias", np.float32),
    ("net.3.weight", np.float32), ("net.3.bias", np.float32),
    ("net.7.weight", np.float32), ("net.7.bias", np.float32),
    ("net.9.weight", np.float32), ("net.9.bias", np.float32),
]

os.makedirs(os.path.dirname(BIN), exist_ok=True)
count = 0
with open(BIN, "wb") as f:
    for key, _ in blocks:
        arr = state[key].detach().cpu().numpy().astype(np.float32)
        f.write(np.asarray(arr, dtype=">f4").tobytes())  # big-endian: Java DataInputStream
        count += arr.size
        print(f"{key}: shape={arr.shape} bytes={arr.nbytes}")

print(f"total floats={count} bytes={count * 4} -> {BIN}")
