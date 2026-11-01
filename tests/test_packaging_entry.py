"""Mở app desktop không được thấy cửa sổ console.

Ba thứ giữ cho điều đó đúng: spec build `Kyluat.exe` kiểu windowed, `Kyluat-cli.exe` mới
là file in ra terminal, và code chạy với `sys.stdout = None` không được crash.
"""

import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
ENTRY = ROOT / "packaging" / "kyluat.py"
GUI_EXE = ROOT / "dist" / "Kyluat" / "Kyluat.exe"
CLI_EXE = ROOT / "dist" / "Kyluat" / "Kyluat-cli.exe"

GUI_SUBSYSTEM = 2
CONSOLE_SUBSYSTEM = 3


def pe_subsystem(path: Path) -> int:
    data = path.read_bytes()
    pe = int.from_bytes(data[0x3C:0x40], "little")
    # Subsystem nằm ở byte 68 của Optional Header, và Optional Header bắt đầu sau 24 byte NT headers.
    return int.from_bytes(data[pe + 24 + 68 : pe + 24 + 70], "little")


def load_entry():
    spec = importlib.util.spec_from_file_location("kyluat_entry", ENTRY)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_entry_imports_without_launching_the_app():
    assert callable(load_entry()._quiet_streams)


def test_windowed_streams_keep_print_alive(monkeypatch):
    module = load_entry()
    monkeypatch.setattr(sys, "stdout", None, raising=False)
    monkeypatch.setattr(sys, "stderr", None, raising=False)
    module._quiet_streams()
    print("dòng này rơi vào nul")
    assert sys.stdout is not None and sys.stderr is not None


@pytest.mark.skipif(sys.platform != "win32", reason="Windows only")
def test_cli_build_is_the_one_that_prints():
    assert "kyluat-cli.exe" in ENTRY.read_text(encoding="utf-8")


needs_build = pytest.mark.skipif(
    sys.platform != "win32" or not (GUI_EXE.exists() and CLI_EXE.exists()),
    reason="cần chạy python tools/build_windows.py trước",
)


@needs_build
def test_gui_exe_is_windowed_so_no_console_flash():
    assert pe_subsystem(GUI_EXE) == GUI_SUBSYSTEM


@needs_build
def test_cli_exe_still_attaches_to_the_terminal():
    assert pe_subsystem(CLI_EXE) == CONSOLE_SUBSYSTEM
