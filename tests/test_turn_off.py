"""Hai nhánh của `turn-off`: máy chưa bật chặn web thì không được đòi quyền admin,
máy đang bật mà khôi phục DNS thất bại thì phải từ chối gỡ để không mất mạng."""

import argparse

import pytest

from enforcer import cli
from enforcer.store import NET_BACKUP_KEY, NET_ENABLED_KEY, Store


@pytest.fixture
def sandbox(tmp_path, monkeypatch):
    data = tmp_path / "data"
    data.mkdir()
    monkeypatch.setattr(cli, "ROOT", tmp_path)
    monkeypatch.setattr(cli, "DB_PATH", data / "enforcer.db")
    monkeypatch.setattr(cli, "LOCK_PATH", data / "enforcer.lock")

    calls = []

    def fake_run(command, **kwargs):
        calls.append(command)

    def fake_uninstall(args):
        calls.append(["UNINSTALL"])
        return 0

    monkeypatch.setattr(cli.proc, "run", fake_run)
    monkeypatch.setattr(cli, "_daemon_pid", lambda: None)
    monkeypatch.setattr(cli, "cmd_uninstall", fake_uninstall)
    return calls


def mark_dns_touched():
    """Ghi đúng dấu vết mà cmd_net_install để lại sau khi đổi DNS của máy."""
    store = Store(cli.DB_PATH)
    store.set_state(NET_ENABLED_KEY, "1")
    store.set_state(NET_BACKUP_KEY, '{"index": 1}')
    store.close()


def turnoff_log() -> str:
    return (cli.ROOT / "data" / "turnoff.log").read_text(encoding="utf-8")


def test_no_dns_history_skips_restore_and_admin(sandbox, monkeypatch):
    def must_not_run(args):
        raise AssertionError("máy chưa đổi DNS thì không được gọi net uninstall")

    monkeypatch.setattr(cli, "cmd_net_uninstall", must_not_run)

    assert cli.cmd_turn_off(argparse.Namespace()) == 0
    assert ["UNINSTALL"] in sandbox
    assert "bỏ qua bước khôi phục DNS" in turnoff_log()
    assert "ABORT" not in turnoff_log()


def test_abort_keeps_task_and_daemon_when_dns_stuck(sandbox, monkeypatch):
    mark_dns_touched()
    monkeypatch.setattr(cli, "cmd_net_uninstall", lambda args: 1)

    assert cli.cmd_turn_off(argparse.Namespace()) == 1
    assert ["UNINSTALL"] not in sandbox
    assert ["schtasks", "/Change", "/TN", cli.TASK_NAME, "/ENABLE"] in sandbox
    assert "ABORT" in turnoff_log()


def test_success_after_dns_is_restored(sandbox, monkeypatch):
    mark_dns_touched()
    monkeypatch.setattr(cli, "cmd_net_uninstall", lambda args: 0)
    store = Store(cli.DB_PATH)
    store.set_state(NET_ENABLED_KEY, "0")
    store.close()

    assert cli.cmd_turn_off(argparse.Namespace()) == 0
    assert ["UNINSTALL"] in sandbox
    assert "ABORT" not in turnoff_log()
