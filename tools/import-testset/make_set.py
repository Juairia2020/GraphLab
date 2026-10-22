#!/usr/bin/env python3
"""Makes the synthetic photo test set for GraphLab image import (MS16) and runs the detection prototype on it.

For every image it writes: <name>.jpg (the "photo"), and one entry in detections.json with
  - truth:   the graph that was drawn,
  - circles, segments: what HoughCircles / HoughLinesP found (the same steps the Android detector uses),
  - tokens:  a perfect recogniser (true text at its true place); the Java test adds recogniser mistakes to it;
  - tokensTesseract: what the Tesseract OCR program really read with per-crop reads (ML Kit is not available here).
The Java test ImageSetAccuracyTest reads detections.json, runs GeometryAssembler and reports the accuracy.

usage: make_set.py OUT_DIR
"""
import json, math, os, random, subprocess, sys, tempfile
import cv2
import numpy as np

LONG_SIDE = 1000
# Detector settings: keep identical to ShapeDetector.java in the app.
BLUR = 5
CIRCLE_DP = 1.2
CIRCLE_MIN_DIST = 70
CIRCLE_P1 = 120
CIRCLE_P2 = 34
CIRCLE_MIN_R = 16
CIRCLE_MAX_R = 48
MASK_GROW = 5
HOUGH_THRESHOLD = 45
HOUGH_MIN_LEN = 45
HOUGH_MAX_GAP = 14
BORDER = 14            # lines this close to the photo's edge are shadow or paper edge, not drawing
RING_SAMPLES = 72
LINE_SAMPLES = 40


