"""Máy không được phép ở trạng thái "DNS trỏ về 127.0.0.1 mà không có gì nghe cổng 53".

Ca lỗi thật ngày 03/11/2026: card mạng Ethernet (ifIndex 14) trỏ DNS về loopback trong khi
`net_enabled` và `net_backup` đều sạch dấu vết, nên `turn-off` bỏ qua bước khôi phục và cả máy
mất phân giải tên miền. Mỗi test dưới đây chốt một cửa từng để chết mạng.
"""

import argparse
import json

import pytest

from enforcer import cli, net
from enforcer.store import NET_BACKUP_KEY, NET_ENABLED_KEY, Store

LOOPBACK_WIRING = {"index": 14, "v4": ["127.0.0.1"], "v6": ["::1"], "loopback": True}
ROUTER_WIRING = {"index": 14, "v4": ["192.168.100.1"], "v6": [], "loopback": False}

REG: list[tuple[str, ...]] = []


@pytest.fixture
def sandbox(tmp_path, monkeypatch):
    data = tmp_path / "data"
    data.mkdir()
    monkeypatch.setattr(cli, "ROOT", tmp_path)
    monkeypatch.setattr(cli, "DB_PATH", data / "enforcer.db")
    monkeypatch.setattr(cli, "LOCK_PATH", data / "enforcer.lock")
    monkeypatch.setattr(net.notify if hasattr(net, "notify") else cli.notify, "toast_async", lambda *a: None)
    monkeypatch.setattr(cli.net, "flush_dns", lambda: None)
    monkeypatch.setattr(cli.net, "is_admin", lambda: True)
    REG.clear()
    monkeypatch.setattr(cli.net, "reg_get", lambda key, name: None)
    monkeypatch.setattr(cli.net, "reg_set", lambda key, name, value: REG.append(("set", key, value)))
    monkeypatch.setattr(cli.net, "reg_delete", lambda key, name: REG.append(("del", key, name)))
    return data


def write_backup(store_values: dict) -> None:
    store = Store(cli.DB_PATH)
    for key, value in store_values.items():
        store.set_state(key, value)
    store.close()


def events() -> list[tuple[str, str]]:
    store = Store(cli.DB_PATH)
    try:
        return [(row[0], row[1]) for row in store.conn.execute("select kind, detail from events")]
    finally:
        store.close()


# --- net.rescue_dns: ba đường về, không đường nào được để máy ở loopback ---


def test_rescue_prefers_the_real_backup(monkeypatch):
    calls = []
    monkeypatch.setattr(net, "set_dns", lambda index, v4, v6: calls.append((index, v4, v6)))
    mode, v4, v6 = net.rescue_dns(14, {"v4": ["192.168.100.1"], "v6": []})
    assert (mode, v4, v6) == ("backup", ["192.168.100.1"], [])
    assert calls == [(14, ["192.168.100.1"], [])]


def test_rescue_refuses_to_restore_loopback_backup(monkeypatch):
    calls = []
    monkeypatch.setattr(net, "set_dns", lambda index, v4, v6: calls.append((index, v4, v6)))
    mode, v4, v6 = net.rescue_dns(14, {"v4": ["127.0.0.1"], "v6": ["::1"]})
    assert mode == "dhcp"
    assert calls == [(14, [], [])], "backup chứa loopback phải được hiểu là trả về DHCP"


def test_rescue_without_any_backup_resets_to_dhcp(monkeypatch):
    calls = []
    monkeypatch.setattr(net, "set_dns", lambda index, v4, v6: calls.append((index, v4, v6)))
    assert net.rescue_dns(14, None) == ("dhcp", [], [])
    assert calls == [(14, [], [])]


# --- cmd_net_uninstall: cửa thoát duy nhất, phải hoạt động cả khi DB sạch ---


