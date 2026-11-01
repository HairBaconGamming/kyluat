from pathlib import Path

from enforcer.config import Config
from enforcer.net import Sinkhole, domain_matches


def make_config(domains):
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=tuple(domains),
        windows=(),
    )


def test_domain_matches():
    assert domain_matches("youtube.com", "youtube.com")
    assert domain_matches("WWW.YouTube.com.", "youtube.com")
    assert domain_matches("m.youtube.com", "youtube.com")
    assert not domain_matches("notyoutube.com", "youtube.com")
    assert not domain_matches("youtube.com.evil.example", "youtube.com")


def test_sinkhole_respects_focus_state(tmp_path):
    config = make_config(["youtube.com"])
    active = {"value": True}
    hole = Sinkhole(lambda: config, lambda: active["value"], tmp_path / "dns.db", port=0)
    assert hole.blocked("youtube.com")
    assert hole.blocked("www.youtube.com")
    assert not hole.blocked("example.com")
    active["value"] = False
    assert not hole.blocked("youtube.com")
    hole.store.close()


def test_sinkhole_without_domains(tmp_path):
    hole = Sinkhole(lambda: make_config([]), lambda: True, tmp_path / "dns.db", port=0)
    assert not hole.blocked("youtube.com")
    hole.store.close()
