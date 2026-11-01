from datetime import datetime, timedelta
from pathlib import Path

from enforcer import pairing
from enforcer.store import Store


def test_pair_code_lifecycle(tmp_path):
    store = Store(tmp_path / "data.db")
    now = datetime(2026, 10, 31, 20, 0)
    code = pairing.issue_code(store, now=now)
    assert len(code) == 6 and code.isdigit()
    assert pairing.verify_code(store, "000000", now=now + timedelta(seconds=5)) is False
    assert pairing.verify_code(store, code, now=now + timedelta(seconds=10)) is True
    assert pairing.verify_code(store, code, now=now + timedelta(seconds=15)) is False
    store.close()


def test_pair_code_expiry_and_attempts(tmp_path):
    store = Store(tmp_path / "data.db")
    now = datetime(2026, 10, 31, 20, 0)
    code = pairing.issue_code(store, now=now, ttl=30)
    assert pairing.verify_code(store, code, now=now + timedelta(seconds=31)) is False
    store.close()

    store = Store(tmp_path / "second.db")
    code = pairing.issue_code(store, now=now)
    for _ in range(pairing.PAIR_MAX_ATTEMPTS):
        assert pairing.verify_code(store, "111111", now=now + timedelta(seconds=1)) is False
    assert pairing.verify_code(store, code, now=now + timedelta(seconds=2)) is False
    store.close()


def test_qr_payload_and_png(tmp_path):
    payload = pairing.qr_payload("192.168.1.10", 8766, "123456")
    assert payload.startswith("kyluat://pair?")
    png = pairing.qr_png(payload)
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
    ascii_qr = pairing.qr_ascii(payload)
    assert len(ascii_qr) > 50
