"""Render a workout video with the pose skeleton and a running rep count at top centre.

The count is the PRODUCTION counter's rep events (from PoseReplayTest "REP <file> t=… reps=…"),
so the overlay shows exactly what the app would count, synced to frame time. Joints come from
the replay CSV (same bundled MediaPipe model), drawn in original video pixel space.

Usage:
    python tools/render_count_overlay.py VIDEO CSV REP_EVENTS_TXT OUT_MP4
"""
import argparse
import os
import sys

import cv2

# Full BlazePose topology (both arms, both legs, torso, head) — matches the app overlay.
CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24), (23, 25), (24, 26), (25, 27), (26, 28),
    (27, 29), (28, 30), (29, 31), (30, 32), (27, 31), (28, 32),
]


def load_rows(csv_path):
    by_time = {}
    with open(csv_path, encoding="utf-8") as fh:
        for line in fh.read().splitlines():
            cells = line.split(",")
            if len(cells) < 100:
                continue
            t = int(cells[0])
            vals = [float(x) if x else None for x in cells[1:]]
            by_time[t] = vals
    return by_time


def load_reps(events_path, name):
    reps = []
    with open(events_path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) >= 4 and parts[0] == "REP" and parts[1] == name:
                t = int(parts[2].split("=")[1])
                n = int(parts[3].split("=")[1])
                reps.append((t, n))
    return sorted(reps)


def draw(frame, vals, count, width):
    pts = {}
    if vals:
        for i in range(33):
            x, y, c = vals[i * 3], vals[i * 3 + 1], vals[i * 3 + 2]
            if x is None or y is None:
                continue
            pts[i] = (int(x), int(y), c if c is not None else 0.0)
    for a, b in CONNECTIONS:
        if a in pts and b in pts and pts[a][2] >= 0.7 and pts[b][2] >= 0.7:
            cv2.line(frame, pts[a][:2], pts[b][:2], (0, 255, 0), 2)
    for i, (x, y, c) in pts.items():
        if c >= 0.7:
            cv2.circle(frame, (x, y), 4, (0, 255, 0), -1)
    text = str(count)
    scale, thick = 1.6, 4
    (tw, th), _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, scale, thick)
    org = (width // 2 - tw // 2, 46)
    cv2.putText(frame, text, org, cv2.FONT_HERSHEY_SIMPLEX, scale, (0, 0, 0), thick + 3)
    cv2.putText(frame, text, org, cv2.FONT_HERSHEY_SIMPLEX, scale, (255, 255, 255), thick)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("csv")
    ap.add_argument("events")
    ap.add_argument("out")
    args = ap.parse_args()

    by_time = load_rows(args.csv)
    rep_name = os.path.basename(args.csv).replace("_full.csv", "_raw.csv")
    reps = load_reps(args.events, rep_name)
    capture = cv2.VideoCapture(args.video)
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    writer = cv2.VideoWriter(args.out, cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))
    index = 0
    ri = 0
    count = 0
    while True:
        ok, frame = capture.read()
        if not ok:
            break
        index += 1
        t = int(index * 1000 / fps)
        while ri < len(reps) and reps[ri][0] <= t:
            count = reps[ri][1]
            ri += 1
        rows = by_time.get(t)
        draw(frame, rows, count, width)
        writer.write(frame)
    capture.release()
    writer.release()
    print(f"{args.out}: frames={index} final_count={count}")


if __name__ == "__main__":
    sys.exit(main())