from datetime import datetime, time, timedelta
from pathlib import Path

import pytest

from enforcer import sessions
from enforcer.config import Config, FocusWindow
from enforcer.dashboard import build_summary
from enforcer.store import APPROVED_KEY, Store


def test_session_lifecycle(tmp_path):
    store = Store(tmp_path / "data.db")
    now = datetime(2026, 10, 31, 9, 0)
    session = sessions.start(store, 60, "Học", now)
    assert session.end == now + timedelta(minutes=60)
    assert sessions.current(store, now + timedelta(minutes=10)) is not None

    with pytest.raises(ValueError):
        sessions.start(store, 30, "Khác", now)

    extended = sessions.extend(store, 15, now + timedelta(minutes=10))
    assert extended.end == now + timedelta(minutes=75)

    stopping = sessions.request_stop(store, 10, now + timedelta(minutes=20))
    assert stopping.stop_after == now + timedelta(minutes=30)
    assert sessions.current(store, now + timedelta(minutes=29)) is not None
    assert sessions.current(store, now + timedelta(minutes=31)) is None
    assert store.get_state(sessions.SESSION_KEY) is None
    store.close()


def test_session_cancel_stop(tmp_path):
    store = Store(tmp_path / "data.db")
    now = datetime(2026, 10, 31, 9, 0)
    sessions.start(store, 60, "Học", now)
    sessions.request_stop(store, 10, now)
    cancelled = sessions.cancel_stop(store, now)
    assert cancelled.stop_after is None
    assert sessions.current(store, now + timedelta(minutes=30)) is not None
    store.close()


def test_session_expires_by_end(tmp_path):
    store = Store(tmp_path / "data.db")
    now = datetime(2026, 10, 31, 9, 0)
    sessions.start(store, 5, "Ngắn", now)
    assert sessions.current(store, now + timedelta(minutes=5)) is None
    store.close()


def test_validation(tmp_path):
    store = Store(tmp_path / "data.db")
    now = datetime(2026, 10, 31, 9, 0)
    with pytest.raises(ValueError):
        sessions.start(store, 1, "Quá ngắn", now)
    sessions.start(store, 30, "Ổn", now)
    with pytest.raises(ValueError):
        sessions.extend(store, 0, now)
    store.close()


def test_summary_distinguishes_schedule_from_manual_session(tmp_path, monkeypatch):
    now = datetime(2026, 11, 2, 16, 0)

    class FixedDateTime(datetime):
        @classmethod
        def now(cls, tz=None):
            return cls(2026, 11, 2, 16, 0, tzinfo=tz)

    monkeypatch.setattr("enforcer.dashboard.datetime", FixedDateTime)
    db = tmp_path / "data.db"
    approved = Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=(),
        windows=(FocusWindow((0,), time(13, 0), time(17, 0), "Ca chiều"),),
    )
    store = Store(db)
    store.set_state(APPROVED_KEY, approved.to_json())
    store.close()

    def summary():
        return build_summary(db, tmp_path / "config.toml", tmp_path / "lock")

    scheduled = summary()
    assert scheduled["focus"] == {
        "active": True,
        "text": "Đang focus «Ca chiều»",
        "detail": "đến 17:00",
    }
    assert scheduled["session"] is None

    store = Store(db)
    sessions.start(store, 25, "Học", now)
    store.close()
    manual = summary()
    assert manual["session"]["label"] == "Học"
    assert manual["session"]["remaining_minutes"] == 25

    store = Store(db)
    sessions.request_stop(store, 10, now)
    store.close()
    stopping = summary()
    assert stopping["session"]["stop_after"] == (now + timedelta(minutes=10)).isoformat(timespec="seconds")

    store = Store(db)
    sessions.cancel_stop(store, now)
    store.close()
    cancelled = summary()
    assert cancelled["session"]["stop_after"] is None
