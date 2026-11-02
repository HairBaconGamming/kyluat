from dataclasses import replace

from PIL import Image

from enforcer import visual
from enforcer.config import Config, weakening_changes
from enforcer.visual import THRESHOLD, VisualGuard, model_available


def base_config() -> Config:
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=(),
        windows=(),
    )


def test_model_is_packaged_and_loads():
    assert model_available()
    guard = VisualGuard()
    try:
        guard._load()
        scores = guard._scan(Image.new("RGB", (960, 640), (128, 128, 128)))
    finally:
        guard.close()
    assert 0.0 <= scores < 1.0
    assert scores < THRESHOLD


def test_disabling_visual_guard_is_immediate():
    enabled = replace(base_config(), adult_visual=True)
    disabled = replace(enabled, adult_visual=False)
    assert weakening_changes(disabled, enabled) == []


def test_detection_minimises_the_browser(monkeypatch):
    guard = VisualGuard()
    calls = []
    guard._load = lambda: None
    guard._scan = lambda image: 1.0
    monkeypatch.setattr(visual, "_foreground", lambda: (123, "chrome.exe"))
    monkeypatch.setattr(visual, "_window_box", lambda hwnd: (0, 0, 800, 600))
    monkeypatch.setattr(visual, "_minimize", lambda hwnd: calls.append(hwnd))
    monkeypatch.setattr(visual.notify, "toast_async", lambda *args: calls.append("toast"))
    monkeypatch.setattr("PIL.ImageGrab.grab", lambda **kwargs: Image.new("RGB", (800, 600)))
    try:
        assert guard.check() is True
    finally:
        guard.close()
    assert 123 in calls
    assert "toast" in calls
