from __future__ import annotations

import sqlite3
from datetime import datetime
from pathlib import Path

APPROVED_KEY = "approved_config"
PENDING_KEY = "pending_config"
NET_ENABLED_KEY = "net_enabled"
NET_BACKUP_KEY = "net_backup"

SCHEMA = """
CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts TEXT NOT NULL,
    kind TEXT NOT NULL,
    detail TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS checkins (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts TEXT NOT NULL,
    habit TEXT NOT NULL,
    minutes INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts);
CREATE INDEX IF NOT EXISTS idx_checkins_habit_ts ON checkins(habit, ts);
"""


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


class Store:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(path, timeout=10)
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.executescript(SCHEMA)
        self.conn.commit()

    def close(self) -> None:
        self.conn.close()

    def backup(self, folder: Path, keep: int = 7) -> Path:
        folder.mkdir(parents=True, exist_ok=True)
        target = folder / f"enforcer-{datetime.now():%Y%m%d-%H%M%S}.db"
        destination = sqlite3.connect(target)
        try:
            self.conn.backup(destination)
        finally:
            destination.close()
        for old in sorted(folder.glob("enforcer-*.db"))[:-keep]:
            try:
                old.unlink()
            except OSError:
                pass
        return target

    def get_state(self, key: str, default: str | None = None) -> str | None:
        row = self.conn.execute("SELECT value FROM state WHERE key = ?", (key,)).fetchone()
        return row[0] if row else default

    def set_state(self, key: str, value: str) -> None:
        self.conn.execute(
            "INSERT INTO state(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )
        self.conn.commit()

    def del_state(self, key: str) -> None:
        self.conn.execute("DELETE FROM state WHERE key = ?", (key,))
        self.conn.commit()

    def event(self, kind: str, detail: str = "") -> None:
        self.conn.execute("INSERT INTO events(ts, kind, detail) VALUES(?, ?, ?)", (now_iso(), kind, detail))
        self.conn.commit()

    def recent_events(self, limit: int = 20) -> list[tuple[str, str, str]]:
        return self.conn.execute(
            "SELECT ts, kind, detail FROM events ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()

    def blocks_today(self) -> int:
        prefix = datetime.now().date().isoformat()
        row = self.conn.execute(
            "SELECT COUNT(*) FROM events WHERE kind IN ('block', 'block_web') AND ts LIKE ?",
            (prefix + "%",),
        ).fetchone()
        return row[0]

    def add_checkin(self, habit: str, minutes: int, note: str = "") -> None:
        self.conn.execute(
            "INSERT INTO checkins(ts, habit, minutes, note) VALUES(?, ?, ?, ?)",
            (now_iso(), habit, minutes, note),
        )
        self.conn.commit()

    def checkin_dates(self, habit: str) -> set[str]:
        rows = self.conn.execute(
            "SELECT DISTINCT substr(ts, 1, 10) FROM checkins WHERE habit = ? AND minutes > 0",
            (habit,),
        ).fetchall()
        return {row[0] for row in rows}

    def first_checkin_date(self) -> str | None:
        row = self.conn.execute("SELECT MIN(substr(ts, 1, 10)) FROM checkins").fetchone()
        return row[0] if row else None

    def minutes_by_day(self, habit: str, since: str) -> dict[str, int]:
        rows = self.conn.execute(
            "SELECT substr(ts, 1, 10), SUM(minutes) FROM checkins "
            "WHERE habit = ? AND substr(ts, 1, 10) >= ? GROUP BY 1",
            (habit, since),
        ).fetchall()
        return {day: int(total or 0) for day, total in rows}

    def count_by_day(self, habit: str, since: str) -> dict[str, int]:
        rows = self.conn.execute(
            "SELECT substr(ts, 1, 10), COUNT(*) FROM checkins "
            "WHERE habit = ? AND substr(ts, 1, 10) >= ? GROUP BY 1",
            (habit, since),
        ).fetchall()
        return {day: int(count) for day, count in rows}

    def blocks_by_day(self, since: str) -> dict[str, int]:
        rows = self.conn.execute(
            "SELECT substr(ts, 1, 10), COUNT(*) FROM events "
            "WHERE kind IN ('block', 'block_web') AND substr(ts, 1, 10) >= ? GROUP BY 1",
            (since,),
        ).fetchall()
        return {day: int(count) for day, count in rows}

    def top_blocked(self, since: str, limit: int = 3) -> list[tuple[str, int]]:
        rows = self.conn.execute(
            "SELECT detail FROM events WHERE kind IN ('block', 'block_web') AND substr(ts, 1, 10) >= ?",
            (since,),
        ).fetchall()
        counts: dict[str, int] = {}
        for (detail,) in rows:
            name = detail.split(" · ", 1)[0].strip()
            if name:
                counts[name] = counts.get(name, 0) + 1
        return sorted(counts.items(), key=lambda item: item[1], reverse=True)[:limit]

    def had_unclean_stop(self) -> bool:
        last_start = self.conn.execute("SELECT MAX(id) FROM events WHERE kind = 'start'").fetchone()[0]
        if last_start is None:
            return False
        last_stop = self.conn.execute("SELECT MAX(id) FROM events WHERE kind = 'stop'").fetchone()[0]
        return last_stop is None or last_stop < last_start
