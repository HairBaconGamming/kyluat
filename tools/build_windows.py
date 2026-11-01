"""Build bản Windows: exe một thư mục + bộ cài Inno Setup + số liệu để dán vào trang tải.

    python tools/build_windows.py            # build exe rồi compile bộ cài
    python tools/build_windows.py --skip-exe # chỉ compile bộ cài từ exe đã có
    python tools/build_windows.py --no-installer

Cần Inno Setup 6.7 trở lên cho bước compile; chưa có thì tool in ra lệnh cài và vẫn làm các bước khác.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "packaging" / "Kyluat.spec"
ISS = ROOT / "packaging" / "kyluat.iss"
VERSION_FILE = ROOT / "packaging" / "version.txt"
VERSION_CLI_FILE = ROOT / "packaging" / "version-cli.txt"
DIST_EXE_DIR = ROOT / "dist" / "Kyluat"
DIST_EXE = DIST_EXE_DIR / "Kyluat.exe"
ISCC_NAMES = [
    Path(p) for p in [os.environ.get("ISCC", "")] if p
] + [
    Path(r"C:/Program Files (x86)/Inno Setup 6/ISCC.exe"),
    Path(r"C:/Program Files/Inno Setup 6/ISCC.exe"),
    Path.home() / "AppData/Local/Programs/Inno Setup 6/ISCC.exe",
]


def version() -> str:
    found = []
    for path in (VERSION_FILE, VERSION_CLI_FILE):
        match = re.search(r"StringStruct\('FileVersion',\s*'([^']+)'\)", path.read_text(encoding="utf-8"))
        if not match:
            raise SystemExit(f"Không đọc được FileVersion trong {path}")
        found.append(match.group(1))
    if len(set(found)) > 1:
        raise SystemExit("Hai file version không trùng nhau: Kyluat.exe và Kyluat-cli.exe phải cùng số bản.")
    return found[0]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def report(label: str, path: Path) -> None:
    size = path.stat().st_size
    megabytes = f"{size / 1_000_000:.2f}".replace(".", ",")
    bytes_label = f"{size:,}".replace(",", ".")
    print(f"\n{label}")
    print(f"  file        {path.name}")
    print(f"  dung lượng  {size:,} bytes")
    print(f"  site.js     size: \"{megabytes} MB\" · bytes: \"{bytes_label}\" · sha256: \"{sha256(path)}\"")


def build_exe() -> None:
    print("== PyInstaller ==")
    subprocess.run(
        [sys.executable, "-m", "PyInstaller", str(SPEC), "--noconfirm", "--clean"],
        cwd=str(ROOT),
        check=True,
    )


def make_zip(app_version: str) -> Path:
    target = ROOT / "dist" / f"KYLUAT-windows-x64-v{app_version}.zip"
    target.unlink(missing_ok=True)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(DIST_EXE_DIR.rglob("*")):
            if not path.is_file() or path.name == "config.toml":
                continue
            if DIST_EXE_DIR / "data" in path.parents:
                continue
            archive.write(path, Path("Kyluat") / path.relative_to(DIST_EXE_DIR))
        archive.write(ROOT / "config.example.toml", "Kyluat/config.toml")
    return target


def find_iscc() -> Path | None:
    for candidate in ISCC_NAMES:
        if str(candidate) and candidate.exists():
            return candidate
    found = shutil.which("iscc")
    return Path(found) if found else None


def build_installer(app_version: str) -> Path | None:
    iscc = find_iscc()
    if iscc is None:
        print("\n!! Không thấy ISCC.exe (Inno Setup 6.7+). Bộ cài chưa được tạo.")
        print("   Cài cho riêng user:  winget install --id JRSoftware.InnoSetup --scope user")
        return None
    if not ISS.read_bytes().startswith(b"\xef\xbb\xbf"):
        raise SystemExit(f"{ISS} thiếu dấu BOM UTF-8: ISCC sẽ đọc sai dấu tiếng Việt trong script.")
    print(f"\n== Inno Setup ({iscc}) ==")
    defines = [
        f"/DMyAppVersion={app_version}",
        f"/DSourceDir={DIST_EXE_DIR}",
    ]
    vietnamese = iscc.parent / "Languages" / "Vietnamese.isl"
    if vietnamese.exists():
        defines.append("/DHasViIsl=1")
    else:
        print("   (không có tiếng Việt chính thức cho wizard, dùng bộ nhớ mặc định của Inno)")
    subprocess.run([str(iscc), *defines, str(ISS)], cwd=str(ROOT), check=True)
    output = ROOT / "dist" / f"KYLUAT-Setup-v{app_version}.exe"
    return output if output.exists() else None


def main() -> int:
    parser = argparse.ArgumentParser(description="Build exe Windows và bộ cài Kỷ luật.")
    parser.add_argument("--skip-exe", action="store_true", help="dùng dist/Kyluat đang có, không chạy PyInstaller")
    parser.add_argument("--no-installer", action="store_true", help="bỏ bước compile bộ cài")
    parser.add_argument("--no-zip", action="store_true", help="bỏ bản nén không cần cài đặt")
    args = parser.parse_args()

    if os.name != "nt":
        raise SystemExit("Tool chỉ chạy trên Windows.")

    app_version = version()
    print(f"Phiên bản Windows: {app_version}")

    if not args.skip_exe:
        build_exe()
    if not DIST_EXE.exists():
        raise SystemExit(f"Thiếu {DIST_EXE}. Bỏ --skip-exe để build lại exe.")

    if not args.no_zip:
        report(f"Bản nén (không cài đặt) v{app_version}", make_zip(app_version))

    if not args.no_installer:
        installer = build_installer(app_version)
        if installer is None:
            return 1
        report(f"Bộ cài v{app_version}", installer)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
