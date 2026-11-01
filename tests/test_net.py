from pathlib import Path

from enforcer.adult_list import ADULT_DOMAINS
from enforcer.config import Config
from enforcer.net import ADULT_UPSTREAMS, UPSTREAMS, Sinkhole, domain_matches, upstreams_for
from enforcer.store import Store


def make_config(domains, adult_block=False):
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=(),
        web_domains=tuple(domains),
        windows=(),
        adult_block=adult_block,
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


def test_adult_blocks_around_the_clock(tmp_path):
    config = make_config(["youtube.com"], adult_block=True)
    idle = {"value": False}
    hole = Sinkhole(lambda: config, lambda: idle["value"], tmp_path / "dns.db", port=0)
    assert hole.blocked(ADULT_DOMAINS[0])
    assert hole.blocked(f"www.{ADULT_DOMAINS[0]}")
    assert not hole.blocked("youtube.com")
    idle["value"] = True
    assert hole.blocked("youtube.com")
    hole.store.close()


def test_adult_off_leaves_only_focus_rules(tmp_path):
    hole = Sinkhole(
        lambda: make_config(["youtube.com"]), lambda: False, tmp_path / "dns.db", port=0
    )
    assert not hole.blocked(ADULT_DOMAINS[0])
    hole.store.close()


def test_family_filter_upstream_only_when_adult_on():
    assert upstreams_for(make_config([])) == UPSTREAMS
    assert upstreams_for(make_config([], adult_block=True)) == ADULT_UPSTREAMS


def test_adult_event_counts_as_a_block(tmp_path):
    store = Store(tmp_path / "blocks.db")
    store.event("block_adult", ADULT_DOMAINS[0])
    assert store.blocks_today() == 1
    store.close()
