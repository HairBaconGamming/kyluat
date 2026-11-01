from __future__ import annotations

from pathlib import Path

import tomlkit

from .config import DAY_NAMES, Config, Plan, Reminders


def _load(path: Path):
    if not path.exists():
        return tomlkit.document()
    return tomlkit.parse(path.read_text(encoding="utf-8"))


def _table(doc, key: str):
    if key not in doc or not isinstance(doc[key], dict):
        doc[key] = tomlkit.table()
    return doc[key]


def _save(path: Path, doc) -> None:
    path.write_text(tomlkit.dumps(doc), encoding="utf-8")


def write_rules(path: Path, config: Config) -> None:
    doc = _load(path)
    block = _table(doc, "block")
    block["processes"] = list(config.processes)
    block["kill_browsers"] = config.kill_browsers
    web = _table(doc, "web")
    web["domains"] = list(config.web_domains)

    windows = tomlkit.aot()
    for window in config.windows:
        table = tomlkit.table()
        table["label"] = window.label
        table["days"] = [DAY_NAMES[day] for day in window.days]
        table["start"] = window.start.strftime("%H:%M")
        table["end"] = window.end.strftime("%H:%M")
        windows.append(table)
    doc["focus"] = windows
    _save(path, doc)


def write_plan(path: Path, plan: Plan) -> None:
    doc = _load(path)
    table = _table(doc, "plan")
    table["study_minutes_daily"] = plan.study_minutes_daily
    table["gym_sessions_weekly"] = plan.gym_sessions_weekly
    _save(path, doc)


def write_reminders(path: Path, reminders: Reminders) -> None:
    doc = _load(path)
    table = _table(doc, "reminders")
    table["enabled"] = reminders.enabled
    table["study_check_time"] = reminders.study_check_time.strftime("%H:%M")
    _save(path, doc)
