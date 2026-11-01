from pathlib import Path

import pytest

from enforcer import cli, rules
from enforcer.config import (
    Config,
    ConfigError,
    load_config,
    load_plan,
    load_reminders,
    weakening_changes,
)
from enforcer.store import Store

CONFIG = """
[general]
poll_seconds = 3
cooldown_hours = 24
session_stop_delay_minutes = 10

[block]
processes = ["steam.exe"]
kill_browsers = false

[web]
domains = ["youtube.com", "https://bad.example"]

[[focus]]
label = "Học"
days = ["mon"]
start = "08:00"
end = "09:00"

[plan]
study_minutes_daily = 90
gym_sessions_weekly = 3

[reminders]
enabled = true
study_check_time = "20:30"
"""


def write(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "config.toml"
    path.write_text(text, encoding="utf-8")
    return path


def test_load_config(tmp_path):
    path = write(tmp_path, CONFIG.replace('"https://bad.example"', '"twitter.com"'))
    config = load_config(path)
    assert config.processes == ("steam.exe",)
    assert config.web_domains == ("youtube.com", "twitter.com")
    assert config.session_stop_delay_minutes == 10
    assert config.weekly_focus_minutes() == 60
    plan = load_plan(path)
    assert plan.study_minutes_daily == 90
    assert plan.gym_sessions_weekly == 3
    reminders = load_reminders(path)
    assert reminders.enabled
    assert reminders.study_check_time.strftime("%H:%M") == "20:30"


def test_invalid_domain(tmp_path):
    path = write(tmp_path, CONFIG)
    with pytest.raises(ConfigError):
        load_config(path)


def test_weakening_rules():
    base = Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=("steam.exe", "discord.exe"),
        web_domains=("youtube.com",),
        windows=(),
        session_stop_delay_minutes=10,
    )
    weaker = Config(
        poll_seconds=3,
        cooldown_hours=12,
        kill_browsers=False,
        processes=("steam.exe",),
        web_domains=(),
        windows=(),
        session_stop_delay_minutes=5,
    )
    reasons = weakening_changes(weaker, base)
    assert any("discord.exe" in reason for reason in reasons)
    assert any("web" in reason for reason in reasons)
    assert any("cooldown" in reason for reason in reasons)
    assert any("chờ dừng phiên" in reason for reason in reasons)


def test_strengthening_is_allowed():
    base = Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=(),
        windows=(),
    )
    stricter = Config(
        poll_seconds=3,
        cooldown_hours=48,
        kill_browsers=False,
        processes=("steam.exe",),
        web_domains=("youtube.com",),
        windows=(),
        session_stop_delay_minutes=30,
    )
    assert weakening_changes(stricter, base) == []


def test_seed_initial_law_runs_once(tmp_path, monkeypatch):
    path = write(tmp_path, CONFIG.replace('"https://bad.example"', '"twitter.com"'))
    monkeypatch.setattr(cli, "CONFIG_PATH", path)
    store = Store(tmp_path / "law.db")
    try:
        assert cli.seed_initial_law(store)
        assert not cli.seed_initial_law(store)
        assert rules.approved_config(store).processes == ("steam.exe",)
    finally:
        store.close()


def test_seed_initial_law_needs_valid_config(tmp_path, monkeypatch):
    monkeypatch.setattr(cli, "CONFIG_PATH", write(tmp_path, CONFIG))
    store = Store(tmp_path / "law.db")
    try:
        assert not cli.seed_initial_law(store)
        assert rules.approved_config(store) is None
    finally:
        store.close()
