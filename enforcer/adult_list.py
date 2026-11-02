"""Danh sách tên miền 18+ dùng chung cho Windows và Android.

Nguồn trong `enforcer/data/adult_domains.txt`, nhập từ BlockNSFW data/HOSTS.txt
(MIT, snapshot bee0db2, tải 2026-06-03), đã bỏ tiền tố www và dedupe. Danh sách
vẫn chỉ chặn đúng tên miền: nội dung nằm trong YouTube, trang được phép, hoặc
trình duyệt tự bật DoH thì không đi qua lớp DNS này.

`android/app/src/main/res/raw/adult_domains.txt` là bản sinh từ file dữ liệu.
Sửa danh sách thì chạy `python tools/sync_adult_list.py`; tests/test_adult_list.py
giữ cho hai bên không lệch nhau.
"""

from __future__ import annotations

from pathlib import Path

DATA = Path(__file__).with_name("data") / "adult_domains.txt"


def _load() -> tuple[str, ...]:
    try:
        text = DATA.read_text(encoding="utf-8")
    except OSError:
        return ()
    return tuple(
        sorted(
            {
                line.strip().lower()
                for line in text.splitlines()
                if line.strip() and not line.lstrip().startswith("#")
            }
        )
    )


ADULT_DOMAINS: tuple[str, ...] = _load()
