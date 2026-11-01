from datetime import date, timedelta
from pathlib import Path

from enforcer.config import Plan
from enforcer.habits import _week_streak, summary
from enforcer.store import Store


def seed(store: Store, habit: str, ts: str, minutes: int = 60) -> None:
    store.conn.execute(
        "INSERT INTO checkins(ts, habit, minutes, note) VALUES(?, ?, ?, '')",
        (ts, habit, minutes),
    )
    store.conn.commit()


def test_study_totals_and_streak(tmp_path):
    store = Store(tmp_path / "data.db")
    today = date.today()
    for offset in (2, 1, 0):
        day = today - timedelta(days=offset)
        seed(store, "study", f"{day.isoformat()}T09:00:00", 30)
    data = summary(store, Plan(study_minutes_daily=120, gym_sessions_weekly=4))
    assert data["study"]["today"] == 30
    assert data["study"]["streak"] == 3
    store.close()


def test_missed_starts_from_first_checkin(tmp_path):
    store = Store(tmp_path / "data.db")
    today = date.today()
    first = today - timedelta(days=2)
    seed(store, "study", f"{first.isoformat()}T09:00:00", 30)
    data = summary(store, Plan())
    week_start = today - timedelta(days=today.weekday())
    yesterday = today - timedelta(days=1)
    expected = [yesterday.isoformat()] if yesterday >= week_start else []
    assert data["study"]["missed"] == expected
    store.close()


def test_week_streak_uses_completed_weeks():
    today = date(2026, 10, 26)
    counts = {}
    for week_offset in (0, 1, 2):
        start = today - timedelta(weeks=week_offset)
        for offset in range(4):
            counts[(start + timedelta(days=offset)).isoformat()] = 1
    counts.pop((today + timedelta(days=3)).isoformat())
    assert _week_streak(counts, 4, today) == 2