def detect(img_bgr):
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.medianBlur(gray, BLUR)
    # ink mask that survives uneven light: dark marks on local background
    ink = cv2.adaptiveThreshold(blur, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, 41, 12)
    ink = cv2.morphologyEx(ink, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    raw = cv2.HoughCircles(blur, cv2.HOUGH_GRADIENT, CIRCLE_DP, CIRCLE_MIN_DIST,
                           param1=CIRCLE_P1, param2=CIRCLE_P2, minRadius=CIRCLE_MIN_R, maxRadius=CIRCLE_MAX_R)
    circles = []
    if raw is not None:
        for x, y, r in np.array(raw).reshape(-1, 3)[:, :3]:
            cov = ring_coverage(ink, x, y, r)
            if cov >= 0.45:
                circles.append((float(x), float(y), float(r), min(1.0, cov / 0.85)))
    # remove circles (outline and the name inside) before looking for lines
    lines_src = ink.copy()
    for x, y, r, _ in circles:
        cv2.circle(lines_src, (int(round(x)), int(round(y))), int(r + MASK_GROW), 0, -1)
    edges = cv2.Canny(lines_src, 50, 150)
    raw_l = cv2.HoughLinesP(edges, 1, math.pi / 180, HOUGH_THRESHOLD, minLineLength=HOUGH_MIN_LEN, maxLineGap=HOUGH_MAX_GAP)
    segs = []
    if raw_l is not None:
        for x1, y1, x2, y2 in np.array(raw_l).reshape(-1, 4):
            cov = line_coverage(lines_src, x1, y1, x2, y2)
            h_img, w_img = ink.shape
            on_border = min(x1, x2) < BORDER or max(x1, x2) > w_img - BORDER or min(y1, y2) < BORDER or max(y1, y2) > h_img - BORDER
            if cov >= 0.5 and not on_border:
                ex1, ey1, ex2, ey2 = extend(lines_src, float(x1), float(y1), float(x2), float(y2))
                segs.append((ex1, ey1, ex2, ey2, min(1.0, cov / 0.85)))
    return circles, segs


EXTEND_MAX = 160       # how far an end may be pushed along its own direction
EXTEND_GAP = 8         # a break in the ink up to this long does not stop the push


def extend(ink, x1, y1, x2, y2):
    """Hough often returns only the middle of a line. Walk each end outwards while ink continues along the same direction."""
    length = math.hypot(x2 - x1, y2 - y1)
    if length == 0:
        return x1, y1, x2, y2
    ux, uy = (x2 - x1) / length, (y2 - y1) / length
    h, w = ink.shape

    def walk(px, py, dx, dy):
        last = 0
        gap = 0
        for step in range(1, EXTEND_MAX + 1):
            qx, qy = px + dx * step, py + dy * step
            ok = False
            for off in (-2, -1, 0, 1, 2):
                rx = int(round(qx - dy * off))
                ry = int(round(qy + dx * off))
                if 0 <= rx < w and 0 <= ry < h and ink[ry, rx]:
                    ok = True
                    break
            if ok:
                last = step
                gap = 0
            else:
                gap += 1
                if gap > EXTEND_GAP:
                    break
        return px + dx * last, py + dy * last

    nx2, ny2 = walk(x2, y2, ux, uy)
    nx1, ny1 = walk(x1, y1, -ux, -uy)
    return nx1, ny1, nx2, ny2


def ring_coverage(ink, x, y, r):
    h, w = ink.shape
    hit = 0
    for k in range(RING_SAMPLES):
        a = 2 * math.pi * k / RING_SAMPLES
        ok = False
        for dr in (-3, -2, -1, 0, 1, 2, 3):
            px = int(round(x + (r + dr) * math.cos(a)))
            py = int(round(y + (r + dr) * math.sin(a)))
            if 0 <= px < w and 0 <= py < h and ink[py, px]:
                ok = True
                break
        hit += ok
    return hit / RING_SAMPLES


def line_coverage(ink, x1, y1, x2, y2):
    h, w = ink.shape
    hit = 0
    for k in range(LINE_SAMPLES):
        t = (k + 0.5) / LINE_SAMPLES
        px = x1 + (x2 - x1) * t
        py = y1 + (y2 - y1) * t
        ok = False
        for dx in (-2, -1, 0, 1, 2):
            for dy in (-2, -1, 0, 1, 2):
                qx = int(round(px + dx))
                qy = int(round(py + dy))
                if 0 <= qx < w and 0 <= qy < h and ink[qy, qx]:
                    ok = True
        hit += ok
    return hit / LINE_SAMPLES


PROBE_HALF = 48       # half side of the crop read around a line middle, in pixels
LABEL_SCALE = 3


def flat_gray(img_bgr):
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    bg = cv2.medianBlur(cv2.dilate(gray, np.ones((15, 15), np.uint8)), 31)      # local paper brightness
    return cv2.divide(gray, bg, scale=255)                                      # evens out the light


def read_crop(flat, cx, cy, half, whitelist, psm):
    """OCR of one small square. Returns (text, confidence, bbox centre x, y) or None. Same idea as ML Kit on a cropped bitmap."""
    h, w = flat.shape
    x0, y0 = max(0, int(cx - half)), max(0, int(cy - half))
    x1, y1 = min(w, int(cx + half)), min(h, int(cy + half))
    if x1 - x0 < 10 or y1 - y0 < 10:
        return None
    crop = cv2.resize(flat[y0:y1, x0:x1], None, fx=LABEL_SCALE, fy=LABEL_SCALE, interpolation=cv2.INTER_CUBIC)
    crop = cv2.copyMakeBorder(crop, 20, 20, 20, 20, cv2.BORDER_CONSTANT, value=255)
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "c.png")
        cv2.imwrite(p, crop)
        out = subprocess.run(["tesseract", p, "-", "--psm", str(psm), "tsv"],
                             capture_output=True, text=True).stdout
    words = []
    for line in out.splitlines()[1:]:
        f = line.split("\t")
        if len(f) >= 12 and f[11].strip():
            try:
                conf = float(f[10])
            except ValueError:
                continue
            if conf >= 0:
                bx, by, bw, bh = [int(v) for v in f[6:10]]
                words.append((f[11].strip(), conf / 100.0, x0 + (bx + bw / 2 - 20) / LABEL_SCALE, y0 + (by + bh / 2 - 20) / LABEL_SCALE, bw / LABEL_SCALE, bh / LABEL_SCALE))
    if not words:
        return None
    text = "".join(wd[0] for wd in words)
    conf = min(wd[1] for wd in words)
    return text, conf, sum(wd[2] for wd in words) / len(words), sum(wd[3] for wd in words) / len(words), max(wd[4] for wd in words), words[0][5]


