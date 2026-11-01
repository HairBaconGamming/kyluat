from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import threading
import time
import webbrowser
from datetime import date, datetime, timedelta
from pathlib import Path

import psutil

from . import net, notify, pairing, phone_api, rules, sessions
from .config import Config, ConfigError, load_config, load_plan, weakening_changes
from .dashboard import serve as serve_dashboard
from .guard import Guard, acquire_lock, release_lock
from .habits import WEEKDAY_VI, day_label, summary as habit_summary
from .schedule import current_window, next_start, window_end
from .store import APPROVED_KEY, NET_BACKUP_KEY, NET_ENABLED_KEY, PENDING_KEY, Store, now_iso

FROZEN = bool(getattr(sys, "frozen", False))
# Bản đóng gói một file không có thư mục repo: mọi đường dẫn phải tính từ vị trí exe.
ROOT = Path(sys.executable).resolve().parent if FROZEN else Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "config.toml"
DB_PATH = ROOT / "data" / "enforcer.db"
LOCK_PATH = ROOT / "data" / "enforcer.lock"
TASK_NAME = "CaiNghienEnforcer"
CLI_HINT = Path(sys.executable).name if FROZEN else "python -m enforcer"


def resource_path(relative: str) -> Path:
    bundle = getattr(sys, "_MEIPASS", None)
    if bundle is not None:
        return Path(bundle) / relative
    return Path(__file__).resolve().parent / relative


def cmd_apply(args) -> int:
    try:
        candidate = load_config(CONFIG_PATH)
    except ConfigError as exc:
        print(f"Không nạp được config.toml: {exc}")
        return 1

    store = Store(DB_PATH)
    try:
        result = rules.apply_candidate(store, candidate)
    finally:
        store.close()

    if result.state == "initial":
        print(
            f"Đã áp dụng config.toml làm luật gốc: {len(candidate.processes)} app, "
            f"{len(candidate.web_domains)} domain, {candidate.weekly_focus_minutes()} phút focus/tuần."
        )
        return 0
    if result.state == "applied":
        print("Đã áp dụng ngay (không làm yếu luật).")
        return 0

    print("Thay đổi này làm YẾU luật nên chưa được áp dụng:")
    for reason in result.reasons:
        print(f"  - {reason}")
    assert result.effective_at is not None
    print(f"Hiệu lực sau {result.effective_at:%H:%M ngày %d/%m}.")
    print("Trong lúc chờ, luật cũ vẫn thi hành. Muốn hủy: trả config.toml về như cũ rồi chạy apply lại.")
    return 0


def seed_initial_law(store: Store) -> bool:
    """Lần chạy đầu: biến config.toml có sẵn thành luật gốc để app còn có gì mà thi hành."""
    if store.get_state(APPROVED_KEY) or not CONFIG_PATH.exists():
        return False
    try:
        candidate = load_config(CONFIG_PATH)
    except ConfigError:
        return False
    return rules.apply_candidate(store, candidate).state == "initial"


def cmd_run(args) -> int:
    if not acquire_lock(LOCK_PATH):
        return 0
    store = Store(DB_PATH)
    try:
        approved_json = store.get_state(APPROVED_KEY)
        if not approved_json:
            try:
                candidate = load_config(CONFIG_PATH)
            except ConfigError as exc:
                print(f"Không nạp được config.toml: {exc}")
                return 1
            store.set_state(APPROVED_KEY, candidate.to_json())
            store.event("apply", "tự khởi tạo khi chưa có luật")
            approved_json = store.get_state(APPROVED_KEY)

        unclean = store.had_unclean_stop()
        store.event("start", "khởi động lại sau khi bị tắt ngoài ý muốn" if unclean else "")
        config = Config.from_json(approved_json)
        store.backup(DB_PATH.parent / "backups")
        guard = Guard(store, config, CONFIG_PATH)
        net.start_sinkhole(lambda: guard.config, lambda: guard.focus_active, DB_PATH)
        threading.Thread(target=_net_watch, args=(DB_PATH,), daemon=True).start()
        threading.Thread(target=_dns_health_watch, args=(DB_PATH,), daemon=True).start()
        if phone_api.start_if_paired(DB_PATH, CONFIG_PATH, LOCK_PATH) is not None:
            store.event("phone_api", f"0.0.0.0:{phone_api.PHONE_PORT}")
        print(
            f"Enforcer chạy (PID {os.getpid()}): {len(config.processes)} app, "
            f"{len(config.web_domains)} domain, {config.weekly_focus_minutes()} phút focus/tuần.",
            flush=True,
        )
        try:
            guard.run()
        except KeyboardInterrupt:
            pass
    finally:
        store.event("stop", "")
        store.close()
        release_lock(LOCK_PATH)
    return 0


