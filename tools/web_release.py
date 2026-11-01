"""In tên file, dung lượng, SHA-256 cho các bản phát hành để dán vào trang tải về.

    python tools/web_release.py
"""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = [
    ROOT / "dist" / "KYLUAT-Setup-v1.2.exe",
    ROOT / "dist" / "KYLUAT-windows-x64-v1.2.zip",
    ROOT / "dist" / "kyluat-android-v3.7.apk",
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    missing = False
    for path in FILES:
        if not path.exists():
            print(f"THIẾU FILE: {path}")
            missing = True
            continue
        size = path.stat().st_size
        print(path.name)
        print(f"  dung lượng  {size:,} bytes = {size / 1_000_000:.2f} MB")
        print(f"  sha256      {sha256(path)}")
    return 1 if missing else 0


if __name__ == "__main__":
    raise SystemExit(main())
