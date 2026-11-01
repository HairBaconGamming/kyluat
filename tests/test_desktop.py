import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import pytest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
pytest.importorskip("PySide6")

pytestmark = pytest.mark.skipif(sys.platform != "win32", reason="Windows only")


def test_icon_and_shortcut(tmp_path):
    from PySide6.QtWidgets import QApplication

    from enforcer import desktop

    QApplication.instance() or QApplication([sys.argv[0]])
    icon = desktop.write_ico(tmp_path / "app.ico")
    assert icon.read_bytes()[:4] == b"\x00\x00\x01\x00"

    link = desktop.create_shortcut(icon, tmp_path)
    assert link.exists()
    assert link.name == "Kỷ luật.lnk"

    readable = Path(tempfile.gettempdir()) / f"cainghien-test-{os.getpid()}.lnk"
    shutil.copy2(link, readable)
    try:
        env = dict(os.environ, CAINGHIEN_LNK=str(readable))
        result = subprocess.run(
            [
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "$ws=New-Object -ComObject WScript.Shell; "
                "$s=$ws.CreateShortcut($env:CAINGHIEN_LNK); "
                "Write-Output $s.Arguments",
            ],
            capture_output=True,
            env=env,
        )
        text = (result.stdout or result.stderr).decode("utf-8", "replace")
        assert "run_desktop.pyw" in text
    finally:
        readable.unlink(missing_ok=True)
