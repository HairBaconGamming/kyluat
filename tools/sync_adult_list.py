"""Sinh danh sách domain 18+ cho Android từ enforcer/data/adult_domains.txt.

    python tools/sync_adult_list.py

File kết quả chỉ chứa tên miền, mỗi dòng một cái, để phần đọc trên Android không
phải xử lý chú thích. tests/test_adult_list.py so nội dung với nguồn, sửa tay
một dòng cũng bị phát hiện.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from enforcer.adult_list import ADULT_DOMAINS  # noqa: E402

TARGET = ROOT / "android" / "app" / "src" / "main" / "res" / "raw" / "adult_domains.txt"

# Console Windows mặc định cp1252 nên in tiếng Việt ra là hỏng lệnh; đường dẫn file thì luôn utf-8.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")


def render() -> str:
    return "".join(f"{domain}\n" for domain in ADULT_DOMAINS)


def main(argv: list[str]) -> int:
    label = TARGET.relative_to(ROOT)
    if TARGET.exists() and TARGET.read_text(encoding="utf-8") == render():
        print(f"{label} đã đúng · {len(ADULT_DOMAINS)} domain")
        return 0
    if "--check" in argv:
        print(f"{label} lệch nguồn. Chạy: python tools/sync_adult_list.py", file=sys.stderr)
        return 1
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(render(), encoding="utf-8", newline="\n")
    print(f"đã ghi {label} · {len(ADULT_DOMAINS)} domain")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
