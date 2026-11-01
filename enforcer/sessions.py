from __future__ import annotations

import json
import math
from dataclasses import dataclass, replace
from datetime import datetime, timedelta

from .store import Store

SESSION_KEY = "active_session"
MIN_MINUTES = 5
MAX_MINUTES = 480


@dataclass(frozen=True)
class Session:
    label: str
    start: datetime
    end: datetime
    stop_after: datetime | None = None

    def to_json(self) -> str:
        data = {
            "label": self.label,
            "start": self.start.isoformat(timespec="seconds"),
            "end": self.end.isoformat(timespec="seconds"),
            "stop_after": self.stop_after.isoformat(timespec="seconds") if self.stop_after else None,
        }
        return json.dumps(data, ensure_ascii=False)

    @classmethod
    def from_json(cls, text: str) -> "Session":
        data = json.loads(text)
        stop_after = data.get("stop_after")
        return cls(
            label=str(data["label"]),
            start=datetime.fromisoformat(data["start"]),
            end=datetime.fromisoformat(data["end"]),
            stop_after=datetime.fromisoformat(stop_after) if stop_after else None,
        )

    def remaining_minutes(self, now: datetime) -> int:
        return max(0, math.ceil((self.end - now).total_seconds() / 60))


def load(store: Store) -> Session | None:
    text = store.get_state(SESSION_KEY)
    if not text:
        return None
    try:
        return Session.from_json(text)
    except (ValueError, KeyError, TypeError):
        store.del_state(SESSION_KEY)
        return None


def current(store: Store, now: datetime) -> Session | None:
    session = load(store)
    if session is None:
        return None
    if session.stop_after is not None and now >= session.stop_after:
        _finish(store, session, "dừng theo yêu cầu")
        return None
    if now >= session.end:
        _finish(store, session, "hết giờ")
        return None
    return session


def start(store: Store, minutes: int, label: str, now: datetime) -> Session:
    active = current(store, now)
    if active is not None:
        raise ValueError(f"đang có phiên «{active.label}» đến {active.end:%H:%M}")
    if not MIN_MINUTES <= minutes <= MAX_MINUTES:
        raise ValueError(f"số phút phải trong {MIN_MINUTES}..{MAX_MINUTES}")
    clean_label = (label.strip() or "Focus")[:60]
    session = Session(label=clean_label, start=now, end=now + timedelta(minutes=minutes))
    store.set_state(SESSION_KEY, session.to_json())
    store.event("session_start", f"{clean_label} · {minutes} phút")
    return session


def extend(store: Store, minutes: int, now: datetime) -> Session:
    session = current(store, now)
    if session is None:
        raise ValueError("không có phiên nào đang chạy")
    if not 1 <= minutes <= MAX_MINUTES:
        raise ValueError(f"số phút phải trong 1..{MAX_MINUTES}")
    session = replace(session, end=session.end + timedelta(minutes=minutes))
    store.set_state(SESSION_KEY, session.to_json())
    store.event("session_extend", f"+{minutes} phút · đến {session.end:%H:%M}")
    return session


def request_stop(store: Store, delay_minutes: int, now: datetime) -> Session:
    session = current(store, now)
    if session is None:
        raise ValueError("không có phiên nào đang chạy")
    if session.stop_after is not None:
        return session
    session = replace(session, stop_after=now + timedelta(minutes=delay_minutes))
    store.set_state(SESSION_KEY, session.to_json())
    store.event("session_stop_request", f"dừng sau {delay_minutes} phút")
    return session


def cancel_stop(store: Store, now: datetime) -> Session:
    session = current(store, now)
    if session is None:
        raise ValueError("không có phiên nào đang chạy")
    if session.stop_after is None:
        return session
    session = replace(session, stop_after=None)
    store.set_state(SESSION_KEY, session.to_json())
    store.event("session_stop_cancel", "")
    return session


def _finish(store: Store, session: Session, reason: str) -> None:
    store.del_state(SESSION_KEY)
    store.event("session_end", f"{session.label} · {reason}")


def as_dict(session: Session | None, now: datetime) -> dict | None:
    if session is None:
        return None
    return {
        "label": session.label,
        "start": session.start.isoformat(timespec="seconds"),
        "end": session.end.isoformat(timespec="seconds"),
        "stop_after": session.stop_after.isoformat(timespec="seconds") if session.stop_after else None,
        "remaining_minutes": session.remaining_minutes(now),
    }


def apply_action(
    store: Store,
    action: str,
    minutes: int,
    label: str,
    delay_minutes: int,
    now: datetime,
) -> Session:
    if action == "start":
        return start(store, minutes, label, now)
    if action == "stop":
        return request_stop(store, delay_minutes, now)
    if action == "cancel_stop":
        return cancel_stop(store, now)
    if action == "extend":
        return extend(store, minutes, now)
    raise ValueError("hành động không hợp lệ")
