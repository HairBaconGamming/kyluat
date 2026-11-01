from __future__ import annotations

import re
from pathlib import Path

from enforcer.adult_list import ADULT_DOMAINS

ROOT = Path(__file__).resolve().parents[1]
ANDROID_ASSET = ROOT / "android" / "app" / "src" / "main" / "res" / "raw" / "adult_domains.txt"
DOMAIN_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$")


def test_source_list_is_clean():
    assert ADULT_DOMAINS
    assert len(set(ADULT_DOMAINS)) == len(ADULT_DOMAINS)
    for domain in ADULT_DOMAINS:
        assert DOMAIN_RE.match(domain), domain
        assert not domain.startswith("www.")
        assert domain == domain.strip().lower()


def test_android_asset_is_generated_from_source():
    assert ANDROID_ASSET.read_text(encoding="utf-8") == "".join(f"{d}\n" for d in ADULT_DOMAINS)
