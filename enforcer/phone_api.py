from __future__ import annotations

import hmac
import ipaddress
import json
import secrets
import socket
import threading
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from . import discovery, pairing, proc, sessions
from .config import Config
from .dashboard import build_summary
from .store import APPROVED_KEY, Store

TOKEN_KEY = "phone_token"
PHONE_PORT = 8766
MAX_BODY = 10_000
_PAIR_ATTEMPTS: dict[str, list[float]] = {}
_PAIR_LOCK = threading.Lock()


def _pair_rate_ok(address: str) -> bool:
    now = time.monotonic()
    with _PAIR_LOCK:
        attempts = [stamp for stamp in _PAIR_ATTEMPTS.get(address, []) if now - stamp < 60]
        if len(attempts) >= 10:
            _PAIR_ATTEMPTS[address] = attempts
            return False
        attempts.append(now)
        _PAIR_ATTEMPTS[address] = attempts
        return True


def get_token(store: Store) -> str | None:
    return store.get_state(TOKEN_KEY)


def create_token(store: Store) -> str:
    token = secrets.token_urlsafe(32)
    store.set_state(TOKEN_KEY, token)
    store.event("phone_pair", "tạo token mới")
    return token


def clear_token(store: Store) -> None:
    store.del_state(TOKEN_KEY)
    store.event("phone_unpair", "")


def lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def add_firewall_rule(port: int = PHONE_PORT) -> bool:
    result = proc.run(
        [
            "netsh", "advfirewall", "firewall", "add", "rule",
            f"name=CaiNghienPhone{port}", "dir=in", "action=allow",
            "protocol=TCP", f"localport={port}", "profile=private",
        ],
        capture_output=True,
    )
    return result.returncode == 0


class _Handler(BaseHTTPRequestHandler):
    server_version = "KyLuatPhone"

    def do_GET(self) -> None:
        if not self._authorized():
            return
        if self.path == "/api/phone/summary":
            self._json(200, build_summary(self.server.db_path, self.server.config_path, self.server.lock_path))
        elif self.path == "/api/phone/ping":
            self._json(200, {"ok": True, "version": 1})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self) -> None:
        if self.path == "/api/phone/pair":
            self._handle_pair()
            return
        if not self._authorized():
            return
        body = self._read_json()
        if body is None:
            return
        if self.path == "/api/phone/checkin":
            self._handle_checkin(body)
        elif self.path == "/api/phone/session":
            self._handle_session(body)
        else:
            self._json(404, {"error": "not found"})

    def _handle_pair(self) -> None:
        address = self.client_address[0]
        try:
            remote = ipaddress.ip_address(address)
        except ValueError:
            self._json(403, {"error": "forbidden"})
            return
        if not (remote.is_private or remote.is_loopback):
            self._json(403, {"error": "forbidden"})
            return
        if not _pair_rate_ok(address):
            self._json(429, {"error": "too many attempts"})
            return
        body = self._read_json()
        if body is None:
            return
        code = str(body.get("code", ""))
        device = str(body.get("device", "Android"))[:80]
        store = Store(self.server.db_path)
        try:
            if not pairing.verify_code(store, code):
                self._json(401, {"error": "mã không đúng hoặc đã hết hạn"})
                return
            token = get_token(store)
            if not token:
                token = create_token(store)
            store.event("phone_pair_success", device)
            self.server.token = token
        finally:
            store.close()
        self._json(200, {"ok": True, "token": token, "device": device})

    def _authorized(self) -> bool:
        try:
            remote = ipaddress.ip_address(self.client_address[0])
        except ValueError:
            self._json(403, {"error": "forbidden"})
            return False
        if not (remote.is_private or remote.is_loopback):
            self._json(403, {"error": "forbidden"})
            return False
        header = self.headers.get("Authorization", "")
        token = header[7:] if header.startswith("Bearer ") else ""
        if not token or not hmac.compare_digest(token, self.server.token):
            self._json(401, {"error": "unauthorized"})
            return False
        return True

    def _read_json(self):
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY:
            self._json(400, {"error": "bad body"})
            return None
        try:
            return json.loads(self.rfile.read(length))
        except (ValueError, json.JSONDecodeError):
            self._json(400, {"error": "bad json"})
            return None

    def _handle_checkin(self, body: dict) -> None:
        try:
            minutes = int(body.get("minutes", 0))
        except (TypeError, ValueError):
            self._json(400, {"error": "bad minutes"})
            return
        habit = body.get("habit")
        if habit not in ("study", "gym") or not 0 <= minutes <= 720:
            self._json(400, {"error": "bad values"})
            return
        note = str(body.get("note", ""))[:200]
        store = Store(self.server.db_path)
        try:
            store.add_checkin(habit, minutes, note)
        finally:
            store.close()
        self._json(200, {"ok": True})

    def _handle_session(self, body: dict) -> None:
        action = body.get("action")
        if action not in ("start", "stop", "cancel_stop", "extend"):
            self._json(400, {"error": "unknown action"})
            return
        store = Store(self.server.db_path)
        try:
            approved_json = store.get_state(APPROVED_KEY)
            delay = Config.from_json(approved_json).session_stop_delay_minutes if approved_json else 10
            try:
                minutes = int(body.get("minutes", 25))
            except (TypeError, ValueError):
                self._json(400, {"error": "bad minutes"})
                return
            label = str(body.get("label", "Focus"))[:60]
            try:
                session = sessions.apply_action(store, action, minutes, label, delay, datetime.now())
            except ValueError as exc:
                self._json(400, {"error": str(exc)[:200]})
                return
            payload = sessions.as_dict(session, datetime.now())
        finally:
            store.close()
        self._json(200, {"ok": True, "session": payload})

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:
        pass


class PhoneServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, db_path: Path, config_path: Path, lock_path: Path, token: str):
        super().__init__(address, _Handler)
        self.db_path = db_path
        self.config_path = config_path
        self.lock_path = lock_path
        self.token = token


def start_if_paired(
    db_path: Path,
    config_path: Path,
    lock_path: Path,
    port: int = PHONE_PORT,
) -> PhoneServer | None:
    store = Store(db_path)
    try:
        token = get_token(store)
        has_code = bool(store.get_state(pairing.PAIR_KEY))
    finally:
        store.close()
    if not token and not has_code:
        return None
    try:
        server = PhoneServer(("0.0.0.0", port), db_path, config_path, lock_path, token or "")
    except OSError:
        return None
    threading.Thread(target=server.serve_forever, name="enforcer-phone", daemon=True).start()
    discovery.start(lan_ip(), port)
    return server