def test_uninstall_rescues_even_with_no_backup_recorded(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    done = []
    monkeypatch.setattr(cli.net, "rescue_dns", lambda index, backup: done.append(index) or ("dhcp", [], []))

    assert cli.cmd_net_uninstall(argparse.Namespace()) == 0
    assert done == [14], "máy đang bị nhốt ở loopback thì phải được cứu dù DB không còn backup"
    assert ("net_uninstall", "adapter 14: dhcp") in events()


def test_uninstall_uses_the_stored_backup_values(sandbox, monkeypatch):
    write_backup({NET_BACKUP_KEY: json.dumps({"interface_index": 14, "v4": ["1.1.1.1"], "v6": []})})
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    seen = []
    monkeypatch.setattr(cli.net, "rescue_dns", lambda index, backup: seen.append(backup) or ("backup", ["1.1.1.1"], []))

    assert cli.cmd_net_uninstall(argparse.Namespace()) == 0
    assert seen == [{"interface_index": 14, "v4": ["1.1.1.1"], "v6": []}]


def test_uninstall_leaves_a_healthy_adapter_alone(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(ROUTER_WIRING))
    monkeypatch.setattr(
        cli.net, "rescue_dns", lambda *a: pytest.fail("DNS không trỏ về máy thì đừng đụng vào card mạng")
    )
    assert cli.cmd_net_uninstall(argparse.Namespace()) == 0


def test_uninstall_fails_loudly_when_dns_cannot_be_restored(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))

    def broken(index, backup):
        raise RuntimeError("PowerShell timeout")

    monkeypatch.setattr(cli.net, "rescue_dns", broken)
    assert cli.cmd_net_uninstall(argparse.Namespace()) == 1


def test_uninstall_clears_browser_policy_even_when_backup_is_gone(sandbox, monkeypatch):
    """`DnsOverHttpsMode=off` là do app đặt; DB mất dấu thì gỡ vẫn phải gỡ, không được bỏ lại."""
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(ROUTER_WIRING))

    assert cli.cmd_net_uninstall(argparse.Namespace()) == 0
    assert REG == [("del", key, name) for key, name in net.DOH_POLICIES]


def test_uninstall_restores_a_policy_value_the_machine_already_had(sandbox, monkeypatch):
    write_backup(
        {
            NET_BACKUP_KEY: json.dumps(
                {
                    "interface_index": 14,
                    "v4": ["192.168.100.1"],
                    "v6": [],
                    "doh": [
                        {
                            "key": net.DOH_POLICIES[0][0],
                            "name": net.DOH_POLICIES[0][1],
                            "previous": "automatic",
                        }
                    ],
                }
            )
        }
    )
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    monkeypatch.setattr(
        cli.net, "rescue_dns", lambda index, backup: ("backup", ["192.168.100.1"], [])
    )

    assert cli.cmd_net_uninstall(argparse.Namespace()) == 0
    assert REG == [("set", net.DOH_POLICIES[0][0], "automatic")]


def test_net_status_flags_a_policy_left_behind(sandbox, monkeypatch, capsys):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(ROUTER_WIRING))
    monkeypatch.setattr(cli.net, "probe", lambda **kwargs: None)
    monkeypatch.setattr(cli.net, "reg_get", lambda key, name: "off")
    monkeypatch.setattr(cli, "_daemon_pid", lambda: None)

    cli.cmd_net_status(argparse.Namespace())
    assert capsys.readouterr().out.count("tàn dư") == 2


# --- turn-off: không được tin DB hơn tin card mạng ---


