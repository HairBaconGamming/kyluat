from datetime import datetime
from pathlib import Path

from enforcer import guard as guard_module
from enforcer.config import Config
from enforcer.guard import Guard
from enforcer.store import Store


def make_config() -> Config:
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=(),
        windows=(),
        session_stop_delay_minutes=10,
    )


def test_study_reminder_fires_once_per_day(tmp_path, monkeypatch):
    config_path = tmp_path / "config.toml"
    config_path.write_text(
        '[plan]\nstudy_minutes_daily = 120\n\n[reminders]\nenabled = true\nstudy_check_time = "07:00"\n',
        encoding="utf-8",
    )
    store = Store(tmp_path / "data.db")
    guard = Guard(store, make_config(), config_path)
    calls = []
    monkeypatch.setattr(guard_module.notify, "toast_async", lambda title, message: calls.append((title, message)))
    now = datetime(2026, 10, 31, 21, 0)
    guard._check_reminders(now)
    guard._check_reminders(now)
    assert len(calls) == 1
    assert "Chưa đủ giờ học" in calls[0][0]
    store.close()


def test_reminder_respects_goal_and_time(tmp_path, monkeypatch):
    config_path = tmp_path / "config.toml"
    config_path.write_text(
        '[plan]\nstudy_minutes_daily = 30\n\n[reminders]\nenabled = true\nstudy_check_time = "07:00"\n',
        encoding="utf-8",
    )
    store = Store(tmp_path / "data.db")
    store.add_checkin("study", 45)
    guard = Guard(store, make_config(), config_path)
    calls = []
    monkeypatch.setattr(guard_module.notify, "toast_async", lambda title, message: calls.append((title, message)))
    guard._check_reminders(datetime.now().replace(hour=6, minute=0))
    guard._check_reminders(datetime.now().replace(hour=21, minute=0))
    assert calls == []
    store.close()
