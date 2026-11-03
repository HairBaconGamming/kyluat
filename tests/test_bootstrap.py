"""Chạy nhầm bản Python là lỗi thật đã xảy ra (ModuleNotFoundError: dnslib).

App không được phép ném traceback tiếng Anh, và daemon chết vì không nạp được module thì phải
để lại dấu vết: chính nó là cái giữ cổng 53, im lặng biến mất là máy mất phân giải tên miền.
"""

import os
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path
from types import SimpleNamespace

import pytest

from enforcer import bootstrap

ROOT = Path(__file__).resolve().parents[1]

BLOCKER = """
import sys

class Blocker:
    def find_spec(self, name, path=None, target=None):
        if name == "dnslib":
            raise ModuleNotFoundError("No module named 'dnslib'", name="dnslib")
        return None

sys.meta_path.insert(0, Blocker())
"""


def no_popup(monkeypatch):
    boxes = []
    monkeypatch.setattr(
        bootstrap.ctypes, "windll", SimpleNamespace(user32=SimpleNamespace(MessageBoxW=lambda *a: boxes.append(a)))
    )
    return boxes


def test_cli_names_the_missing_library_instead_of_a_traceback(tmp_path):
    (tmp_path / "sitecustomize.py").write_text(BLOCKER, encoding="utf-8")
    env = dict(os.environ, PYTHONPATH=str(tmp_path))
    result = subprocess.run(
        [sys.executable, "-m", "enforcer", "doctor"], cwd=ROOT, env=env, capture_output=True, timeout=120
    )
    text = (result.stdout + result.stderr).decode("utf-8", "replace")
    assert result.returncode == 1
    assert "dnslib" in text
    assert "Traceback" not in text


def test_explain_offers_the_working_interpreter(monkeypatch):
    monkeypatch.setattr(bootstrap, "interpreter_with", lambda module: r"C:\py312\python.exe")
    exc = ModuleNotFoundError("No module named 'dnslib'", name="dnslib")
    text = bootstrap.explain(exc)
    assert "thiếu thư viện dnslib" in text
    assert r'& "C:\py312\python.exe" -m enforcer doctor' in text


def test_explain_offers_pip_when_no_other_interpreter(monkeypatch):
    monkeypatch.setattr(bootstrap, "interpreter_with", lambda module: None)
    text = bootstrap.explain(ModuleNotFoundError("No module named 'dnslib'", name="dnslib"))
    assert "pip install -r requirements.txt" in text


def test_startup_failure_logs_and_shows_one_popup_then_stays_quiet(tmp_path, monkeypatch):
    boxes = no_popup(monkeypatch)
    first = bootstrap.report_startup_failure(tmp_path, "thiếu dnslib", popup=True)
    second = bootstrap.report_startup_failure(tmp_path, "thiếu dnslib", popup=True)
    assert first == second
    assert len(boxes) == 1, "task gọi mỗi phút, không được đổ hộp thoại mỗi phút"
    lines = first.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 2 and "thiếu dnslib" in lines[-1]


def test_an_old_failure_record_lets_the_popup_return(tmp_path, monkeypatch):
    boxes = no_popup(monkeypatch)
    log = tmp_path / "data" / "startup.log"
    log.parent.mkdir(parents=True)
    old = (datetime.now() - timedelta(hours=3)).isoformat(timespec="seconds")
    log.write_text(f"[{old}] thiếu dnslib\n", encoding="utf-8")
    bootstrap.report_startup_failure(tmp_path, "thiếu dnslib", popup=True)
    assert len(boxes) == 1


@pytest.mark.parametrize("text", ["a", "multi\nline"])
def test_emit_never_crashes_on_a_windows_console(text):
    bootstrap.emit(text)
