from __future__ import annotations

APP_CATALOG = (
    {"name": "Steam", "category": "Trò chơi", "processes": ("steam.exe", "steamwebhelper.exe")},
    {"name": "Discord", "category": "Trò chuyện", "processes": ("discord.exe",)},
    {"name": "TikTok", "category": "Mạng xã hội", "processes": ("tiktok.exe",)},
    {"name": "Epic Games", "category": "Trò chơi", "processes": ("epicgameslauncher.exe",)},
    {"name": "League of Legends", "category": "Trò chơi", "processes": ("leagueclient.exe", "leagueclientux.exe")},
    {"name": "Facebook Messenger", "category": "Trò chuyện", "processes": ("messenger.exe",)},
)

WEB_CATALOG = (
    {"name": "YouTube", "category": "Video", "domains": ("youtube.com", "youtu.be")},
    {"name": "Facebook", "category": "Mạng xã hội", "domains": ("facebook.com",)},
    {"name": "Instagram", "category": "Mạng xã hội", "domains": ("instagram.com",)},
    {"name": "TikTok", "category": "Mạng xã hội", "domains": ("tiktok.com",)},
    {"name": "Reddit", "category": "Diễn đàn", "domains": ("reddit.com",)},
    {"name": "X / Twitter", "category": "Mạng xã hội", "domains": ("x.com", "twitter.com")},
)


def app_enabled(processes: tuple[str, ...], entry: dict) -> bool:
    current = {name.lower() for name in processes}
    return all(item.lower() in current for item in entry["processes"])


def web_enabled(domains: tuple[str, ...], entry: dict) -> bool:
    current = {name.lower() for name in domains}
    return all(item.lower() in current for item in entry["domains"])


def set_app(processes: tuple[str, ...], entry: dict, enabled: bool) -> tuple[str, ...]:
    values = list(processes)
    lowered = {name.lower() for name in values}
    for item in entry["processes"]:
        if enabled and item.lower() not in lowered:
            values.append(item)
        if not enabled:
            values = [value for value in values if value.lower() != item.lower()]
    return tuple(values)


def set_web(domains: tuple[str, ...], entry: dict, enabled: bool) -> tuple[str, ...]:
    values = list(domains)
    lowered = {name.lower() for name in values}
    for item in entry["domains"]:
        if enabled and item.lower() not in lowered:
            values.append(item)
        if not enabled:
            values = [value for value in values if value.lower() != item.lower()]
    return tuple(values)


PROCESS_LABELS = {
    process.lower(): entry["name"]
    for entry in APP_CATALOG
    for process in entry["processes"]
}
DOMAIN_LABELS = {
    domain.lower(): entry["name"]
    for entry in WEB_CATALOG
    for domain in entry["domains"]
}


def display_block(name: str) -> str:
    key = name.strip().lower()
    if key in PROCESS_LABELS:
        return PROCESS_LABELS[key]
    if key in DOMAIN_LABELS:
        return DOMAIN_LABELS[key]
    if key.endswith(".exe"):
        return name[:-4]
    return name
