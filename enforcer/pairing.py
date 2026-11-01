from __future__ import annotations

import hmac
import json
import secrets
from datetime import datetime, timedelta
from io import BytesIO, StringIO

import segno

from .store import Store

PAIR_KEY = "pair_code"
PAIR_TTL_SECONDS = 300
PAIR_MAX_ATTEMPTS = 5


def issue_code(store: Store, now: datetime | None = None, ttl: int = PAIR_TTL_SECONDS) -> str:
    now = now or datetime.now()
    code = str(100000 + secrets.randbelow(900000))
    data = {
        "code": code,
        "issued_at": now.isoformat(timespec="seconds"),
        "expires_at": (now + timedelta(seconds=ttl)).isoformat(timespec="seconds"),
        "attempts": 0,
    }
    store.set_state(PAIR_KEY, json.dumps(data))
    store.event("pair_code", "tạo mã kết nối")
    return code


def verify_code(store: Store, code: str, now: datetime | None = None) -> bool:
    now = now or datetime.now()
    text = store.get_state(PAIR_KEY)
    if not text:
        return False
    try:
        data = json.loads(text)
        expires_at = datetime.fromisoformat(data["expires_at"])
    except (ValueError, KeyError, TypeError):
        store.del_state(PAIR_KEY)
        return False
    if now >= expires_at:
        store.del_state(PAIR_KEY)
        return False

    attempts = int(data.get("attempts", 0)) + 1
    data["attempts"] = attempts
    store.set_state(PAIR_KEY, json.dumps(data))
    if attempts > PAIR_MAX_ATTEMPTS:
        store.del_state(PAIR_KEY)
        return False

    ok = hmac.compare_digest(str(data.get("code", "")), str(code))
    if ok:
        store.del_state(PAIR_KEY)
        store.set_state("pair_success_at", now.isoformat(timespec="seconds"))
    return ok


def qr_payload(host: str, port: int, code: str) -> str:
    return f"kyluat://pair?host={host}&port={port}&code={code}"


def qr_png(payload: str, scale: int = 6) -> bytes:
    qr = segno.make(payload, error="m")
    buffer = BytesIO()
    qr.save(buffer, kind="png", scale=scale, border=2, dark="#12171b", light="#ffffff")
    return buffer.getvalue()


def qr_ascii(payload: str) -> str:
    qr = segno.make(payload, error="m")
    buffer = StringIO()
    qr.terminal(out=buffer, compact=True)
    return buffer.getvalue()
