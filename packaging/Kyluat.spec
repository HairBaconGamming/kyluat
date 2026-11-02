# -*- mode: python ; coding: utf-8 -*-
"""Build bản Windows tải về được: pyinstaller packaging/Kyluat.spec"""

from pathlib import Path

from PyInstaller.utils.hooks import collect_all

ROOT = Path(SPECPATH).resolve().parent

litert_datas, litert_binaries, litert_hidden = collect_all("ai_edge_litert")
# WebGPU (31 MB), compiler plugin và công cụ dòng lệnh không dùng cho suy luận CPU.
_skip = ("libLiteRtWebGpuAccelerator", "tools\\", "tools/", "vendors\\", "vendors/")
litert_datas = [item for item in litert_datas if not any(part in item[0] for part in _skip)]
litert_binaries = [item for item in litert_binaries if not any(part in item[0] for part in _skip)]

a = Analysis(
    [str(ROOT / "packaging" / "kyluat.py")],
    pathex=[str(ROOT)],
    binaries=litert_binaries,
    datas=[
        (str(ROOT / "enforcer" / "static"), "static"),
        (str(ROOT / "enforcer" / "data" / "adult_domains.txt"), "enforcer/data"),
        (str(ROOT / "enforcer" / "data" / "nsfw_model.tflite"), "enforcer/data"),
        (str(ROOT / "enforcer" / "data" / "NOTICE-nsfw-model.txt"), "enforcer/data"),
        *litert_datas,
    ],
    hiddenimports=[
        "enforcer.adult_list",
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
        "enforcer.proc",
        "enforcer.rules",
        "enforcer.schedule",
        "enforcer.sessions",
        "enforcer.store",
        "enforcer.visual",
        "PIL.ImageGrab",
        *litert_hidden,
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

# Kyluat.exe là app GUI nên build kiểu windowed: Windows không cấp console, không có
# cửa sổ đen nhấp nháy lúc mở app hay lúc task mỗi phút đánh thức daemon.
# Muốn gõ lệnh thì dùng Kyluat-cli.exe (subsystem console) — giống cặp pythonw/python.
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="Kyluat",
    debug=False,
    strip=False,
    upx=False,
    console=False,
    version=str(ROOT / "packaging" / "version.txt"),
    icon=str(ROOT / "enforcer" / "static" / "app.ico"),
)

cli_exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="Kyluat-cli",
    debug=False,
    strip=False,
    upx=False,
    console=True,
    version=str(ROOT / "packaging" / "version-cli.txt"),
    icon=str(ROOT / "enforcer" / "static" / "app.ico"),
)

coll = COLLECT(exe, cli_exe, a.binaries, a.datas, strip=False, upx=False, name="Kyluat")
