from __future__ import annotations

import json
import tomllib
from dataclasses import dataclass
from datetime import time
from pathlib import Path

DAY_NAMES = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")
BROWSERS = ("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe")


class ConfigError(ValueError):
    pass


def parse_time(value: str) -> time:
    try:
        hour_text, minute_text = value.split(":")
        return time(int(hour_text), int(minute_text))
    except (ValueError, AttributeError):
        raise ConfigError(f"giờ không hợp lệ: {value!r} (định dạng HH:MM)") from None


@dataclass(frozen=True)
class FocusWindow:
    days: tuple[int, ...]
    start: time
    end: time
    label: str

    def to_dict(self) -> dict:
        return {
            "days": list(self.days),
            "start": self.start.strftime("%H:%M"),
            "end": self.end.strftime("%H:%M"),
            "label": self.label,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "FocusWindow":
        return cls(
            days=tuple(int(day) for day in data["days"]),
            start=parse_time(data["start"]),
            end=parse_time(data["end"]),
            label=str(data["label"]),
        )

    def minutes(self) -> int:
        span = (self.end.hour * 60 + self.end.minute) - (self.start.hour * 60 + self.start.minute)
        return span if span > 0 else span + 24 * 60


@dataclass(frozen=True)
class Config:
    poll_seconds: int
    cooldown_hours: float
    kill_browsers: bool
    processes: tuple[str, ...]
    web_domains: tuple[str, ...]
    windows: tuple[FocusWindow, ...]
    session_stop_delay_minutes: int = 10

    def to_dict(self) -> dict:
        return {
            "poll_seconds": self.poll_seconds,
            "cooldown_hours": self.cooldown_hours,
            "kill_browsers": self.kill_browsers,
            "processes": list(self.processes),
            "web_domains": list(self.web_domains),
            "windows": [window.to_dict() for window in self.windows],
            "session_stop_delay_minutes": self.session_stop_delay_minutes,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "Config":
        return cls(
            poll_seconds=int(data.get("poll_seconds", 3)),
            cooldown_hours=float(data.get("cooldown_hours", 24)),
            kill_browsers=bool(data.get("kill_browsers", False)),
            processes=tuple(str(name) for name in data.get("processes", [])),
            web_domains=tuple(str(domain) for domain in data.get("web_domains", [])),
            windows=tuple(FocusWindow.from_dict(item) for item in data.get("windows", [])),
            session_stop_delay_minutes=int(data.get("session_stop_delay_minutes", 10)),
        )

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), ensure_ascii=False)

    @classmethod
    def from_json(cls, text: str) -> "Config":
        return cls.from_dict(json.loads(text))

    def weekly_focus_minutes(self) -> int:
        return sum(window.minutes() * len(window.days) for window in self.windows)

    def blocked_patterns(self) -> tuple[str, ...]:
        extra = BROWSERS if self.kill_browsers else ()
        return tuple(dict.fromkeys(name.lower() for name in (*self.processes, *extra)))


