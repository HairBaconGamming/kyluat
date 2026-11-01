from __future__ import annotations

import ctypes
import os
import sys
import traceback
from pathlib import Path

if not getattr(sys, "frozen", False):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


def _quiet_streams() -> None:
    """Kyluat.exe build kiểu GUI nên sinh ra không có console; giữ print chạy được nhưng không in ra đâu."""
    sink = open(os.devnull, "w", encoding="utf-8")
    sys.stdout = sink
    sys.stderr = sink


def _log_failure() -> None:
    from enforcer.cli import ROOT

    try:
        log = ROOT / "data" / "desktop-error.log"
        log.parent.mkdir(parents=True, exist_ok=True)
        log.write_text(traceback.format_exc(), encoding="utf-8")
    except OSError:
        pass
    try:
        ctypes.windll.user32.MessageBoxW(
            None,
            "Không mở được app Kỷ luật.\nXem data/desktop-error.log để biết lỗi.",
            "Kỷ luật",
            0x10,
        )
    except (AttributeError, OSError):
        pass


if __name__ == "__main__":
    argv = sys.argv[1:]
    if sys.stdout is None:
        _quiet_streams()
    if argv or Path(sys.argv[0]).name.lower() == "kyluat-cli.exe":
        from enforcer.cli import main

        sys.exit(main(argv))
    try:
        from enforcer.desktop import run

        sys.exit(run())
    except Exception:
        _log_failure()
        raise
