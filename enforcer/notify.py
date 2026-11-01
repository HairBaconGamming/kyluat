from __future__ import annotations

import os
import subprocess
import threading

from . import proc

TOAST_SCRIPT = r"""
$ErrorActionPreference = 'Stop'
[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
$template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
$texts = $template.GetElementsByTagName('text')
$texts.Item(0).AppendChild($template.CreateTextNode($env:CAINGHIEN_TITLE)) | Out-Null
$texts.Item(1).AppendChild($template.CreateTextNode($env:CAINGHIEN_MESSAGE)) | Out-Null
$toast = [Windows.UI.Notifications.ToastNotification]::new($template)
[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\WindowsPowerShell\v1.0\powershell.exe').Show($toast)
"""


def toast(title: str, message: str) -> bool:
    if os.name != "nt":
        return False
    env = dict(os.environ, CAINGHIEN_TITLE=title, CAINGHIEN_MESSAGE=message)
    try:
        result = proc.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", TOAST_SCRIPT],
            capture_output=True,
            timeout=15,
            env=env,
        )
        return result.returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def toast_async(title: str, message: str) -> None:
    threading.Thread(target=toast, args=(title, message), daemon=True).start()
