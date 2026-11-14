"""Render a workout replay: joints, the production count and floor metrics in a top-left panel.

Every number on screen comes from the per-frame dump that PoseReplayTest writes
(android/app/build/replay-metrics/<fixture>.tsv), so the video shows exactly what
ExerciseCounter computed for that frame. Joints come from the matching 33-landmark CSV
written by tools/pose_stream.py --full-out.

Usage:
    python tools/render_count_overlay.py VIDEO FULL_CSV DUMP_TSV OUT_MP4 [--scale 5]
"""
import argparse
import math
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24), (23, 25), (24, 26), (25, 27), (26, 28),
    (27, 29), (28, 30), (29, 31), (30, 32), (27, 31), (28, 32),
]

# Mirrors ExerciseCounter's floor gates.
PUSH_MAX_TILT = 35.0
PUSH_DROP = 0.08
PLANK_MAX_TILT = 25.0
PLANK_MIN_ARM = 40.0
PLANK_HIP = (0.3, 1.6)

OK = (88, 214, 141)
BAD = (255, 107, 107)
NEUTRAL = (235, 235, 235)
MUTED = (170, 178, 189)
AMBER = (255, 196, 87)
FLOOR = (110, 180, 255)
SKELETON = (120, 230, 150)

VIEWS = {"SIDE": "nghiêng", "FRONT": "chính diện", "DIAGONAL": "chéo"}
FONT_DIR = "C:/Windows/Fonts"


def font(bold, size):
    return ImageFont.truetype(os.path.join(FONT_DIR, "segoeuib.ttf" if bold else "segoeui.ttf"), size)


def num(v, digits=2):
    return f"{v:.{digits}f}".replace(".", ",")


def load_full(path):
    by_time = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh.read().splitlines():
            cells = line.split(",")
            if len(cells) < 100:
                continue
            by_time[int(cells[0])] = [float(x) if x else None for x in cells[1:100]]
    return by_time


def load_dump(path):
    rows = []
    with open(path, encoding="utf-8") as fh:
        header = fh.readline().rstrip("\n").split("\t")
        for line in fh:
            cells = line.rstrip("\n").split("\t")
            row = dict(zip(header, cells))
            for key, value in row.items():
                if key not in ("view", "side", "feedback"):
                    row[key] = float(value) if value not in ("", None) else None
            row["t"] = int(row["t"])
            rows.append(row)
    return rows


def joint(row, key):
    x, y = row.get(key + "x"), row.get(key + "y")
    return None if x is None or y is None else (x, y)


def angle(a, b, c):
    u = [a[i] - b[i] for i in range(len(a))]
    v = [c[i] - b[i] for i in range(len(a))]
    nu, nv = math.hypot(*u), math.hypot(*v)
    if nu == 0 or nv == 0:
        return None
    cos = sum(p * q for p, q in zip(u, v)) / (nu * nv)
    return math.degrees(math.acos(max(-1.0, min(1.0, cos))))


def elbow_angle(row):
    world = [row.get(k) for k in ("wsx", "wsy", "wsz", "wex", "wey", "wez", "wwx", "wwy", "wwz")]
    if row["view"] != "SIDE" and all(v is not None for v in world):
        return angle(world[0:3], world[3:6], world[6:9])
    s, e, w = joint(row, "s"), joint(row, "e"), joint(row, "w")
    return angle(s, e, w) if s and e and w else None


def extended_line(a, b, width, height):
    dx, dy = b[0] - a[0], b[1] - a[1]
    if abs(dx) < 1e-6:
        return (a[0], 0), (a[0], height)
    slope = dy / dx
    return (0, a[1] - slope * a[0]), (width, a[1] + slope * (width - a[0]))


