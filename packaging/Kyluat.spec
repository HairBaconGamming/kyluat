# -*- mode: python ; coding: utf-8 -*-
"""Build bản Windows tải về được: pyinstaller packaging/Kyluat.spec"""

from pathlib import Path

ROOT = Path(SPECPATH).resolve().parent

a = Analysis(
    [str(ROOT / "packaging" / "kyluat.py")],
    pathex=[str(ROOT)],
    binaries=[],
    datas=[(str(ROOT / "enforcer" / "static"), "static")],
    hiddenimports=[
        "enforcer.catalog",
        "enforcer.cli",
        "enforcer.config",
        "enforcer.config_writer",
        "enforcer.dashboard",
        "enforcer.desktop",
        "enforcer.discovery",
        "enforcer.guard",
        "enforcer.habits",
        "enforcer.net",
        "enforcer.notify",
        "enforcer.pairing",
        "enforcer.phone_api",
        "enforcer.rules",
        "enforcer.schedule",
        "enforcer.sessions",
        "enforcer.store",
        "dnslib",
        "psutil",
        "segno",
        "tomlkit",
        "zeroconf",
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=["tkinter", "PySide6.QtTest", "PySide6.QtPrintSupport"],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="Kyluat",
    debug=False,
    strip=False,
    upx=False,
    console=True,
    icon=str(ROOT / "enforcer" / "static" / "app.ico"),
)

coll = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name="Kyluat")
