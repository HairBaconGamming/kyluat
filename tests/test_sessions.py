from datetime import datetime, timedelta
from pathlib import Path

import pytest

from enforcer import sessions
from enforcer.store import Store


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
