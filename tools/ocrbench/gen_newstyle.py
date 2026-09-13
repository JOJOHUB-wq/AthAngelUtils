#!/usr/bin/env python3
"""Compose a synthetic NEW-style captcha (light tiles, DARK digits) from bundled old
templates (inverted) so we can end-to-end test solve() polarity inversion offline.
Usage: python3 gen_newstyle.py <repo_root> <out_png> <5digits>
"""
import sys, os
import numpy as np
from PIL import Image

def digit_mask(path):
    g = np.array(Image.open(path).convert("L"))
    light = g > 128
    ink = light if light.sum()*2 <= g.size else ~light
    ys, xs = np.nonzero(ink)
    return ink[ys.min():ys.max()+1, xs.min():xs.max()+1]

def main():
    root = sys.argv[1]; out = sys.argv[2]; digits = sys.argv[3]
    res = os.path.join(root, "src/client/resources/assets/athagnelutils/captcha")
    W = H = 128
    img = np.full((H, W, 3), 235, np.uint8)
    rng = np.random.default_rng(3)
    tilecols = [(250,240,240),(240,250,250),(245,245,235),(250,245,240),(240,245,250)]
    for i, ch in enumerate(digits):
        m = digit_mask(os.path.join(res, ch + ".png"))
        th, tw = m.shape
        # scale to ~ (22..26)x(26..30)
        scale = min(24/tw, 28/th)
        nh, nw = int(th*scale), int(tw*scale)
        im = Image.fromarray((m*255).astype(np.uint8)).resize((nw, nh))
        mm = np.array(im) > 127
        x = 8 + i*24; y = 50
        col = tilecols[i]
        # tile bg
        img[y-3:y+nh+3, x-3:x+nw+3] = col
        # dark digit
        for yy in range(nh):
            for xx in range(nw):
                if mm[yy, xx]:
                    img[y+yy, x+xx] = (20, 20, 20)
    # noise lines (dark)
    for _ in range(3):
        x0, y0 = rng.integers(0, W), rng.integers(0, H)
        x1, y1 = rng.integers(0, W), rng.integers(0, H)
        n = 60
        for t in np.linspace(0, 1, n):
            xi = int(x0 + (x1-x0)*t); yi = int(y0 + (y1-y0)*t)
            img[max(0,yi-1):yi+1, max(0,xi-1):xi+1] = (60,60,60)
    Image.fromarray(img).save(out)
    print("wrote", out, "digits", digits)

if __name__ == "__main__":
    main()
