#!/usr/bin/env python3
"""Retrain the captcha digit MLP on the REAL captured font (both polarities) + bundled
templates, with augmentation. Outputs a model in the exact schema MlpOcr.load() expects
(classes,w1,b1,w2,b2) so it can replace src/.../captcha/mlp.json. Tooling only (not in jar).

Usage: python3 train_mlp.py <repo_root> <out_dir>
"""
import sys, os, json, random
import numpy as np
from PIL import Image

TW, TH = 24, 32
rng = random.Random(7)
np.random.seed(7)
CH = "0123456789"
IDX = {c: i for i, c in enumerate(CH)}


def normalize(g):
    h, w = g.shape
    light = g > 128
    ink = light if light.sum() * 2 <= w * h else ~light
    ys, xs = np.nonzero(ink)
    if len(xs) == 0:
        return np.zeros((TH, TW), np.float32)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    gw, gh = int(x1 - x0 + 1), int(y1 - y0 + 1)
    scale = min(TW / gw, TH / gh)
    dw, dh = max(1, round(gw * scale)), max(1, round(gh * scale))
    ox, oy = (TW - dw) // 2, (TH - dh) // 2
    m = np.zeros((TH, TW), np.float32)
    sub = ink[y0:y1 + 1, x0:x1 + 1]
    yy = np.minimum((np.arange(dh) * gh) // dh, gh - 1)
    xx = np.minimum((np.arange(dw) * gw) // dw, gw - 1)
    m[oy:oy + dh, ox:ox + dw] = sub[np.ix_(yy, xx)].astype(np.float32)
    return m


def rot(m, deg):
    if abs(deg) < 1e-6:
        return m
    im = Image.fromarray((m * 255).astype(np.uint8))
    im = im.rotate(deg, resample=Image.NEAREST, expand=False, fillcolor=0)
    return (np.array(im) > 127).astype(np.float32)


def pad(m):
    return np.pad(m > 0.5, 1)


def dilate(m):
    p = pad(m)
    acc = np.zeros(m.shape, bool)
    for dy in (0, 1, 2):
        for dx in (0, 1, 2):
            acc |= p[dy:dy + m.shape[0], dx:dx + m.shape[1]]
    return acc.astype(np.float32)


def erode(m):
    p = pad(m)
    acc = np.ones(m.shape, bool)
    for dy in (0, 1, 2):
        for dx in (0, 1, 2):
            acc &= p[dy:dy + m.shape[0], dx:dx + m.shape[1]]
    return acc.astype(np.float32)


def augment(m):
    out = [m]
    for _ in range(7):
        a = rot(m, rng.uniform(-14, 14))
        r = rng.random()
        if r < 0.3:
            a = dilate(a)
        elif r < 0.6:
            a = erode(a)
        if rng.random() < 0.5:
            for _ in range(rng.randint(2, 14)):
                a[rng.randrange(TH), rng.randrange(TW)] = 1.0
        if rng.random() < 0.3:
            for _ in range(rng.randint(2, 10)):
                a[rng.randrange(TH), rng.randrange(TW)] = 0.0
        out.append(a)
    return out


def collect(root):
    items = []
    base = os.path.join(root, "capwork")
    for sub in ("real", "newcaps", "v17874"):
        d = os.path.join(base, sub)
        for f in os.listdir(d):
            if f.endswith(".png"):
                items.append((os.path.join(d, f), f[0]))
    for f in os.listdir(base):
        if f.startswith("real_") and f.endswith(".png"):
            items.append((os.path.join(base, f), f.split("_")[1]))
    res = os.path.join(root, "src", "client", "resources", "assets", "athagnelutils", "captcha")
    for f in os.listdir(res):
        if f.endswith(".png") and f[0] in IDX:
            items.append((os.path.join(res, f), f[0]))
    return [(p, l) for p, l in items if l in IDX]


def forward(X, W1, b1, W2, b2):
    H = np.maximum(X @ W1 + b1, 0)
    return H, H @ W2 + b2


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    outdir = sys.argv[2] if len(sys.argv) > 2 else "/tmp/newmlp"
    items = collect(root)
    X, Y = [], []
    for path, lab in items:
        try:
            m = normalize(np.array(Image.open(path).convert("L")))
        except Exception:
            continue
        for a in augment(m):
            X.append(a.reshape(-1))
            Y.append(IDX[lab])
    X = np.array(X, np.float32)
    Y = np.array(Y)
    n, d = X.shape
    C = len(CH)
    print("samples:", n, "from", len(items), "crops")

    # held-out test (real crops level): keep 15% of raw crops aside
    H = 96
    W1 = (np.random.randn(d, H) * np.sqrt(2.0 / d)).astype(np.float32)
    b1 = np.zeros(H, np.float32)
    W2 = (np.random.randn(H, C) * np.sqrt(2.0 / H)).astype(np.float32)
    b2 = np.zeros(C, np.float32)

    # Adam
    params = [W1, b1, W2, b2]
    m_ = [np.zeros_like(p) for p in params]
    v_ = [np.zeros_like(p) for p in params]
    t = 0
    bs = 256
    for epoch in range(12):
        perm = np.random.permutation(n)
        Xs, Ys = X[perm], Y[perm]
        for i in range(0, n, bs):
            xb, yb = Xs[i:i + bs], Ys[i:i + bs]
            Hb, Z = forward(xb, W1, b1, W2, b2)
            P = np.exp(Z - Z.max(1, keepdims=True))
            P /= P.sum(1, keepdims=True)
            dZ = P
            dZ[np.arange(len(yb)), yb] -= 1.0
            dZ /= len(yb)
            gW2 = Hb.T @ dZ
            gb2 = dZ.sum(0)
            dH = (dZ @ W2.T) * (Hb > 0)
            gW1 = xb.T @ dH
            gb1 = dH.sum(0)
            grads = [gW1, gb1, gW2, gb2]
            t += 1
            lr = 2e-3
            for k, p in enumerate(params):
                m_[k] = 0.9 * m_[k] + 0.1 * grads[k]
                v_[k] = 0.999 * v_[k] + 0.001 * (grads[k] ** 2)
                mh = m_[k] / (1 - 0.9 ** t)
                vh = v_[k] / (1 - 0.999 ** t)
                p -= lr * mh / (np.sqrt(vh) + 1e-8)
        # train acc
        Hb, Z = forward(X, W1, b1, W2, b2)
        acc = (Z.argmax(1) == Y).mean()
        print(f"epoch {epoch} train_acc={acc:.3f}", flush=True)

    os.makedirs(os.path.join(outdir, "assets", "athagnelutils", "captcha"), exist_ok=True)
    model = {
        "classes": CH,
        "w1": W1.round(4).tolist(),
        "b1": b1.round(4).tolist(),
        "w2": W2.round(4).tolist(),
        "b2": b2.round(4).tolist(),
    }
    with open(os.path.join(outdir, "assets", "athagnelutils", "captcha", "mlp.json"), "w") as f:
        json.dump(model, f, separators=(",", ":"))
    print("wrote", os.path.join(outdir, "assets", "athagnelutils", "captcha", "mlp.json"))


if __name__ == "__main__":
    main()