def load_config(path: Path) -> Config:
    try:
        raw = tomllib.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise ConfigError(f"không tìm thấy {path}") from None
    except tomllib.TOMLDecodeError as exc:
        raise ConfigError(f"TOML sai cú pháp: {exc}") from None

    general = raw.get("general", {})
    block = raw.get("block", {})
    web = raw.get("web", {})
    focus_items = raw.get("focus", [])
    if not isinstance(general, dict) or not isinstance(block, dict) or not isinstance(web, dict):
        raise ConfigError("'general', 'block' và 'web' phải là bảng ([general], [block], [web])")
    if not isinstance(focus_items, list) or any(not isinstance(item, dict) for item in focus_items):
        raise ConfigError("mỗi khung giờ phải là một bảng (dùng [[focus]])")
    processes_raw = block.get("processes", [])
    if not isinstance(processes_raw, list) or any(not isinstance(name, str) for name in processes_raw):
        raise ConfigError("'processes' phải là mảng chuỗi")
    domains_raw = web.get("domains", [])
    if not isinstance(domains_raw, list) or any(not isinstance(domain, str) for domain in domains_raw):
        raise ConfigError("'web.domains' phải là mảng chuỗi")
    domains: list[str] = []
    for item in domains_raw:
        domain = item.strip().lower().strip(".")
        if not domain or "://" in domain or "/" in domain or " " in domain:
            raise ConfigError(f"domain không hợp lệ: {item!r} (chỉ nhập tên miền, vd youtube.com)")
        domains.append(domain)
    domains = list(dict.fromkeys(domains))

    windows: list[FocusWindow] = []
    for index, item in enumerate(focus_items, start=1):
        days: list[int] = []
        raw_days = item.get("days", [])
        if not isinstance(raw_days, list):
            raise ConfigError(f"focus #{index}: 'days' phải là mảng")
        for name in raw_days:
            key = str(name).strip().lower()[:3]
            if key not in DAY_NAMES:
                raise ConfigError(f"focus #{index}: thứ không hợp lệ: {name!r}")
            days.append(DAY_NAMES.index(key))
        if not days:
            raise ConfigError(f"focus #{index}: thiếu 'days'")
        start = parse_time(item.get("start", ""))
        end = parse_time(item.get("end", ""))
        if start == end:
            raise ConfigError(f"focus #{index}: start trùng end")
        windows.append(FocusWindow(tuple(sorted(set(days))), start, end, str(item.get("label", f"phiên {index}"))))

    try:
        poll_seconds = int(general.get("poll_seconds", 3))
        cooldown_hours = float(general.get("cooldown_hours", 24))
        session_delay = int(general.get("session_stop_delay_minutes", 10))
    except (TypeError, ValueError):
        raise ConfigError("poll_seconds, cooldown_hours và session_stop_delay_minutes phải là số") from None
    if poll_seconds < 1:
        raise ConfigError("poll_seconds phải >= 1")
    if cooldown_hours <= 0:
        raise ConfigError("cooldown_hours phải > 0")
    if not 0 <= session_delay <= 120:
        raise ConfigError("session_stop_delay_minutes phải trong 0..120")
    return Config(
        poll_seconds=poll_seconds,
        cooldown_hours=cooldown_hours,
        kill_browsers=bool(block.get("kill_browsers", False)),
        processes=tuple(str(name) for name in processes_raw),
        web_domains=tuple(domains),
        windows=tuple(windows),
        session_stop_delay_minutes=session_delay,
    )


def weakening_changes(new: Config, old: Config) -> list[str]:
    reasons: list[str] = []
    removed = sorted(set(name.lower() for name in old.processes) - set(name.lower() for name in new.processes))
    if removed:
        reasons.append("bỏ chặn app: " + ", ".join(removed))
    removed_domains = sorted(set(old.web_domains) - set(new.web_domains))
    if removed_domains:
        reasons.append("bỏ chặn web: " + ", ".join(removed_domains))
    old_minutes, new_minutes = old.weekly_focus_minutes(), new.weekly_focus_minutes()
    if new_minutes < old_minutes:
        reasons.append(f"giảm giờ focus: {old_minutes} → {new_minutes} phút/tuần")
    if new.cooldown_hours < old.cooldown_hours:
        reasons.append(f"giảm cooldown: {old.cooldown_hours:g}h → {new.cooldown_hours:g}h")
    if old.kill_browsers and not new.kill_browsers:
        reasons.append("tắt chặn trình duyệt")
    if new.session_stop_delay_minutes < old.session_stop_delay_minutes:
        reasons.append(
            f"giảm chờ dừng phiên: {old.session_stop_delay_minutes} → {new.session_stop_delay_minutes} phút"
        )
    return reasons


@dataclass(frozen=True)
class Plan:
    study_minutes_daily: int = 120
    gym_sessions_weekly: int = 4


def load_plan(path: Path) -> Plan:
    try:
        raw = tomllib.loads(path.read_text(encoding="utf-8")).get("plan", {})
        if not isinstance(raw, dict):
            return Plan()
        return Plan(
            study_minutes_daily=max(0, int(raw.get("study_minutes_daily", 120))),
            gym_sessions_weekly=max(0, int(raw.get("gym_sessions_weekly", 4))),
        )
    except (OSError, ValueError, TypeError):
        return Plan()


@dataclass(frozen=True)
class Reminders:
    enabled: bool = True
    study_check_time: time = time(21, 0)


def load_reminders(path: Path) -> Reminders:
    try:
        raw = tomllib.loads(path.read_text(encoding="utf-8")).get("reminders", {})
        if not isinstance(raw, dict):
            return Reminders()
        return Reminders(
            enabled=bool(raw.get("enabled", True)),
            study_check_time=parse_time(str(raw.get("study_check_time", "21:00"))),
        )
    except (OSError, ValueError, TypeError):
        return Reminders()