def cmd_status(args) -> int:
    store = Store(DB_PATH)
    approved_json = store.get_state(APPROVED_KEY)
    if not approved_json:
        print(f"Chưa có luật nào. Chạy: {CLI_HINT} apply")
        return 1

    approved = Config.from_json(approved_json)
    now = datetime.now()
    session = sessions.current(store, now)
    print(f"Trạng thái  : {_focus_text(approved, session, now)}")
    if session is not None:
        extra = f" · dừng lúc {session.stop_after:%H:%M}" if session.stop_after else ""
        print(f"Phiên focus : «{session.label}» đến {session.end:%H:%M}{extra}")
    print(
        f"Luật        : {len(approved.processes)} app, {len(approved.web_domains)} domain, "
        f"{approved.weekly_focus_minutes()} phút focus/tuần, cooldown {approved.cooldown_hours:g}h"
    )
    net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
    print(f"Chặn web    : {'bật' if net_enabled else 'tắt'}")
    print(f"Daemon      : {_daemon_status()}")
    print(f"Tự chạy     : {_task_status()}")
    print(f"Hôm nay     : {store.blocks_today()} lần chặn")

    pending_text = store.get_state(PENDING_KEY)
    if pending_text:
        try:
            pending = json.loads(pending_text)
            effective_at = datetime.fromisoformat(pending["effective_at"])
            print(f"Chờ áp dụng : luật mới hiệu lực {effective_at:%H:%M ngày %d/%m} (còn {_fmt_delta(effective_at - now)})")
        except (ValueError, KeyError, TypeError):
            print("Chờ áp dụng : dữ liệu chờ bị hỏng, daemon sẽ bỏ qua")

    try:
        on_disk = load_config(CONFIG_PATH)
    except ConfigError as exc:
        print(f"config.toml : lỗi — {exc}")
    else:
        if on_disk.to_dict() != approved.to_dict():
            print(f"config.toml : có thay đổi chưa apply (chạy: {CLI_HINT} apply)")
    return 0


def cmd_log(args) -> int:
    store = Store(DB_PATH)
    rows = store.recent_events(args.limit)
    if not rows:
        print("Chưa có sự kiện nào.")
        return 0
    for ts, kind, detail in rows:
        print(f"{ts.replace('T', ' ')}  {kind:<13} {detail}")
    return 0


def cmd_checkin(args) -> int:
    habit = args.habit
    minutes = args.minutes
    if minutes is None:
        minutes = 30 if habit == "study" else 0
    if not 0 <= minutes <= 720:
        print("Số phút phải trong khoảng 0..720.")
        return 1
    note = " ".join(args.note or [])
    store = Store(DB_PATH)
    try:
        store.add_checkin(habit, minutes, note)
        data = habit_summary(store, load_plan(CONFIG_PATH))
    finally:
        store.close()
    if habit == "study":
        print(
            f"Đã ghi học {minutes} phút. Hôm nay {data['study']['today']}/{data['study']['goal']} phút, "
            f"chuỗi {data['study']['streak']} ngày."
        )
    else:
        print(
            f"Đã ghi 1 buổi gym. Tuần này {data['gym']['week']}/{data['gym']['goal']} buổi, "
            f"chuỗi {data['gym']['streak_weeks']} tuần đạt mục tiêu."
        )
    return 0


def cmd_today(args) -> int:
    store = Store(DB_PATH)
    try:
        now = datetime.now()
        approved_json = store.get_state(APPROVED_KEY)
        session = sessions.current(store, now)
        if approved_json:
            print(f"Focus       : {_focus_text(Config.from_json(approved_json), session, now)}")
        if session is not None:
            print(f"Phiên focus : «{session.label}» đến {session.end:%H:%M}")
        data = habit_summary(store, load_plan(CONFIG_PATH))
    finally:
        store.close()
    study, gym, blocks = data["study"], data["gym"], data["blocks"]
    print(f"Học         : {study['today']}/{study['goal']} phút hôm nay · {study['week']}/{study['week_goal']} phút tuần · chuỗi {study['streak']} ngày")
    print(f"Gym         : {gym['week']}/{gym['goal']} buổi tuần · hôm nay {gym['today']} · chuỗi {gym['streak_weeks']} tuần")
    print(f"Chặn hôm nay: {blocks['today']} lần")
    if study["missed"]:
        print("Thiếu học   : " + ", ".join(day_label(date.fromisoformat(item)) for item in study["missed"]))
    if blocks["top"]:
        print("Chặn nhiều  : " + ", ".join(f"{name} ({count})" for name, count in blocks["top"]))
    return 0


def cmd_review(args) -> int:
    store = Store(DB_PATH)
    try:
        data = habit_summary(store, load_plan(CONFIG_PATH))
    finally:
        store.close()
    study, gym, blocks = data["study"], data["gym"], data["blocks"]
    study_by_day = {row["date"]: row for row in study["series"]}
    gym_by_day = {row["date"]: row for row in gym["series"]}
    block_by_day = {row["date"]: row for row in blocks["series"]}

    print(f"7 ngày qua: {study['series'][0]['label']} – {study['series'][-1]['label']}")
    print(f"{'Ngày':<10}{'Học':>9}{'Gym':>6}{'Chặn':>7}")
    for row in study["series"]:
        day = row["date"]
        print(
            f"{row['label']:<10}{study_by_day[day]['value']:>7}′"
            f"{gym_by_day[day]['value']:>6}{block_by_day[day]['value']:>7}"
        )
    print()
    print(f"Học         : {study['week']}/{study['week_goal']} phút · chuỗi {study['streak']} ngày")
    print(f"Gym         : {gym['week']}/{gym['goal']} buổi · chuỗi {gym['streak_weeks']} tuần đạt mục tiêu")
    if blocks["top"]:
        print("Bị chặn     : " + ", ".join(f"{name} ({count})" for name, count in blocks["top"]))
    if study["missed"]:
        print("Thiếu học   : " + ", ".join(day_label(date.fromisoformat(item)) for item in study["missed"]))
    tips = []
    if study["missed"]:
        tips.append("thêm một phiên học ngắn vào ngày đã thiếu")
    if gym["goal"] and gym["week"] < gym["goal"]:
        tips.append(f"còn {gym['goal'] - gym['week']} buổi gym trong tuần này")
    if not tips:
        tips.append("giữ đúng nhịp như tuần này")
    print("Việc tiếp   : " + "; ".join(tips) + ".")
    return 0


