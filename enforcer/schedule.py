from __future__ import annotations

from datetime import datetime, timedelta

from .config import Config, FocusWindow


def window_active(window: FocusWindow, now: datetime) -> bool:
    if window.start <= window.end:
        return now.weekday() in window.days and window.start <= now.time() < window.end
    if now.weekday() in window.days and now.time() >= window.start:
        return True
    return ((now.weekday() - 1) % 7) in window.days and now.time() < window.end


def current_window(config: Config, now: datetime) -> FocusWindow | None:
    for window in config.windows:
        if window_active(window, now):
            return window
    return None


def next_start(config: Config, now: datetime) -> datetime | None:
    upcoming: list[datetime] = []
    for offset in range(8):
        day = now.date() + timedelta(days=offset)
        for window in config.windows:
            if day.weekday() in window.days:
                candidate = datetime.combine(day, window.start)
                if candidate > now:
                    upcoming.append(candidate)
    return min(upcoming) if upcoming else None


def window_end(window: FocusWindow, now: datetime) -> datetime:
    end = datetime.combine(now.date(), window.end)
    if end <= now:
        end += timedelta(days=1)
    return end
