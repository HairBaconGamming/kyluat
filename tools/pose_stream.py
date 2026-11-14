"""Stream-mode pose replay: treat a video as a live camera feed.

Uses MediaPipe LIVE_STREAM (detect_async) and processes each frame's result the moment it
arrives, exactly like the on-device pipeline — no lookahead, no offline VIDEO mode. Emits:
  --out       joint CSV (same layout as pose_replay.py) for the Kotlin PoseReplayTest
  --full-out  per-frame full 33-landmark CSV (t, x,y,vis * 33) for the overlay renderer

Usage:
    python tools/pose_stream.py VIDEO --out replay/pushup4_raw.csv --full-out output/pushup4_full.csv --kind PUSH_UP
"""
import argparse
import os
import queue
import sys

import cv2
import mediapipe as mp
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.core.base_options import BaseOptions

MODEL = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets", "pose_landmarker_full.task")
SIDES = [(11, 13, 15, 23, 25, 27), (12, 14, 16, 24, 26, 28)]
OTHER = [(12, 24), (11, 23)]
JOINT_HEADER = ["sx", "sy", "sc", "ex", "ey", "ec", "wx", "wy", "wc",
                "hx", "hy", "hc", "kx", "ky", "kc", "ax", "ay", "ac",
                "osx", "osy", "osc", "ohx", "ohy", "ohc"]
WORLD_HEADER = [f"w_{p}{c}" for p in ("s", "e", "w", "h", "k", "a") for c in ("x", "y", "z", "c")]


def fmt(v):
    return "" if v is None else f"{v:.4f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("--out", required=True)
    ap.add_argument("--full-out", required=True)
    ap.add_argument("--kind", default="PUSH_UP", choices=["PUSH_UP", "SQUAT", "PLANK"])
    args = ap.parse_args()

    results = queue.Queue()

    def on_result(result, image, timestamp_ms):
        results.put((timestamp_ms, result))

    options = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=MODEL),
        running_mode=vision.RunningMode.LIVE_STREAM,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
        result_callback=on_result,
    )
    capture = cv2.VideoCapture(args.video)
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.full_out)), exist_ok=True)
    rows = 0
    frames = 0
    with vision.PoseLandmarker.create_from_options(options) as landmarker, \
            open(args.out, "w", encoding="utf-8") as out, \
            open(args.full_out, "w", encoding="utf-8") as full:
        out.write(f"# kind={args.kind}\n")
        out.write("t,side," + ",".join(JOINT_HEADER + WORLD_HEADER) + "\n")
        index = 0
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            index += 1
            height, width = frame.shape[:2]
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            ts = int(index * 1000 / fps)
            # Feed one frame and consume its result immediately: causal, live-like.
            landmarker.detect_async(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), ts)
            _, result = results.get()
            frames += 1
            if not result.pose_landmarks:
                continue
            landmarks = result.pose_landmarks[0]
            world = result.pose_world_landmarks[0] if result.pose_world_landmarks else None
            full.write(str(ts) + "," + ",".join(
                fmt(v) for lm in landmarks for v in (lm.x * width, lm.y * height, lm.visibility or 0.0)
            ) + "\n")

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
                shoulder = point(ids[0])
                hip = point(ids[3])
                if shoulder is None or hip is None:
                    continue
                elbow = point(ids[1]) or (shoulder[0], shoulder[1], 0.0)
                wrist = point(ids[2]) or (shoulder[0], shoulder[1], 0.0)
                knee = point(ids[4]) or (hip[0], hip[1], 0.0)
                ankle = point(ids[5]) or (hip[0], hip[1], 0.0)
                other = [point(i) for i in OTHER[side]]
                values = [shoulder, elbow, wrist, hip, knee, ankle] + other
                wvalues = [wpoint(i) for i in ids]
                cells = [fmt(v) for p in values for v in (p if p else (None, None, None))]
                cells += [fmt(v) for p in wvalues for v in (p if p else (None, None, None, None))]
                out.write(f"{ts},{side}," + ",".join(cells) + "\n")
                rows += 1
    capture.release()
    print(f"frames={frames} candidate_rows={rows} -> {args.out} + {args.full_out}")


if __name__ == "__main__":
    sys.exit(main())