class Renderer:
    def __init__(self, kind, scale):
        self.kind = kind
        self.s = scale
        self.title = font(True, 13 * scale // 2)
        self.count = font(True, 22 * scale // 2)
        self.body = font(False, 7 * scale // 2)
        self.small = font(False, 6 * scale // 2)
        self.panel_w = 0

    def p(self, xy):
        return xy[0] * self.s, xy[1] * self.s

    def draw(self, image, landmarks, row, fresh, flash):
        draw = ImageDraw.Draw(image, "RGBA")
        width, height = image.size
        if row is not None and fresh:
            self.floor_and_body(draw, row, width, height)
        if landmarks is not None:
            self.skeleton(draw, landmarks)
        if row is not None and fresh:
            for key in ("s", "e", "w", "h", "k", "a"):
                point = joint(row, key)
                if point:
                    x, y = self.p(point)
                    r = 2.2 * self.s
                    draw.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255), outline=(30, 30, 30), width=2)
        self.panel(draw, row, flash)

    def skeleton(self, draw, vals):
        pts = {}
        for i in range(33):
            x, y, c = vals[i * 3], vals[i * 3 + 1], vals[i * 3 + 2]
            if x is not None and y is not None and (c or 0.0) >= 0.5:
                pts[i] = self.p((x, y))
        for a, b in CONNECTIONS:
            if a in pts and b in pts:
                draw.line((pts[a], pts[b]), fill=SKELETON + (220,), width=max(2, self.s // 2 + 1))
        r = 0.9 * self.s
        for x, y in pts.values():
            draw.ellipse((x - r, y - r, x + r, y + r), fill=SKELETON)

    def floor_and_body(self, draw, row, width, height):
        s, h, a, e, w = (joint(row, k) for k in ("s", "h", "a", "e", "w"))
        tilt = row.get("tilt")
        if self.kind == "PUSH_UP":
            if w:
                y = w[1] * self.s
                draw.line(((0, y), (width, y)), fill=FLOOR + (200,), width=3)
                draw.text((width - 12, y - 6), "sàn", font=self.small, fill=FLOOR, anchor="rb")
            body = (s, h)
            good = tilt is not None and tilt <= PUSH_MAX_TILT
        else:
            if e and a:
                start, end = extended_line(self.p(e), self.p(a), width, height)
                draw.line((start, end), fill=FLOOR + (200,), width=3)
                draw.text((width - 12, end[1] - 6), "sàn (khuỷu → cổ chân)", font=self.small, fill=FLOOR, anchor="rb")
            body = (s, a)
            good = tilt is not None and tilt <= PLANK_MAX_TILT
        if body[0] and body[1]:
            draw.line((self.p(body[0]), self.p(body[1])), fill=(OK if good else BAD) + (235,), width=self.s + 1)

    def panel(self, draw, row, flash):
        pad = 4 * self.s
        lines = []
        if self.kind == "PUSH_UP":
            reps = int(row["reps"]) if row else 0
            head = ("CHỐNG ĐẨY", f"{reps}")
        else:
            millis = row["plank_ms"] if row else 0
            head = ("PLANK", f"{num(millis / 1000, 1)} s")
        if row:
            view = VIEWS.get(row["view"], row["view"])
            if self.kind == "PUSH_UP":
                elbow = elbow_angle(row)
                lines.append((f"Góc nhìn {view} · khuỷu {num(elbow, 0) if elbow is not None else '–'}°", MUTED))
                lines += self.push_lines(row)
            else:
                lines.append((f"Góc nhìn {view}", MUTED))
                lines += self.plank_lines(row)
            lines.append((row["feedback"] or "", AMBER if row.get("corr") else NEUTRAL))
        line_h = 10 * self.s // 2 + 6
        head_h = 15 * self.s
        title_w = draw.textlength(head[0] + "  ", font=self.title)
        head_w = title_w + draw.textlength(head[1] + " +1", font=self.count)
        text_w = max([head_w] + [draw.textlength(text, font=self.body) for text, _ in lines])
        # Only ever grows, so the panel does not jitter with the text.
        self.panel_w = max(self.panel_w, min(text_w, 150 * self.s) + pad * 2)
        width = self.panel_w
        height = 2 * self.s + head_h + line_h * len(lines) + pad
        draw.rounded_rectangle((pad, pad, pad + width, pad + height), radius=3 * self.s, fill=(16, 18, 22, 178))
        x, y = pad * 2, pad + 2 * self.s
        draw.text((x, y + 3 * self.s), head[0], font=self.title, fill=NEUTRAL, anchor="lt")
        draw.text((x + title_w, y), head[1], font=self.count, fill=OK if flash else (255, 255, 255), anchor="lt")
        if flash:
            draw.text((x + title_w + draw.textlength(head[1] + " ", font=self.count), y + 3 * self.s),
                      "+1", font=self.title, fill=OK, anchor="lt")
        y += head_h
        for text, colour in lines:
            draw.text((x, y), self.fit(draw, text, width - pad), font=self.body, fill=colour, anchor="lt")
            y += line_h

    def fit(self, draw, text, max_w):
        if draw.textlength(text, font=self.body) <= max_w:
            return text
        while text and draw.textlength(text + "…", font=self.body) > max_w:
            text = text[:-1]
        return text + "…"

    @staticmethod
    def status(value, ok, fmt, limit):
        if value is None:
            return "–", MUTED
        return f"{fmt(value)}   ({limit})", OK if ok(value) else BAD

    def push_lines(self, row):
        tilt, drop = row.get("tilt"), row.get("drop")
        height, arm = row.get("height"), row.get("arm")
        last_drop, last_tilt = row.get("last_drop"), row.get("last_tilt")
        t_text, t_col = self.status(tilt, lambda v: v <= PUSH_MAX_TILT, lambda v: f"{num(v, 1)}°", f"≤ {PUSH_MAX_TILT:.0f}°")
        d_text, d_col = self.status(drop, lambda v: v >= PUSH_DROP, lambda v: num(v), f"≥ {num(PUSH_DROP)}")
        # Depth is only judged at the bottom of a rep, so a shallow live value is not a failure yet.
        d_col = OK if d_col == OK else NEUTRAL
        lines = [
            (f"Thân nghiêng so với sàn: {t_text}", t_col),
            (f"Vai hạ / cả tay: {d_text}", d_col),
            (f"Vai cao / cả tay: {num(height) if height is not None else '–'} · "
             f"tay–sàn {num(arm, 0) if arm is not None else '–'}°", NEUTRAL),
        ]
        if last_drop is None:
            lines.append(("Nhịp vừa xét: chưa có", MUTED))
        else:
            passed = last_drop >= PUSH_DROP and (last_tilt or 0.0) <= PUSH_MAX_TILT
            lines.append((f"Nhịp vừa xét: hạ {num(last_drop)} · nghiêng {num(last_tilt or 0.0, 1)}° → "
                          f"{'đạt' if passed else 'chưa đạt'}", OK if passed else BAD))
        return lines

    def plank_lines(self, row):
        tilt, arm, height, hip = row.get("tilt"), row.get("arm"), row.get("height"), row.get("hip")
        t_text, t_col = self.status(tilt, lambda v: v <= PLANK_MAX_TILT, lambda v: f"{num(v, 1)}°", f"≤ {PLANK_MAX_TILT:.0f}°")
        a_text, a_col = self.status(arm, lambda v: v >= PLANK_MIN_ARM, lambda v: f"{num(v, 0)}°", f"≥ {PLANK_MIN_ARM:.0f}°")
        h_text, h_col = self.status(hip, lambda v: PLANK_HIP[0] <= v <= PLANK_HIP[1], lambda v: num(v),
                                    f"{num(PLANK_HIP[0], 1)}–{num(PLANK_HIP[1], 1)}")
        return [
            (f"Thân nghiêng so với sàn: {t_text}", t_col),
            (f"Cánh tay so với sàn: {a_text}", a_col),
            (f"Vai cao / cánh tay: {num(height) if height is not None else '–'}", NEUTRAL),
            (f"Hông cao / vai cao: {h_text}", h_col),
        ]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("full_csv")
    ap.add_argument("dump")
    ap.add_argument("out")
    ap.add_argument("--scale", type=int, default=5)
    args = ap.parse_args()

    landmarks = load_full(args.full_csv)
    rows = load_dump(args.dump)
    by_t = {row["t"]: row for row in rows}
    kind = "PLANK" if any(row["plank_ms"] for row in rows) or "plank" in os.path.basename(args.dump) else "PUSH_UP"
    renderer = Renderer(kind, args.scale)

    capture = cv2.VideoCapture(args.video)
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)) * args.scale
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)) * args.scale
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    writer = cv2.VideoWriter(args.out, cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))
    index, ri, last, last_reps, flash_until = 0, 0, None, 0, -1
    while True:
        ok, frame = capture.read()
        if not ok:
            break
        index += 1
        # Same timestamps pose_stream.py fed to MediaPipe.
        t = int(index * 1000 / fps)
        while ri < len(rows) and rows[ri]["t"] <= t:
            last = rows[ri]
            ri += 1
            if kind == "PUSH_UP" and last["reps"] > last_reps:
                last_reps = int(last["reps"])
                flash_until = last["t"] + 600
        big = cv2.resize(frame, (width, height), interpolation=cv2.INTER_CUBIC)
        image = Image.fromarray(cv2.cvtColor(big, cv2.COLOR_BGR2RGB))
        renderer.draw(image, landmarks.get(t), last, t in by_t, t <= flash_until)
        writer.write(cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR))
    capture.release()
    writer.release()
    final = f"reps={int(last['reps'])}" if kind == "PUSH_UP" else f"plank={last['plank_ms'] / 1000:.1f}s"
    print(f"{args.out}: frames={index} {final}")


if __name__ == "__main__":
    sys.exit(main())
