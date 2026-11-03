"""Cửa khởi động: khi app không nạp nổi chính nó thì phải nói rõ, không ném traceback.

Hai chuyện đã thật sự xảy ra: chạy nhầm bản Python chưa cài thư viện
(`ModuleNotFoundError: No module named 'dnslib'`) và daemon chết im lặng. Daemon im lặng còn
nguy hiểm hơn, vì chính nó giữ cổng 53 — DNS máy vẫn trỏ về 127.0.0.1 mà không ai trả lời là
cả máy mất phân giải tên miền.
"""

from __future__ import annotations

import ctypes
import os
import subprocess
import sys
import traceback
from datetime import datetime
from pathlib import Path

from . import proc

PROBE = "import importlib.util, sys; sys.exit(0 if importlib.util.find_spec(sys.argv[1]) else 1)"
QUIET_MINUTES = 60


def emit(text: str) -> None:
    """In ra console kể cả khi codepage là cp1252: tiếng Việt không được phép làm lời báo nổ tiếp."""
    if sys.stdout is None:
        return
    try:
        if os.name == "nt":
            ctypes.windll.kernel32.SetConsoleOutputCP(65001)
    except (AttributeError, OSError):
        pass
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        print(text)
    except (OSError, ValueError):
        payload = text.encode("ascii", "replace")
        stream = sys.stdout.buffer if hasattr(sys.stdout, "buffer") else None
        if stream is not None:
            stream.write(payload + b"\n")
            stream.flush()
        else:
            sys.stderr.write(payload.decode("ascii") + "\n")


def interpreter_with(module: str) -> str | None:
    """Đường dẫn một bản Python khác trên máy có sẵn thư viện đang thiếu, nếu tìm được."""
    root = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Python"
    if not module.isidentifier() or not root.is_dir():
        return None
    current = Path(sys.executable).resolve()
    for folder in sorted(root.glob("Python*"), reverse=True):
        exe = folder / "python.exe"
        if not exe.is_file() or exe.resolve() == current:
            continue
        try:
            result = proc.run([str(exe), "-c", PROBE, module], capture_output=True, timeout=20)
        except (OSError, subprocess.TimeoutExpired):
            continue
        if result.returncode == 0:
            return str(exe)
    return None


def explain(exc: BaseException) -> str:
    """Văn bản ngắn gọn: lỗi gì, trình nào, và lệnh nào chạy được."""
    module = getattr(exc, "name", None) or "?"
    running = Path(sys.executable)
    version = ".".join(str(part) for part in sys.version_info[:3])
    lines = [
        f"Kỷ luật không chạy được: thiếu thư viện {module}."
        if module != "?"
        else "Kỷ luật không chạy được.",
        f"Trình Python đang dùng: {running} (bản {version})",
    ]
    other = interpreter_with(module) if module != "?" else None
    if other:
        lines += [f"Máy này có bản đã cài đủ thư viện: {other}", f'    & "{other}" -m enforcer doctor']
    else:
        lines += [f'Cài thư viện vào trình đang chạy: "{running}" -m pip install -r requirements.txt']
    lines.append("Bản đã kiểm chứng cho app này là Python 3.12.10 (xem README).")
    return "\n".join(lines)


def report_startup_failure(root: Path, text: str, popup: bool = False) -> Path:
    """Ghi nhật ký khởi động; popup chỉ hiện khi lần báo trước đã qua một giờ, vì task gọi mỗi phút."""
    log = root / "data" / "startup.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    now = datetime.now()
    last = _last_failure(log)
    quiet = last is not None and (now - last).total_seconds() < QUIET_MINUTES * 60
    with log.open("a", encoding="utf-8") as handle:
        handle.write(f"[{now.isoformat(timespec='seconds')}] {text}\n")
    if popup and not quiet:
        try:
            ctypes.windll.user32.MessageBoxW(None, f"{text}\n\nLog: {log}", "Kỷ luật", 0x10)
        except (AttributeError, OSError):
            pass
    return log


def _last_failure(log: Path) -> datetime | None:
    try:
        stamp = log.read_text(encoding="utf-8").splitlines()[-1].split("]")[0].removeprefix("[")
        return datetime.fromisoformat(stamp)
    except (OSError, ValueError, IndexError):
        return None


def load_main(root: Path):
    """Nạp enforcer.cli cho các file .pyw: lỗi khởi động phải để lại dấu vết, không im lặng biến mất."""
    try:
        from enforcer.cli import main

        return main
    except ImportError as exc:
        text = explain(exc) if isinstance(exc, ModuleNotFoundError) else traceback.format_exc()
        log = report_startup_failure(root, text, popup=True)
        emit(f"{text}\nLog: {log}")
        raise SystemExit(1) from exc
