import ctypes
import sys
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))


def _log_failure() -> None:
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


try:
    from enforcer.desktop import run
except Exception:
    _log_failure()
    raise

if __name__ == "__main__":
    try:
        sys.exit(run())
    except Exception:
        _log_failure()
        raise
