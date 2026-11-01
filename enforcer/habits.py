from __future__ import annotations

from datetime import date, timedelta

from .config import Plan
from .store import Store

WEEKDAY_VI = ("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "CN")
WEEKDAY_SHORT = ("T2", "T3", "T4", "T5", "T6", "T7", "CN")


def day_label(day: date) -> str:
    return f"{WEEKDAY_SHORT[day.weekday()]} {day:%d/%m}"


def _day_streak(dates: set[str], today: date) -> int:
    cursor = today if today.isoformat() in dates else today - timedelta(days=1)
    count = 0
    while cursor.isoformat() in dates:
        count += 1
        cursor -= timedelta(days=1)
    return count


def _week_streak(counts: dict[str, int], goal: int, today: date) -> int:
    if goal <= 0:
        return 0
    start = today - timedelta(days=today.weekday())

    def week_total(week_start: date) -> int:
        return sum(counts.get((week_start + timedelta(days=offset)).isoformat(), 0) for offset in range(7))

    cursor = start
    if week_total(cursor) < goal:
        cursor -= timedelta(days=7)
    count = 0
    while week_total(cursor) >= goal and count < 520:
        count += 1
        cursor -= timedelta(days=7)
    return count


def summary(store: Store, plan: Plan, days: int = 7) -> dict:
    today = date.today()
    start = today - timedelta(days=days - 1)
    week_start = today - timedelta(days=today.weekday())
    dates = [start + timedelta(days=offset) for offset in range(days)]

    study_minutes = store.minutes_by_day("study", start.isoformat())
    gym_counts = store.count_by_day("gym", (today - timedelta(days=730)).isoformat())
    block_counts = store.blocks_by_day(start.isoformat())

    study_series = [
        {"date": day.isoformat(), "label": day_label(day), "value": study_minutes.get(day.isoformat(), 0)}
        for day in dates
    ]
    gym_series = [
        {"date": day.isoformat(), "label": day_label(day), "value": gym_counts.get(day.isoformat(), 0)}
        for day in dates
    ]
    block_series = [
        {"date": day.isoformat(), "label": day_label(day), "value": block_counts.get(day.isoformat(), 0)}
        for day in dates
    ]
    week_study = sum(study_minutes.get(day.isoformat(), 0) for day in dates if day >= week_start)
    week_gym = sum(gym_counts.get(day.isoformat(), 0) for day in dates if day >= week_start)
    first_day = store.first_checkin_date()
    missed = [
        day.isoformat()
        for day in dates
        if week_start <= day < today
        and first_day is not None
        and day.isoformat() >= first_day
        and study_minutes.get(day.isoformat(), 0) == 0
    ]
    return {
        "study": {
            "today": study_minutes.get(today.isoformat(), 0),
            "goal": plan.study_minutes_daily,
            "week": week_study,
            "week_goal": plan.study_minutes_daily * 7,
            "streak": _day_streak(store.checkin_dates("study"), today),
            "missed": missed,
            "series": study_series,
        },
        "gym": {
            "today": gym_counts.get(today.isoformat(), 0),
            "week": week_gym,
            "goal": plan.gym_sessions_weekly,
            "streak_weeks": _week_streak(gym_counts, plan.gym_sessions_weekly, today),
            "series": gym_series,
        },
        "blocks": {
            "today": store.blocks_today(),
            "series": block_series,
            "top": store.top_blocked(start.isoformat(), 3),
        },
    }
