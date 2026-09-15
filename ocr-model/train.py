# -*- coding: utf-8 -*-
"""Train a tiny CNN for single-digit pickup-code recognition.

Architecture kept deliberately small so it can be re-implemented as a
zero-dependency Java inference kernel on Android:
  conv3x3(8) -> relu -> maxpool2 -> conv3x3(16) -> relu -> maxpool2
  -> fc(128) -> relu -> fc(10)
Input: 28x28 grayscale (digit=1, bg=0). Output: 10-class softmax.
"""
import os
import glob
import numpy as np
import torch
import torch.nn as nn

HERE = os.path.dirname(os.path.abspath(__file__))
TRAIN_DIR = os.path.join(HERE, "data", "train")
VAL_DIR = os.path.join(HERE, "data", "val")
MODEL_DIR = os.path.join(HERE, "models")
os.makedirs(MODEL_DIR, exist_ok=True)


class TinyCNN(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(1, 8, 3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),              # 14x14
            nn.Conv2d(8, 16, 3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),              # 7x7
            nn.Flatten(),
            nn.Linear(16 * 7 * 7, 128),
            nn.ReLU(),
            nn.Linear(128, 10),
        )

    def forward(self, x):
        return self.net(x)


def load_set(directory):
    xs, ys = [], []
    for path in sorted(glob.glob(os.path.join(directory, "*.npz"))):
        data = np.load(path)
        xs.append(data["x"])
        ys.append(data["y"])
    x = np.concatenate(xs).astype(np.float32)
    y = np.concatenate(ys).astype(np.int64)
    return x, y


def evaluate(model, x, y, device):
    model.eval()
    with torch.no_grad():
        xb = torch.from_numpy(x).unsqueeze(1).to(device)
        pred = model(xb).argmax(dim=1).cpu().numpy()
    return (pred == y).mean()


def main():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print("device:", device)

    x_tr, y_tr = load_set(TRAIN_DIR)
    x_va, y_va = load_set(VAL_DIR)
    print(f"train {x_tr.shape} val {x_va.shape}")

    model = TinyCNN().to(device)
    opt = torch.optim.Adam(model.parameters(), lr=1e-3)
    loss_fn = nn.CrossEntropyLoss()

    batch = 256
    n = x_tr.shape[0]
    epochs = 12
    for epoch in range(1, epochs + 1):
        model.train()
        perm = np.random.permutation(n)
        total_loss = 0.0
        steps = 0
        for i in range(0, n, batch):
            idx = perm[i:i + batch]
            xb = torch.from_numpy(x_tr[idx]).unsqueeze(1).to(device)
            yb = torch.from_numpy(y_tr[idx]).to(device)
            opt.zero_grad()
            out = model(xb)
            loss = loss_fn(out, yb)
            loss.backward()
            opt.step()
            total_loss += loss.item()
            steps += 1
        acc = evaluate(model, x_va, y_va, device)
        print(f"epoch {epoch:02d} loss {total_loss / steps:.4f} val_acc {acc * 100:.2f}%")

    acc = evaluate(model, x_va, y_va, device)
    print(f"FINAL val_acc {acc * 100:.2f}%")

    # export weights as float32 npy for the Java inference kernel
    state = {k: v.detach().cpu().numpy() for k, v in model.state_dict().items()}
    np.savez_compressed(os.path.join(MODEL_DIR, "pickup_cnn_weights.npz"), **state)
    torch.save(model.state_dict(), os.path.join(MODEL_DIR, "pickup_cnn.pt"))
    print("weights ->", MODEL_DIR)


if __name__ == "__main__":
    main()
