from __future__ import annotations

import fnmatch
import json
import os
import time
from datetime import datetime
from pathlib import Path

import psutil

from . import notify, sessions
from .config import Config, load_plan, load_reminders
from .net import flush_dns
from .schedule import current_window, window_end
from .store import APPROVED_KEY, PENDING_KEY, Store


def acquire_lock(path: Path) -> bool:
    if path.exists():
        pid = created = None
        try:
            pid_text, _, created_text = path.read_text().strip().partition(" ")
            pid = int(pid_text)
            created = float(created_text) if created_text else None
        except (OSError, ValueError):
            pid = created = None
        if pid and psutil.pid_exists(pid):
            try:
                proc = psutil.Process(pid)
                same_start = created is None or abs(proc.create_time() - created) < 2
                if (proc.name() or "").lower().startswith("python") and same_start:
                    return False
            except psutil.Error:
                pass
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"{os.getpid()} {psutil.Process().create_time():.0f}")
    return True


def release_lock(path: Path) -> None:
    try:
        if path.exists() and path.read_text().strip().split(" ", 1)[0] == str(os.getpid()):
            path.unlink()
    except OSError:
        pass


class Guard:
    def __init__(self, store: Store, config: Config, config_path: Path):
        self.store = store
        self.config = config
        self.config_path = config_path
        self._logged_at: dict[str, float] = {}
        self._last_window: str | None = None
        self._last_reminder_check = 0.0
        self.focus_active = False
        self._visual = None
        self._visual_error_at = 0.0

    def run(self) -> None:
        last_refresh = 0.0
        while True:
            now = datetime.now()
            if time.monotonic() - last_refresh >= 5:
                self._refresh_rules()
                last_refresh = time.monotonic()
            if time.monotonic() - self._last_reminder_check >= 30:
                self._check_reminders(now)
                self._last_reminder_check = time.monotonic()
            state = self._focus_state(now)
            label = state[0] if state is not None else None
            self.focus_active = state is not None
            if label != self._last_window:
                self._last_window = label
                self.store.event("focus_on" if state is not None else "focus_off", label or "")
                flush_dns()
                if state is not None:
                    notify.toast_async(
                        "Bắt đầu phiên focus",
                        f"«{label}» đến {state[1]:%H:%M}. App gây xao nhãng đã bị khóa.",
                    )
                else:
                    notify.toast_async(
                        "Hết phiên focus",
                        "Ghi nhận buổi học: python -m enforcer checkin study <số phút>.",
                    )
            if state is not None:
                self._enforce(label)
            self._check_visual()
            time.sleep(self.config.poll_seconds)

    def _focus_state(self, now: datetime) -> tuple[str, datetime] | None:
        session = sessions.current(self.store, now)
        if session is not None:
            return session.label, session.end
        window = current_window(self.config, now)
        if window is not None:
            return window.label, window_end(window, now)
        return None

    def _check_reminders(self, now: datetime) -> None:
        reminders = load_reminders(self.config_path)
        if not reminders.enabled:
            return
        today = now.date().isoformat()
        if self.store.get_state("last_study_reminder") == today:
            return
        if now.time() < reminders.study_check_time:
            return
        plan = load_plan(self.config_path)
        minutes = self.store.minutes_by_day("study", today).get(today, 0)
        if minutes >= plan.study_minutes_daily:
            return
        remaining = max(0, plan.study_minutes_daily - minutes)
        notify.toast_async(
            "Chưa đủ giờ học",
            f"Hôm nay {minutes}/{plan.study_minutes_daily} phút. Còn {remaining} phút để đạt mục tiêu.",
        )
        self.store.set_state("last_study_reminder", today)
        self.store.event("reminder", f"học {minutes}/{plan.study_minutes_daily} phút")

    def _refresh_rules(self) -> None:
        self._apply_due_pending()
        approved_json = self.store.get_state(APPROVED_KEY)
        if not approved_json:
            return
        try:
            new_config = Config.from_json(approved_json)
        except (ValueError, KeyError, TypeError):
            return
        if new_config != self.config:
            self.config = new_config
            print(
                f"[luật mới] {len(new_config.processes)} tiến trình chặn, "
                f"{new_config.weekly_focus_minutes()} phút/tuần",
                flush=True,
            )

    def _apply_due_pending(self) -> None:
        text = self.store.get_state(PENDING_KEY)
        if not text:
            return
        try:
            pending = json.loads(text)
            effective_at = datetime.fromisoformat(pending["effective_at"])
            new_config = Config.from_dict(pending["config"])
        except (ValueError, KeyError, TypeError):
            self.store.del_state(PENDING_KEY)
            return
        if datetime.now() < effective_at:
            return
        self.store.set_state(APPROVED_KEY, new_config.to_json())
        self.store.del_state(PENDING_KEY)
        self.store.event("apply_pending", f"{new_config.weekly_focus_minutes()} phút/tuần")

    def _enforce(self, label: str) -> None:
        patterns = self.config.blocked_patterns()
        for proc in psutil.process_iter(["pid", "name"]):
            if proc.info["pid"] == os.getpid():
                continue
            name = proc.info["name"] or ""
            lowered = name.lower()
            if not any(fnmatch.fnmatch(lowered, pattern) for pattern in patterns):
                continue
            self._kill(proc, name, label)

    def _kill(self, proc: psutil.Process, name: str, label: str) -> None:
        pid = proc.pid
        try:
            proc.terminate()
            try:
                proc.wait(timeout=2)
            except psutil.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=2)
        except psutil.NoSuchProcess:
            return
        except psutil.AccessDenied:
            self._log_once(name, f"{name} — không đủ quyền để tắt (chạy enforcer bằng quyền admin?)")
            return
        self._log_once(name, f"{name} · {label} · pid {pid}")

    def _check_visual(self) -> None:
        if not self.config.adult_visual:
            if self._visual is not None:
                self._visual.close()
                self._visual = None
            return
        if self._visual is None:
            if time.monotonic() < self._visual_error_at:
                return
            try:
                from .visual import VisualGuard

                self._visual = VisualGuard()
            except Exception as error:
                self._visual_error_at = time.monotonic() + 600
                self.store.event("visual_error", str(error)[:200])
                return
        try:
            if self._visual.check():
                self.store.event("adult_visual", "thu nhỏ cửa sổ trình duyệt")
        except Exception as error:
            self.store.event("visual_error", str(error)[:200])
            try:
                self._visual.close()
            except Exception:
                pass
            self._visual = None
            self._visual_error_at = time.monotonic() + 600

    def _log_once(self, key: str, message: str) -> None:
        current = time.monotonic()
        if current - self._logged_at.get(key, 0.0) < 60:
            return
        self._logged_at[key] = current
        self.store.event("block", message)
        print(f"[chặn] {message}", flush=True)
