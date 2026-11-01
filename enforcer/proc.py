"""Cổng duy nhất để gọi tiến trình con.

Hai thứ ở đây là lý do app trông chuyên nghiệp hay không trên màn hình:

- Bản exe GUI từng gọi FreeConsole() để ẩn cửa sổ console, nên handle stdin của tiến
  trình trỏ vào console đã chết; mọi subprocess.run để stdin=None sẽ rơi vào
  _make_inheritable và nổ OSError WinError 50/6. DEVNULL đi qua
  msvcrt.get_osfhandle(devnull) nên luôn nhân bản được.
- SchTasks, NetSh, Reg, IpConfig, PowerShell đều là app console. Nếu không đặt
  CREATE_NO_WINDOW, Windows mở một cửa sổ đen nhấp nháy cho từng lời gọi.
"""

from __future__ import annotations

import subprocess
import sys

NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)
DETACHED = getattr(subprocess, "DETACHED_PROCESS", 0)


def _hide_window(kwargs: dict) -> dict:
    """Thêm CREATE_NO_WINDOW, trừ khi người gọi đã tự đặt DETACHED_PROCESS (không console nào cả)."""
    if sys.platform != "win32" or not NO_WINDOW:
        return kwargs
    flags = int(kwargs.get("creationflags", 0) or 0)
    if not flags & DETACHED:
        kwargs["creationflags"] = flags | NO_WINDOW
    return kwargs


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    kwargs.setdefault("stdin", subprocess.DEVNULL)
    return subprocess.run(command, **_hide_window(kwargs))


def popen(command: list[str], **kwargs) -> subprocess.Popen:
    """Popen mặc định cắt sạch ba luồng chuẩn: tiến trình nền không được kế thừa handle console."""
    for key in ("stdin", "stdout", "stderr"):
        kwargs.setdefault(key, subprocess.DEVNULL)
    return subprocess.Popen(command, **_hide_window(kwargs))