def cmd_focus_start(args) -> int:
    store = Store(DB_PATH)
    try:
        approved_json = store.get_state(APPROVED_KEY)
        delay = Config.from_json(approved_json).session_stop_delay_minutes if approved_json else 10
        try:
            session = sessions.start(store, args.minutes, " ".join(args.label or []), datetime.now())
        except ValueError as exc:
            print(f"Không bắt đầu được: {exc}")
            return 1
    finally:
        store.close()
    if _daemon_pid() is None:
        _start_daemon()
        print("Đã khởi động daemon để thi hành phiên.")
    print(f"Phiên «{session.label}» đến {session.end:%H:%M} ({args.minutes} phút).")
    print(f"App và web bị chặn ngay. Dừng sớm: {CLI_HINT} focus stop (chờ {delay} phút).")
    return 0


def cmd_focus_stop(args) -> int:
    store = Store(DB_PATH)
    try:
        approved_json = store.get_state(APPROVED_KEY)
        delay = Config.from_json(approved_json).session_stop_delay_minutes if approved_json else 10
        try:
            session = sessions.request_stop(store, delay, datetime.now())
        except ValueError as exc:
            print(f"Không dừng được: {exc}")
            return 1
    finally:
        store.close()
    if session.stop_after is None:
        print("Phiên đã có yêu cầu dừng từ trước.")
    else:
        print(f"Đã ghi nhận. Phiên dừng lúc {session.stop_after:%H:%M} (chờ {delay} phút).")
        print(f"Hủy: {CLI_HINT} focus cancel")
    return 0


def cmd_focus_cancel(args) -> int:
    store = Store(DB_PATH)
    try:
        try:
            session = sessions.cancel_stop(store, datetime.now())
        except ValueError as exc:
            print(f"Không hủy được: {exc}")
            return 1
    finally:
        store.close()
    if session.stop_after is None:
        print(f"Đã hủy yêu cầu dừng. Phiên «{session.label}» chạy đến {session.end:%H:%M}.")
    return 0


def cmd_focus_extend(args) -> int:
    store = Store(DB_PATH)
    try:
        try:
            session = sessions.extend(store, args.minutes, datetime.now())
        except ValueError as exc:
            print(f"Không gia hạn được: {exc}")
            return 1
    finally:
        store.close()
    print(f"Đã gia hạn {args.minutes} phút. Phiên «{session.label}» đến {session.end:%H:%M}.")
    return 0


def cmd_focus_status(args) -> int:
    store = Store(DB_PATH)
    try:
        now = datetime.now()
        approved_json = store.get_state(APPROVED_KEY)
        session = sessions.current(store, now)
        if session is None:
            print("Không có phiên tự do nào.")
            if approved_json:
                print(f"Lịch        : {_focus_line(Config.from_json(approved_json), now)}")
        else:
            print(f"Phiên       : «{session.label}» đến {session.end:%H:%M} · còn {session.remaining_minutes(now)} phút")
            if session.stop_after is not None:
                print(f"Yêu cầu dừng: lúc {session.stop_after:%H:%M}")
            else:
                print("Yêu cầu dừng: chưa có (dừng sớm sẽ phải chờ cooldown)")
    finally:
        store.close()
    print(f"Daemon      : {_daemon_status()}")
    return 0


def cmd_export(args) -> int:
    store = Store(DB_PATH)
    try:
        checkins = [
            {"ts": ts, "habit": habit, "minutes": minutes, "note": note}
            for ts, habit, minutes, note in store.conn.execute(
                "SELECT ts, habit, minutes, note FROM checkins ORDER BY ts"
            )
        ]
        events = [
            {"ts": ts, "kind": kind, "detail": detail}
            for ts, kind, detail in store.conn.execute(
                "SELECT ts, kind, detail FROM events ORDER BY id"
            )
        ]
        approved_json = store.get_state(APPROVED_KEY)
    finally:
        store.close()
    data = {
        "exported_at": now_iso(),
        "approved_config": json.loads(approved_json) if approved_json else None,
        "checkins": checkins,
        "events": events,
    }
    target = Path(args.out) if args.out else DB_PATH.parent / f"export-{datetime.now():%Y%m%d-%H%M%S}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Đã xuất {len(checkins)} check-in và {len(events)} sự kiện: {target}")
    return 0


def cmd_net_repair(args) -> int:
    if not net.is_admin():
        print("Cần quyền Administrator. Mở PowerShell bằng quyền Administrator rồi chạy lại.")
        return 1
    store = Store(DB_PATH)
    try:
        if store.get_state(NET_ENABLED_KEY) != "1":
            print(f"Chặn web chưa bật. Chạy: {CLI_HINT} net install")
            return 1
        wiring = net.current_wiring()
        if wiring is None:
            print("Không đọc được card mạng đang hoạt động.")
            return 1
        if wiring["loopback"]:
            print("DNS đang trỏ đúng về sinkhole, không cần sửa.")
            return 0
        store.event("net_repair_manual", f"adapter {wiring['index']}")
        ok = _repair_wiring(store, wiring)
    finally:
        store.close()
    if ok:
        print(f"Đã trỏ DNS adapter {wiring['index']} về 127.0.0.1 / ::1.")
    else:
        print("Sửa DNS thất bại — xem lại kết nối mạng hoặc chạy lại net install.")
    return 0 if ok else 1


