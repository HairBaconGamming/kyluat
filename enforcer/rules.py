from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timedelta

from .config import Config, weakening_changes
from .store import APPROVED_KEY, PENDING_KEY, Store, now_iso


@dataclass(frozen=True)
class ApplyResult:
    state: str
    reasons: tuple[str, ...] = ()
    effective_at: datetime | None = None


def approved_config(store: Store) -> Config | None:
    text = store.get_state(APPROVED_KEY)
    if not text:
        return None
    try:
        return Config.from_json(text)
    except (ValueError, KeyError, TypeError):
        return None


def apply_candidate(store: Store, candidate: Config, now: datetime | None = None) -> ApplyResult:
    now = now or datetime.now()
    approved = approved_config(store)
    if approved is None:
        store.set_state(APPROVED_KEY, candidate.to_json())
        store.event("apply", "khởi tạo luật gốc")
        return ApplyResult("initial")

    reasons = weakening_changes(candidate, approved)
    if not reasons:
        store.set_state(APPROVED_KEY, candidate.to_json())
        store.del_state(PENDING_KEY)
        store.event("apply", "siết chặt hoặc không đổi")
        return ApplyResult("applied")

    effective_at = now + timedelta(hours=approved.cooldown_hours)
    pending = {
        "config": candidate.to_dict(),
        "requested_at": now_iso(),
        "effective_at": effective_at.isoformat(timespec="seconds"),
    }
    store.set_state(PENDING_KEY, json.dumps(pending, ensure_ascii=False))
    store.event("queue", "; ".join(reasons))
    return ApplyResult("queued", tuple(reasons), effective_at)