def read_tokens(img_bgr, circles, segs):
    """Names inside the circles, and a number near the middle between every pair of circles (the app only reads the real lines)."""
    flat = flat_gray(img_bgr)
    toks = []
    inner = flat.copy()
    for x, y, r, _ in circles:
        m = np.zeros_like(inner)
        cv2.circle(m, (int(round(x)), int(round(y))), int(r), 255, 9)   # ring painted out
        inner[m > 0] = 255
    for x, y, r, _ in circles:
        got = read_crop(inner, x, y, r * 0.62, "", 10)
        if got:
            t, c, gx, gy, gw, gh = got
            t = "".join(ch for ch in t.upper() if ch.isalnum())
            if not t or len(t) > 2:
                continue          # a name that long is ring or noise, not a label
            toks.append({"text": t, "x": gx - gw / 2, "y": gy - gh / 2, "w": gw, "h": gh, "conf": round(c, 3)})
    num = flat.copy()
    for x1, y1, x2, y2, _ in segs:
        cv2.line(num, (int(x1), int(y1)), (int(x2), int(y2)), 255, 7)
    for x, y, r, _ in circles:
        cv2.circle(num, (int(round(x)), int(round(y))), int(r + 5), 255, -1)
    for i in range(len(circles)):
        for j in range(i + 1, len(circles)):
            mx = (circles[i][0] + circles[j][0]) / 2
            my = (circles[i][1] + circles[j][1]) / 2
            got = read_crop(num, mx, my, PROBE_HALF, "", 7)
            if got:
                t, c, gx, gy, gw, gh = got
                toks.append({"text": t, "x": gx - gw / 2, "y": gy - gh / 2, "w": gw, "h": gh, "conf": round(c, 3)})
    return toks



def perfect_tokens(names, pos, weights, wpos):
    """What a perfect recogniser would report: the true text at its true place. The Java test adds mistakes to it."""
    toks = []
    for text, (x, y) in [(names[i], pos[i]) for i in range(len(names))] + [(str(w), p) for w, p in zip(weights, wpos)]:
        w = 14.0 * len(text) + 2
        toks.append({"text": text, "x": x - w / 2, "y": y - 11, "w": w, "h": 22.0, "conf": 0.95})
    return toks


# ---------- drawing ----------

FONTS = [cv2.FONT_HERSHEY_SIMPLEX, cv2.FONT_HERSHEY_DUPLEX, cv2.FONT_HERSHEY_COMPLEX, cv2.FONT_HERSHEY_TRIPLEX,
         cv2.FONT_HERSHEY_SCRIPT_COMPLEX]


