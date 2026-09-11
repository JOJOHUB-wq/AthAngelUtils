from PIL import Image, ImageDraw, ImageFont
import numpy as np, random, json
rng = random.Random(99)
FNT = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf', 22)
TILECOLS = [(200,40,40),(0,150,150),(0,150,130),(100,140,100),(60,60,130),(200,120,30),(150,40,120)]
LINECOLS = [(30,30,30),(120,30,120),(30,120,120),(200,200,30),(200,60,120),(90,90,220),(30,120,60)]
CHARS='0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
FW,FH = 160,104
def glyph(ch, tile):
    g = Image.new('L', tile, 0); d = ImageDraw.Draw(g)
    d.text((3, 2), ch, font=FNT, fill=255)
    for _ in range(rng.randint(3,9)):
        x,y = rng.randint(0,tile[0]-1), rng.randint(0,tile[1]-1)
        r = rng.randint(1,3)
        d.ellipse([x-r,y-r,x+r,y+r], fill=0)
    return g
def captcha_quad():
    W,H = 128,128
    img = Image.new('RGB',(W,H),(255,255,255)); d = ImageDraw.Draw(img)
    text = ''.join(rng.choice(CHARS) for _ in range(5))
    for i,ch in enumerate(text):
        tw,th = rng.randint(23,27), rng.randint(26,32)
        tile = Image.new('RGB',(tw,th), rng.choice(TILECOLS))
        tile.paste(Image.new('RGB',(tw,th),(255,255,255)), mask=glyph(ch,(tw,th)))
        rot = tile.rotate(rng.uniform(-14,14), resample=Image.BILINEAR, expand=True)
        preench = np.array(rot).sum(axis=2) > 30
        x = 1+i*25+rng.randint(-3,3); y = 47+rng.randint(-6,6)
        img.paste(rot,(x,y),Image.fromarray((preench*255).astype(np.uint8)))
    for _ in range(rng.randint(4,7)):
        col = rng.choice(LINECOLS); w = rng.randint(1,2)
        x0,y0 = rng.randint(0,W),rng.randint(0,H)
        pts=[(x0,y0)]
        for _ in range(3):
            pts.append((rng.randint(0,W),rng.randint(0,H)))
        d.line(pts, fill=col, width=w, joint='curve')
    for _ in range(rng.randint(2,4)):
        col = rng.choice(LINECOLS)
        x0,y0 = rng.randint(0,W),rng.randint(0,H); r0=rng.randint(6,14)
        d.ellipse([x0-r0,y0-r0,x0+r0,y0+r0], outline=col, width=rng.randint(1,2))
    return img, text
def sky():
    img = Image.new('RGB',(FW,FH))
    px = np.zeros((FH,FW,3),np.uint8)
    top = np.array([150,200,245]); bot = np.array([220,235,250])
    for y in range(FH):
        px[y,:,:] = (top*(1-y/FH)+bot*(y/FH)).astype(np.uint8)
    for _ in range(rng.randint(3,7)):
        x0,y0 = rng.randint(0,FW),rng.randint(0,FH); rx,ry = rng.randint(15,45),rng.randint(6,14)
        yy,xx = np.ogrid[:FH,:FW]
        m = ((xx-x0)/rx)**2+((yy-y0)/ry)**2 < 1
        px[m] = [250,250,252]
    return Image.fromarray(px)
def find_coeffs(t, s):
    import numpy as np
    m = []
    for (x1,y1),(x2,y2) in zip(s,t):
        m.append([x1,y1,1,0,0,0,-x2*x1,-x2*y1])
        m.append([0,0,0,x1,y1,1,-y2*x1,-y2*y1])
    A = np.array(m); B = np.array(t).reshape(8)
    return np.linalg.solve(A,B)
def scene():
    bg = sky()
    q,_t = captcha_quad(), None
    quad, text = q
    sc = rng.uniform(0.85, 1.0)
    qw,qh = int(128*sc), int(128*sc)
    quad = quad.resize((qw,qh), Image.BILINEAR)
    j = 6
    src = [(0,0),(qw,0),(qw,qh),(0,qh)]
    dst = [(rng.uniform(-j,j),rng.uniform(-j,j)),(qw+rng.uniform(-j,j),rng.uniform(-j,j)),
           (qw+rng.uniform(-j,j),qh+rng.uniform(-j,j)),(rng.uniform(-j,j),qh+rng.uniform(-j,j))]
    quad = quad.transform((qw,qh), Image.Transform.PERSPECTIVE, find_coeffs(dst,src), Image.BICUBIC)
    ox = (FW-qw)//2 + rng.randint(-5,5); oy = (FH-qh)//2 + rng.randint(-4,4)
    bg.paste(quad, (ox,oy))
    g = np.array(bg.convert('L')).astype(np.float32)/255.0
    return g, text
# strips: 5 zones, width 44 at 160 scale -> features from 32x32 downscale
ZX = [12+i*27 for i in range(5)]  # centers approx (map ~ centered)
def strips(g):
    outs=[]
    for cx in ZX:
        x0 = max(0, int(cx-28)); x1 = min(FW, int(cx+28))
        s = Image.fromarray((g*255).astype(np.uint8)).crop((x0,0,x1,FH)).resize((32,32), Image.BILINEAR)
        outs.append((np.array(s).astype(np.float32)/255.0).reshape(-1))
    return outs
N = 12000
Xs = [[] for _ in range(5)]; Ys = [[] for _ in range(5)]
for n in range(N):
    g,t = scene()
    ss = strips(g)
    for i in range(5):
        Xs[i].append(ss[i]); Ys[i].append(CHARS.index(t[i]))
    if (n+1)%3000==0: print('gen',n+1, flush=True)
for i in range(5):
    np.save(f'capwork/Xh{i}.npy', np.array(Xs[i])); np.save(f'capwork/Yh{i}.npy', np.array(Ys[i]))
print('saved')