def cmd_phone_pair(args) -> int:
    store = Store(DB_PATH)
    try:
        token = phone_api.get_token(store)
        if token is None:
            token = phone_api.create_token(store)
        code = pairing.issue_code(store)
        ip = phone_api.lan_ip()
        port = phone_api.PHONE_PORT
    finally:
        store.close()

    if _daemon_pid() is None:
        _start_daemon()
    elif not _port_open(ip, port):
        _restart_daemon()

    payload = pairing.qr_payload(ip, port, code)
    print("Quét mã QR hoặc nhập mã 6 số trong app điện thoại:")
    print(pairing.qr_ascii(payload))
    print(f"Mã 6 số : {code} · hết hạn sau 5 phút")
    print(f"URL     : http://{ip}:{port} · app tự tìm máy trong cùng Wi-Fi")
    print(f"Token   : {token} (dành cho app bản cũ)")
    print(f"Nếu điện thoại không kết nối được: PowerShell Admin → {CLI_HINT} phone firewall")
    return 0


def cmd_phone_rotate(args) -> int:
    store = Store(DB_PATH)
    try:
        token = phone_api.create_token(store)
    finally:
        store.close()
    if _daemon_pid() is not None:
        _restart_daemon()
    print("Đã đổi token. App trên điện thoại phải nhập lại token mới.")
    _print_pairing(token)
    return 0


def cmd_phone_off(args) -> int:
    store = Store(DB_PATH)
    try:
        phone_api.clear_token(store)
    finally:
        store.close()
    if _daemon_pid() is not None:
        _restart_daemon()
    print("Đã tắt đồng bộ điện thoại và xóa token.")
    return 0


def cmd_phone_status(args) -> int:
    store = Store(DB_PATH)
    try:
        token = phone_api.get_token(store)
    finally:
        store.close()
    ip = phone_api.lan_ip()
    listening = _port_open(ip, phone_api.PHONE_PORT)
    print(f"Cặp kết nối : {'có' if token else f'chưa có (chạy: {CLI_HINT} phone pair)'}")
    print(f"URL         : http://{ip}:{phone_api.PHONE_PORT}")
    if token:
        print(f"Token       : {token[:6]}… (ẩn)")
    print(f"API         : {'đang lắng nghe' if listening else 'không lắng nghe (daemon chưa chạy?)'}")
    print(f"Daemon      : {_daemon_status()}")
    return 0


def cmd_phone_firewall(args) -> int:
    if not net.is_admin():
        print("Cần quyền Administrator. Mở PowerShell bằng quyền Administrator rồi chạy lại.")
        return 1
    if phone_api.add_firewall_rule():
        print(f"Đã mở firewall TCP {phone_api.PHONE_PORT} cho mạng riêng.")
        return 0
    print("Không thêm được rule firewall.")
    return 1


def cmd_desktop(args) -> int:
    from .desktop import run as run_desktop

    return run_desktop(screenshot=getattr(args, "screenshot", None))


def cmd_dashboard(args) -> int:
    url = f"http://127.0.0.1:{args.port}"
    if not args.no_open:
        threading.Timer(0.8, lambda: webbrowser.open(url)).start()
    ok = serve_dashboard(DB_PATH, CONFIG_PATH, LOCK_PATH, resource_path("static/index.html"), args.port)
    return 0 if ok else 1


def _create_task(elevated: bool = False) -> subprocess.CompletedProcess:
    if FROZEN:
        action = f'"{sys.executable}" run'
    else:
        action = f'"{_pythonw()}" "{ROOT / "run_enforcer.pyw"}"'
    command = ["schtasks", "/Create", "/F", "/TN", TASK_NAME, "/SC", "MINUTE", "/MO", "1", "/TR", action]
    if elevated:
        command += ["/RL", "HIGHEST"]
    return subprocess.run(command, capture_output=True)


def cmd_install(args) -> int:
    elevated = getattr(args, "elevated", False)
    result = _create_task(elevated)
    if result.returncode != 0:
        message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
        print(f"Tạo task thất bại: {message}")
        print("Thử mở PowerShell bằng quyền Administrator rồi chạy lại.")
        return 1
    mode = "quyền admin" if elevated else "quyền thường"
    print(f"Đã tạo task {TASK_NAME} ({mode}): mỗi phút chạy daemon nếu nó chưa chạy.")
    print("Tác dụng: tự khởi động trong 1 phút sau đăng nhập, và tự sống lại nếu bị tắt.")
    return 0


def cmd_uninstall(args) -> int:
    result = subprocess.run(["schtasks", "/Delete", "/F", "/TN", TASK_NAME], capture_output=True)
    message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
    print(f"{TASK_NAME}: {'đã xóa' if result.returncode == 0 else message}")
    return 0


