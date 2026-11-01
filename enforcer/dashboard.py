from __future__ import annotations

import json
import os
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import psutil

from . import sessions
from .config import Config, load_plan
from .habits import WEEKDAY_VI, summary as habit_summary
from .schedule import current_window, next_start, window_end
from .store import APPROVED_KEY, NET_ENABLED_KEY, Store

MAX_BODY = 10_000


def build_summary(db_path: Path, config_path: Path, lock_path: Path) -> dict:
    store = Store(db_path)
    try:
        approved_json = store.get_state(APPROVED_KEY)
        approved = Config.from_json(approved_json) if approved_json else None
        plan = load_plan(config_path)
        now = datetime.now()

        focus = {"active": False, "text": "Chưa có luật — chạy apply", "detail": ""}
        rules = {"processes": 0, "domains": 0, "weekly_minutes": 0, "cooldown_hours": 0}
        session = sessions.current(store, now)
        if session is not None:
            detail = f"phiên đến {session.end:%H:%M}"
            if session.stop_after is not None:
                detail += f" · dừng lúc {session.stop_after:%H:%M}"
            focus = {"active": True, "text": f"Đang focus «{session.label}»", "detail": detail}
        elif approved is not None:
            window = current_window(approved, now)
            if window is not None:
                focus = {
                    "active": True,
                    "text": f"Đang focus «{window.label}»",
                    "detail": f"đến {window_end(window, now):%H:%M}",
                }
            else:
                upcoming = next_start(approved, now)
                detail = (
                    f"phiên kế {WEEKDAY_VI[upcoming.weekday()]} {upcoming:%H:%M}" if upcoming else ""
                )
                focus = {"active": False, "text": "Ngoài giờ focus", "detail": detail}
        if approved is not None:
            rules = {
                "processes": len(approved.processes),
                "domains": len(approved.web_domains),
                "weekly_minutes": approved.weekly_focus_minutes(),
                "cooldown_hours": approved.cooldown_hours,
                "stop_delay": approved.session_stop_delay_minutes,
            }

        habits = habit_summary(store, plan)
        return {
            "date": f"{WEEKDAY_VI[now.weekday()]} {now:%d/%m}",
            "time": f"{now:%H:%M}",
            "focus": focus,
            "session": sessions.as_dict(session, now),
            "rules": rules,
            "net": store.get_state(NET_ENABLED_KEY) == "1",
            "daemon": _daemon_running(lock_path),
            "habits": {"study": habits["study"], "gym": habits["gym"]},
            "blocks": habits["blocks"],
        }
    finally:
        store.close()


def _daemon_running(lock_path: Path) -> bool:
    try:
        pid = int(lock_path.read_text().strip().split(" ", 1)[0])
    except (OSError, ValueError):
        return False
    return psutil.pid_exists(pid)


class _Handler(BaseHTTPRequestHandler):
    server_version = "KyLuat"

    def do_GET(self) -> None:
        if self.path in ("/", "/index.html"):
            self._send(200, "text/html; charset=utf-8", self.server.html)
        elif self.path == "/api/summary":
            payload = build_summary(self.server.db_path, self.server.config_path, self.server.lock_path)
            self._send(200, "application/json; charset=utf-8", json.dumps(payload, ensure_ascii=False).encode("utf-8"))
        else:
            self._send(404, "text/plain; charset=utf-8", b"not found")

    def do_POST(self) -> None:
        if self.path not in ("/api/checkin", "/api/session"):
            self._send(404, "text/plain; charset=utf-8", b"not found")
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY:
            self._send(400, "application/json; charset=utf-8", b'{"error": "bad body"}')
            return
        try:
            data = json.loads(self.rfile.read(length))
            if not isinstance(data, dict):
                raise ValueError
        except (ValueError, json.JSONDecodeError):
            self._send(400, "application/json; charset=utf-8", b'{"error": "bad json"}')
            return
        if self.path == "/api/checkin":
            self._handle_checkin(data)
        else:
            self._handle_session(data)

    def _handle_checkin(self, data: dict) -> None:
        try:
            habit = data.get("habit")
            minutes = int(data.get("minutes", 0))
        except (ValueError, TypeError):
            self._send(400, "application/json; charset=utf-8", b'{"error": "bad values"}')
            return
        if habit not in ("study", "gym") or not 0 <= minutes <= 720:
            self._send(400, "application/json; charset=utf-8", b'{"error": "bad values"}')
            return
        store = Store(self.server.db_path)
        try:
            store.add_checkin(habit, minutes)
        finally:
            store.close()
        self._send(200, "application/json; charset=utf-8", b'{"ok": true}')

    def _handle_session(self, data: dict) -> None:
        action = data.get("action")
        if action not in ("start", "stop", "cancel_stop", "extend"):
            self._send(400, "application/json; charset=utf-8", b'{"error": "unknown action"}')
            return
        store = Store(self.server.db_path)
        try:
            approved_json = store.get_state(APPROVED_KEY)
            delay = Config.from_json(approved_json).session_stop_delay_minutes if approved_json else 10
            try:
                minutes = int(data.get("minutes", 25))
            except (TypeError, ValueError):
                self._send(400, "application/json; charset=utf-8", b'{"error": "bad minutes"}')
                return
            label = str(data.get("label", "Focus"))[:60]
            try:
                session = sessions.apply_action(store, action, minutes, label, delay, datetime.now())
            except ValueError as exc:
                body = json.dumps({"error": str(exc)[:200]}, ensure_ascii=False).encode("utf-8")
                self._send(400, "application/json; charset=utf-8", body)
                return
            payload = json.dumps(
                {"ok": True, "session": sessions.as_dict(session, datetime.now())},
                ensure_ascii=False,
            ).encode("utf-8")
        finally:
            store.close()
        self._send(200, "application/json; charset=utf-8", payload)

    def _send(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:
        pass


class DashboardServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, html: bytes, db_path: Path, config_path: Path, lock_path: Path):
        super().__init__(address, _Handler)
        self.html = html
        self.db_path = db_path
        self.config_path = config_path
        self.lock_path = lock_path


def serve(db_path: Path, config_path: Path, lock_path: Path, html_path: Path, port: int = 8765) -> bool:
    html = html_path.read_bytes()
    try:
        server = DashboardServer(("127.0.0.1", port), html, db_path, config_path, lock_path)
    except OSError as exc:
        print(f"Không mở được cổng {port}: {exc}")
        return False
    print(f"Dashboard: http://127.0.0.1:{port}")
    print("Ctrl+C để dừng.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return True