def test_turn_off_restores_dns_when_db_lost_the_trace(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    monkeypatch.setattr(cli.proc, "run", lambda command, **kwargs: None)
    monkeypatch.setattr(cli, "_daemon_pid", lambda: None)
    monkeypatch.setattr(cli, "cmd_uninstall", lambda args: None)

    called = []
    monkeypatch.setattr(cli, "cmd_net_uninstall", lambda args: called.append(1) or 0)

    assert cli.cmd_turn_off(argparse.Namespace()) == 0
    assert called, "DB sạch nhưng card mạng còn bị nhốt thì vẫn phải chạy khôi phục"


def test_turn_off_still_skips_admin_when_machine_is_clean(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(ROUTER_WIRING))
    monkeypatch.setattr(cli.proc, "run", lambda command, **kwargs: None)
    monkeypatch.setattr(cli, "_daemon_pid", lambda: None)
    monkeypatch.setattr(cli, "cmd_uninstall", lambda args: None)
    monkeypatch.setattr(
        cli, "cmd_net_uninstall", lambda args: pytest.fail("máy chưa đổi DNS thì không được đòi quyền admin")
    )

    assert cli.cmd_turn_off(argparse.Namespace()) == 0


# --- watchdog và repair: mạng được ưu tiên hơn luật ---


def test_fail_open_pulls_the_machine_out_of_loopback(sandbox, monkeypatch):
    write_backup({NET_BACKUP_KEY: json.dumps({"interface_index": 14, "v4": ["192.168.100.1"], "v6": []})})
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    done = []
    monkeypatch.setattr(
        cli.net, "rescue_dns",
        lambda index, backup: done.append((index, backup["v4"])) or ("backup", ["192.168.100.1"], []),
    )
    store = Store(cli.DB_PATH)
    try:
        assert cli._fail_open_dns(store, "sinkhole không phản hồi") is True
    finally:
        store.close()
    assert done == [(14, ["192.168.100.1"])]
    assert any(kind == "net_failopen" for kind, _ in events())


def test_fail_open_does_nothing_when_dns_is_already_real(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(ROUTER_WIRING))
    monkeypatch.setattr(
        cli.net, "rescue_dns", lambda *a: pytest.fail("không có gì bị nhốt thì đừng đổi DNS")
    )
    store = Store(cli.DB_PATH)
    try:
        assert cli._fail_open_dns(store, "sinkhole không phản hồi") is False
    finally:
        store.close()


def test_repair_refuses_to_point_dns_at_a_dead_sinkhole(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "probe", lambda **kwargs: None)
    monkeypatch.setattr(
        cli.net, "set_dns", lambda *a: pytest.fail("trỏ DNS về máy khi cổng 53 chết là cắt mạng cả máy")
    )
    store = Store(cli.DB_PATH)
    try:
        assert cli._repair_wiring(store, dict(ROUTER_WIRING)) is False
    finally:
        store.close()


def test_repair_never_records_loopback_as_the_pristine_dns(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "probe", lambda **kwargs: "127.0.0.1")
    monkeypatch.setattr(cli.net, "set_dns", lambda *a: None)
    store = Store(cli.DB_PATH)
    try:
        assert cli._repair_wiring(store, dict(LOOPBACK_WIRING)) is True
        backup = json.loads(store.get_state(NET_BACKUP_KEY))
    finally:
        store.close()
    assert backup["v4"] == [] and backup["v6"] == []


def test_net_repair_command_reports_a_dead_sinkhole(sandbox, monkeypatch):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    monkeypatch.setattr(cli.net, "probe", lambda **kwargs: None)
    assert cli.cmd_net_repair(argparse.Namespace()) == 1


def test_status_and_doctor_shout_when_the_machine_is_trapped(sandbox, monkeypatch, capsys):
    monkeypatch.setattr(cli.net, "current_wiring", lambda: dict(LOOPBACK_WIRING))
    monkeypatch.setattr(cli.net, "probe", lambda **kwargs: None)
    monkeypatch.setattr(cli.net, "reg_get", lambda key, name: None)
    monkeypatch.setattr(cli, "_daemon_pid", lambda: None)

    cli.cmd_net_status(argparse.Namespace())
    cli.cmd_doctor(argparse.Namespace())
    printed = capsys.readouterr().out
    assert printed.count("CẢNH BÁO") == 2
    assert "net uninstall" in printed