def cmd_turn_off(args) -> int:
    """Tắt hẳn bảo vệ. Khôi phục DNS trước; chỉ xóa task và dừng daemon khi mạng đã về bình thường."""
    log_path = ROOT / "data" / "turnoff.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)

    def log(text: str) -> None:
        with log_path.open("a", encoding="utf-8") as handle:
            handle.write(text + "\n")

    log("")
    log(f"=== turn off {time.strftime('%Y-%m-%d %H:%M:%S')} · {sys.executable} ===")
    print("Đang tắt bảo vệ…")
    subprocess.run(["schtasks", "/Change", "/TN", TASK_NAME, "/DISABLE"], capture_output=True)

    code = cmd_net_uninstall(args)
    store = Store(DB_PATH)
    try:
        still_blocking = store.get_state(NET_ENABLED_KEY) == "1"
    finally:
        store.close()
    if code != 0 or still_blocking:
        log(f"ABORT: net uninstall rc={code} still_blocking={still_blocking}; giữ daemon để không mất mạng")
        subprocess.run(["schtasks", "/Change", "/TN", TASK_NAME, "/ENABLE"], capture_output=True)
        print("Chưa tắt được: chặn web chưa khôi phục xong, daemon vẫn chạy để mạng không bị đứt.")
        print(f"Xem chi tiết: {log_path}")
        return 1
    log("net uninstall ok")

    cmd_uninstall(args)
    subprocess.run(
        ["netsh", "advfirewall", "firewall", "delete", "rule", f"name=CaiNghienPhone{phone_api.PHONE_PORT}"],
        capture_output=True,
    )
    pid = _daemon_pid()
    if pid is not None:
        try:
            process = psutil.Process(pid)
            process.terminate()
            process.wait(timeout=10)
            log(f"stopped daemon {pid}")
        except (psutil.Error, OSError) as exc:
            log(f"daemon stop: {exc}")
    LOCK_PATH.unlink(missing_ok=True)
    log("DONE")
    print("Đã tắt bảo vệ: task bị xóa, DNS khôi phục, daemon dừng.")
    return 0


def cmd_net_install(args) -> int:
    if os.name != "nt":
        print("Chỉ hỗ trợ Windows.")
        return 1
    if not net.is_admin():
        print("Cần quyền Administrator để đổi DNS và policy trình duyệt.")
        print("Mở PowerShell bằng quyền Administrator rồi chạy lại lệnh này.")
        return 1
    store = Store(DB_PATH)
    if store.get_state(NET_ENABLED_KEY) == "1":
        print(f"Chặn web đã bật. Muốn tắt: {CLI_HINT} net uninstall")
        return 0
    index = net.active_interface_index()
    if index is None:
        print("Không tìm thấy card mạng đang có internet.")
        return 1
    old_dns = net.get_dns(index)

    if _daemon_pid() is None:
        _start_daemon()
        for _ in range(10):
            if net.probe() is not None:
                break
            time.sleep(1)
    if net.probe() is None:
        _restart_daemon()
        for _ in range(10):
            if net.probe() is not None:
                break
            time.sleep(1)
    if net.probe() is None:
        store.event("net_error", "sinkhole không phản hồi khi install")
        print(f"Sinkhole không phản hồi cổng 53 — chưa đổi DNS. Xem: {CLI_HINT} log")
        return 1

    doh_backup = [
        {"key": key, "name": name, "previous": net.reg_get(key, name)}
        for key, name in net.DOH_POLICIES
    ]
    backup = {"interface_index": index, "v4": old_dns["v4"], "v6": old_dns["v6"], "doh": doh_backup}
    try:
        store.set_state(NET_BACKUP_KEY, json.dumps(backup, ensure_ascii=False))
        net.set_dns(index, ["127.0.0.1"], ["::1"])
        for key, name in net.DOH_POLICIES:
            net.reg_set(key, name, "off")
        net.flush_dns()
    except Exception as exc:
        try:
            net.set_dns(index, old_dns["v4"], old_dns["v6"])
        except Exception:
            pass
        store.del_state(NET_BACKUP_KEY)
        store.del_state(NET_ENABLED_KEY)
        print(f"Lỗi khi cấu hình, đã cố khôi phục DNS: {exc}")
        return 1

    store.set_state(NET_ENABLED_KEY, "1")
    store.event("net_install", f"adapter {index}: {old_dns['v4'] or ['DHCP']} → 127.0.0.1")
    task_result = _create_task(elevated=True)
    print("Đã bật chặn web.")
    print(f"  DNS adapter {index}: {', '.join(old_dns['v4']) or 'DHCP'} → 127.0.0.1 / ::1")
    print("  Chrome và Edge: tắt DoH bằng policy máy.")
    print("  Firefox: tự tắt Settings → Privacy & Security → DNS over HTTPS (nếu đang bật).")
    print(f"  Khởi động lại trình duyệt. Kiểm tra: {CLI_HINT} net status")
    if task_result.returncode == 0:
        print("  Đã cài task tự chạy quyền admin để tự sửa DNS khi đổi mạng (áp dụng từ lần daemon chạy kế tiếp).")
    else:
        print(f"  Chưa cài được task quyền admin; khi đổi mạng chạy: {CLI_HINT} net repair")
    if not approved_domain_count(store):
        print("  Cảnh báo: config chưa có domain nào trong [web] — chưa có gì bị chặn.")
    if _task_status().startswith("chưa cài"):
        print("  Cảnh báo: chưa cài task tự chạy; nếu daemon dừng, mạng sẽ mất cho tới khi daemon chạy lại.")
        print(f"  Nên chạy: {CLI_HINT} install")
    return 0