def make_graph(rnd, n, weighted, W, H, numbered):
    pos = []
    for _ in range(n):
        for _ in range(500):
            x, y = rnd.uniform(80, W - 80), rnd.uniform(80, H - 80)
            if all(math.hypot(x - a, y - b) >= 170 for a, b in pos):
                pos.append((x, y))
                break
        else:
            return None
    r = [rnd.randint(26, 34) for _ in range(n)]
    pairs = [(i, j) for i in range(n) for j in range(i + 1, n)]
    rnd.shuffle(pairs)
    edges, mids = [], []
    want = n - 1 + rnd.randint(0, n // 2)
    for a, b in pairs:
        if len(edges) >= want:
            break
        dx, dy = pos[b][0] - pos[a][0], pos[b][1] - pos[a][1]
        d = math.hypot(dx, dy)
        ok = True
        for k in range(n):
            if k in (a, b):
                continue
            t = ((pos[k][0] - pos[a][0]) * dx + (pos[k][1] - pos[a][1]) * dy) / (d * d)
            px, py = pos[a][0] + t * dx, pos[a][1] + t * dy
            if -0.2 < t < 1.2 and math.hypot(pos[k][0] - px, pos[k][1] - py) < r[k] + 30:
                ok = False
        mx, my = pos[a][0] + dx / 2, pos[a][1] + dy / 2
        for (c, e) in edges:
            for shared in (a, b):
                if shared in (c, e):
                    o = e if shared == c else c
                    other = b if shared == a else a
                    v1 = (pos[other][0] - pos[shared][0], pos[other][1] - pos[shared][1])
                    v2 = (pos[o][0] - pos[shared][0], pos[o][1] - pos[shared][1])
                    ang = math.degrees(abs(math.atan2(v1[0] * v2[1] - v1[1] * v2[0], v1[0] * v2[0] + v1[1] * v2[1])))
                    if ang < 28:
                        ok = False
        if any(math.hypot(mx - m[0], my - m[1]) < 90 for m in mids):
            ok = False
        if any(math.hypot(mx - pos[k][0], my - pos[k][1]) < r[k] + 45 for k in range(n)):
            ok = False
        if ok:
            edges.append((a, b))
            mids.append((mx, my))
    touched = {v for e in edges for v in e}
    if len(touched) < n:
        return None
    names = [str(i + 1) for i in range(n)] if numbered else rnd.sample("ABCDEFGHJKMNPRTUVWXYZ", n)
    weights = [rnd.randint(1, 20) if weighted else 1 for _ in edges]
    return pos, r, edges, names, weights


def wobbly_circle(img, cx, cy, r, rnd, thick, color, wobble):
    pts = []
    ax, ay = r * rnd.uniform(1 - wobble, 1 + wobble), r * rnd.uniform(1 - wobble, 1 + wobble)
    rot = rnd.uniform(0, math.pi)
    for k in range(0, 361, 10):
        a = math.radians(k)
        ex, ey = ax * math.cos(a), ay * math.sin(a)
        px = cx + ex * math.cos(rot) - ey * math.sin(rot) + rnd.uniform(-wobble * 4, wobble * 4)
        py = cy + ex * math.sin(rot) + ey * math.cos(rot) + rnd.uniform(-wobble * 4, wobble * 4)
        pts.append((px, py))
    cv2.polylines(img, [np.array(pts, np.int32)], True, color, thick, cv2.LINE_AA)


def wobbly_line(img, p, q, rnd, thick, color, wobble):
    n = 4
    pts = []
    for k in range(n + 1):
        t = k / n
        x = p[0] + (q[0] - p[0]) * t + (rnd.uniform(-wobble, wobble) if 0 < k < n else 0)
        y = p[1] + (q[1] - p[1]) * t + (rnd.uniform(-wobble, wobble) if 0 < k < n else 0)
        pts.append((x, y))
    cv2.polylines(img, [np.array(pts, np.int32)], False, color, thick, cv2.LINE_AA)


def render(spec, rnd, level):
    W, H = spec["W"], spec["H"]
    pos, r, edges, names, weights = spec["graph"]
    paper = (rnd.randint(225, 245), rnd.randint(228, 246), rnd.randint(232, 250))
    img = np.full((H, W, 3), paper, np.uint8)
    pen = [(30, 30, 30), (90, 30, 20), (20, 20, 90), (50, 50, 50)][rnd.randint(0, 3)]
    thick = {0: 2, 1: 3, 2: 3}[level]
    wobble = {0: 0.0, 1: 0.04, 2: 0.07}[level]
    font = FONTS[rnd.randint(0, 3)] if level < 2 else FONTS[rnd.randint(0, 4)]
    for (a, b), w in zip(edges, weights):
        dx, dy = pos[b][0] - pos[a][0], pos[b][1] - pos[a][1]
        d = math.hypot(dx, dy)
        p = (pos[a][0] + dx / d * r[a], pos[a][1] + dy / d * r[a])
        q = (pos[b][0] - dx / d * r[b], pos[b][1] - dy / d * r[b])
        wobbly_line(img, p, q, rnd, thick, pen, wobble * 20)
        if spec["weighted"]:
            mx, my = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
            nx, ny = -dy / d, dx / d
            off = 20 if rnd.random() < 0.8 else -20
            txt = str(w)
            (tw, th), _ = cv2.getTextSize(txt, font, 0.9, 2)
            cv2.putText(img, txt, (int(mx + nx * off - tw / 2), int(my + ny * off + th / 2)), font, 0.9, pen, 2, cv2.LINE_AA)
            spec.setdefault("weight_pos", []).append((mx + nx * off, my + ny * off))
    for (x, y), rr, name in zip(pos, r, names):
        wobbly_circle(img, x, y, rr, rnd, thick, pen, wobble)
        (tw, th), _ = cv2.getTextSize(name, font, 0.9, 2)
        cv2.putText(img, name, (int(x - tw / 2), int(y + th / 2)), font, 0.9, pen, 2, cv2.LINE_AA)
    # photo effects
    f = img.astype(np.float32)
    if level >= 1:
        gx = np.linspace(rnd.uniform(0.75, 1.0), rnd.uniform(0.75, 1.0), W)[None, :]
        gy = np.linspace(rnd.uniform(0.8, 1.0), rnd.uniform(0.8, 1.0), H)[:, None]
        f *= (gx * gy)[:, :, None]
    if level == 2:
        yy, xx = np.mgrid[0:H, 0:W]
        cx, cy = rnd.uniform(0, W), rnd.uniform(0, H)
        f *= (0.62 + 0.38 * np.clip(1 - np.hypot(xx - cx, yy - cy) / (0.8 * W), 0, 1))[:, :, None]
    sigma = {0: 0.4, 1: 0.9, 2: 1.4}[level]
    f = cv2.GaussianBlur(f, (0, 0), sigma)
    f += np.random.RandomState(rnd.randint(0, 10**6)).normal(0, {0: 2, 1: 4, 2: 7}[level], f.shape)
    img = np.clip(f, 0, 255).astype(np.uint8)
    if level == 2:
        M = cv2.getRotationMatrix2D((W / 2, H / 2), rnd.uniform(-3, 3), 1.0)
        img = cv2.warpAffine(img, M, (W, H), borderValue=paper)
        spec["rot"] = M.tolist()
    return img


def main(out):
    os.makedirs(out, exist_ok=True)
    rnd = random.Random(20261014)
    levels = [0] * 7 + [1] * 8 + [2] * 5      # 20 photos: clean, normal light, hard light
    results = []
    for idx, level in enumerate(levels):
        while True:
            n = rnd.randint(4, 6) if level < 2 else rnd.randint(6, 8)
            weighted = idx % 5 != 4
            W, H = 1000, 760
            g = make_graph(rnd, n, weighted, W, H, idx % 2 == 0)
            if g:
                break
        spec = {"W": W, "H": H, "graph": g, "weighted": weighted}
        img = render(spec, rnd, level)
        name = "photo_%02d" % (idx + 1)
        cv2.imwrite(os.path.join(out, name + ".jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 80])
        img = cv2.imread(os.path.join(out, name + ".jpg"))   # what the phone would hand over
        circles, segs = detect(img)
        toks = read_tokens(img, circles, segs)
        pos, r, edges, names, weights = g
        wpos = list(spec.get("weight_pos", []))
        if "rot" in spec:
            M = np.array(spec["rot"])
            pos = [tuple((M @ np.array([x, y, 1.0])).tolist()) for x, y in pos]
            wpos = [tuple((M @ np.array([x, y, 1.0])).tolist()) for x, y in wpos]
        sim = perfect_tokens(names, pos, weights if weighted else [], wpos)
        results.append({
            "name": name, "level": level, "weighted": weighted,
            "truth": {"nodes": [{"label": names[i], "x": pos[i][0], "y": pos[i][1]} for i in range(len(names))],
                      "edges": [{"a": a, "b": b, "w": weights[k]} for k, (a, b) in enumerate(edges)]},
            "circles": [{"x": x, "y": y, "r": rr, "c": c} for x, y, rr, c in circles],
            "segments": [{"x1": a, "y1": b, "x2": c, "y2": d, "c": e} for a, b, c, d, e in segs],
            "tokens": sim, "tokensTesseract": toks})
        print(name, "level", level, "nodes", len(names), "circles", len(circles), "edges", len(edges), "segs", len(segs), "tokens", len(toks))
    with open(os.path.join(out, "detections.json"), "w") as f:
        json.dump({"schemaVersion": 1, "note": "synthetic photos; HoughCircles/HoughLinesP are real; tokens are the true text at its true place (a perfect recogniser); tokensTesseract is real Tesseract output", "photos": results}, f)


if __name__ == "__main__":
    main(sys.argv[1])
