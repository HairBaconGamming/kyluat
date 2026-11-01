from dataclasses import replace
from pathlib import Path

from enforcer import rules
from enforcer.config import Config
from enforcer.store import PENDING_KEY, Store


def base_config() -> Config:
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=("steam.exe",),
        web_domains=("youtube.com",),
        windows=(),
        session_stop_delay_minutes=10,
    )


def test_apply_lifecycle(tmp_path):
    store = Store(tmp_path / "data.db")
    first = rules.apply_candidate(store, base_config())
    assert first.state == "initial"
    assert rules.approved_config(store) == base_config()

    stricter = replace(base_config(), processes=("steam.exe", "discord.exe"))
    applied = rules.apply_candidate(store, stricter)
    assert applied.state == "applied"
    assert rules.approved_config(store) == stricter

    weaker = replace(base_config(), processes=(), cooldown_hours=12)
    queued = rules.apply_candidate(store, weaker)
    assert queued.state == "queued"
    assert queued.effective_at is not None
    assert store.get_state(PENDING_KEY)
    assert rules.approved_config(store) == stricter
    store.close()