def cmd_net_uninstall(args) -> int:
    if os.name != "nt":
        print("Chỉ hỗ trợ Windows.")
        return 1
    if not net.is_admin():
        print("Cần quyền Administrator. Mở PowerShell bằng quyền Administrator rồi chạy lại.")
        return 1
    store = Store(DB_PATH)
    backup_text = store.get_state(NET_BACKUP_KEY)
    if backup_text:
        try:
            backup = json.loads(backup_text)
        except ValueError:
            backup = None
        if backup:
            try:
                net.set_dns(backup["interface_index"], backup.get("v4", []), backup.get("v6", []))
            except Exception as exc:
                print(f"Khôi phục DNS lỗi: {exc}")
            for item in backup.get("doh", []):
                try:
                    if item.get("previous") is None:
                        net.reg_delete(item["key"], item["name"])
                    else:
                        net.reg_set(item["key"], item["name"], item["previous"])
                except RuntimeError as exc:
                    print(f"Không khôi phục được policy {item.get('name')}: {exc}")
    else:
        print("Không có bản backup DNS — bỏ qua phần DNS.")
    store.del_state(NET_ENABLED_KEY)
    store.del_state(NET_BACKUP_KEY)
    store.event("net_uninstall", "")
    net.flush_dns()
    print("Đã tắt chặn web và khôi phục DNS.")
    return 0


def cmd_net_status(args) -> int:
    store = Store(DB_PATH)
    enabled = store.get_state(NET_ENABLED_KEY) == "1"
    approved_json = store.get_state(APPROVED_KEY)
    domains = len(Config.from_json(approved_json).web_domains) if approved_json else 0
    print(f"Trạng thái  : {'bật' if enabled else 'tắt'} · {domains} domain")
    print(f"Quyền admin : {'có' if net.is_admin() else 'không'}")
    try:
        index = net.active_interface_index()
        if index is None:
            print("Card mạng   : không tìm thấy card đang có internet")
        else:
            dns = net.get_dns(index)
            print(f"Card mạng   : {index} · DNS v4 {dns['v4'] or 'DHCP'} · v6 {dns['v6'] or 'trống'}")
    except RuntimeError as exc:
        print(f"Card mạng   : không đọc được ({exc})")
    if enabled:
        wiring = net.current_wiring()
        if wiring is not None:
            print(f"DNS khớp    : {'có' if wiring['loopback'] else 'KHÔNG (Admin: net repair)'}")
    reply = net.probe()
    if reply:
        print(f"Sinkhole    : phản hồi {reply}")
    else:
        hint = "daemon chưa chạy" if _daemon_pid() is None else f"xem: {CLI_HINT} log"
        print(f"Sinkhole    : không phản hồi ({hint})")
    for key, name in net.DOH_POLICIES:
        browser = "Chrome" if "Chrome" in key else "Edge"
        value = net.reg_get(key, name)
        print(f"DoH {browser:<8}: {value if value is not None else 'không đặt'}")
    return 0


def cmd_doctor(args) -> int:
    daemon = _daemon_status()
    store = Store(DB_PATH)
    try:
        approved_json = store.get_state(APPROVED_KEY)
        approved = Config.from_json(approved_json) if approved_json else None
        net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
        blocks = store.blocks_today()
    finally:
        store.close()
    if net.probe():
        sinkhole = "phản hồi"
    elif daemon.startswith("không"):
        sinkhole = "daemon chưa chạy"
    else:
        sinkhole = f"không phản hồi (xem: {CLI_HINT} log)"
    print(f"{'Chạy bằng':<12}: {'file exe đóng gói' if FROZEN else 'mã nguồn'}")
    print(f"{'File chạy':<12}: {sys.executable}")
    print(f"{'Dữ liệu':<12}: {DB_PATH}")
    if not FROZEN:
        windowless = _pythonw().name.lower() == "pythonw.exe"
        print(f"{'pythonw':<12}: {'có' if windowless else 'KHÔNG (task sẽ hiện cửa sổ)'}")
    print(f"Quyền admin : {'có' if net.is_admin() else 'không'}")
    print(f"Tự chạy     : {_task_status()}")
    print(f"Daemon      : {daemon}")
    print(f"Chặn web    : {'bật' if net_enabled else 'tắt'}")
    if net_enabled:
        wiring = net.current_wiring()
        if wiring is not None:
            print(f"DNS khớp    : {'có' if wiring['loopback'] else 'KHÔNG (Admin: net repair)'}")
    print(f"Sinkhole    : {sinkhole}")
    if approved is not None:
        print(
            f"Luật        : {len(approved.processes)} app · {len(approved.web_domains)} domain · "
            f"{approved.weekly_focus_minutes()} phút focus/tuần"
        )
        try:
            synced = load_config(CONFIG_PATH).to_dict() == approved.to_dict()
        except ConfigError:
            synced = False
        print(f"config.toml : {'khớp luật đang chạy' if synced else 'có thay đổi chưa apply'}")
    else:
        print(f"Luật        : chưa có (chạy: {CLI_HINT} apply)")
    print(f"Hôm nay     : {blocks} lần chặn")
    return 0


def _start_daemon() -> None:
    command = [sys.executable, "run"] if FROZEN else [str(_pythonw()), str(ROOT / "run_enforcer.pyw")]
    subprocess.Popen(
        command,
        cwd=str(ROOT),
        creationflags=getattr(subprocess, "DETACHED_PROCESS", 0),
    )


def _restart_daemon() -> None:
    pid = _daemon_pid()
    if pid is not None:
        try:
            psutil.Process(pid).terminate()
        except psutil.Error:
            pass
        for _ in range(20):
            if not psutil.pid_exists(pid):
                break
            time.sleep(0.25)
    _start_daemon()


