import json
import threading
import urllib.error
import urllib.request
from pathlib import Path

from enforcer import pairing
from enforcer.config import Config
from enforcer.phone_api import PhoneServer
from enforcer.store import APPROVED_KEY, Store


def config() -> Config:
    return Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=("steam.exe",),
        web_domains=("youtube.com",),
        windows=(),
        session_stop_delay_minutes=10,
    )


def test_phone_api_auth_and_actions(tmp_path):
    db = tmp_path / "data.db"
    store = Store(db)
    store.set_state(APPROVED_KEY, config().to_json())
    store.close()

    server = PhoneServer(("127.0.0.1", 0), db, tmp_path / "config.toml", tmp_path / "lock", "secret")
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{port}"

    def call(path, method="GET", body=None, token="secret"):
        data = json.dumps(body).encode("utf-8") if body else None
        request = urllib.request.Request(base + path, method=method, data=data)
        request.add_header("Content-Type", "application/json")
        if token:
            request.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read())

    try:
        assert call("/api/phone/summary", token=None)[0] == 401
        assert call("/api/phone/summary", token="wrong")[0] == 401

        status, payload = call("/api/phone/summary")
        assert status == 200
        assert payload["rules"]["domains"] == 1

        assert call("/api/phone/checkin", "POST", {"habit": "gym", "minutes": 0})[0] == 200
        assert call("/api/phone/checkin", "POST", {"habit": "bad", "minutes": 0})[0] == 400

        status, payload = call("/api/phone/session", "POST", {"action": "start", "minutes": 25, "label": "T"})
        assert status == 200
        assert payload["session"]["label"] == "T"

        assert call("/api/phone/session", "POST", {"action": "stop"})[0] == 200
        assert call("/api/phone/session", "POST", {"action": "cancel_stop"})[0] == 200
        assert call("/api/phone/session", "POST", {"action": "nope"})[0] == 400
    finally:
        server.shutdown()
        server.server_close()


def test_phone_pair_with_six_digit_code(tmp_path):
    db = tmp_path / "data.db"
    store = Store(db)
    store.set_state(APPROVED_KEY, config().to_json())
    code = pairing.issue_code(store)
    store.close()

    server = PhoneServer(("127.0.0.1", 0), db, tmp_path / "config.toml", tmp_path / "lock", "")
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{port}"

    def post(path, body, token=None):
        request = urllib.request.Request(base + path, method="POST", data=json.dumps(body).encode("utf-8"))
        request.add_header("Content-Type", "application/json")
        if token:
            request.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read())

    try:
        assert post("/api/phone/pair", {"code": "000000", "device": "test"})[0] == 401
        status, payload = post("/api/phone/pair", {"code": code, "device": "test"})
        assert status == 200
        token = payload["token"]
        assert token

        request = urllib.request.Request(base + "/api/phone/summary")
        request.add_header("Authorization", "Bearer " + token)
        with urllib.request.urlopen(request, timeout=5) as response:
            assert response.status == 200
    finally:
        server.shutdown()
        server.server_close()
