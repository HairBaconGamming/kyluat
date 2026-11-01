from datetime import time
from datetime import datetime

from enforcer.config import Config, FocusWindow
from enforcer.schedule import current_window, next_start, window_active, window_end


def make_config(windows):
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=(),
        windows=tuple(windows),
        session_stop_delay_minutes=10,
    )


def test_window_normal_boundaries():
    window = FocusWindow((0,), time(8, 0), time(11, 30), "sáng")
    assert window_active(window, datetime(2026, 10, 26, 8, 0))
    assert window_active(window, datetime(2026, 10, 26, 11, 29))
    assert not window_active(window, datetime(2026, 10, 26, 11, 30))
    assert not window_active(window, datetime(2026, 10, 27, 9, 0))


def test_window_overnight():
    window = FocusWindow((0,), time(22, 0), time(2, 0), "đêm")
    assert window_active(window, datetime(2026, 10, 26, 23, 0))
    assert window_active(window, datetime(2026, 10, 27, 1, 0))
    assert not window_active(window, datetime(2026, 10, 27, 2, 0))


def test_current_and_next():
    config = make_config([FocusWindow((0,), time(8, 0), time(9, 0), "một")])
    assert current_window(config, datetime(2026, 10, 26, 8, 30)) is not None
    assert current_window(config, datetime(2026, 10, 26, 9, 30)) is None
    upcoming = next_start(config, datetime(2026, 10, 26, 9, 30))
    assert upcoming == datetime(2026, 11, 2, 8, 0)


def test_window_end_crosses_midnight():
    window = FocusWindow((0,), time(22, 0), time(2, 0), "đêm")
    end = window_end(window, datetime(2026, 10, 26, 23, 0))
    assert end == datetime(2026, 10, 27, 2, 0)