def approved_domain_count(store: Store) -> int:
    approved_json = store.get_state(APPROVED_KEY)
    return len(Config.from_json(approved_json).web_domains) if approved_json else 0


def _focus_line(approved: Config, now: datetime) -> str:
    window = current_window(approved, now)
    if window is not None:
        return f"ĐANG FOCUS «{window.label}» đến {window_end(window, now):%H:%M}"
    upcoming = next_start(approved, now)
    if upcoming is None:
        return "ngoài giờ focus"
    return f"ngoài giờ focus · phiên kế {WEEKDAY_VI[upcoming.weekday()]} {upcoming:%H:%M}"


def _focus_text(approved: Config, session: sessions.Session | None, now: datetime) -> str:
    if session is not None:
        return f"ĐANG FOCUS «{session.label}» (phiên) đến {session.end:%H:%M}"
    return _focus_line(approved, now)


def _print_pairing(token: str) -> None:
    ip = phone_api.lan_ip()
    print(f"  URL   : http://{ip}:{phone_api.PHONE_PORT}")
    print(f"  Token : {token}")
    print("  Cùng Wi-Fi, tắt VPN trên điện thoại; nhập URL và token trong app.")
    print(f"  Nếu không kết nối được: PowerShell Admin → {CLI_HINT} phone firewall")


def _port_open(host: str, port: int, timeout: float = 0.5) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def _dns_health_watch(db_path: Path) -> None:
    failures = 0
    while True:
        time.sleep(60)
        store = None
        try:
            store = Store(db_path)
            if store.get_state(NET_ENABLED_KEY) != "1":
                failures = 0
                continue
            if net.probe() is not None:
                failures = 0
                continue
            failures += 1
            if failures < 2:
                continue
            store.event("net_watchdog", "DNS sinkhole không phản hồi — khởi động lại daemon")
            store.close()
            store = None
            if _task_status().startswith("có"):
                os._exit(1)
        except Exception:
            pass
        finally:
            if store is not None:
                store.close()


def _net_watch(db_path: Path) -> None:
    while True:
        time.sleep(300)
        store = None
        try:
            store = Store(db_path)
            if store.get_state(NET_ENABLED_KEY) != "1":
                continue
            wiring = net.current_wiring()
            if wiring is None or wiring["loopback"]:
                continue
            if net.is_admin():
                _repair_wiring(store, wiring)
            else:
                _warn_wiring(store)
        except Exception:
            pass
        finally:
            if store is not None:
                store.close()


def _repair_wiring(store: Store, wiring: dict) -> bool:
    backup_text = store.get_state(NET_BACKUP_KEY)
    try:
        backup = json.loads(backup_text) if backup_text else {}
    except ValueError:
        backup = {}
    if backup.get("interface_index") != wiring["index"]:
        backup = {
            "interface_index": wiring["index"],
            "v4": wiring["v4"],
            "v6": wiring["v6"],
            "doh": backup.get("doh", []),
        }
        store.set_state(NET_BACKUP_KEY, json.dumps(backup, ensure_ascii=False))
    try:
        net.set_dns(wiring["index"], ["127.0.0.1"], ["::1"])
    except Exception:
        _warn_wiring(store)
        return False
    net.flush_dns()
    store.event("net_repair", f"adapter {wiring['index']}")
    notify.toast_async("Đã khôi phục chặn web", "DNS được trỏ lại về sinkhole sau khi đổi mạng.")
    return True


def _warn_wiring(store: Store) -> None:
    last = store.get_state("net_warn_at")
    now = datetime.now()
    if last:
        try:
            if now - datetime.fromisoformat(last) < timedelta(hours=6):
                return
        except ValueError:
            pass
    store.set_state("net_warn_at", now.isoformat(timespec="seconds"))
    store.event("net_warning", "DNS không trỏ về 127.0.0.1; chạy net repair")
    notify.toast_async("Chặn web đang lệch DNS", f"Chạy PowerShell Admin: {CLI_HINT} net repair")


def _daemon_pid() -> int | None:
    try:
        pid = int(LOCK_PATH.read_text().strip().split(" ", 1)[0])
    except (OSError, ValueError):
        return None
    return pid if psutil.pid_exists(pid) else None


def _daemon_status() -> str:
    pid = _daemon_pid()
    if pid is not None:
        return f"đang chạy (PID {pid})"
    return "không chạy"


def _task_status() -> str:
    result = subprocess.run(["schtasks", "/Query", "/TN", TASK_NAME], capture_output=True)
    if result.returncode == 0:
        return f"có (task {TASK_NAME}, mỗi phút)"
    return f"chưa cài (chạy: {CLI_HINT} install)"


def _fmt_delta(delta: timedelta) -> str:
    total = int(max(delta.total_seconds(), 0))
    return f"{total // 3600}h{(total % 3600) // 60:02d}m"


def _pythonw() -> Path:
    candidate = Path(sys.executable).with_name("pythonw.exe")
    return candidate if candidate.exists() else Path(sys.executable)


