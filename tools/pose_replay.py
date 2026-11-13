"""Offline pose replay: run the bundled MediaPipe model on a workout video and emit
per-frame landmark candidates as CSV for the Kotlin PoseReplayTest.

Usage:
    pip install mediapipe
    python tools/pose_replay.py VIDEO --out android/app/src/test/resources/replay/squat_name.csv \
        [--crop X,Y,W,H] [--overlay DIR] [--kind SQUAT] [--expected N]
    python tools/pose_replay.py VIDEO --video-out tests/videos/output/pushup_joints.mp4 --kind PUSH_UP

Record raw camera footage (phone fixed, full body visible, good light), not a screen
recording of the app: overlays and the count panel occlude the body and poison landmarks.
"""
import argparse
import os
import sys

import cv2
import mediapipe as mp
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.core.base_options import BaseOptions

MODEL = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets", "pose_landmarker_full.task")
SIDES = [(11, 13, 15, 23, 25, 27), (12, 14, 16, 24, 26, 28)]
OTHER = [(12, 24), (11, 23)]
# Standard BlazePose topology, so the overlay reads like the model's own skeleton.
CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24), (23, 25), (24, 26), (25, 27), (26, 28),
    (27, 29), (28, 30), (29, 31), (30, 32), (27, 31), (28, 32),
]
JOINT_HEADER = ["sx", "sy", "sc", "ex", "ey", "ec", "wx", "wy", "wc",
                "hx", "hy", "hc", "kx", "ky", "kc", "ax", "ay", "ac",
                "osx", "osy", "osc", "ohx", "ohy", "ohc"]
WORLD_HEADER = [f"w_{p}{c}" for p in ("s", "e", "w", "h", "k", "a") for c in ("x", "y", "z", "c")]


def fmt(value):
    return "" if value is None else f"{value:.4f}"


def draw_skeleton(frame, landmarks, width, height):
    """Draw joints (colour = visibility) and limb lines onto a BGR frame in place."""
    pts = [(int(lm.x * width), int(lm.y * height),
            lm.visibility if lm.visibility is not None else 0.0) for lm in landmarks]
    for a, b in CONNECTIONS:
        if pts[a][2] >= 0.5 and pts[b][2] >= 0.5:
            cv2.line(frame, pts[a][:2], pts[b][:2], (0, 255, 0), 2)
    for x, y, vis in pts:
        colour = (0, 255, 0) if vis >= 0.7 else (0, 200, 255) if vis >= 0.5 else (0, 0, 255)
        cv2.circle(frame, (x, y), 3, colour, -1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("video")
    parser.add_argument("--out", help="CSV path for the Kotlin replay test")
    parser.add_argument("--crop", help="X,Y,W,H of the clean camera area")
    parser.add_argument("--overlay", help="directory for skeleton overlay frames")
    parser.add_argument("--video-out", help="write an annotated MP4 with joints + skeleton")
    parser.add_argument("--kind", default="SQUAT", choices=["PUSH_UP", "SQUAT", "PLANK"])
    parser.add_argument("--expected", type=int, help="ground-truth reps or plank seconds")
    parser.add_argument("--every", type=int, default=1, help="process every Nth frame")
    args = parser.parse_args()

    crop = tuple(int(v) for v in args.crop.split(",")) if args.crop else None
    options = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=MODEL),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    capture = cv2.VideoCapture(args.video)
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    if args.overlay:
        os.makedirs(args.overlay, exist_ok=True)
    writer = None
    if args.video_out:
        os.makedirs(os.path.dirname(os.path.abspath(args.video_out)), exist_ok=True)
        vw, vh = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)), int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
        if crop:
            vw, vh = crop[2], crop[3]
        writer = cv2.VideoWriter(args.video_out, cv2.VideoWriter_fourcc(*"mp4v"), fps, (vw, vh))
    rows = 0
    frames = 0
    out = open(args.out, "w", encoding="utf-8") if args.out else None
    try:
        with vision.PoseLandmarker.create_from_options(options) as landmarker:
            if out:
                out.write(f"# kind={args.kind}" + (f" expected={args.expected}" if args.expected is not None else "") + "\n")
                out.write("t,side," + ",".join(JOINT_HEADER + WORLD_HEADER) + "\n")
            index = 0
            while True:
                ok, frame = capture.read()
                if not ok:
                    break
                index += 1
                if index % args.every:
                    continue
                if crop:
                    x, y, w, h = crop
                    frame = frame[y:y + h, x:x + w]
                height, width = frame.shape[:2]
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                result = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), int(index * 1000 / fps))
                frames += 1
                landmarks = result.pose_landmarks[0] if result.pose_landmarks else None
                if writer is not None:
                    annotated = frame.copy()
                    if landmarks is not None:
                        draw_skeleton(annotated, landmarks, width, height)
                    writer.write(annotated)
                if landmarks is None:
                    continue
                world = result.pose_world_landmarks[0] if result.pose_world_landmarks else None

                def point(i):
                    lm = landmarks[i]
                    x, y = lm.x * width, lm.y * height
                    if not (0 <= x <= width and 0 <= y <= height):
                        return None
                    return (x, y, lm.visibility if lm.visibility is not None else 0.0)

                def wpoint(i):
                    if world is None:
                        return None
                    lm = world[i]
                    return (lm.x, lm.y, lm.z, lm.visibility if lm.visibility is not None else 0.0)

                for side, ids in enumerate(SIDES):
                    core_pts = [point(i) for i in (ids[0], ids[3], ids[4], ids[5])]
                    if any(p is None for p in core_pts):
                        continue
                    shoulder = core_pts[0]
                    elbow = point(ids[1]) or (shoulder[0], shoulder[1], 0.0)
                    wrist = point(ids[2]) or (shoulder[0], shoulder[1], 0.0)
                    other = [point(i) for i in OTHER[side]]
                    values = [shoulder, elbow, wrist, core_pts[1], core_pts[2], core_pts[3]] + other
                    wvalues = [wpoint(i) for i in ids]
                    cells = [fmt(v) for p in values for v in (p if p else (None, None, None))]
                    cells += [fmt(v) for p in wvalues for v in (p if p else (None, None, None, None))]
                    if out:
                        out.write(f"{int(index * 1000 / fps)},{side}," + ",".join(cells) + "\n")
                        rows += 1
                if args.overlay and frames % 15 == 0:
                    for lm in landmarks:
                        cv2.circle(frame, (int(lm.x * width), int(lm.y * height)), 3, (0, 255, 0), -1)
                    cv2.imwrite(os.path.join(args.overlay, f"f_{index:04d}.png"), frame)
    finally:
        if out:
            out.close()
        if writer:
            writer.release()
        capture.release()
    print(f"frames={frames} candidate_rows={rows} -> {args.out or args.video_out}")


if __name__ == "__main__":
    sys.exit(main())