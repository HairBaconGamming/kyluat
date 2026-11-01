from datetime import time
from pathlib import Path

from enforcer import config_writer
from enforcer.config import Config, FocusWindow, Plan, Reminders, load_config, load_plan, load_reminders


def test_write_rules_roundtrip(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text(
        "# giữ comment\n[general]\npoll_seconds = 3\ncooldown_hours = 24\nsession_stop_delay_minutes = 10\n"
        "[block]\nprocesses = [\"old.exe\"]\nkill_browsers = false\n"
        "[web]\ndomains = [\"old.com\"]\n"
        "[[focus]]\nlabel = \"Cũ\"\ndays = [\"mon\"]\nstart = \"08:00\"\nend = \"09:00\"\n",
        encoding="utf-8",
    )
    config = Config(
        poll_seconds=3,
        cooldown_hours=24,
        kill_browsers=False,
        processes=("steam.exe", "discord.exe"),
        web_domains=("youtube.com",),
        windows=(FocusWindow((0, 1), time(9, 0), time(11, 0), "Học"),),
        session_stop_delay_minutes=10,
    )
    config_writer.write_rules(path, config)
    loaded = load_config(path)
    assert loaded.processes == config.processes
    assert loaded.web_domains == config.web_domains
    assert loaded.windows == config.windows
    assert "# giữ comment" in path.read_text(encoding="utf-8")


def test_write_plan_and_reminders(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text("[general]\npoll_seconds = 3\n[focus]\n", encoding="utf-8")
    config_writer.write_plan(path, Plan(study_minutes_daily=90, gym_sessions_weekly=5))
    config_writer.write_reminders(path, Reminders(enabled=False, study_check_time=time(20, 15)))
    plan = load_plan(path)
    reminders = load_reminders(path)
    assert plan.study_minutes_daily == 90
    assert plan.gym_sessions_weekly == 5
    assert reminders.enabled is False
    assert reminders.study_check_time.strftime("%H:%M") == "20:15"