def _prepare_streams() -> None:
    if sys.stdout is None or sys.stderr is None:
        sink = open(os.devnull, "w", encoding="utf-8")
        sys.stdout = sys.stdout or sink
        sys.stderr = sys.stderr or sink
    if os.name == "nt":
        try:
            import ctypes

            ctypes.windll.kernel32.SetConsoleOutputCP(65001)
            ctypes.windll.kernel32.SetConsoleCP(65001)
        except (AttributeError, OSError):
            pass
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def main(argv: list[str] | None = None) -> int:
    _prepare_streams()
    parser = argparse.ArgumentParser(prog="enforcer", description="Chặn cứng app/web gây xao nhãng trong giờ focus (Windows).")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("apply", help="áp dụng config.toml (thay đổi làm yếu luật phải chờ cooldown)").set_defaults(func=cmd_apply)
    sub.add_parser("run", help="chạy daemon ở foreground (dùng cho Task Scheduler)").set_defaults(func=cmd_run)
    sub.add_parser("status", help="xem trạng thái focus, luật, daemon, thay đổi đang chờ").set_defaults(func=cmd_status)
    sub.add_parser("today", help="tình hình hôm nay: focus, học, gym, chặn").set_defaults(func=cmd_today)
    sub.add_parser("review", help="bảng 7 ngày + việc cần làm tuần này").set_defaults(func=cmd_review)
    log = sub.add_parser("log", help="xem các sự kiện gần đây")
    log.add_argument("-n", "--limit", type=int, default=20, help="số sự kiện (mặc định 20)")
    log.set_defaults(func=cmd_log)
    check = sub.add_parser("checkin", help="ghi nhận học hoặc gym")
    check.add_argument("habit", choices=("study", "gym"))
    check.add_argument("minutes", nargs="?", type=int, default=None, help="số phút (học mặc định 30, gym mặc định 0)")
    check.add_argument("note", nargs="*", help="ghi chú")
    check.set_defaults(func=cmd_checkin)
    focus_parser = sub.add_parser("focus", help="phiên focus tự do, khóa app và web ngay")
    focus_sub = focus_parser.add_subparsers(dest="focus_command", required=True)
    focus_start = focus_sub.add_parser("start", help="bắt đầu phiên")
    focus_start.add_argument("minutes", type=int)
    focus_start.add_argument("label", nargs="*", help="tên phiên")
    focus_start.set_defaults(func=cmd_focus_start)
    focus_sub.add_parser("stop", help="yêu cầu dừng sớm (có trễ)").set_defaults(func=cmd_focus_stop)
    focus_sub.add_parser("cancel", help="hủy yêu cầu dừng").set_defaults(func=cmd_focus_cancel)
    focus_extend = focus_sub.add_parser("extend", help="cộng thêm phút")
    focus_extend.add_argument("minutes", type=int)
    focus_extend.set_defaults(func=cmd_focus_extend)
    focus_sub.add_parser("status", help="xem phiên đang chạy").set_defaults(func=cmd_focus_status)
    export_parser = sub.add_parser("export", help="xuất check-in và lịch sử ra JSON")
    export_parser.add_argument("--out", default=None, help="đường dẫn file (mặc định data/export-*.json)")
    export_parser.set_defaults(func=cmd_export)
    phone_parser = sub.add_parser("phone", help="đồng bộ app Android qua Wi-Fi")
    phone_sub = phone_parser.add_subparsers(dest="phone_command", required=True)
    phone_sub.add_parser("pair", help="tạo token và in URL").set_defaults(func=cmd_phone_pair)
    phone_sub.add_parser("rotate", help="đổi token").set_defaults(func=cmd_phone_rotate)
    phone_sub.add_parser("off", help="tắt đồng bộ, xóa token").set_defaults(func=cmd_phone_off)
    phone_sub.add_parser("status", help="xem URL, token, API").set_defaults(func=cmd_phone_status)
    phone_sub.add_parser("firewall", help="mở cổng firewall (cần admin)").set_defaults(func=cmd_phone_firewall)
    dash = sub.add_parser("dashboard", help="mở dashboard local")
    dash.add_argument("--port", type=int, default=8765)
    dash.add_argument("--no-open", action="store_true", help="không tự mở trình duyệt")
    dash.set_defaults(func=cmd_dashboard)
    desktop_parser = sub.add_parser("desktop", help="mở app desktop native (Qt)")
    desktop_parser.add_argument("--screenshot", default=None, help=argparse.SUPPRESS)
    desktop_parser.set_defaults(func=cmd_desktop)
    net_parser = sub.add_parser("net", help="chặn web theo domain qua DNS (cần admin)")
    net_sub = net_parser.add_subparsers(dest="net_command", required=True)
    net_sub.add_parser("install", help="bật chặn web, có backup DNS").set_defaults(func=cmd_net_install)
    net_sub.add_parser("uninstall", help="khôi phục DNS và tắt chặn web").set_defaults(func=cmd_net_uninstall)
    net_sub.add_parser("status", help="kiểm tra DNS, sinkhole, DoH").set_defaults(func=cmd_net_status)
    net_sub.add_parser("repair", help="trỏ lại DNS sau khi đổi mạng (cần admin)").set_defaults(func=cmd_net_repair)
    install_parser = sub.add_parser("install", help="tạo Scheduled Task: tự chạy + watchdog mỗi phút")
    install_parser.add_argument("--elevated", action="store_true", help="chạy daemon bằng quyền admin")
    install_parser.set_defaults(func=cmd_install)
    sub.add_parser("uninstall", help="xóa Scheduled Task").set_defaults(func=cmd_uninstall)
    sub.add_parser("turn-off", help="tắt hẳn bảo vệ: khôi phục DNS, xóa task, dừng daemon (cần admin)").set_defaults(
        func=cmd_turn_off
    )
    sub.add_parser("doctor", help="kiểm tra toàn bộ thiết lập").set_defaults(func=cmd_doctor)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
