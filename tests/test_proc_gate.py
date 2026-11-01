"""Cổng proc phải là nơi duy nhất gọi subprocess, và phải cắt đường kế thừa stdin.

Lỗi thực tế: bản exe GUI gọi FreeConsole() rồi crash ở _make_inheritable
(OSError WinError 50) khi màn hình Cài đặt hỏi trạng thái task.
"""

import ast
import subprocess
import sys
from pathlib import Path

import pytest

from enforcer import proc

ENFORCER_DIR = Path(proc.__file__).resolve().parent
GATE = "proc.py"
BANNED = {"run", "Popen", "check_output", "check_call", "call", "getoutput", "getstatusoutput"}


def _direct_subprocess_calls():
    found = []
    for module in sorted(ENFORCER_DIR.glob("*.py")):
        if module.name == GATE:
            continue
        tree = ast.parse(module.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            func = node.func
            if (
                isinstance(func, ast.Attribute)
                and func.attr in BANNED
                and isinstance(func.value, ast.Name)
                and func.value.id == "subprocess"
            ):
                found.append(f"{module.name}:{node.lineno} subprocess.{func.attr}")
    return found


def test_only_the_gate_calls_subprocess():
    assert _direct_subprocess_calls() == []


def test_run_always_detaches_stdin(monkeypatch):
    seen = {}
    real_run = subprocess.run

    def spy(command, **kwargs):
        seen.update(kwargs)
        return real_run(command, **kwargs)

    monkeypatch.setattr(subprocess, "run", spy)
    proc.run([sys.executable, "-c", "pass"], capture_output=True)
    assert seen["stdin"] is subprocess.DEVNULL


def test_popen_detaches_all_three_streams(monkeypatch):
    seen = {}
    real_popen = subprocess.Popen

    class Spy(real_popen):
        def __init__(self, command, **kwargs):
            seen.update(kwargs)
            super().__init__(command, **kwargs)

    monkeypatch.setattr(subprocess, "Popen", Spy)
    proc.popen([sys.executable, "-c", "pass"]).wait(timeout=30)
    assert seen["stdin"] is subprocess.DEVNULL
    assert seen["stdout"] is subprocess.DEVNULL
    assert seen["stderr"] is subprocess.DEVNULL


def test_child_reads_empty_stdin(tmp_path):
    script = tmp_path / "read_stdin.py"
    script.write_text("import sys; sys.stdout.write(str(len(sys.stdin.read())))", encoding="utf-8")
    result = proc.run([sys.executable, str(script)], capture_output=True, timeout=30)
    assert result.stdout == b"0"


@pytest.mark.skipif(sys.platform != "win32", reason="Windows only")
def test_child_processes_get_no_console_window(monkeypatch):
    """SchTasks, NetSh, Reg, PowerShell đều là app console: không chặn cửa sổ thì màn hình nhấp nháy."""
    seen = {}
    real_run = subprocess.run

    def spy(command, **kwargs):
        seen.update(kwargs)
        return real_run(command, **kwargs)

    monkeypatch.setattr(subprocess, "run", spy)
    proc.run([sys.executable, "-c", "pass"], capture_output=True)
    assert seen["creationflags"] & subprocess.CREATE_NO_WINDOW


@pytest.mark.skipif(sys.platform != "win32", reason="Windows only")
def test_detached_flag_is_not_stacked_with_no_window():
    """DETACHED_PROCESS đã là 'không console'; thêm CREATE_NO_WINDOW vào là hai cờ xung đột."""
    flags = proc._hide_window({"creationflags": subprocess.DETACHED_PROCESS})["creationflags"]
    assert flags == subprocess.DETACHED_PROCESS


@pytest.mark.skipif(sys.platform != "win32", reason="Windows only")
def test_gate_survives_dead_console_stdin(monkeypatch):
    """Mô phỏng đúng điều kiện của exe đã FreeConsole: handle stdin cha không đọc được nữa."""
    winapi = subprocess._winapi
    real = winapi.GetStdHandle

    def broken(handle_type):
        if handle_type == winapi.STD_INPUT_HANDLE:
            raise OSError(50, "The request is not supported")
        return real(handle_type)

    monkeypatch.setattr(winapi, "GetStdHandle", broken)
    assert proc.run([sys.executable, "-c", "pass"], capture_output=True, timeout=30).returncode == 0
    assert proc.popen([sys.executable, "-c", "pass"]).wait(timeout=30) == 0
